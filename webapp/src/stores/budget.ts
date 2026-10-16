import { api, isNetworkError } from '@/lib/api';
import { dropSummaries, readSummary, readTrend, writeSummary, writeTrend } from '@/lib/cache';
import { addMonths, currentMonth } from '@/lib/dates';
import { defineStore } from 'pinia';
import { computed, ref } from 'vue';
import type { Summary, Trend } from '@/types';
import { t } from '@/i18n';

/** How many rows the local copy of summaries holds: six months, each with the trend that ends at it. */
const KEEP_ROWS = 12;

/** How many months the dashboard's trend covers, ending at the month on show. Mirrors `TREND_MONTHS` in `BudgetRepository.kt`. */
export const TREND_MONTHS = 12;

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
	 * The month before's summary, which the dashboard compares "Where the money went" with. Only the dashboard
	 * asks for it, through `loadPrevious`; it is null until it is known for the month on show.
	 */
	const previous = ref<Summary | null>(null);

	/** Fills `previous` for the month on show, from the local copy and then the API. Without it the bars stand alone, so a failure is not reported. */
	async function loadPrevious(): Promise<void> {
		const requested = addMonths(month.value, -1);
		const stillWanted = () => addMonths(month.value, -1) === requested;
		if (previous.value?.month !== requested) previous.value = null;

		const copy = await readSummary(requested);
		if (copy && stillWanted() && !previous.value) previous.value = copy;

		try {
			const fresh = await api.summary(requested);
			if (!stillWanted()) return;
			previous.value = fresh;
			void writeSummary(fresh, KEEP_ROWS);
		} catch {
			// Only a comparison: whatever copy there was stays.
		}
	}

	/** The months up to the one on show, for the dashboard's trend charts; null until known. Only the dashboard asks, through `loadTrend`. */
	const trend = ref<Trend | null>(null);

	/** Fills `trend` for the month on show, from the local copy and then the API. A failure leaves whatever copy there was, unreported. */
	async function loadTrend(): Promise<void> {
		const requested = month.value;
		if (trend.value?.month !== requested) trend.value = null;

		const copy = await readTrend(requested);
		if (copy && month.value === requested && !trend.value) trend.value = copy;

		try {
			const fresh = await api.trend(requested, TREND_MONTHS);
			if (month.value !== requested) return;
			trend.value = fresh;
			void writeTrend(fresh, KEEP_ROWS);
		} catch {
			// Only a picture of the months behind this one: whatever copy there was stays.
		}
	}

	/** Bumped whenever the summary on screen is replaced, so a copy still being read cannot land on top of it. */
	let revision = 0;

	/** Shows the local copy of [requested], if there is one and nothing has replaced the summary since it was asked for. */
	async function showCopy(requested: string): Promise<void> {
		const asked = revision;
		const cached = await readSummary(requested);
		if (!cached || revision !== asked) return;

		revision++;
		summary.value = cached;
	}

	/**
	 * Resolves whether the summary is now known to match the API's. It is false when the API could
	 * not be reached and the local copy (or nothing) is what is on screen.
	 */
	async function load(force = false): Promise<boolean> {
		if (loading.value) return false;
		if (summary.value?.month === month.value && freshFor === month.value && !force) return true;

		const requested = month.value;
		loading.value = true;
		error.value = null;
		// Read beside the request rather than before it: the copy only fills the wait for the answer.
		const copy = summary.value?.month === requested ? null : showCopy(requested);

		try {
			const fresh = await api.summary(requested);
			revision++;
			summary.value = fresh;
			freshFor = requested;
			void writeSummary(fresh, KEEP_ROWS);
			return true;
		} catch (caught) {
			// Out of reach with this month's figures on screen: keep showing them. A copy still on its
			// way counts — offline, the request can fail before the copy is read.
			await copy;
			const showingLocalCopy = summary.value?.month === requested;
			if (!(showingLocalCopy && isNetworkError(caught))) {
				error.value = caught instanceof Error ? caught.message : t('common.errors.loadSummary');
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
		if (await load(true)) void dropSummaries(month.value);
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
		revision++;
		summary.value = null;
		previous.value = null;
		trend.value = null;
		freshFor = null;
		month.value = currentMonth();
	}

	return {
		month,
		summary,
		previous,
		trend,
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
		loadPrevious,
		loadTrend,
		setMonth,
		refresh,
		setBudget,
		setIncomePlan,
		reset,
	};
});
