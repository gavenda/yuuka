import { api, isNetworkError } from '@/lib/api';
import { dropTransactionPages, readTransactionPage, writeTransactionPage } from '@/lib/cache';
import { defineStore } from 'pinia';
import { computed, ref } from 'vue';
import type { Transaction, TransactionFilters } from '@/types';
import { t } from '@/i18n';

const PAGE_SIZE = 50;

/** How many filter sets' first pages the local copy holds. */
const KEEP_SNAPSHOTS = 8;

/**
 * The list on screen. A filter set seen before opens on the browser's local copy of its first
 * page — its rows, as the copy holds them now — while the API is asked for the current one;
 * further pages are only ever fetched, one at a time, as the end of the list comes within reach.
 */
export const useTransactionStore = defineStore('transactions', () => {
	const transactions = ref<Transaction[]>([]);
	const total = ref(0);
	const loading = ref(false);
	/** Whether the next page is on its way: the list being added to, where [loading] is it being replaced. */
	const loadingMore = ref(false);
	const error = ref<string | null>(null);
	/** Why the next page could not be had. It is the foot of the list's to say, since the rows above it are still good. */
	const moreError = ref<string | null>(null);
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

	/**
	 * Bumped whenever the list on screen is replaced, or another filter set is asked for, so a copy
	 * still being read cannot land on top of either.
	 */
	let revision = 0;

	/** Replaces the rows a filter set's first page covers in the local copy with what the API answered for it. */
	function persist(requested: TransactionFilters, page: { transactions: Transaction[]; total: number }): void {
		void writeTransactionPage(requested, page.transactions, page.total, KEEP_SNAPSHOTS);
	}

	/** Shows the local copy of [next]'s first page, unless something has replaced the list since it was asked for; whether it did. */
	async function showCopy(next: TransactionFilters): Promise<boolean> {
		const asked = revision;
		const cached = await readTransactionPage(next, PAGE_SIZE);
		if (!cached || revision !== asked) return false;

		revision++;
		transactions.value = cached.transactions;
		total.value = cached.total;
		loaded.value = true;
		return true;
	}

	/**
	 * [copy] settles on whether the list on screen is this filter set's local copy, which an
	 * unreachable API leaves as it is. It is waited for only on a failure: offline, the request can
	 * fail before the copy is read.
	 */
	async function fetchFirstPage(copy: Promise<boolean>): Promise<void> {
		loading.value = true;
		error.value = null;
		moreError.value = null;

		try {
			const requested = filters.value;
			const page = await api.listTransactions({ ...requested, limit: PAGE_SIZE, offset: 0 });
			revision++;
			transactions.value = page.transactions;
			total.value = page.total;
			loaded.value = true;
			persist(requested, page);
		} catch (caught) {
			if (!((await copy) && isNetworkError(caught))) {
				error.value = caught instanceof Error ? caught.message : t('common.errors.loadTransactions');
			}
		} finally {
			loading.value = false;
		}
	}

	/** Shows the list as the local copy now holds it — what a save has just written there — without asking the API. */
	async function showSaved(): Promise<void> {
		if (loaded.value) await showCopy(filters.value);
	}

	async function load(next: TransactionFilters = filters.value): Promise<void> {
		filters.value = next;
		// Neither a copy nor a further page of the filter set this one replaces may land once it is asked for.
		revision++;

		// Read beside the request rather than before it: the copy only fills the wait for the answer.
		await fetchFirstPage(showCopy(next));
	}

	/**
	 * Adds the next page, which the list asks for itself as its end comes within reach — there is
	 * nothing to press. A failure is kept apart from the screen's ([moreError]): the rows already
	 * shown stay, and the list stops asking until it is tried again or replaced, so being offline
	 * costs one failed request rather than a loop of them.
	 */
	async function loadMore(): Promise<void> {
		if (loading.value || loadingMore.value || !hasMore.value) return;

		const asked = revision;
		loadingMore.value = true;
		moreError.value = null;

		try {
			const page = await api.listTransactions({ ...filters.value, limit: PAGE_SIZE, offset: transactions.value.length });
			// The list this page carries on from has been replaced while it was on its way.
			if (revision !== asked) return;

			revision++;
			transactions.value = [...transactions.value, ...page.transactions];
			total.value = page.total;
		} catch (caught) {
			if (revision === asked) moreError.value = caught instanceof Error ? caught.message : t('common.errors.loadTransactions');
		} finally {
			loadingMore.value = false;
		}
	}

	/**
	 * Reloads the current page in place, keeping however much has been paged in. Call it after any
	 * write, or for a full sync; other filter sets' local copies are dropped once it has.
	 */
	async function refresh(): Promise<void> {
		const loadedCount = Math.max(PAGE_SIZE, transactions.value.length);
		loading.value = true;

		try {
			const requested = filters.value;
			const page = await api.listTransactions({ ...requested, limit: Math.min(200, loadedCount), offset: 0 });
			revision++;
			transactions.value = page.transactions;
			total.value = page.total;
			loaded.value = true;
			// The API has answered, so a page that could not be had before is worth asking for again.
			moreError.value = null;
			persist(requested, page);
			// A write has landed, and it may have moved other filter sets' pages too.
			void dropTransactionPages(requested);
		} finally {
			loading.value = false;
		}
	}

	function reset(): void {
		revision++;
		transactions.value = [];
		total.value = 0;
		moreError.value = null;
		filters.value = {};
		loaded.value = false;
	}

	return {
		transactions,
		total,
		loading,
		loadingMore,
		error,
		moreError,
		loaded,
		filters,
		hasMore,
		byDate,
		load,
		loadMore,
		refresh,
		showSaved,
		reset,
	};
});
