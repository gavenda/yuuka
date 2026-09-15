/** SQLite expression producing the same ISO-8601 shape the schema defaults use. */
export const NOW_SQL = "strftime('%Y-%m-%dT%H:%M:%fZ', 'now')";

/**
 * Turns a column map into a `SET` clause, skipping `undefined` so a PATCH only
 * writes the fields the caller actually sent.
 */
export function buildUpdate(columns: Record<string, unknown>): { clause: string; values: unknown[] } {
	const entries = Object.entries(columns).filter(([, value]) => value !== undefined);
	return {
		clause: entries.map(([column]) => `${column} = ?`).join(', '),
		values: entries.map(([, value]) => value),
	};
}

/** SQLite has no boolean type; the schema stores 0/1 with CHECK constraints. */
export function toSqliteBool(value: boolean | undefined): number | undefined {
	return value === undefined ? undefined : value ? 1 : 0;
}

/** True when D1 rejected a write because of a UNIQUE index. */
export function isUniqueViolation(error: unknown): boolean {
	const message = error instanceof Error ? `${error.message} ${error.cause instanceof Error ? error.cause.message : ''}` : String(error);
	return message.includes('UNIQUE constraint failed');
}

/** True when D1 rejected a write because a referenced row does not exist. */
export function isForeignKeyViolation(error: unknown): boolean {
	const message = error instanceof Error ? `${error.message} ${error.cause instanceof Error ? error.cause.message : ''}` : String(error);
	return message.includes('FOREIGN KEY constraint failed');
}
