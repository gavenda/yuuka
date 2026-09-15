import { api } from '@/lib/api';
import { defineStore } from 'pinia';
import { computed, ref } from 'vue';
import type { Transaction, TransactionFilters } from '@/types';

const PAGE_SIZE = 50;

export const useTransactionStore = defineStore('transactions', () => {
	const transactions = ref<Transaction[]>([]);
	const total = ref(0);
	const offset = ref(0);
	const loading = ref(false);
	const error = ref<string | null>(null);
	const filters = ref<TransactionFilters>({});

	const hasMore = computed(() => transactions.value.length < total.value);

	/** Groups a page into date-keyed sections for the list view. */
	const byDate = computed(() => {
		const groups = new Map<string, Transaction[]>();
		for (const transaction of transactions.value) {
			const bucket = groups.get(transaction.occurredOn);
			if (bucket) bucket.push(transaction);
			else groups.set(transaction.occurredOn, [transaction]);
		}
		return [...groups.entries()];
	});

	async function fetchPage(append: boolean): Promise<void> {
		loading.value = true;
		error.value = null;

		try {
			const page = await api.listTransactions({ ...filters.value, limit: PAGE_SIZE, offset: offset.value });
			transactions.value = append ? [...transactions.value, ...page.transactions] : page.transactions;
			total.value = page.total;
		} catch (caught) {
			error.value = caught instanceof Error ? caught.message : 'Could not load transactions.';
		} finally {
			loading.value = false;
		}
	}

	async function load(next: TransactionFilters = filters.value): Promise<void> {
		filters.value = next;
		offset.value = 0;
		await fetchPage(false);
	}

	async function loadMore(): Promise<void> {
		if (loading.value || !hasMore.value) return;
		offset.value = transactions.value.length;
		await fetchPage(true);
	}

	/** Reloads the current page in place, keeping however much has been paged in. */
	async function refresh(): Promise<void> {
		const loadedCount = Math.max(PAGE_SIZE, transactions.value.length);
		loading.value = true;

		try {
			const page = await api.listTransactions({ ...filters.value, limit: Math.min(200, loadedCount), offset: 0 });
			transactions.value = page.transactions;
			total.value = page.total;
			offset.value = 0;
		} finally {
			loading.value = false;
		}
	}

	function reset(): void {
		transactions.value = [];
		total.value = 0;
		offset.value = 0;
		filters.value = {};
	}

	return { transactions, total, loading, error, filters, hasMore, byDate, load, loadMore, refresh, reset };
});
