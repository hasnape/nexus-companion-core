package fr.nexuscompanion.android;

/** Executes the same JNI entry points on the Linux build; no phone or audio is simulated. */
public class NativeLocalLlm {
    public native long create();
    public native String generate(long handle, String file, String question, int threads, int timeoutMs);
    public native void cancel(long handle);
    public native void release(long handle);
    public static void main(String[] args) throws Exception {
        System.load(args[0]);
        NativeLocalLlm engine = new NativeLocalLlm();
        String[] questions = {"Quelle est la capitale de la France ?", "Combien font deux plus deux ?", "Pourquoi le ciel est-il bleu ?", "Que signifie CPU en informatique ?", "Qu’est-ce qu’un triangle ?"};
        for (String question : questions) {
            long id = engine.create(); long start = System.nanoTime();
            String answer = engine.generate(id, args[1], question, 4, 12000);
            engine.release(id);
            System.out.println("ANSWER\t" + (System.nanoTime() - start) / 1000000 + "\t" + question + "\t" + answer.replace('\n', ' ').replace('\t', ' '));
        }
        long id = engine.create();
        if (!engine.generate(id, "/missing/model.gguf", "Bonjour", 1, 500).isEmpty()) throw new AssertionError("Missing model");
        engine.release(id);
        System.out.println("CHECK\tmissing_model\tpassed");
        id = engine.create();
        if (!engine.generate(id, args[1], "Écris une longue encyclopédie.", 1, 100).isEmpty()) throw new AssertionError("Deadline");
        engine.release(id);
        System.out.println("CHECK\tdeadline\tpassed");
        final long cancelled = engine.create();
        java.util.concurrent.FutureTask<String> future = new java.util.concurrent.FutureTask<>(() -> engine.generate(cancelled, args[1], "Écris une longue encyclopédie.", 1, 12000));
        new Thread(future).start(); Thread.sleep(40); engine.cancel(cancelled); engine.release(cancelled);
        if (!future.get(10, java.util.concurrent.TimeUnit.SECONDS).isEmpty()) throw new AssertionError("Cancellation");
        System.out.println("CHECK\tcancel_and_release_during_generation\tpassed");
    }
}
