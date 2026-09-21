/**
 * Telling the other device.
 *
 * Both apps paint from a local copy and treat the API as the source of truth,
 * so a change made on the phone leaves the browser's copy quietly wrong until
 * something makes it ask again. Pull-to-refresh is a full sync and too blunt to
 * run on a hunch; polling would cost a request a minute to catch a change a day.
 * So a write pushes: a data-only FCM message naming which slices of the ledger
 * moved, and the receiving client refreshes those and nothing else.
 *
 * Two rules keep it honest:
 *
 * - **A device is never told about its own write.** The client sends its
 *   install id as `X-Yuuka-Device`, the fan-out skips that row, and the device
 *   that made the change already has the API's answer in hand.
 * - **The push is a hint, never the data.** It carries slice names, not rows.
 *   FCM does not promise delivery, and a client that missed one is only as
 *   stale as it would have been without push at all — it still syncs on open,
 *   on reconnect and on pull-to-refresh. Nothing may be built on a message
 *   having arrived.
 */

import { sendToTokens } from './fcm';

/**
 * The named slices of the ledger a client can refresh on its own. These are
 * the units a push speaks in: `transactions` means "the pages and months you
 * are holding are out of date", not any particular row.
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

/**
 * Which slices a write to each path family disturbs.
 *
 * The blast radius is wider than the row that changed, and deliberately so: a
 * transaction moves an account's balance and the month's summary, renaming a
 * category changes what every transaction card reads, and archiving an account
 * type changes which accounts are pickable. Saying too much costs a refresh the
 * client would have done on its next open anyway; saying too little leaves a
 * figure wrong on screen with nothing to correct it.
 */
const SLICES_BY_PREFIX: ReadonlyArray<readonly [string, readonly ChangeSlice[]]> = [
	['/api/account-types', ['accountTypes', 'accounts']],
	// Includes `/accounts/:id/adjust`, which posts a transaction like any other.
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

/** The slices a write to this path disturbs, or none for a path that changes nothing shared. */
export function slicesForPath(path: string): readonly ChangeSlice[] {
	const match = SLICES_BY_PREFIX.find(([prefix]) => path === prefix || path.startsWith(`${prefix}/`));
	return match ? match[1] : [];
}

/**
 * Fans a change out to this user's other installs.
 *
 * Tokens FCM rejects as dead are dropped here rather than by a cron: a token is
 * only known to be dead at the moment we try to use it, and the table is small
 * enough that pruning it in place costs nothing.
 */
export async function notifyChange(env: Env, userId: string, slices: readonly ChangeSlice[], originDeviceId: string | null): Promise<void> {
	if (slices.length === 0) return;

	try {
		const { results } = await env.DB.prepare('SELECT token FROM devices WHERE user_id = ? AND device_id IS NOT ?')
			.bind(userId, originDeviceId)
			.all<{ token: string }>();

		const tokens = results.map((row) => row.token);
		if (tokens.length === 0) return;

		const outcomes = await sendToTokens(env, tokens, {
			type: 'ledger.changed',
			slices: [...new Set(slices)].join(','),
			at: new Date().toISOString(),
		});

		const dead = outcomes.filter((outcome) => outcome.stale).map((outcome) => outcome.token);
		if (dead.length > 0) {
			await env.DB.prepare(`DELETE FROM devices WHERE token IN (${dead.map(() => '?').join(', ')})`)
				.bind(...dead)
				.run();
		}
	} catch (error) {
		// A write that succeeded must not be reported as failed because the
		// other device could not be told; the client will sync without us.
		console.error('Change notification failed', error);
	}
}
