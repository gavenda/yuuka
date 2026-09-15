/** Generates a prefixed, URL-safe identifier, e.g. `txn_9f1c...`. */
export function newId(prefix: string): string {
	return `${prefix}_${crypto.randomUUID().replaceAll('-', '')}`;
}

/**
 * A stable id derived from its parts, for rows that may be written more than
 * once for the same user.
 *
 * Provisioning runs concurrently — the first page load fires several requests
 * at once — so two batches can race. With random ids the loser's `INSERT OR
 * IGNORE` silently drops its categories while its transactions still point at
 * them, and the batch dies on a foreign key. Deriving the id from the owner and
 * the row makes a repeat run a complete no-op instead.
 *
 * Parts are JSON-encoded before hashing so that ["a", "bc"] and ["ab", "c"]
 * cannot collide.
 */
export async function stableId(prefix: string, ...parts: string[]): Promise<string> {
	const digest = await crypto.subtle.digest('SHA-256', new TextEncoder().encode(JSON.stringify(parts)));
	const hex = [...new Uint8Array(digest)].map((byte) => byte.toString(16).padStart(2, '0')).join('');
	return `${prefix}_${hex.slice(0, 24)}`;
}
