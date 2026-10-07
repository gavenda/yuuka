<script setup lang="ts">
import CategoryBars from '@/components/CategoryBars.vue';
import MonthSwitcher from '@/components/MonthSwitcher.vue';
import SpendChart from '@/components/SpendChart.vue';
import StatCarousel from '@/components/StatCarousel.vue';
import TopBar from '@/components/TopBar.vue';
import { useBudgetStore } from '@/stores/budget';
import { useLedgerStore } from '@/stores/ledger';
import { computed, onMounted } from 'vue';

const budget = useBudgetStore();
const ledger = useLedgerStore();

const currency = computed(() => ledger.displayCurrency);

const month = computed({
	get: () => budget.month,
	set: (next: string) => void budget.setMonth(next),
});

/** Where things stand: what there is, and what came in and went out this month. */
const standing = computed(() => [
	{ label: 'Net worth', amount: budget.summary?.netWorth ?? 0, watermark: true },
	{ label: 'Income', amount: budget.summary?.income ?? 0 },
	{ label: 'Spent', amount: budget.summary?.expenses ?? 0 },
]);

/** How the month is going against the plan. */
const progress = computed(() => {
	const net = budget.summary?.net ?? 0;
	return [
		{ label: 'Net this month', amount: net, caption: net >= 0 ? 'Saved' : 'Overspent', signed: true },
		{ label: 'Budget remaining', amount: budget.unspent, caption: 'Across budgeted categories' },
		{
			label: 'Over budget',
			amount: budget.overspent,
			caption: budget.overspent < 0 ? 'Needs attention' : 'Nothing overspent',
			signed: true,
		},
	];
});

onMounted(() => Promise.all([ledger.load(), budget.load(true)]));
</script>

<template>
	<div class="flex flex-col gap-4 p-4">
		<TopBar><MonthSwitcher v-model="month" /></TopBar>

		<p v-if="budget.error" class="type-body-medium text-error" role="alert">{{ budget.error }}</p>

		<StatCarousel :stats="standing" :currency="currency" tone="tertiary" />
		<StatCarousel :stats="progress" :currency="currency" />

		<SpendChart :month="budget.month" :days="budget.summary?.dailySpend ?? []" :currency="currency" />

		<CategoryBars title="Where the money went" :entries="budget.expenseBreakdown" :currency="currency" />
	</div>
</template>
