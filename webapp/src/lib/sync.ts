import { useBudgetStore } from '@/stores/budget';
import { useLedgerStore } from '@/stores/ledger';
import { useSubscriptionStore } from '@/stores/subscriptions';
import { useTransactionStore } from '@/stores/transactions';

/**
 * Brings what is on screen back in line with the API, the way a full sync does in the Android app:
 * everything already loaded is fetched again, and a store that has not been opened is left for
 * when it is. Each part is independent, and a failure — offline again, an expired session — leaves
 * that part showing its last-seen copy rather than empty.
 */
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
