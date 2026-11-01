<script setup lang="ts">
import MoneyText from '@/components/MoneyText.vue';
import type { InvestmentSummary } from '@/lib/dashboard';
import { formatLongDate } from '@/lib/dates';
import { percentChange } from '@/lib/investments';
import { computed } from 'vue';
import { t } from '@/i18n';

/**
 * The holdings at their latest close (`InvestmentsCard` in `DashboardCards.kt`): what they are worth, what they
 * have gained since bought and on the day, and the largest stocks. Pressing it opens Investments.
 */
const props = defineProps<{ summary: InvestmentSummary }>();

const gains = computed(() => [
	{
		label: t('investments.unrealised'),
		amount: props.summary.unrealised,
		percent: percentChange(props.summary.unrealised, props.summary.costBasis),
	},
	{
		label: t('investments.today'),
		amount: props.summary.dayChange,
		percent: percentChange(props.summary.dayChange, props.summary.marketValue - props.summary.dayChange),
	},
]);
</script>

<template>
	<RouterLink to="/investments" class="card state-layer focus-ring block p-5">
		<h2 class="type-title-large">{{ t('nav.investments') }}</h2>
		<p v-if="summary.asOf" class="type-body-small">{{ t('dashboard.pricesAsOf', { date: formatLongDate(summary.asOf) }) }}</p>

		<MoneyText :amount="summary.marketValue" class="type-headline-medium mt-3 block [font-variant-numeric:normal]" />

		<div class="mt-3 grid grid-cols-2 gap-6">
			<div v-for="gain in gains" :key="gain.label">
				<p class="type-label-medium text-on-surface-variant">{{ gain.label }}</p>
				<MoneyText :amount="gain.amount" tone="signed-alert" :explicit="gain.amount !== 0" class="type-title-medium block" />
				<p class="type-body-small text-on-surface-variant">{{ gain.percent }}</p>
			</div>
		</div>

		<h3 class="type-label-large mt-4 mb-1 text-on-surface-variant">{{ t('dashboard.largestHoldings') }}</h3>
		<ul>
			<li v-for="stock in summary.largest" :key="stock.symbol" class="flex items-center gap-3 py-1.5">
				<div class="min-w-0 flex-1">
					<p class="type-body-medium">{{ stock.symbol }}</p>
					<p class="type-body-small truncate text-on-surface-variant">{{ stock.name }}</p>
				</div>
				<MoneyText :amount="stock.marketValue" class="type-body-medium" />
			</li>
		</ul>
	</RouterLink>
</template>
