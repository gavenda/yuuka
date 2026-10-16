<script setup lang="ts">
import PlanMeter from '@/components/PlanMeter.vue';
import { paceHealth, type BudgetPace } from '@/lib/dashboard';
import { DEFAULT_CURRENCY } from '@/lib/money';
import { STATUS } from '@/lib/palette';
import { displayMoney } from '@/lib/privacy';
import { computed } from 'vue';
import { t } from '@/i18n';

/**
 * How the month stands against its plans (`PlanCard` in `DashboardCards.kt`): the expense budget used, with a
 * mark where the month has got to while it runs, and the income received against what was planned. Each is
 * left out without a plan to be measured against, and the card with them.
 */
const props = withDefaults(
	defineProps<{ pace: BudgetPace | null; income: number; plannedIncome: number; incomePercent: number | null; currency?: string }>(),
	{ currency: DEFAULT_CURRENCY },
);

const paceLines = computed(() => {
	const pace = props.pace;
	if (!pace) return [];
	const health = paceHealth(pace);
	const status =
		health === 'critical'
			? t('dashboard.pace.past')
			: pace.day === null
				? t('dashboard.pace.within')
				: t(health === 'warning' ? 'dashboard.pace.ahead' : 'dashboard.pace.keeping', { day: pace.day, days: pace.days });
	return [
		t('dashboard.spentOfPlanned', { spent: displayMoney(pace.spent, props.currency), planned: displayMoney(pace.planned, props.currency) }),
		status,
	];
});
</script>

<template>
	<section class="card flex flex-col gap-4 p-5">
		<h2 class="type-title-large">{{ t('dashboard.againstPlan') }}</h2>

		<PlanMeter
			v-if="pace"
			:label="t('dashboard.budgetUsed')"
			:percent="pace.percent"
			:color="STATUS[paceHealth(pace)]"
			:marker="pace.day === null ? null : pace.day / pace.days"
			:lines="paceLines"
		/>

		<PlanMeter
			v-if="incomePercent !== null"
			:label="t('dashboard.incomeReceived')"
			:percent="incomePercent"
			:color="STATUS.good"
			:lines="[t('dashboard.spentOfPlanned', { spent: displayMoney(income, currency), planned: displayMoney(plannedIncome, currency) })]"
		/>
	</section>
</template>
