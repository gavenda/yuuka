/**
 * Naming a row before the server has seen it.
 *
 * Offline-first means a row exists, and is referenced by later rows, before it
 * has ever been sent: a transaction entered on a train names an account that
 * may itself still be queued. The client therefore names what it creates, and
 * the server stores the name it was given — so a queued transaction's
 * `accountId` is still right when it finally lands, with no second pass to
 * rewrite references.
 *
 * The shape is the server's own (`server/ids.ts`): a prefix, an underscore and
 * a hex string. Nothing downstream can tell which side generated an id, which
 * is the point — there is no "provisional id" concept to leak into the schema,
 * the URLs or the cache.
 */

/** The prefix each kind of row carries. The server uses exactly these. */
export const ID_PREFIXES = {
	account: 'acc',
	accountType: 'atp',
	category: 'cat',
	tag: 'tag',
	transaction: 'txn',
	transfer: 'tfr',
	subscription: 'sub',
	budget: 'bdg',
} as const;

export type IdKind = keyof typeof ID_PREFIXES;

/** A new id for a row of this kind, e.g. `txn_9f1c…`. */
export function newId(kind: IdKind): string {
	const bytes = crypto.getRandomValues(new Uint8Array(16));
	const hex = [...bytes].map((byte) => byte.toString(16).padStart(2, '0')).join('');
	return `${ID_PREFIXES[kind]}_${hex}`;
}
