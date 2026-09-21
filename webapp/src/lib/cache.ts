/**
 * The browser's local copy of the ledger, kept in `localStorage`.
 *
 * It plays the part Room plays in the Android app: screens paint from it at once, and the API is
 * still the source of truth. A copy is only ever replaced by what the API returned, never edited
 * on its own, and losing it costs nothing but a network round trip — which is why every function
 * here is best-effort and none throws. A store that finds nothing simply loads as it always did.
 *
 * Each value is one entry under `yuuka.cache.<family>` or `yuuka.cache.<family>:<variant>`. The
 * variants of a family (one summary per month, one transaction page per filter set) are bounded
 * by [writeCache]'s `keep`, so browsing does not grow the copy without limit.
 */

const PREFIX = 'yuuka.cache.';
const OWNER_KEY = `${PREFIX}owner`;

/** Bump when the shape of a stored value changes: entries written under another version read as missing. */
const VERSION = 2;

interface Entry<T> {
	v: number;
	/** When it was written, so the oldest can be dropped first. */
	at: number;
	data: T;
}

function storage(): Storage | null {
	try {
		return localStorage;
	} catch {
		// Blocked, or unavailable in this context.
		return null;
	}
}

function familyOf(key: string): string {
	const colon = key.indexOf(':');
	return colon === -1 ? key : key.slice(0, colon);
}

/** Every stored key of a family, whole keys without the prefix. */
function keysOf(store: Storage, family: string): string[] {
	const keys: string[] = [];
	for (let index = 0; index < store.length; index++) {
		const stored = store.key(index);
		if (!stored?.startsWith(PREFIX) || stored === OWNER_KEY) continue;

		const key = stored.slice(PREFIX.length);
		if (familyOf(key) === family) keys.push(key);
	}
	return keys;
}

function parse(raw: string | null): Entry<unknown> | null {
	if (!raw) return null;

	try {
		const entry = JSON.parse(raw) as Partial<Entry<unknown>> | null;
		return entry && entry.v === VERSION && typeof entry.at === 'number' && 'data' in entry ? (entry as Entry<unknown>) : null;
	} catch {
		return null;
	}
}

/** Reads a stored value, or null when there is none, it is from another version, or [guard] rejects it. */
export function readCache<T>(key: string, guard?: (data: unknown) => data is T): T | null {
	const store = storage();
	if (!store) return null;

	try {
		const entry = parse(store.getItem(PREFIX + key));
		if (!entry) return null;
		return guard && !guard(entry.data) ? null : (entry.data as T);
	} catch {
		return null;
	}
}

/** Removes the oldest entry other than [except]; false when there was nothing left to remove. */
function evictOldest(store: Storage, except: string): boolean {
	let oldest: { stored: string; at: number } | null = null;

	for (let index = 0; index < store.length; index++) {
		const stored = store.key(index);
		if (!stored?.startsWith(PREFIX) || stored === OWNER_KEY || stored === PREFIX + except) continue;

		const at = parse(store.getItem(stored))?.at ?? 0;
		if (!oldest || at < oldest.at) oldest = { stored, at };
	}

	if (!oldest) return false;
	store.removeItem(oldest.stored);
	return true;
}

/**
 * Stores a value. When [keep] is given, the family's variants are trimmed to that many, newest
 * first. A full quota is answered by dropping the oldest entries; if even that is not enough, the
 * value is not stored — the API is primary, so a missing copy is only slower, never wrong.
 */
export function writeCache(key: string, data: unknown, keep?: number): void {
	const store = storage();
	if (!store) return;

	const serialised = JSON.stringify({ v: VERSION, at: Date.now(), data } satisfies Entry<unknown>);

	for (;;) {
		try {
			store.setItem(PREFIX + key, serialised);
			break;
		} catch {
			if (!evictOldest(store, key)) return;
		}
	}

	if (keep === undefined) return;

	const stale = keysOf(store, familyOf(key))
		.map((other) => ({ other, at: parse(store.getItem(PREFIX + other))?.at ?? 0 }))
		.sort((a, b) => b.at - a.at)
		.slice(keep);
	for (const { other } of stale) if (other !== key) store.removeItem(PREFIX + other);
}

/** Removes every variant of a family, except [except] when given. */
export function dropCacheFamily(family: string, except?: string): void {
	const store = storage();
	if (!store) return;

	try {
		for (const key of keysOf(store, family)) if (key !== except) store.removeItem(PREFIX + key);
	} catch {
		// Nothing to do: a copy that could not be dropped is replaced by the next refresh.
	}
}

/** Discards the whole local copy. */
export function clearCache(): void {
	const store = storage();
	if (!store) return;

	try {
		const stored: string[] = [];
		for (let index = 0; index < store.length; index++) {
			const key = store.key(index);
			if (key?.startsWith(PREFIX)) stored.push(key);
		}
		for (const key of stored) store.removeItem(key);
	} catch {
		// See dropCacheFamily.
	}
}

/**
 * Ties the local copy to whoever is signed in. It holds one person's books, so a different
 * account signing in on the same browser must never be shown it, even for the instant before its
 * own data arrives.
 */
export function adoptCacheFor(owner: string | undefined): boolean {
	const store = storage();
	if (!store) return false;

	try {
		if (store.getItem(OWNER_KEY) === owner) return false;

		clearCache();
		if (owner) store.setItem(OWNER_KEY, owner);
		return true;
	} catch {
		// Without the marker the copy is cleared again next time, which is the safe direction.
		return false;
	}
}

/** A stable key variant for a set of query parameters: the same filters always give the same string. */
export function snapshotKey(family: string, params: Record<string, string | number | undefined>): string {
	const parts = Object.entries(params)
		.filter(([, value]) => value !== undefined && value !== '')
		.sort(([a], [b]) => a.localeCompare(b))
		.map(([name, value]) => `${name}=${value}`);

	return `${family}:${parts.join('&')}`;
}
