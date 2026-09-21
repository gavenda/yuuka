import { useBudgetStore } from '@/stores/budget';
import { useLedgerStore } from '@/stores/ledger';
import { useSubscriptionStore } from '@/stores/subscriptions';
import { useTransactionStore } from '@/stores/transactions';
import { setSliceRefresher } from './queue';
import type { ChangeSlice } from './slices';

/**
 * Bringing the screens back in line with the API.
 *
 * There are two occasions for it, and they want different amounts of work:
 *
 * - **A full sync**, on reconnect or pull-to-refresh, which refetches
 *   everything already loaded because anything at all may have moved.
 * - **A slice refresh**, after a queued batch lands or a push arrives, which
 *   refetches only what the change touched. That is the point of the push: the
 *   other device says which parts of the ledger moved, so a transaction added
 *   on the phone does not cost the browser a refetch of its subscriptions.
 *
 * Each part is independent, and a failure — offline again, an expired session —
 * leaves that part showing its last-seen copy rather than empty.
 */

/** Refetches everything on screen, the way a full sync does in the Android app. */
export async function fullSync(): Promise<void> {
	const ledger = useLedgerStore();
	const budget = useBudgetStore();
	const transactions = useTransactionStore();
	const subscriptions = useSubscriptionStore();

	const jobs: Promise<unknown>[] = [];
	if (ledger.loaded) jobs.push(ledger.load(true));
	if (budget.summary) jobs.push(budget.refresh());
	if (transactions.loaded) jobs.push(transactions.refresh());
	if (subscriptions.loaded) jobs.push(subscriptions.refresh());

	await Promise.allSettled(jobs);
}

/**
 * Refetches just the named slices.
 *
 * Several slices often resolve to the same call — `accounts` and `categories`
 * both live in the ledger store — so the work is collected first and each
 * store asked at most once. A store that has never been opened is left alone:
 * it will load when it is.
 */
export async function refreshSlices(slices: ChangeSlice[]): Promise<void> {
	const wanted = new Set(slices);
	const ledger = useLedgerStore();
	const budget = useBudgetStore();
	const transactions = useTransactionStore();
	const subscriptions = useSubscriptionStore();

	const jobs: Promise<unknown>[] = [];

	const ledgerSlices: ChangeSlice[] = ['accounts', 'accountTypes', 'categories', 'tags', 'settings', 'roundUpRule'];
	if (ledger.loaded && ledgerSlices.some((slice) => wanted.has(slice))) jobs.push(ledger.load(true));

	// The summary carries the month's budgets and planned income with it, so one
	// refresh answers all three.
	const budgetSlices: ChangeSlice[] = ['budgets', 'incomePlan', 'summary'];
	if (budget.summary && budgetSlices.some((slice) => wanted.has(slice))) jobs.push(budget.refresh());

	if (transactions.loaded && wanted.has('transactions')) jobs.push(transactions.refresh());
	if (subscriptions.loaded && wanted.has('subscriptions')) jobs.push(subscriptions.refresh());

	await Promise.allSettled(jobs);
}

/**
 * Hands the queue a way to refresh, once Pinia is up.
 *
 * The queue cannot import the stores — every store imports the API, and the API
 * imports the queue — so it is given the function instead. Called from
 * `main.ts`.
 */
export function installSliceRefresher(): void {
	setSliceRefresher(refreshSlices);
}
