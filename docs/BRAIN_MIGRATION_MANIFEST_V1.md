# Portable brain manifest V1 — preflight only

Scope: all individuals of the **Nexus Companion** family, not just Alif.

This stage adds a pure, offline metadata-only inventory and a strict import dry-run. It does **not** transport, restore, encrypt, authenticate, sign, or move memories. No UI, sensor, network access, credentials, biometric templates, or live device state are accessed. It does not create a clone or mutate a companion identity.

`createBrainMigrationManifest(graph, snapshotId, generatedAt)` returns a versioned manifest with the companion ID, snapshot ID, creation time, public/general node and edge counts, and cognitive regions. Only explicitly `companion-general` nodes contribute to counts; private knowledge, labels, owner identifiers, edges and raw content are not included. Counts themselves can reveal information (rough activity levels), so treat the manifest as owner-controlled, not public telemetry.

`inspectBrainMigration(input, destination)` validates untrusted metadata and checks target companion identity, target occupation, source active/unknown status, and clock drift. The success result means **metadata compatibility only**, not permission to install or migrate a brain. The input `sourceState` is caller-supplied and must be independently verified by a future authenticated transfer orchestrator.

## Safe path to real brain export/import

1. Bind family-level software to per-companion durable storage, keyed by immutable companion ID; create new IDs for new installations, not for moves. Validate owner-private partitions separately.
2. Add reviewed provenance, sensitive-content classification, retention and conflict resolution; general scope alone is not de-identification.
3. Review threat model and design offline owner authentication, encryption, key storage, recovery and authenticated metadata integrity.
4. Implement versioned encrypted full-state packages, offline transactional restore and rollback, with explicit source-device retirement after destination integrity verification.
5. Test crash/retry recovery, duplicate identity/clone conflicts, attacker-crafted import payloads, wrong-owner access, large graphs and cross-version migrations on actual devices.

Never treat the metadata-only manifest as an actual backup, proof of source deactivation, cryptographic anti-cloning mechanism, or an authorized transfer. GitHub Actions may be disabled; validate manually in an isolated environment with lint, strict TypeScript, tests and build before production integration.
