<script setup lang="ts">
import AlertDialog from '@/components/AlertDialog.vue';
import BudgetCard from '@/components/BudgetCard.vue';
import ConnectedButtonGroup from '@/components/ConnectedButtonGroup.vue';
import EmptyState from '@/components/EmptyState.vue';
import MoneyText from '@/components/MoneyText.vue';
import MonthAction from '@/components/MonthAction.vue';
import StatCarousel, { type StatItem } from '@/components/StatCarousel.vue';
import TextField from '@/components/TextField.vue';
import { currencySymbol, parseMoney, percentOf, toDecimalString } from '@/lib/money';
import { computeNetPay } from '@/lib/philippinesTax';
import { displayMoney } from '@/lib/privacy';
import { useBudgetStore } from '@/stores/budget';
import { useLedgerStore } from '@/stores/ledger';
import { computed, onMounted, ref, watch } from 'vue';
import { t } from '@/i18n';

const budget = useBudgetStore();
const ledger = useLedgerStore();

const currency = computed(() => ledger.displayCurrency);

/** PH-specific: what's typed is gross pay, and the take-home net is what actually gets budgeted from. */
const isPhp = computed(() => currency.value === 'PHP');

const month = computed({
	get: () => budget.month,
	set: (next: string) => void budget.setMonth(next),
});

const totalPlanned = computed(() => budget.expenseBreakdown.reduce((sum, entry) => sum + entry.planned, 0));
const totalActual = computed(() => budget.expenseBreakdown.reduce((sum, entry) => sum + entry.actual, 0));

/** Everything with a plan against it, expense or cashflow — what a percentage of income has been put towards. */
const totalAllocated = computed(() =>
	[...budget.expenseBreakdown, ...budget.cashflowBreakdown].reduce((sum, entry) => sum + entry.planned, 0),
);
const unallocatedIncome = computed(() => budget.plannedIncome - totalAllocated.value);
/** Unclamped, unlike `percentOf`: over-allocating reads as a negative share. */
const unallocatedPercent = computed(() =>
	budget.plannedIncome > 0 ? Math.round((unallocatedIncome.value / budget.plannedIncome) * 100) : 0,
);

const editingIncome = ref(false);
const savingIncome = ref(false);
const incomeDraft = ref('');

/** PH-only: whether the figure being typed is gross pay to convert, or the budgeted amount itself. */
const incomeMode = ref<'gross' | 'fixed'>('gross');
const usingGross = computed(() => isPhp.value && incomeMode.value === 'gross');

/** The gross figure being typed, parsed live so the PH breakdown can update as they type. */
const grossDraft = computed(() => {
	if (incomeDraft.value.trim() === '') return 0;
	const amount = parseMoney(incomeDraft.value);
	return amount && amount > 0 ? amount : 0;
});

const netPayBreakdown = computed(() => (usingGross.value ? computeNetPay(grossDraft.value) : null));

/** What the closed field should read for the mode currently selected, from what's saved server-side. */
function syncIncomeDraft(): void {
	if (usingGross.value) {
		incomeDraft.value = budget.plannedIncomeGrossAmount ? toDecimalString(budget.plannedIncomeGrossAmount) : '';
	} else {
		incomeDraft.value = budget.plannedIncome > 0 ? toDecimalString(budget.plannedIncome) : '';
	}
}

// The server remembers both the mode and the figure it produced the saved amount from, so
// there's nothing to persist in the browser: every load or refresh — first mount, switching
// months, right after saving — picks it back up here.
watch(
	() => budget.summary,
	(summary) => {
		if (!summary) return;
		incomeMode.value = summary.plannedIncomeMode;
		syncIncomeDraft();
	},
	{ immediate: true },
);

/**
 * A switch is written as it is made. The figure on show stays and only its meaning changes — gross pay to
 * take home from, or the amount budgeted — so switching back and forth loses nothing.
 */
async function switchIncomeMode(mode: 'gross' | 'fixed'): Promise<void> {
	if (mode === incomeMode.value) return;
	const figure = usingGross.value ? grossDraft.value : budget.plannedIncome;
	incomeMode.value = mode;
	incomeDraft.value = figure > 0 ? toDecimalString(figure) : '';
	editingIncome.value = false;
	savingIncome.value = true;
	try {
		if (mode === 'gross') await budget.setIncomePlan(figure > 0 ? computeNetPay(figure).netPay : 0, 'gross', figure);
		else await budget.setIncomePlan(figure, 'fixed');
	} finally {
		savingIncome.value = false;
	}
}

function startEditingIncome(): void {
	editingIncome.value = true;
}

/** Closing without saving drops what was typed, so the figure shown is always one that was saved. */
function cancelEditingIncome(): void {
	editingIncome.value = false;
	syncIncomeDraft();
}

const incomeHint = computed(() => (usingGross.value ? t('budget.grossHint') : t('budget.netHint')));

async function commitIncome(): Promise<void> {
	const amount = incomeDraft.value.trim() === '' ? 0 : parseMoney(incomeDraft.value);
	if (amount === null || amount < 0) return;

	const toSave = usingGross.value && amount > 0 ? computeNetPay(amount).netPay : amount;

	savingIncome.value = true;
	try {
		await budget.setIncomePlan(toSave, usingGross.value ? 'gross' : 'fixed', usingGross.value ? amount : undefined);
		editingIncome.value = false;
	} finally {
		savingIncome.value = false;
	}
}

/** The income figure as the closed card reads: what was typed or saved, or an invitation to set it. */
const incomeLabel = computed(() => {
	if (usingGross.value) return grossDraft.value > 0 ? displayMoney(grossDraft.value, 'PHP') : t('budget.setGross');
	return budget.plannedIncome > 0 ? displayMoney(budget.plannedIncome, currency.value) : t('budget.setIncome');
});

/** The month's plan in figures: what is planned and spent, then — once there is an income to share out — where it has gone. */
const stats = computed<StatItem[]>(() => {
	const list: StatItem[] = [
		{ label: t('common.planned'), amount: totalPlanned.value, caption: t('budget.acrossExpense'), watermark: true },
		{
			label: t('common.spent'),
			amount: totalActual.value,
			caption:
				totalPlanned.value > 0
					? t('budget.percentOfPlan', { percent: percentOf(totalActual.value, totalPlanned.value) })
					: t('budget.noBudget'),
		},
	];

	if (netPayBreakdown.value) {
		list.push({ label: t('budget.netPay'), amount: netPayBreakdown.value.netPay, caption: t('budget.usedAsIncome') });
	}

	if (budget.plannedIncome > 0) {
		list.push(
			{ label: t('budget.allocated'), amount: totalAllocated.value, caption: t('budget.plannedAcross') },
			{
				label: t('budget.unallocated'),
				amount: unallocatedIncome.value,
				caption: t('budget.percentOfIncome', { percent: unallocatedPercent.value }),
				signed: true,
			},
		);
	}

	return list;
});

const hasAnyCategories = computed(() => (budget.summary?.categories.length ?? 0) > 0);

// Together, so the summary's saved copy is not held back by the ledger's round trip.
onMounted(() => Promise.all([ledger.load(), budget.load()]));
</script>

<template>
	<div class="flex flex-col gap-4 p-4">
		<MonthAction v-model="month" />

		<p v-if="budget.error" class="type-body-medium text-error" role="alert">{{ budget.error }}</p>

		<!-- Planned income: what a percentage-based budget is a share of. Pressing the figure edits it in a dialog. -->
		<section class="card flex flex-col items-center p-5">
			<h2 class="type-label-medium pt-3 uppercase">{{ t('budget.plannedIncome') }}</h2>

			<button type="button" class="btn-text type-headline-medium min-h-13 w-full" @click="startEditingIncome">
				{{ incomeLabel }}
			</button>

			<!-- PH-specific: what is typed is gross pay, and the take-home net is what actually gets budgeted from. -->
			<ConnectedButtonGroup
				v-if="isPhp"
				:model-value="incomeMode"
				class="mt-2 w-full"
				@update:model-value="switchIncomeMode"
				:label="t('budget.howIncomeIsSet')"
				:options="[
					{ value: 'gross', label: t('budget.gross') },
					{ value: 'fixed', label: t('budget.fixed') },
				]"
			/>

			<p class="type-body-small pt-1 text-center whitespace-pre-line">{{ incomeHint }}</p>

			<template v-if="netPayBreakdown">
				<h3 class="type-label-medium w-full pt-4">{{ t('budget.contributions') }}</h3>
				<ul class="type-body-medium w-full pt-1">
					<li v-for="line in netPayBreakdown.contributions" :key="line.label" class="flex justify-between gap-3 py-1">
						<span>{{ line.label }}</span>
						<MoneyText :amount="line.amount" currency="PHP" />
					</li>
				</ul>
			</template>

			<template v-if="budget.incomeBreakdown.length">
				<h3 class="type-label-medium w-full pt-4">{{ t('common.income') }}</h3>
				<ul class="type-body-medium w-full pt-1">
					<li v-for="entry in budget.incomeBreakdown" :key="entry.categoryId" class="flex justify-between gap-3 py-1">
						<span>{{ entry.name }}</span>
						<MoneyText :amount="entry.actual" :currency="currency" />
					</li>
				</ul>
			</template>
		</section>

		<StatCarousel :stats="stats" :currency="currency" tone="tertiary" />

		<EmptyState v-if="!hasAnyCategories" mood="stern" :title="t('budget.emptyTitle')" :description="t('budget.emptyDescription')" />

		<!-- Each kind is one segmented list under its heading, as Categories draws its sections. -->
		<section v-if="budget.expenseBreakdown.length" class="flex flex-col">
			<h2 class="settings-header">{{ t('common.expense') }}</h2>
			<div class="group-rows">
				<BudgetCard v-for="entry in budget.expenseBreakdown" :key="entry.categoryId" :entry="entry" :currency="currency" />
			</div>
		</section>

		<!-- Budgeted separately from expenses: a movement into savings or investments is planned, not spent. -->
		<section v-if="budget.cashflowBreakdown.length" class="flex flex-col">
			<h2 class="settings-header">{{ t('common.cashflow') }}</h2>
			<div class="group-rows">
				<BudgetCard v-for="entry in budget.cashflowBreakdown" :key="entry.categoryId" :entry="entry" :currency="currency" />
			</div>
		</section>

		<AlertDialog :open="editingIncome" :title="t('budget.plannedIncome')" role="dialog" @close="cancelEditingIncome">
			<form id="planned-income-form" class="sm:w-96" novalidate @submit.prevent="commitIncome">
				<TextField
					id="planned-income"
					v-model="incomeDraft"
					:label="t('common.amount')"
					inputmode="decimal"
					autofocus
					clearable
					:placeholder="usingGross ? t('budget.grossPlaceholder') : '0.00'"
					:prefix="currencySymbol(usingGross ? 'PHP' : currency)"
					:disabled="savingIncome"
				/>
			</form>

			<template #actions>
				<button type="button" class="btn-text" :disabled="savingIncome" @click="cancelEditingIncome">{{ t('common.cancel') }}</button>
				<button type="submit" form="planned-income-form" class="btn-primary" :disabled="savingIncome">{{ t('common.save') }}</button>
			</template>
		</AlertDialog>
	</div>
</template>
