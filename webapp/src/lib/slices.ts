/**
 * The named parts of the ledger a client can refresh on its own.
 *
 * These are the words a push speaks in, and the words the queue uses to say
 * what to reload once a change has landed. A slice is not a row: `transactions`
 * means "the pages and months you are holding are out of date", not any
 * particular transaction.
 *
 * The server has the same list in `server/notify.ts`, and the same mapping from
 * a path to the slices it disturbs, because both sides have to agree on what a
 * write affects. Keep them together.
 */

export const CHANGE_SLICES = [
	'accounts',
	'accountTypes',
	'categories',
	'tags',
	'transactions',
	'budgets',
	'incomePlan',
	'subscriptions',
	'settings',
	'roundUpRule',
	'payees',
	'summary',
] as const;

export type ChangeSlice = (typeof CHANGE_SLICES)[number];

const SLICES_BY_PREFIX: ReadonlyArray<readonly [string, readonly ChangeSlice[]]> = [
	['/api/account-types', ['accountTypes', 'accounts']],
	['/api/accounts', ['accounts', 'transactions', 'summary', 'payees']],
	['/api/categories', ['categories', 'transactions', 'budgets', 'summary']],
	['/api/tags', ['tags', 'transactions']],
	['/api/transactions', ['transactions', 'accounts', 'summary', 'payees']],
	['/api/budgets', ['budgets', 'summary']],
	['/api/income-plan', ['incomePlan', 'summary']],
	['/api/subscriptions', ['subscriptions']],
	['/api/settings', ['settings', 'summary']],
	['/api/round-up', ['roundUpRule']],
	['/api/payees', ['payees']],
];

/** The slices a write to this path disturbs. */
export function slicesForPath(path: string): ChangeSlice[] {
	const match = SLICES_BY_PREFIX.find(([prefix]) => path === prefix || path.startsWith(`${prefix}/`));
	return match ? [...match[1]] : [];
}

/** Reads the slice list off a push message's payload, ignoring anything it does not recognise. */
export function parseSlices(value: string | undefined): ChangeSlice[] {
	if (!value) return [];
	const known = new Set<string>(CHANGE_SLICES);
	return value
		.split(',')
		.map((slice) => slice.trim())
		.filter((slice): slice is ChangeSlice => known.has(slice));
}
