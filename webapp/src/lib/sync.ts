import { useBudgetStore } from '@/stores/budget';
import { useLedgerStore } from '@/stores/ledger';
import { useSubscriptionStore } from '@/stores/subscriptions';
import { useTransactionStore } from '@/stores/transactions';
import { api } from './api';
import { discardMonthly, replacePayees, type MonthlySlice } from './cache';
import { PAYEE_LIMIT } from './payees';
import { setSliceRefresher } from './queue';
import type { ChangeSlice } from './slices';

/**
 * Bringing the screens, and the local copy behind them, back in line with the API — the twin of
 * Android's `SyncRepository`.
 *
 * There are two occasions for it, and they want different amounts of work:
 *
 * - **A full sync**, on reconnect or pull-to-refresh. The per-screen loads only fold rows in, so
 *   something deleted elsewhere would otherwise linger here indefinitely; a full sync replaces the
 *   whole lists and throws away what is held per month and per page.
 * - **A slice refresh**, after a queued batch lands or a push arrives, which refetches only what the
 *   change touched. That is the point of the push: the other device says which parts of the ledger
 *   moved, so a transaction added on the phone does not cost the browser a refetch of its
 *   subscriptions.
 *
 * A failure — offline again, an expired session — leaves the last-seen copy as it was rather than
 * empty.
 */

/** Replaces the remembered payees in the local copy. Throws when the API cannot be had. */
export async function refreshPayees(): Promise<void> {
	await replacePayees((await api.listPayees(undefined, PAYEE_LIMIT)).payees);
}

/**
 * Replaces accounts, types, categories, tags, settings, the round-up rule, subscriptions and payees
 * from the API, and only once all of that has succeeded discards the cached transactions, budgets,
 * income plans and summaries — then the screens on show load theirs again. A sync that fails part
 * way leaves the last-seen ledger untouched.
 */
export async function fullSync(): Promise<void> {
	const ledger = useLedgerStore();
	const budget = useBudgetStore();
	const transactions = useTransactionStore();
	const subscriptions = useSubscriptionStore();

	const lists = await Promise.allSettled([ledger.load(true), subscriptions.load(true), refreshPayees()]);
	if (lists.some((list) => list.status === 'rejected' || list.value === false)) return;

	await discardMonthly(['transactions', 'budgets', 'incomePlans', 'summaries']);

	const jobs: Promise<unknown>[] = [];
	if (budget.summary) jobs.push(budget.refresh());
	if (transactions.loaded) jobs.push(transactions.refresh());
	await Promise.allSettled(jobs);
}

/** What each slice held per month or per page is filed under in the copy. */
const MONTHLY: Partial<Record<ChangeSlice, MonthlySlice>> = {
	transactions: 'transactions',
	budgets: 'budgets',
	incomePlan: 'incomePlans',
	summary: 'summaries',
};

/**
 * Refetches just the named slices.
 *
 * Several slices often resolve to the same call — `accounts` and `categories`
 * both live in the ledger store — so the work is collected first and each
 * store asked at most once. A store that has never been opened is left alone:
 * it will load when it is. What is held per month and per page is dropped from
 * the copy for the slices named, and whatever of it is on show fetched again.
 */
export async function refreshSlices(slices: ChangeSlice[]): Promise<void> {
	const wanted = new Set(slices);
	const ledger = useLedgerStore();
	const budget = useBudgetStore();
	const transactions = useTransactionStore();
	const subscriptions = useSubscriptionStore();

	const monthly = [...wanted].map((slice) => MONTHLY[slice]).filter((slice): slice is MonthlySlice => slice !== undefined);
	if (monthly.length > 0) await discardMonthly(monthly);

	const jobs: Promise<unknown>[] = [];

	const ledgerSlices: ChangeSlice[] = ['accounts', 'accountTypes', 'categories', 'tags', 'settings', 'roundUpRule'];
	if (ledger.loaded && ledgerSlices.some((slice) => wanted.has(slice))) jobs.push(ledger.load(true));

	// The summary carries the month's budgets and planned income with it, so one
	// refresh answers all three.
	const budgetSlices: ChangeSlice[] = ['budgets', 'incomePlan', 'summary'];
	if (budget.summary && budgetSlices.some((slice) => wanted.has(slice))) jobs.push(budget.refresh());

	if (transactions.loaded && wanted.has('transactions')) jobs.push(transactions.refresh());
	if (subscriptions.loaded && wanted.has('subscriptions')) jobs.push(subscriptions.refresh());
	if (wanted.has('payees')) jobs.push(refreshPayees());

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
