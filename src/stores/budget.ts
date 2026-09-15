import { api } from '@/lib/api';
import { currentMonth } from '@/lib/dates';
import { defineStore } from 'pinia';
import { computed, ref } from 'vue';
import type { Summary } from '@/types';

/** Owns the selected month and the summary computed for it. */
export const useBudgetStore = defineStore('budget', () => {
	const month = ref(currentMonth());
	const summary = ref<Summary | null>(null);
	const loading = ref(false);
	const error = ref<string | null>(null);

	// Only top-level categories appear in the summary, so these lists can be
	// summed without double-counting a subcategory.
	const standard = computed(() => (summary.value?.categories ?? []).filter((entry) => entry.appliesTo === 'standard'));

	const expenseBreakdown = computed(() => standard.value.filter((entry) => entry.kind === 'expense').sort((a, b) => b.actual - a.actual));

	const incomeBreakdown = computed(() => standard.value.filter((entry) => entry.kind === 'income').sort((a, b) => b.actual - a.actual));

	/** Transfer-scope categories — the Cashflow tree, e.g. investments. */
	const cashflowBreakdown = computed(() =>
		(summary.value?.categories ?? []).filter((entry) => entry.appliesTo === 'transfer').sort((a, b) => b.actual - a.actual),
	);

	/** Budgeted spend not yet used, floored at zero so overspend does not read as headroom. */
	const unspent = computed(() => expenseBreakdown.value.reduce((total, entry) => total + Math.max(0, entry.remaining), 0));

	const overspent = computed(() => expenseBreakdown.value.reduce((total, entry) => total + Math.min(0, entry.remaining), 0));

	async function load(force = false): Promise<void> {
		if (loading.value) return;
		if (summary.value?.month === month.value && !force) return;

		loading.value = true;
		error.value = null;

		try {
			summary.value = await api.summary(month.value);
		} catch (caught) {
			error.value = caught instanceof Error ? caught.message : 'Could not load the summary.';
		} finally {
			loading.value = false;
		}
	}

	async function setMonth(next: string): Promise<void> {
		month.value = next;
		await load(true);
	}

	/** Re-reads the summary; call after any write that changes the figures. */
	async function refresh(): Promise<void> {
		await load(true);
	}

	async function setBudget(categoryId: string, amount: number): Promise<void> {
		await api.setBudget({ categoryId, month: month.value, amount });
		await refresh();
	}

	function reset(): void {
		summary.value = null;
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
		unspent,
		overspent,
		load,
		setMonth,
		refresh,
		setBudget,
		reset,
	};
});
