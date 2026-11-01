<script setup lang="ts">
import AccountsCard from '@/components/AccountsCard.vue';
import AheadOfPaceCard from '@/components/AheadOfPaceCard.vue';
import CategoryBars from '@/components/CategoryBars.vue';
import IncomeSpendChart from '@/components/IncomeSpendChart.vue';
import InvestmentsCard from '@/components/InvestmentsCard.vue';
import MonthAction from '@/components/MonthAction.vue';
import NetWorthChart from '@/components/NetWorthChart.vue';
import OverBudgetCard from '@/components/OverBudgetCard.vue';
import PlanCard from '@/components/PlanCard.vue';
import SpendChart from '@/components/SpendChart.vue';
import StatCard from '@/components/StatCard.vue';
import StillToPostCard from '@/components/StillToPostCard.vue';
import TopPayeesCard from '@/components/TopPayeesCard.vue';
import StatCarousel from '@/components/StatCarousel.vue';
import {
	accountGroups,
	aheadOfPace,
	budgetPace,
	categoryChanges,
	incomeProgress,
	investmentSummary,
	mixedCurrencies,
	monthName,
	overBudget,
	safeToSpend,
	savingsRate,
	stillToPost,
} from '@/lib/dashboard';
import { displayMoney } from '@/lib/privacy';
import { useBudgetStore } from '@/stores/budget';
import { useInvestmentStore } from '@/stores/investments';
import { useLedgerStore } from '@/stores/ledger';
import { useSubscriptionStore } from '@/stores/subscriptions';
import { currentMonth, formatMonth } from '@/lib/dates';
import { computed, onMounted, watch } from 'vue';
import { t } from '@/i18n';

/**
 * Four sections, each left out when it has nothing to say (`DashboardScreen.kt`): the month on show (one
 * leading figure, the rest of its figures, and the plan), what needs attention, where the month's money went,
 * and where things stand overall. From an 840px page the last stands in a column of its own.
 */
const budget = useBudgetStore();
const ledger = useLedgerStore();
const subscriptionStore = useSubscriptionStore();
const investments = useInvestmentStore();

const currency = computed(() => ledger.displayCurrency);

const month = computed({
	get: () => budget.month,
	set: (next: string) => void budget.setMonth(next),
});

/** What the net comes to in words: overspent, or the share of income kept when there was any. */
function netCaption(net: number): string {
	if (net < 0) return t('dashboard.overspent');
	const rate = savingsRate(net, budget.summary?.income ?? 0);
	return rate === null ? t('dashboard.saved') : t('dashboard.savedShare', { percent: rate });
}

/** What can still be spent each day while the month runs with a plan left; the net leads the month otherwise. */
const safe = computed(() => safeToSpend(pace.value));

const lead = computed(() => {
	const net = budget.summary?.net ?? 0;
	const left = safe.value;
	return left
		? {
				label: t('dashboard.safeToSpend'),
				amount: left.perDay,
				caption: t('dashboard.safeToSpendLeft', { amount: displayMoney(left.left, currency.value), count: left.daysLeft }, left.daysLeft),
			}
		: { label: t('dashboard.netThisMonth'), amount: net, caption: netCaption(net) };
});

/** The month's other figures: what came in and went out, the net when it is not leading, and what was set aside. */
const figures = computed(() => {
	const net = budget.summary?.net ?? 0;
	const cashflow = budget.summary?.cashflow ?? 0;
	// A copy saved before the summary carried round-ups has none, so it reads as nothing rounded up.
	const roundUps = budget.summary?.roundUps ?? 0;
	return [
		{ label: t('common.income'), amount: budget.summary?.income ?? 0 },
		{ label: t('common.spent'), amount: budget.summary?.expenses ?? 0 },
		...(safe.value ? [{ label: t('dashboard.netThisMonth'), amount: net, signed: true }] : []),
		// Only once something has been moved: most months of someone who never files a transfer would read zero.
		...(cashflow > 0 ? [{ label: t('dashboard.setAside'), amount: cashflow, tone: 'secondary' as const }] : []),
		...(roundUps > 0 ? [{ label: t('dashboard.roundedUp'), amount: roundUps, tone: 'secondary' as const }] : []),
	];
});

/** The month against its plans; the card is left out when there is neither. */
const pace = computed(() => budgetPace(budget.summary?.categories ?? [], budget.month));
const incomePercent = computed(() => incomeProgress(budget.summary?.income ?? 0, budget.summary?.plannedIncome ?? 0));

/** The categories past their plan, and those heading there while the month runs. */
const over = computed(() => overBudget(budget.summary?.categories ?? []));
const ahead = computed(() => aheadOfPace(budget.summary?.categories ?? [], budget.month));

/** One source would only repeat the income figure, so it takes two to be worth a chart. */
const manySources = computed(() => budget.incomeBreakdown.filter((entry) => entry.actual > 0).length >= 2);

/** Every month after the one under way would just be the whole list again. */
const upcoming = computed(() => (budget.month === currentMonth() ? stillToPost(subscriptionStore.subscriptions, budget.month) : []));

/** Whether anything needs attention; the section is left out otherwise. */
const attention = computed(() => over.value.length > 0 || ahead.value.length > 0 || upcoming.value.length > 0);

/** The holdings at their latest close, as Investments shows them; the card is left out with nothing held. */
const holdings = computed(() => investmentSummary(investments.held));

/** The accounts in use under their types, each in its own currency. */
const groups = computed(() => accountGroups(budget.summary?.accounts ?? []));
const mixed = computed(() => mixedCurrencies(budget.summary?.accounts ?? []));

/** "Where the money went" against the month before, once that month is known. */
const changes = computed(() => (budget.previous ? categoryChanges(budget.summary?.categories ?? [], budget.previous.categories) : null));
const comparedTo = computed(() => (budget.previous ? monthName(budget.previous.month) : null));

watch(
	() => budget.month,
	() => {
		void budget.loadPrevious();
		void budget.loadTrend();
	},
	{ immediate: true },
);

/** The months up to the one on show; the bars take the last six, which is as many pairs as a phone holds. */
const trendMonths = computed(() => (budget.trend?.month === budget.month ? budget.trend.months : []));
const barMonths = computed(() => trendMonths.value.slice(-6));

onMounted(() => Promise.all([ledger.load(), budget.load(true), subscriptionStore.load(), investments.load()]));
</script>

<template>
	<div class="p-4">
		<MonthAction v-model="month" />

		<p v-if="budget.error" class="type-body-medium mb-4 text-error" role="alert">{{ budget.error }}</p>

		<div class="flex flex-col gap-8 @min-[840px]:grid @min-[840px]:grid-cols-2 @min-[840px]:items-start @min-[840px]:gap-6">
			<div class="flex flex-col gap-8">
				<section class="flex flex-col gap-4">
					<h2 class="type-title-medium text-on-surface-variant">{{ formatMonth(budget.month) }}</h2>
					<StatCard :label="lead.label" :amount="lead.amount" :currency="currency" :caption="lead.caption" hero tone="tertiary" watermark />
					<!-- In a column beside another, the row would scroll into the gap between them. -->
					<StatCarousel :stats="figures" :currency="currency" :bleed="false" class="-mx-4 px-4 @min-[840px]:mx-0 @min-[840px]:px-0" />
					<PlanCard
						v-if="pace || incomePercent !== null"
						:pace="pace"
						:income="budget.summary?.income ?? 0"
						:planned-income="budget.summary?.plannedIncome ?? 0"
						:income-percent="incomePercent"
						:currency="currency"
					/>
				</section>

				<section v-if="attention" class="flex flex-col gap-4">
					<h2 class="type-title-medium text-on-surface-variant">{{ t('dashboard.section.attention') }}</h2>
					<OverBudgetCard v-if="over.length" :entries="over" :currency="currency" />
					<AheadOfPaceCard v-if="ahead.length && pace?.day" :entries="ahead" :day="pace.day" :days="pace.days" :currency="currency" />
					<StillToPostCard v-if="upcoming.length" :subscriptions="upcoming" :currency="currency" />
				</section>

				<section class="flex flex-col gap-4">
					<h2 class="type-title-medium text-on-surface-variant">{{ t('dashboard.section.breakdown') }}</h2>
					<SpendChart :month="budget.month" :days="budget.summary?.dailySpend ?? []" :currency="currency" />
					<CategoryBars
						:title="t('dashboard.whereMoneyWent')"
						:entries="budget.expenseBreakdown"
						:currency="currency"
						:changes="changes"
						:compared-to="comparedTo"
					/>
					<TopPayeesCard v-if="budget.summary?.topPayees?.length" :payees="budget.summary.topPayees" :currency="currency" />
					<CategoryBars
						v-if="manySources"
						:title="t('dashboard.whereMoneyCameFrom')"
						:entries="budget.incomeBreakdown"
						:currency="currency"
					/>
				</section>
			</div>

			<section class="flex flex-col gap-4">
				<h2 class="type-title-medium text-on-surface-variant">{{ t('dashboard.section.overall') }}</h2>
				<StatCard :label="t('common.netWorth')" :amount="budget.summary?.netWorth ?? 0" :currency="currency" />
				<NetWorthChart v-if="trendMonths.length >= 2" :months="trendMonths" :currency="currency" />
				<IncomeSpendChart v-if="barMonths.length >= 2" :months="barMonths" :currency="currency" />
				<InvestmentsCard v-if="holdings" :summary="holdings" />
				<AccountsCard v-if="groups.length" :groups="groups" :mixed="mixed" />
			</section>
		</div>
	</div>
</template>
