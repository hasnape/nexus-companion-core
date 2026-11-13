#include <jni.h>
#include "llama.h"
#include <algorithm>
#include <atomic>
#include <chrono>
#include <memory>
#include <mutex>
#include <string>
#include <unordered_map>
#include <vector>

namespace {
struct Session {
    std::atomic<bool> cancelled{false};
    std::chrono::steady_clock::time_point deadline;
};
std::mutex registry_mutex;
std::unordered_map<jlong, std::shared_ptr<Session>> sessions;
std::atomic<jlong> sequence{1};
std::once_flag backend;
std::shared_ptr<Session> find(jlong handle) {
    std::lock_guard<std::mutex> lock(registry_mutex);
    auto i = sessions.find(handle);
    return i == sessions.end() ? nullptr : i->second;
}
bool abort_generation(void * data) {
    auto * session = static_cast<Session *>(data);
    return session->cancelled.load() || std::chrono::steady_clock::now() >= session->deadline;
}
bool load_progress(float, void * data) { return !abort_generation(data); }
std::string from_java(JNIEnv * env, jstring text) {
    // Java's modified UTF-8 is not standard UTF-8; convert via String.getBytes("UTF-8").
    jclass cls = env->FindClass("java/lang/String");
    jmethodID get = env->GetMethodID(cls, "getBytes", "(Ljava/lang/String;)[B");
    jstring encoding = env->NewStringUTF("UTF-8");
    auto bytes = static_cast<jbyteArray>(env->CallObjectMethod(text, get, encoding));
    if (!bytes || env->ExceptionCheck()) return {};
    std::string result(env->GetArrayLength(bytes), '\0');
    env->GetByteArrayRegion(bytes, 0, result.size(), reinterpret_cast<jbyte *>(result.data()));
    env->DeleteLocalRef(bytes); env->DeleteLocalRef(encoding); env->DeleteLocalRef(cls);
    return result;
}
jstring to_java(JNIEnv * env, const std::string & text) {
    jclass cls = env->FindClass("java/lang/String");
    jmethodID constructor = env->GetMethodID(cls, "<init>", "([BLjava/lang/String;)V");
    auto bytes = env->NewByteArray(text.size());
    env->SetByteArrayRegion(bytes, 0, text.size(), reinterpret_cast<const jbyte *>(text.data()));
    jstring encoding = env->NewStringUTF("UTF-8");
    auto result = static_cast<jstring>(env->NewObject(cls, constructor, bytes, encoding));
    env->DeleteLocalRef(bytes); env->DeleteLocalRef(encoding); env->DeleteLocalRef(cls);
    return result;
}
std::string infer(const std::shared_ptr<Session> & session, const std::string & path, const std::string & question, int threads) {
    if (!session || abort_generation(session.get()) || question.empty() || question.size() > 2400) return {};
    std::call_once(backend, [] { llama_backend_init(); });
    auto mp = llama_model_default_params();
    mp.n_gpu_layers = 0;
    mp.progress_callback = load_progress; mp.progress_callback_user_data = session.get();
    std::unique_ptr<llama_model, decltype(&llama_model_free)> model(llama_model_load_from_file(path.c_str(), mp), llama_model_free);
    if (!model || abort_generation(session.get())) return {};
    auto cp = llama_context_default_params();
    cp.n_ctx = 1024; cp.n_batch = 128; cp.n_ubatch = 128;
    cp.n_threads = threads; cp.n_threads_batch = threads;
    cp.abort_callback = abort_generation; cp.abort_callback_data = session.get();
    std::unique_ptr<llama_context, decltype(&llama_free)> context(llama_init_from_model(model.get(), cp), llama_free);
    if (!context) return {};
    const char * system = "Tu es Nexus Companion, un assistant francophone. Réponds en français en une ou deux phrases courtes. "
        "Réponds uniquement à la question de culture générale. Si tu ne sais pas, dis-le. N'invente pas de source. "
        "Tu n'as accès ni à Internet ni aux informations personnelles. N'écris ni commande, ni balise, ni raisonnement interne.";
    llama_chat_message messages[] = {{"system", system}, {"user", question.c_str()}};
    const char * templ = llama_model_chat_template(model.get(), nullptr);
    std::vector<char> formatted(16000);
    int32_t length = llama_chat_apply_template(templ, messages, 2, true, formatted.data(), formatted.size());
    if (length <= 0 || static_cast<size_t>(length) > formatted.size()) return {};
    const llama_vocab * vocab = llama_model_get_vocab(model.get());
    std::vector<llama_token> tokens(1024);
    int count = llama_tokenize(vocab, formatted.data(), length, tokens.data(), tokens.size(), true, true);
    if (count <= 0 || count + 96 >= 1024) return {};
    for (int i = 0; i < count; i += 128) {
        int n = std::min(128, count - i);
        if (abort_generation(session.get()) || llama_decode(context.get(), llama_batch_get_one(tokens.data() + i, n))) return {};
    }
    auto params = llama_sampler_chain_default_params();
    std::unique_ptr<llama_sampler, decltype(&llama_sampler_free)> sampler(llama_sampler_chain_init(params), llama_sampler_free);
    llama_sampler_chain_add(sampler.get(), llama_sampler_init_greedy());
    std::string answer;
    for (int i = 0; i < 96 && answer.size() < 7000; ++i) {
        if (abort_generation(session.get())) return {};
        llama_token token = llama_sampler_sample(sampler.get(), context.get(), -1);
        if (llama_vocab_is_eog(vocab, token)) return answer;
        std::vector<char> piece(256);
        int n = llama_token_to_piece(vocab, token, piece.data(), piece.size(), 0, false);
        if (n < 0) { piece.resize(-n); n = llama_token_to_piece(vocab, token, piece.data(), piece.size(), 0, false); }
        if (n < 0) return {};
        answer.append(piece.data(), n);
        if (llama_decode(context.get(), llama_batch_get_one(&token, 1))) return {};
    }
    return answer;
}
}
extern "C" JNIEXPORT jlong JNICALL Java_fr_nexuscompanion_android_NativeLocalLlm_create(JNIEnv *, jobject) {
    try { auto s = std::make_shared<Session>(); const auto id = sequence.fetch_add(1);
        std::lock_guard<std::mutex> lock(registry_mutex); sessions[id] = s; return id;
    } catch (...) { return 0; }
}
extern "C" JNIEXPORT jstring JNICALL Java_fr_nexuscompanion_android_NativeLocalLlm_generate(JNIEnv * env, jobject, jlong id, jstring path, jstring question, jint threads, jint timeout) {
    try { auto session = find(id); if (!session) return to_java(env, "");
        session->deadline = std::chrono::steady_clock::now() + std::chrono::milliseconds(std::max(100, std::min(20000, timeout)));
        return to_java(env, infer(session, from_java(env, path), from_java(env, question), std::max(1, std::min(4, threads))));
    } catch (...) { return to_java(env, ""); }
}
extern "C" JNIEXPORT void JNICALL Java_fr_nexuscompanion_android_NativeLocalLlm_cancel(JNIEnv *, jobject, jlong id) { auto session = find(id); if (session) session->cancelled.store(true); }
extern "C" JNIEXPORT void JNICALL Java_fr_nexuscompanion_android_NativeLocalLlm_release(JNIEnv *, jobject, jlong id) {
    std::lock_guard<std::mutex> lock(registry_mutex); auto i = sessions.find(id);
    if (i != sessions.end()) { i->second->cancelled.store(true); sessions.erase(i); }
}
