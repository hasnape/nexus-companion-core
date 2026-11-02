# Nexus Companion — Android native foundation (alpha)

This is a **new, native Android application**, not a WebView/PWA. It has no INTERNET permission, and the microphone is enabled only after the user taps the button and grants RECORD_AUDIO. The microphone stops when the Activity leaves the foreground.

## Implemented in this first slice
- Native Kotlin Activity with simple placeholder face, text input and offline deterministic greeting responses.
- Android on-device SpeechRecognizer (API 31+) only; explicitly **no network recognizer fallback**.
- Wake phrase “Nexus”, a separate next-command turn, bounded restart attempts, and microphone suspension while the companion speaks.
- Android TextToSpeech restricted to a **locally installed French voice** (network-only voices rejected).
- No background recording, telemetry, network permission, cloud account or silent startup.

## Not yet implemented — do not describe this alpha as a complete companion
- Shared TypeScript cognitive engine and existing SVG avatar are **not yet embedded or bridged**. The current native face and local replies are placeholders.
- The current Android on-device SpeechRecognizer still uses Android's finite recognition sessions; it is **not a continuous local wake-word DSP engine**. Need a dedicated bundled on-device wake-word engine (e.g. verified Vosk/Picovoice alternatives after licensing and benchmarking) and foreground microphone service only if user explicitly opts in.
- No bundled ASR model: if the phone lacks an installed on-device recognizer, speech is unavailable; text still works. A distributable offline ASR model is a subsequent task.
- No encrypted portable brain store, no companion migration/import/export, no face phoneme lip-sync, no signed APK/AAB, no device tests.
- A local LLM inference engine and model packaging (e.g. llama.cpp) need memory, licensing, battery and performance evaluation; the deterministic response adapter does not pretend to be an LLM.

## Build
Open `apps/companion-android` in Android Studio with JDK 17, Android SDK 35 and Android Gradle Plugin 8.6.1. Run the `app` configuration on Android 12+ (API 31+).

The project does not commit generated Gradle wrapper binaries. To use CLI builds, generate a Gradle wrapper using a trusted installed Gradle distribution, then run `./gradlew assembleDebug`.

## Next milestones
1. Define a versioned, platform-neutral brain protocol; migrate actual existing companion-core decision/memory logic without reimplementing it inconsistently in Kotlin. Bundle the engine as a local asset and use an explicit bridge, or port to a portable runtime.
2. Port existing avatar visuals and real audio boundary events; use native audio amplitude/viseme signals where available.
3. Bundle and benchmark a fully offline ASR model and wake detector in French; separate consented foreground/background policies.
4. Add per-companion encrypted local persistence and export/import with schema migrations and authenticated backups.
5. Test airplane mode, screen lock, microphone denial, voice pack missing, phone calls/audio focus, lifecycle, low battery and multiple Android devices. Produce a signed, reproducible APK only after build/device validation.

## Security
No INTERNET permission by design. Do not add network access, background microphone service, or sensor capture without explicit product requirements and opt-in. No user speech transcripts are logged or persisted in this alpha.
