# Individual companion local storage: opt-in factory (V3)

`createCompanionScopedMemoryStore(companionId, storage?)` is an additive API. It gives every Nexus Companion instance its own memory key and brain-state key in a shared KeyValueStorage backend. This helps prevent accidental data mixing between individuals such as Alif and Nora when running on the same browser origin.

**Not yet wired into the face-web runtime.** The current runtime still uses the unscoped V2 key. Until first-awakening creates and persists verified immutable companion IDs, switching to V3 without user-visible migration would make existing memories seem to disappear.

## Security boundary

This factory is **logical namespace separation only**, not an authenticated multi-user boundary, per-owner private partition, encryption or a secure brain backup. All JavaScript on the same origin with storage access can access the keys. The browser storage adapter must be selected explicitly by the future identity-aware initialization process.

The V2 shared store is never auto-copied to a new individual. A future one-time migration must require informed user consent, verified origin ownership, an explicit target companion ID, review of owner-private memories, backup/rollback and tests; after success and verification, the old shared state must be retired safely.

## Next integration steps

1. Persist one immutable companion ID after first awakening; never infer it from a display name.
2. Select the V3 scoped store from that ID before creating the decision engine.
3. Handle existing V2 data with explicit opt-in migration, not silent cloning.
4. Add owner-authenticated data protection and independent owner-private partitions.
5. Run real cross-companion isolation, browser persistence, offline upgrade and rollback tests before enabling on production.
