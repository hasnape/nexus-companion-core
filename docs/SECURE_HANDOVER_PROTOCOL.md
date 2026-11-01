# Nexus Companion — Secure Handover Protocol (SHP) V1

Status: design specification only; no runtime implementation or security certification.
Scope: Nexus Companion Core only. Nexus Support Hub is out of scope.

## Foundational invariants

- A companion instance has a stable, unique `companionId`, a name, a persistent identity and a personality independent of its current human owner.
- **Handover is not cloning or factory reset.** Handover preserves `companionId`, companion name, personality and non-personal learned capabilities. It changes the authorized owner and isolates the former owner's private information.
- Cloning the application to another machine creates a **new** companion identity and an empty owner-private memory. Never clone owner credentials, tokens, personal conversations or private memory by default.
- A creator/developer mode is distinct from ownership. The UI creator code `0410` is **not** an owner credential, a secret security boundary or a recovery bypass.
- Every operation must remain local-first and possible offline. No internet connection is required for handover.
- Voice commands only **request** handover; speech recognition and voice similarity are not authentication.

## Initial owner enrollment

During first meeting, ask for a preferred name, presentation/voice settings, consent for memory, and a local owner credential. Explain privacy and recovery in plain language. Allow conversational use without optional memory, camera or microphone permissions. Store credential verifiers using a vetted password KDF (Argon2id where available, otherwise a documented vetted alternative); never store raw passwords or recovery secrets. Provide a recovery mechanism at enrollment, not a universal creator backdoor. A browser-only app cannot claim hardware-backed credential security.

## Handover state machine

`idle -> requested -> current_owner_verified -> scope_reviewed -> new_owner_enrollment -> final_confirmation -> committed`

Cancellation or timeout before commit returns to `idle` without modifying owner rights. Errors during commit must preserve the previous valid owner and avoid mixed ownership states.

1. Current human says “Alif, mode passation” or selects the corresponding UI action.
2. Companion describes consequences, asks for current owner's password in a private input (never spoken aloud, logged, echoed or sent to external services), and enforces rate limits.
3. Companion asks whether the transfer is intended and final; shows explicit choices for former owner's personal memory: delete or export encrypted archive under former owner's exclusive control. An archive must not be readable by the new owner.
4. Future human receives a privacy explanation and enrolls a **new** owner credential, with recovery setup. Avoid exposing former owner's information during this phase.
5. Both parties confirm the precise action. No silent handover, no creator-code override.
6. Commit a durable, recoverable transition: re-key or isolate owner-private stores, revoke old owner sessions/tokens, bind the new owner, persist an audit record with minimal metadata, and confirm success **only after** durable commit.
7. Start a new first-meeting conversation for the new human without changing companion identity or personality.

## Memory separation

- **Companion identity:** immutable `companionId`, persistent name and personality profile; survives handover.
- **General knowledge and skills:** transferable only after classification as non-personal. Derived summaries must be checked for personal-data leakage; do not assume model weights, embeddings or summaries are automatically anonymous.
- **Owner-private memory:** episodic memories, personal preferences, conversations, linked accounts, voice/camera data, access tokens and location history. Must not be accessible to a new owner.
- **Shared/consented memories:** explicitly reviewed and individually authorized where appropriate. Default is **not transferred**.
- **Handover audit:** minimal timestamp, companion ID, transition outcome and privacy-safe identifiers. Never store passwords or raw conversations in audit logs.

## Security and reliability acceptance criteria

- Unauthorized voice command, incorrect password and creator mode cannot change ownership.
- Failed, interrupted or concurrent handovers do not leave split-brain ownership or expose private data.
- Old owner credentials/sessions cannot authenticate after a successful handover.
- New owner cannot read former owner's private memory, archives, secrets or external integrations.
- Companion name, identity and personality remain unchanged across successful handover.
- Clone/installation produces a new identity and does not copy personal data.
- Offline handover works without cloud dependency.
- Tests cover consent, cancellation, timeouts, repeated attempts, crash/restart recovery, migration compatibility and rollback.
- Implementation must provide clear privacy notices and age-appropriate handling for children.

## Implementation boundaries

Implement as separate modules for companion identity, owner authentication, memory classification/isolation and handover orchestration. Define versioned local storage schemas and migrations. Prefer established cryptographic libraries over custom crypto. Require automated tests, lint, typecheck and build before merging; do not claim this document itself enforces security.

## Related first-awakening requirements

On a **new** instance, allow choosing a masculine, feminine or neutral/custom presentation independently of name, voice and visual appearance. Optional online first-run synchronization may check signed software updates and dated world/regional/local context from identified sources, with consent and no automatic execution of untrusted code. Failure to connect must not block local-first companionship. Distinguish software updates from knowledge refresh; preserve rollback and existing functionality.
