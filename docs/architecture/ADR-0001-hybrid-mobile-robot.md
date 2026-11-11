# Nexus Companion — architecture hybride mobile / robot (ADR-0001)

Status: proposed. Scope: the whole Nexus Companion family, not a single persona.

## Physical topology

- **Head:** dedicated display renders the face UI; no personal Android notifications are mirrored.
- **Front torso:** removable, dedicated Android smartphone. Rear cameras face outward through a suitable protective optical window. Phone remains the primary identity and memory host.
- **Back:** ventilated mini-PC, optional accelerator for compute-intensive local inference. A disconnected or unavailable mini-PC must not prevent the phone from running the companion's core functions.
- **Locomotion:** three wheels, with two lateral traction wheels and a central steering wheel configurable at the front or rear; no walking legs. The native virtual body implements the planning stage described in [ADR-0003](ADR-0003-wheeled-body-and-native-memory.md).
- **Robot chassis:** separate real-time microcontroller for motor control, feedback, power management and safety.
- **Power:** robot battery, protected power rails and charging design are separate from the phone battery; thermal and electrical design must be validated before assembly.

## Responsibilities and trust boundaries

1. **Companion core (portable):** persona identity, local memory, cognition, consent, and policy; versioned export/import with integrity verification and encrypted backups.
2. **Android host:** OS permissions, audio input, wake phrase, speech-to-text, text-to-speech, camera capture, GPS when authorized, and discovery of dock capabilities. Never silently start environmental monitoring.
3. **Compute node (mini-PC):** optional local model inference. Requests are bounded and cancellable. The phone owns orchestration and decides whether a result can affect its memory or actions.
4. **Robot controller:** validates bounded, high-level motion requests and enforces speed, torque, joint and workspace limits. Local emergency stop, heartbeat expiry and loss-of-link safe state must work without Android or the mini-PC.
5. **Face renderer:** consumes only explicit face-state events; can render on phone, head display or a browser independently of the core.

## Versioned interface contract (design, not yet implemented)

Every command or event has: protocolVersion, messageId, companionId, sessionId, monotonic sequence, timestamp, messageType, and bounded payload. A negotiated capability manifest advertises available cameras, microphones, display, motion groups, battery and accelerator. Unknown capabilities must be ignored safely. Reject stale, duplicated, unauthorized or out-of-range commands.

- phone ↔ mini-PC: mutually authenticated encrypted channel; optional USB networking, otherwise secured local networking; no direct cloud dependency.
- phone ↔ controller: authenticated, bounded command protocol over a suitable physical transport; safety controller remains authoritative.
- phone → head display: face-state messages, not a full-screen mirror by default.
- docking: explicit paired-device authorization, capability negotiation, health checks and then safe enablement; undocking stops robot motion before disconnect.
- safety: no locomotion or arm movement enabled until physical E-stop, watchdog and hardware tests pass.

## Privacy and observability

Microphone/camera activation is explicit, visibly indicated and revocable. Always-on wake listening is an opt-in future native feature, local by default and subject to OS constraints. Raw ambient audio/video is not retained or transmitted by default. Logs record state transitions and error codes, never transcripts, images or biometric identifiers. User-initiated export must support moving each companion's own memory without cross-contaminating other companion identities.

## Implementation stages

1. Extract platform-neutral companion contracts and adapters; keep face-web working unchanged.
2. Build Android native host for on-device speech, wake detection, permissions, camera and screen output.
3. Build head display renderer and dock detection, with a simulation-only robot capability manifest.
4. Add optional mini-PC inference adapter with offline fallback.
5. Add real robot controller only after mechanical, electrical and emergency-stop verification.

## Non-goals for this ADR

No hardware purchase recommendation, autonomous navigation, face recognition, hidden surveillance, automatic motor control, or claims that browser speech recognition can run in the background.
