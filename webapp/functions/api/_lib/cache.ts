/**
 * Summary responses are expensive enough to be worth caching and cheap enough
 * to recompute, so they get a short TTL plus an explicit purge whenever a write
 * touches the month in question.
 *
 * Every key is namespaced by owner. Getting this wrong would serve one user's
 * figures to another, so the user id is never optional here.
 */
const SUMMARY_TTL_SECONDS = 300;

const userPrefix = (userId: string) => `summary:v1:${userId}:`;

export function summaryKey(userId: string, month: string): string {
	return `${userPrefix(userId)}${month}`;
}

export async function readSummary<T>(cache: KVNamespace, userId: string, month: string): Promise<T | null> {
	return await cache.get<T>(summaryKey(userId, month), 'json');
}

export async function writeSummary(cache: KVNamespace, userId: string, month: string, value: unknown): Promise<void> {
	await cache.put(summaryKey(userId, month), JSON.stringify(value), { expirationTtl: SUMMARY_TTL_SECONDS });
}

/** Drops this user's cached summaries for every month a write may have affected. */
export async function invalidateSummaries(cache: KVNamespace, userId: string, months: Iterable<string>): Promise<void> {
	const unique = [...new Set([...months].filter(Boolean))];
	await Promise.all(unique.map((month) => cache.delete(summaryKey(userId, month))));
}

/**
 * Drops every cached summary for one user. Used by writes whose blast radius is
 * not a single month — renaming a category, deleting an account and its
 * history, and so on.
 */
export async function invalidateAllSummaries(cache: KVNamespace, userId: string): Promise<void> {
	let cursor: string | undefined;
	do {
		const page = await cache.list({ prefix: userPrefix(userId), cursor });
		await Promise.all(page.keys.map((key) => cache.delete(key.name)));
		cursor = page.list_complete ? undefined : page.cursor;
	} while (cursor);
}
