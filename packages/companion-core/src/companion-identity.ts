/** Identity records are locally scoped metadata, not authentication credentials. */
export interface CompanionIdentityV1 {
  readonly schemaVersion: 1;
  readonly companionId: string;
  readonly createdAt: number;
  readonly displayName: string;
}

const validId = (id: unknown): id is string =>
  typeof id === 'string' && id.length > 0 && id.length <= 128 &&
  id === id.trim() && !/[\u0000-\u001f\u007f]/.test(id);

export function isCompanionIdentityV1(input: unknown): input is CompanionIdentityV1 {
  if (typeof input !== 'object' || input === null || Array.isArray(input)) return false;
  const record = input as Record<string, unknown>;
  return Object.keys(record).length === 4 &&
    record.schemaVersion === 1 &&
    validId(record.companionId) &&
    typeof record.displayName === 'string' &&
    record.displayName.trim().length > 0 &&
    record.displayName.length <= 100 &&
    typeof record.createdAt === 'number' &&
    Number.isSafeInteger(record.createdAt) &&
    record.createdAt >= 0;
}

/** No implicit fallback to a shared or default companion ID. */
export function parseCompanionIdentityV1(raw: string | null): CompanionIdentityV1 | null {
  if (raw === null) return null;
  try {
    const value: unknown = JSON.parse(raw);
    return isCompanionIdentityV1(value) ? value : null;
  } catch {
    return null;
  }
}
