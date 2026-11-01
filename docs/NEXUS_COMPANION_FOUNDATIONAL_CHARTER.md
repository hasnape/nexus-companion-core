# Nexus Companion — Foundational Product Charter V1

Status: approved product direction, not proof of implementation. Date: 2026-11-01.
Scope: `nexus-companion-core` only. Never modify Nexus Support Hub as part of this work.
Companion family name: **Nexus Companion**. First intended individual companion: **Alif**.

## Purpose

Nexus Companion is a long-lived personal digital companion that gets to know its human friend progressively, starting in the first minutes of their first meeting. The long-term ambition is a local-first autonomous cognitive system capable of reasoning, learning, consolidating knowledge and developing useful competencies within explicit safety boundaries, without continuous creator intervention. It is not a claim of sentience, human emotions, or present-day general intelligence.

A companion must remain useful without Internet. The local cognitive core owns identity, personality, decision rules, safety, and private memory. Online services are optional, permissioned external sources, never the sole brain.

## Family, individuality and human relationship

- Each instance is an individual with a unique persistent `companionId`, a chosen given name, the shared family name Nexus Companion, an independent personality history and an owner relationship.
- Alif is the first planned individual. Future installations may have different names, voices, presentation and human friends.
- A companion has one current authorized primary human owner/friend at a time. Other people can converse with it under appropriate permissions, but do not gain ownership by talking to it.
- Loyalty means care, reliability, privacy and respect for the human's agency. It must not encourage isolation, dependency or replacement of human relationships.
- Identity, personality and learned non-personal skills survive handover. Owner-private memory, credentials and integrations must not leak to the next owner.
- Installing or duplicating software creates a **new** companion identity with fresh private storage. Handover transfers the **existing** individual; it is not a clone, reset or rename.
- Changing owner does not reset or automatically rename the companion. Renaming, if supported, must be a separate explicit owner-authorized action, not part of handover.

## First Awakening: the first launch

The first activation is a conversational encounter, not a compulsory long form. The companion introduces its actual abilities and limitations, then gets to know the human gradually.

Suggested versioned state machine:
`uninitialized -> integrity_check -> optional_update_check -> optional_world_context -> companion_introduction -> human_introduction -> consent -> owner_enrollment -> acquaintance -> active`.

Requirements:
1. Verify local application/schema integrity and initialize a unique companion identity.
2. Offer masculine, feminine or neutral/custom presentation. Keep pronouns/presentation, given name, voice and visual appearance independently configurable. No assumption that voice determines gender.
3. Check for official software updates **if online and authorized**. Only accept trusted signed versions compatible with the installation. Use a tested release pipeline, backup/migration plan and rollback. Do not run downloaded code merely because a remote source suggests it. Browser PWA update capabilities differ from native/robot hardware; do not promise OS-level rollback in a browser.
4. Optionally synchronize current date/time, timezone and world/national/regional/local context from identified dated sources. Ask before using precise location; manual region entry must work. Distinguish verified current facts, uncertain external claims and stale cached knowledge. Set expiry/revalidation rules.
5. Never block offline use if update, time or context sync fails. State what was actually checked and when. Never claim updates or tests ran unless they did.
6. Introduce companion given name and Nexus Companion family; ask human preferred name/nickname and language.
7. Explain memory, microphone, camera and location permissions **separately**, with opt-in, revocation and ability to converse without optional permissions.
8. Invite one or two light interests/questions. Do not interrogate the user or collect unnecessary sensitive information.
9. Establish owner authentication and recovery without equating speech or creator mode to owner identity.
10. Persist progress locally, resume safely after interruption and avoid duplicate enrollment or accidental overwrites.

Example first greeting: “Bonjour, je m'appelle Alif, de la famille Nexus Companion. C'est notre première rencontre. Comment aimerais-tu que je t'appelle ?”

## Cognitive learning and memory

Use separable, versioned local modules for:
- **Episodic memory:** user-consented events and experiences, timestamped and scoped to the owner.
- **Semantic knowledge:** general facts with source, provenance, confidence, freshness and contradiction handling.
- **Preferences and habits:** explicitly provided or carefully inferred with confidence, editable and revocable.
- **Project context:** tasks, goals and decisions with relevant scope and expiry.
- **Environmental context:** only authorized signals, no hidden monitoring.
- **Consolidation:** importance, relevance, confidence, repetition and conflict checks.
- **Forgetting/expiry:** retention limits, deletion and owner controls.
- **Competency development:** bounded experiments, tests and feedback before promoting a learned skill.

Learning must not silently expand permissions, execute untrusted instructions, or promote personal information into shared transferable knowledge. Provide explainability and human review for important conclusions.

## Human interaction and future embodiment

Start with a browser-safe animated face, expressive attention, accessible reduced-motion animations, text conversation and opt-in voice. Later hardware may add sensors, movable head and a robotic body through capability-gated adapters. Camera/microphone activation must be visible and consent-based. A robot must have separate physical safety controls; never treat a chat instruction as unconditional permission for physical action.

## Creator versus owner

The creator identity (engineering reference: `ingénieur Amine 0410`) is distinct from the owner of each companion. The discreet five-tap face gesture and creator code `0410` are a **UI discovery mechanism only**, not secure authentication. Do not use them to bypass owner credentials, decrypt memories or authorize handover. Prefer a single discreet creator entry point; privileged operations require real access control.

## Handover

See [SECURE_HANDOVER_PROTOCOL.md](./SECURE_HANDOVER_PROTOCOL.md). Owner says “mode passation” to **request** the process; current owner then authenticates privately, answers confirmation/privacy questions, and the next human enrolls new credentials. The companion keeps its name, identity, personality and non-personal competencies; private data stays isolated. Handover is offline-capable, recoverable, auditable and explicitly confirmed.

## Professional engineering rules

- Long-term, maintainable, scalable, privacy-safe architecture over patches or quick hacks.
- Small, conservative, scoped changes; preserve existing validated behavior and avoid regressions.
- Audit first; distinguish verified observations from hypotheses and untested assumptions.
- Isolated branches and reviewed pull requests; no unverified production merges.
- Lint, typecheck, unit/integration/regression tests, build and relevant accessibility/privacy checks. State which tests actually ran and their results.
- Validate desktop/mobile and offline/online behavior where applicable. Never misrepresent browser-only capabilities as native capabilities.
- Keep a per-feature record of files changed, evidence, known risks and outstanding checks.
- Keep Nexus Support Hub and other projects completely untouched by this development.

## Proposed modular architecture (design, not existing code claims)

`companion-identity`, `first-awakening`, `owner-auth`, `human-bond`, `secure-handover`, `local-cognition`, `memory`, `world-context`, `update-manager`, and capability-gated UI/voice/sensor adapters.

Implement incrementally with versioned interfaces, local migrations, backward compatibility and regression tests. This charter defines requirements; it does not assert the modules already exist.
