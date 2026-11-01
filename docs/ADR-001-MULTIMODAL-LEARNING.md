# Nexus Companion — Multimodal Learning Architecture Decision (ADR-001)

Status: accepted design direction, 2026-11-01. Scope: nexus-companion-core only.

## Decision

**Alif is multimodal by design from the beginning.** Text-first guided-learning domain logic is a testing and implementation seam, **not** a product restriction or a commitment to text-only learning. Develop local speech, local vision, and guided learning as coordinated workstreams with stable contracts and progressive integration.

### Conceptual pipeline

1. Explicitly authorized microphone, camera or text input produces observations.
2. Replaceable offline-capable perception adapters interpret sound, speech, objects, scenes or gestures; annotate outputs with modality, model provenance, confidence and timestamp.
3. The local cognitive core proposes tentative interpretations rather than asserting uncertain recognition as fact.
4. The human friend confirms, corrects or rejects a lesson.
5. The local memory layer classifies owner-private versus non-personal general knowledge, applies consent and retention rules, and stores only what is authorized.
6. Alif can retrieve, revisit and revise confirmed knowledge over time.

Example: The human shows a cat. Alif asks whether it is a cat. The human confirms and names it Minou. General knowledge about cats may be retained separately from the owner-private fact that this human has a cat called Minou.

## Parallel engineering tracks

- **Voice:** evaluate pinned, licensed offline STT/VAD models; integrate behind a feature flag without breaking existing wake/manual-stop behavior.
- **Vision:** evaluate pinned, licensed on-device object/scene detection; camera only in visible opt-in sessions, release streams on stop.
- **Learning:** develop human-confirmed lesson lifecycle, corrections, confidence, memory provenance and forget controls.
- **Integration:** unify observations via typed interfaces; never let perception automatically bypass confirmation or consent.
- **Security:** owner voiceprint is a separate optional authentication capability; no face/voice recognition of people by default, no covert recording, no biometric-only handover.

## Release gates

Offline operation, permission revocation, mobile CPU/battery constraints, accessibility, language support, privacy isolation, test execution and review. Separate prototype status from validated end-to-end capabilities. No changes to Nexus Support Hub.

## Relationship to current PRs

- PR #36: local speech/vision engine-neutral contracts, not actual inference.
- PR #39: text-first guided lesson domain, not a text-only product.
- PR #37: master development roadmap.
- PR #38: companion identity foundation.

This ADR is documentation only and does not assert that multimodal inference already works.
