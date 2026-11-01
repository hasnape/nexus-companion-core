# Nexus Companion — Portable Individual Brain Architecture

Status: accepted architectural requirement, initial pure cognitive graph foundation only. 2026-11-01.

## Product scope: the entire Nexus Companion family

**Nexus Companion is the product family and reusable cognitive architecture. Alif is only the first individual instance, not the architecture itself.** Every installation creates a distinct companion with its own immutable companion ID, owner relationship, personality, private and general memories, knowledge graph, learned connections, skills and development history. No individual companion's identity or memory may be hardcoded into shared cognitive modules. Shared software versions, brain regions, adapters, schemas, migration procedures and safety policies belong to the family platform; individual cognitive state belongs to one companion. New companions start with the shared capabilities and independent state, not a copy of Alif's personal history.

**Design invariant:** family-level code and models are reusable across all companions; companion-level data is explicitly keyed by companion ID, and owner-private data is further scoped by authorized owner. Cross-companion knowledge transfer must be explicit, consented, provenance-aware and privacy-reviewed; it must never silently copy personal memories.

## Identity and portability

Each Nexus Companion individual has its **own** individual software identity, personality, memory, learned knowledge, skills and configuration. The owner can move the **same individual** from a phone/PC to a different computer or future robot using a secure, versioned brain package. A move differs from installing a new companion (new identity) and from owner handover (same companion, different authorized human).

An exportable brain package is not simply a ZIP of unencrypted browser storage. The future package needs a versioned manifest, companion ID, compatible software/runtime metadata, model references and license information, encrypted owner-private memories and secrets handled separately, integrity checks, owner authorization, migrations, backup and recovery. Never include plaintext credentials, biometric voiceprints, third-party tokens or private memories by default.

For a **move**, transfer encrypted state with explicit owner authentication, verify on destination, then revoke/deactivate the source instance to prevent two independently active companions with the same identity. If source cannot be deactivated, require an explicit recovery/clone conflict process and warn about divergent state. Offline migration should be supported. Never claim cryptographic anti-cloning guarantees without secure hardware or trusted infrastructure.

For a **new installation**, generate a new ID and independent memory. For **handover**, keep the same ID, name, personality and general skills while isolating former owner's private information.

## Functional regions inspired by the human brain

The brain analogy informs modular architecture but does not imply biological neurons or consciousness:

| Inspiration | Proposed software module |
|---|---|
| Sensory processing | Perception adapters: audio, vision, later robot sensors |
| Attention and working memory | Prioritize inputs, manage short-term context |
| Hippocampal-like episodic functions | Store/retrieve consented events with time and context |
| Temporal/language functions | Speech, semantic knowledge and dialogue |
| Frontal/executive functions | Planning, goals, decisions and permissions |
| Procedural/basal ganglia analogy | Learned skills and tested routines |
| Social/context functions | Human relationship, preferences and situational etiquette |
| Safety/regulation | Permission gating, privacy, uncertainty, action constraints |
| Consolidation | Link, strengthen, correct, expire and forget knowledge |

## Connections and plasticity

Represent associations as a local typed graph: nodes carry provenance, region, confidence, privacy scope and owner binding; edges carry relation, bounded weight and update time. Reinforcement and decay must be driven by explicit evidence and configurable retention, not unbounded self-modification. Contradictions trigger review rather than overwriting facts blindly.

A graph connection must not cross owner privacy boundaries. General knowledge export excludes private nodes and all edges referencing them. **Important:** excluding private nodes alone is not sufficient to guarantee de-identification of derived general knowledge; add review/classification and tests before real export.

## Phased delivery

1. Pure graph model and privacy boundary tests (initial branch).
2. Add provenance, knowledge validation, contradiction and forgetting policy.
3. Implement versioned persistent encrypted storage and owner-authenticated access.
4. Add cognitive consolidation and retrieval, with bounded weight updates and auditable provenance.
5. Implement portable brain manifest and dry-run import compatibility checks.
6. Implement encrypted export/import with owner authentication, backups, atomic destination restore and source deactivation.
7. Integrate learning, multimodal observations and hardware-specific adapters.
8. Benchmark memory scale, migration failures, recovery, offline behavior and privacy leakage.

## Security and engineering gates

Do not export or import active private data before a reviewed threat model, authentication, encryption/key management and migration design. All releases require actual automated tests, lint, typecheck, build and offline/device verification. Keep Nexus Support Hub untouched.

## Current implementation

`apps/face-web/src/services/cognition/cognitiveGraph.ts` defines pure node/edge contracts and public graph filtering. This is **not** a functioning biological brain, neural network, long-term persistent memory or export/import implementation. The test file defines regression scenarios; execution has not yet been verified.
