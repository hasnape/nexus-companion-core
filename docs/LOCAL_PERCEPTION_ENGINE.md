# Nexus Companion — Local Perception Engine V1

Status: architectural foundation implemented as TypeScript contracts and unit test scenarios; actual inference engines are NOT integrated or validated. Scope: Nexus Companion Core only.

## Goal

Create Nexus-owned, interchangeable speech and visual perception components for Alif and later companions. Process data locally whenever possible, offline after approved model installation, without hidden capture, biometric tracking or cloud dependency. The application owns the permissions, orchestration, observations and retention policy; third-party open-source engines are replaceable adapters, not the companion's identity or brain.

## Open-source reference candidates (reviewed 2026-11-01)

- [ggml-org/whisper.cpp](https://github.com/ggml-org/whisper.cpp): local speech-to-text; C/C++, WASM browser example, MIT project license. Browser inference may be resource-heavy; benchmark low-end mobile and verify model licenses.
- [alphacep/vosk-api](https://github.com/alphacep/vosk-api): offline streaming speech recognition, multiple languages, Apache-2.0 project license. Evaluate mobile/browser packaging and language-specific model licenses separately.
- [google-ai-edge/mediapipe](https://github.com/google-ai-edge/mediapipe): on-device face detection, object detection, gesture and pose models with web task APIs. Distinguish **face detection** (a face is present) from **identity recognition** (who the person is). Review runtime metrics/telemetry and package/model licensing; do not claim zero network calls without verification.
- Evaluate sherpa-onnx and ONNX Runtime Web as alternatives before selecting the long-term runtime; pin versions, verify provenance, licenses, integrity and hardware requirements.

No automatic adoption of the newest version. Prefer a vetted compatible version, test and staged upgrades.

## Scope and privacy

- Speech: opt-in microphone capture, voice activity detection, local transcription and command routing; no passive recording by default. Wake phrase may be locally detected only when the user explicitly enables a visible listening session.
- Vision: opt-in camera capture, local object/scene/gesture detection, face **presence** detection. No covert filming, emotion inference presented as fact, or automatic face identity database.
- Speaker or face biometric verification is a **separate**, high-risk, opt-in future feature, never sufficient alone for ownership transfer or password recovery.
- The creator code does not override privacy or owner access control.
- No retention of raw audio/video by default; store derived observations only when specifically consented and useful, with TTL and deletion controls.
- No sharing between cloned companions or between former and new owners.
- For children, default to conservative data minimization and explicit guardian-controlled permissions where required.

## Implementation phases

1. **Foundation (this branch):** capability types, consent checks, local-only enforcement, cancellation, deterministic unit tests. No permissions are requested, no camera/microphone opened, no models downloaded.
2. **Speech adapter:** select and benchmark a version-pinned open-source local model, load via a dedicated worker, integrate with existing voice hook behind feature flag. Preserve current wake/manual stop/restart behavior. Handle cancellation and late getUserMedia resolution.
3. **Vision adapter:** user-initiated camera session and a pinned on-device object detector; show clear camera-on indicator, release tracks on stop/unmount, throttle inference and support reduced-power mode.
4. **Cognitive integration:** map observations to minimal, provenance-tagged, confidence-scored events. Never treat model output as verified personal identity. Memory write requires separate consent.
5. **Portability:** package models offline per platform with integrity checks, storage quotas and rollback strategy. Browser PWA and future native/robot runtimes may use different adapters.

## Release gates

- Validate model license, model weights license, runtime telemetry, dependency security and data flow.
- Unit tests for denied permissions, modality mismatch, offline-only enforcement, cancellation, dispose and stale async completions.
- Integration tests for permissions revoked during processing, tab backgrounding, offline start, no-network verification and hardware failure.
- Accessibility, French-first language support, mobile CPU/memory/battery benchmarking, FR/EN/AR speech quality where model coverage permits.
- `npm run lint`, `npm run typecheck`, `npm run test`, `npm run build` and browser permission checks before merging.
- Mark all unexecuted checks as unverified. Never claim the engine is functional until an actual local model runs end-to-end.

## Current files

- `apps/face-web/src/services/perception/localPerception.ts`
- `apps/face-web/src/services/perception/localPerception.test.ts`

This branch is deliberately additive and not wired into the existing UI or voice flow.
