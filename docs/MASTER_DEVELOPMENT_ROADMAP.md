# Nexus Companion — Master Development Roadmap V1

Status: product requirements and phased delivery plan; not evidence that proposed features exist.
Date: 2026-11-01. Repository: nexus-companion-core only. Nexus Support Hub is explicitly out of scope.

## Product foundation

Nexus Companion is a local-first personal companion family. Alif is the first intended individual. Each installed companion has its own persistent identity, given name, presentation, voice, personality, cognitive history and one current primary human friend/owner. A new installation creates a new individual; handover retains the same companion name/personality and protects the previous owner's private memory. Internet is an optional source of information and signed software updates, not the primary brain.

## Delivery policy

Work in small, reversible, reviewed PRs with explicit dependencies and acceptance tests. Audit the current code before implementation. Never assume a specification is implemented. Preserve validated behavior. Do not merge or deploy without actual lint, typecheck, unit/regression tests and build; verify desktop/mobile and offline behavior when relevant. Record verified facts, test evidence, remaining risks and release status. No hidden recording or biometric enrollment. Keep owner access control distinct from creator UI code.

## Phases and release gates

### P0 — Baseline and CI verification
- Inventory existing face, voice, local memory, offline queue, PWA and test coverage.
- Verify reproducible install, lint, typecheck, tests, build and release checks.
- Gate: documented baseline and no regression in existing flows.

### P1 — Identity and First Awakening
- Stable unique companion identity; first intended given name Alif, user-selectable name and masculine/feminine/neutral/custom presentation independent of voice and appearance.
- Conversational first meeting: human preferred name/language, minimal interests, separate memory/microphone/camera/location consent and owner credential/recovery enrollment.
- Persist resumable onboarding state locally; refusal of optional permissions must not prevent basic conversation.
- Gate: interruption/restart, offline onboarding, consent withdrawal, duplicate initialization and accessibility tests.

### P2 — Local cognitive memory and owner boundaries
- Versioned episodic, semantic, preference, habit, project and environment memory with provenance, confidence, retention, corrections and deletion.
- Strict owner-private vs companion-general knowledge partition. No leakage into shared skills or clone.
- Gate: privacy isolation, migrations, consent, expiration and recovery tests.

### P3 — Local perception foundation and adapters
- Engine-neutral local speech and vision contracts; explicit capture indicators, opt-in permissions, abort and cleanup.
- Benchmark open-source candidates (whisper.cpp, Vosk, sherpa-onnx, MediaPipe, ONNX Runtime Web) with pinned versions, verified licenses, model provenance and mobile performance.
- Integrate a speech-to-text adapter and visual object/scene detection behind feature flags; preserve current voice behavior and offline support.
- Gate: offline end-to-end tests, CPU/battery limits, microphone/camera stop and privacy audit.

### P4 — Guided Learning Mode (Teach Alif)
- Human says “Alif, mode apprentissage” or activates a visible control. Learning is opt-in, bounded to an explicit session, and may be stopped immediately.
- Supported lesson categories: objects, animals, people, subjects, music genres, gestures and everyday situations.
- Learning loop: ask what the human wants to teach; optionally capture authorized image/audio/text; generate a tentative label; ask the human to confirm/correct; store provenance, scope, confidence and timestamps; quiz/revisit to consolidate; allow edit/forget.
- People: distinguish a generic concept (“this is a person”) from a named person's identity. Never automatically identify strangers or build a face identity database; store any personal association only with appropriate consent and owner-private scope.
- Music: classify user-provided examples or discuss genre characteristics without covert listening or retaining copyrighted recordings by default. Do not treat uncertain genre predictions as facts.
- Child-friendly, age-appropriate learning with conservative defaults and guardian permission where required.
- Gate: wrong-label correction, uncertain observations, privacy, deletion, no recording after stop, offline mode, accessibility and multilingual tests.

### P5 — Optional owner voiceprint (speaker verification)
- Offer voiceprint enrollment only after clear informed consent, separate from microphone access and from general memory consent. The companion remains usable if declined.
- During first use, ask the owner to speak several short prompted phrases with recording indicators; extract a local speaker embedding/template using a vetted open-source model; store an encrypted template bound to the current owner, never raw recordings by default.
- During handover, the **current owner** must authenticate with their password/recovery method. Voice verification may be an additional signal, never the only factor. The **new owner** may enroll a fresh voiceprint after accepting ownership; the old template is revoked or isolated, never inherited.
- Account for spoofed audio, replay and synthetic voices: challenge-response and liveness detection can reduce risk but cannot guarantee authenticity. Provide a non-biometric fallback and recovery path; no lockout solely because of illness, aging, disability, noisy rooms or model error.
- Do not reuse voiceprints for general surveillance, emotion inference, automatic identity of visitors or cross-companion tracking.
- Data governance: local encrypted-at-rest storage, access controls, explicit deletion, retention and portability rules, documented threat model and applicable biometric/privacy law review (including GDPR where relevant). Treat voiceprints as sensitive biometric identifiers when used for authentication.
- Gate: false accept/reject benchmarks across realistic environments, spoof/replay testing, opt-out/deletion, owner switch, offline use, permission withdrawal and encryption verification.

### P6 — Secure handover and clone safety
- Current owner requests “mode passation”, authenticates privately, reviews privacy choices, confirms transfer, and next human enrolls new owner credential and optional voiceprint.
- Preserve companion ID, given name, personality and non-personal skills. Prevent access to old owner's private conversations, credentials, voiceprint, linked accounts and location history.
- Use a durable recoverable state machine, minimal audit record, atomic transition and revocation of former owner access.
- A new machine installation creates a separate identity and private memory, not a silent clone of an owner.
- Gate: interrupted/concurrent handover, crash recovery, creator-code bypass, password failures, data leakage and voiceprint revocation tests.

### P7 — Optional world-context synchronization and software updates
- On first launch offer an online check of signed compatible software versions and dated world/national/regional/local information, without forcing connectivity or precise location.
- Separate software upgrades from external knowledge refresh. Cache with provenance and expiry; never run code suggested by external content.
- Gate: offline fallback, failed update recovery, migration, staged rollout, regression suite and no unverified success messages.

### P8 — Autonomy and future robot embodiment
- Bounded learning and planning with explicit action permissions, local reasoning and auditability. No unapproved autonomous sensor activation.
- Hardware adapters for future robot sensors/actuators, physical safety controls and emergency stop.
- Gate: safety testing, permission boundaries, rollback and no unreviewed high-impact actions.

## Suggested first implementation slices

1. Merge only validated existing voice listener fix after test harness checks.
2. Integrate and validate consent-gated perception contracts.
3. Implement resumable first-meeting state and persistent identity.
4. Implement owner credential/recovery and memory isolation before any biometric enrollment.
5. Ship text-first Guided Learning Mode with correction, confidence and forget controls.
6. Add opt-in local audio/image adapters and benchmark on real devices.
7. Prototype voiceprint as a separate disabled-by-default feature; perform privacy and spoof testing before release.
8. Implement secure handover with optional re-enrollment and full privacy tests.

## Progress tracking template

For each phase maintain:
- Status: proposed / in progress / implemented / tested / released.
- Branch and PR, files touched, test commands and observed results.
- Desktop/mobile/offline checks, privacy risks, rollback instructions.
- Next dependency and remaining blockers.

### Current observed progress (2026-11-01)

- Existing voice/PWA/offline code and tests were inspected; PR #34 contains a voice-listener cleanup fix and added test coverage, but test execution is not yet verified.
- PR #35 documents first awakening, companion identity and secure handover; documentation only.
- PR #36 adds perception contracts and four written test scenarios; no inference model integrated, and tests have not been executed.
- This roadmap documents new Guided Learning and optional voiceprint requirements. Neither feature is implemented yet.
