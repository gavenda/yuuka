<script setup lang="ts">
import CategoryBars from '@/components/CategoryBars.vue';
import MonthAction from '@/components/MonthAction.vue';
import SpendChart from '@/components/SpendChart.vue';
import StatCarousel from '@/components/StatCarousel.vue';
import { savingsRate } from '@/lib/dashboard';
import { useBudgetStore } from '@/stores/budget';
import { useLedgerStore } from '@/stores/ledger';
import { computed, onMounted } from 'vue';
import { t } from '@/i18n';

const budget = useBudgetStore();
const ledger = useLedgerStore();

const currency = computed(() => ledger.displayCurrency);

const month = computed({
	get: () => budget.month,
	set: (next: string) => void budget.setMonth(next),
});

/** Where things stand: what there is, and what came in, went out and was set aside this month. */
const standing = computed(() => {
	const cashflow = budget.summary?.cashflow ?? 0;
	return [
		{ label: t('common.netWorth'), amount: budget.summary?.netWorth ?? 0, watermark: true },
		{ label: t('common.income'), amount: budget.summary?.income ?? 0 },
		{ label: t('common.spent'), amount: budget.summary?.expenses ?? 0 },
		// Only once something has been moved: most months of someone who never files a transfer would read zero.
		...(cashflow > 0 ? [{ label: t('dashboard.setAside'), amount: cashflow, caption: t('dashboard.setAsideCaption') }] : []),
	];
});

/** What the net comes to in words: overspent, or the share of income kept when there was any. */
function netCaption(net: number): string {
	if (net < 0) return t('dashboard.overspent');
	const rate = savingsRate(net, budget.summary?.income ?? 0);
	return rate === null ? t('dashboard.saved') : t('dashboard.savedShare', { percent: rate });
}

/** How the month is going against the plan. */
const progress = computed(() => {
	const net = budget.summary?.net ?? 0;
	return [
		{ label: t('dashboard.netThisMonth'), amount: net, caption: netCaption(net), signed: true },
		{ label: t('dashboard.budgetRemaining'), amount: budget.unspent, caption: t('dashboard.acrossBudgeted') },
		{
			label: t('dashboard.overBudget'),
			amount: budget.overspent,
			caption: budget.overspent < 0 ? t('dashboard.needsAttention') : t('dashboard.nothingOverspent'),
			signed: true,
		},
	];
});

onMounted(() => Promise.all([ledger.load(), budget.load(true)]));
</script>

<template>
	<div class="flex flex-col gap-4 p-4">
		<MonthAction v-model="month" />

		<p v-if="budget.error" class="type-body-medium text-error" role="alert">{{ budget.error }}</p>

		<StatCarousel :stats="standing" :currency="currency" tone="tertiary" />
		<StatCarousel :stats="progress" :currency="currency" />

		<SpendChart :month="budget.month" :days="budget.summary?.dailySpend ?? []" :currency="currency" />

		<CategoryBars :title="t('dashboard.whereMoneyWent')" :entries="budget.expenseBreakdown" :currency="currency" />
	</div>
</template>
