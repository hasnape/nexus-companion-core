// The deterministic engine remains available on 32-bit Android.
extern "C" int nexus_local_llm_requires_64_bit() { return 1; }
