import { api, isNetworkError } from '@/lib/api';
import { dropCacheFamily, readCache, snapshotKey, writeCache } from '@/lib/cache';
import { defineStore } from 'pinia';
import { computed, ref } from 'vue';
import type { Transaction, TransactionFilters } from '@/types';

const PAGE_SIZE = 50;

/** How many filter sets' first pages the local copy holds. */
const KEEP_SNAPSHOTS = 8;

/** The first page of one filter set, as kept in the browser's local copy. */
interface PageSnapshot {
	transactions: Transaction[];
	total: number;
}

const isPageSnapshot = (data: unknown): data is PageSnapshot =>
	typeof data === 'object' &&
	data !== null &&
	Array.isArray((data as PageSnapshot).transactions) &&
	typeof (data as PageSnapshot).total === 'number';

const cacheKey = (filters: TransactionFilters) => snapshotKey('transactions', filters as Record<string, string | number | undefined>);

/**
 * The list on screen. A filter set seen before opens on the browser's local copy of its first
 * page while the API is asked for the current one; further pages are only ever fetched.
 */
export const useTransactionStore = defineStore('transactions', () => {
	const transactions = ref<Transaction[]>([]);
	const total = ref(0);
	const offset = ref(0);
	const loading = ref(false);
	const error = ref<string | null>(null);
	/** Whether a list has been shown yet, so a full sync knows whether there is anything to refresh. */
	const loaded = ref(false);
	const filters = ref<TransactionFilters>({});

	const hasMore = computed(() => transactions.value.length < total.value);

	/** Groups a page into date-keyed sections for the list view. */
	const byDate = computed(() => {
		const groups = new Map<string, Transaction[]>();
		for (const transaction of transactions.value) {
			// Group by the date part only — a time of day would otherwise split a
			// single day's transactions into their own one-row sections.
			const date = transaction.occurredOn.slice(0, 10);
			const bucket = groups.get(date);
			if (bucket) bucket.push(transaction);
			else groups.set(date, [transaction]);
		}
		return [...groups.entries()];
	});

	/** Keeps what is on screen as the local copy of this filter set's first page. */
	function persist(): void {
		writeCache(cacheKey(filters.value), { transactions: transactions.value, total: total.value } satisfies PageSnapshot, KEEP_SNAPSHOTS);
	}

	/** [keepOnNetworkError] is set when the list on screen is already the local copy, which an unreachable API leaves as it is. */
	async function fetchPage(append: boolean, keepOnNetworkError = false): Promise<void> {
		loading.value = true;
		error.value = null;

		try {
			const page = await api.listTransactions({ ...filters.value, limit: PAGE_SIZE, offset: offset.value });
			transactions.value = append ? [...transactions.value, ...page.transactions] : page.transactions;
			total.value = page.total;
			loaded.value = true;
			if (!append) persist();
		} catch (caught) {
			if (!(keepOnNetworkError && isNetworkError(caught))) {
				error.value = caught instanceof Error ? caught.message : 'Could not load transactions.';
			}
		} finally {
			loading.value = false;
		}
	}

	async function load(next: TransactionFilters = filters.value): Promise<void> {
		filters.value = next;
		offset.value = 0;

		const cached = readCache(cacheKey(next), isPageSnapshot);
		if (cached) {
			transactions.value = cached.transactions;
			total.value = cached.total;
			loaded.value = true;
		}

		await fetchPage(false, cached !== null);
	}

	async function loadMore(): Promise<void> {
		if (loading.value || !hasMore.value) return;
		offset.value = transactions.value.length;
		await fetchPage(true);
	}

	/**
	 * Reloads the current page in place, keeping however much has been paged in. Call it after any
	 * write, or for a full sync; other filter sets' local copies are dropped once it has.
	 */
	async function refresh(): Promise<void> {
		const loadedCount = Math.max(PAGE_SIZE, transactions.value.length);
		loading.value = true;

		try {
			const page = await api.listTransactions({ ...filters.value, limit: Math.min(200, loadedCount), offset: 0 });
			transactions.value = page.transactions;
			total.value = page.total;
			offset.value = 0;
			loaded.value = true;
			persist();
			// A write has landed, and it may have moved other filter sets' pages too.
			dropCacheFamily('transactions', cacheKey(filters.value));
		} finally {
			loading.value = false;
		}
	}

	function reset(): void {
		transactions.value = [];
		total.value = 0;
		offset.value = 0;
		filters.value = {};
		loaded.value = false;
	}

	return { transactions, total, loading, error, loaded, filters, hasMore, byDate, load, loadMore, refresh, reset };
});
