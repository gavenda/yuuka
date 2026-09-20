import { api, isNetworkError } from '@/lib/api';
import { dropCacheFamily, readCache, snapshotKey, writeCache } from '@/lib/cache';
import { currentMonth } from '@/lib/dates';
import { defineStore } from 'pinia';
import { computed, ref } from 'vue';
import type { Summary } from '@/types';

/** How many months' summaries the local copy holds. */
const KEEP_MONTHS = 6;

const cacheKey = (month: string) => snapshotKey('summary', { month });

const isSummaryFor =
	(month: string) =>
	(data: unknown): data is Summary =>
		typeof data === 'object' &&
		data !== null &&
		(data as Partial<Summary>).month === month &&
		Array.isArray((data as Partial<Summary>).categories);

/**
 * Owns the selected month and the summary computed for it. A month seen before is shown from the
 * browser's local copy while the API is asked for the current figures.
 */
export const useBudgetStore = defineStore('budget', () => {
	const month = ref(currentMonth());
	const summary = ref<Summary | null>(null);
	const loading = ref(false);
	const error = ref<string | null>(null);
	/** The month the API last answered for, so a summary shown from the local copy is not mistaken for a current one. */
	let freshFor: string | null = null;

	// Only top-level categories appear in the summary, so these lists can be
	// summed without double-counting a subcategory.
	const standard = computed(() => (summary.value?.categories ?? []).filter((entry) => entry.kind !== 'transfer'));

	const expenseBreakdown = computed(() => standard.value.filter((entry) => entry.kind === 'expense').sort((a, b) => b.actual - a.actual));

	const incomeBreakdown = computed(() => standard.value.filter((entry) => entry.kind === 'income').sort((a, b) => b.actual - a.actual));

	/** Transfer-scope categories — the Cashflow tree, e.g. investments. */
	const cashflowBreakdown = computed(() =>
		(summary.value?.categories ?? []).filter((entry) => entry.kind === 'transfer').sort((a, b) => b.actual - a.actual),
	);

	/** The planned total for the month — what a percentage-based budget is a share of. */
	const plannedIncome = computed(() => summary.value?.plannedIncome ?? 0);

	/** Whether `plannedIncome` was typed directly or derived from a gross salary, so the editor can reopen in the same mode. */
	const plannedIncomeMode = computed(() => summary.value?.plannedIncomeMode ?? 'fixed');
	const plannedIncomeGrossAmount = computed(() => summary.value?.plannedIncomeGrossAmount ?? null);

	/** Budgeted spend not yet used, floored at zero so overspend does not read as headroom. */
	const unspent = computed(() => expenseBreakdown.value.reduce((total, entry) => total + Math.max(0, entry.remaining), 0));

	const overspent = computed(() => expenseBreakdown.value.reduce((total, entry) => total + Math.min(0, entry.remaining), 0));

	/**
	 * Resolves whether the summary is now known to match the API's. It is false when the API could
	 * not be reached and the local copy (or nothing) is what is on screen.
	 */
	async function load(force = false): Promise<boolean> {
		if (loading.value) return false;
		if (summary.value?.month === month.value && freshFor === month.value && !force) return true;

		const requested = month.value;
		const cached = summary.value?.month === requested ? null : readCache(cacheKey(requested), isSummaryFor(requested));
		if (cached) summary.value = cached;

		loading.value = true;
		error.value = null;

		try {
			const fresh = await api.summary(requested);
			summary.value = fresh;
			freshFor = requested;
			writeCache(cacheKey(requested), fresh, KEEP_MONTHS);
			return true;
		} catch (caught) {
			// Out of reach with this month's figures on screen: keep showing them.
			const showingLocalCopy = summary.value?.month === requested;
			if (!(showingLocalCopy && isNetworkError(caught))) {
				error.value = caught instanceof Error ? caught.message : 'Could not load the summary.';
			}
			return false;
		} finally {
			loading.value = false;
		}
	}

	async function setMonth(next: string): Promise<void> {
		month.value = next;
		await load(true);
	}

	/**
	 * Re-reads the summary; call after any write that changes the figures. Other months' local
	 * copies are dropped once it has: the write may have moved them too, and it is the API that
	 * knows.
	 */
	async function refresh(): Promise<void> {
		if (await load(true)) dropCacheFamily('summary', cacheKey(month.value));
	}

	async function setBudget(categoryId: string, value: { amount: number } | { percent: number }): Promise<void> {
		await api.setBudget({ categoryId, month: month.value, ...value });
		await refresh();
	}

	async function setIncomePlan(amount: number, mode: 'gross' | 'fixed' = 'fixed', grossAmount?: number): Promise<void> {
		await api.setIncomePlan({ month: month.value, amount, mode, grossAmount });
		await refresh();
	}

	function reset(): void {
		summary.value = null;
		freshFor = null;
		month.value = currentMonth();
	}

	return {
		month,
		summary,
		loading,
		error,
		expenseBreakdown,
		incomeBreakdown,
		cashflowBreakdown,
		plannedIncome,
		plannedIncomeMode,
		plannedIncomeGrossAmount,
		unspent,
		overspent,
		load,
		setMonth,
		refresh,
		setBudget,
		setIncomePlan,
		reset,
	};
});
