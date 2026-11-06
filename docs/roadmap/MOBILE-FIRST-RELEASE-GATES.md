# Nexus Companion — mobile-first release plan

Status: active development priority. Applies to the entire Nexus Companion family.

## Rule of sequencing

Do not add further robot-specific features until the mobile release gates below are met. Existing shared robot contracts and dock simulator remain inert, isolated and tested. Browser face-web is a demo and compatibility target, not proof of native Android offline capability.

## Gate M1 — stable mobile face and interaction

- [ ] Validate production face UI on representative Android Chrome devices (portrait/landscape, reduced motion, foreground/background restoration).
- [ ] Confirm typed conversation works locally with airplane mode enabled, including graceful failure and explicit retry of queued network requests.
- [ ] No regression in identity isolation, face states, accessibility or local data persistence.
- [ ] Document exact test devices, OS versions, evidence and unresolved failures.

## Gate M2 — dependable native Android voice

- [ ] Record Android/browser baseline: SpeechRecognition can start and stop without producing transcripts; mic permission alone is not proof of recognition.
- [ ] Choose an Android-native local speech-to-text engine and an on-device wake-phrase engine after benchmarking French accuracy, latency, battery, size, licensing and offline support.
- [ ] Microphone use is consent-based, visible and cancellable. Any background listening requires explicit opt-in and compliance with Android foreground-service restrictions.
- [ ] Verify wake → command → response → return-to-wake, interruptions, Bluetooth headsets, denied permissions and airplane mode on real devices.
- [ ] No ambient audio/transcript retention by default.

## Gate M3 — local cognitive autonomy

- [ ] Core inference, fallback behavior, safety policies and companion identity work without mandatory cloud calls.
- [ ] Per-companion memory is separated and persisted locally; encrypted backup and restore are verified across devices and incompatible versions fail safely.
- [ ] Optional network/cloud capability is transparent and never silently becomes the only working brain.

## Gate M4 — installable Android product

- [ ] Choose and document native host architecture (e.g. Kotlin host around portable core) based on measured Android requirements, not just PWA installation.
- [ ] Signed installable Android build, permission UX, upgrade/migration tests, recovery after app termination and resource profiling.
- [ ] Clear acceptance tests for offline voice, face, memory, security, accessibility and battery/thermal behavior.
- [ ] Device-based release verification with documented pass/fail evidence.

## Gate R — robot integration (blocked until M1–M4)

- Head face display; dedicated Android brain in front torso with outward-facing rear cameras; optional ventilated mini-PC in back; safety microcontroller.
- Hardware pairing, authenticated transport and safety interlocks must be implemented and verified before any actuator can be enabled.
- Robot must never be required for Nexus to work as a mobile companion.

## Change-control rules

For every implementation PR: small scoped patch, deterministic tests, lint/typecheck/build, production regression check where relevant, and explicit **verified** vs **not yet verified** statements. Never equate a green Vercel build with a successful real-device Android test.
