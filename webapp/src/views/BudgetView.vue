<script setup lang="ts">
import BudgetCard from '@/components/BudgetCard.vue';
import ConnectedButtonGroup from '@/components/ConnectedButtonGroup.vue';
import DenseField from '@/components/DenseField.vue';
import EmptyState from '@/components/EmptyState.vue';
import MoneyText from '@/components/MoneyText.vue';
import MonthSwitcher from '@/components/MonthSwitcher.vue';
import StatCarousel, { type StatItem } from '@/components/StatCarousel.vue';
import TopBar from '@/components/TopBar.vue';
import { currencySymbol, parseMoney, percentOf, toDecimalString } from '@/lib/money';
import { computeNetPay } from '@/lib/philippinesTax';
import { displayMoney } from '@/lib/privacy';
import { useBudgetStore } from '@/stores/budget';
import { useLedgerStore } from '@/stores/ledger';
import { computed, onMounted, ref, watch } from 'vue';

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

// Switching modes locally, before saving, shows what was last saved for that mode.
watch(incomeMode, () => {
	if (!isPhp.value) return;
	editingIncome.value = false;
	syncIncomeDraft();
});

function startEditingIncome(): void {
	editingIncome.value = true;
}

/** Closing without saving drops what was typed, so the figure shown is always one that was saved. */
function cancelEditingIncome(): void {
	editingIncome.value = false;
	syncIncomeDraft();
}

const incomeHint = computed(() =>
	usingGross.value
		? 'Enter your gross monthly pay.\nThe take-home net is what you budget from.'
		: 'Set what you expect to bring in.\nBudget a category as a percentage of it.',
);

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
	if (usingGross.value) return grossDraft.value > 0 ? displayMoney(grossDraft.value, 'PHP') : 'Set gross income';
	return budget.plannedIncome > 0 ? displayMoney(budget.plannedIncome, currency.value) : 'Set income';
});

/** The month's plan in figures: what is planned and spent, then — once there is an income to share out — where it has gone. */
const stats = computed<StatItem[]>(() => {
	const list: StatItem[] = [
		{ label: 'Planned', amount: totalPlanned.value, caption: 'Across expense categories' },
		{
			label: 'Spent',
			amount: totalActual.value,
			caption: totalPlanned.value > 0 ? `${percentOf(totalActual.value, totalPlanned.value)}% of plan` : 'No plan set',
		},
	];

	if (netPayBreakdown.value) {
		list.push({ label: 'Net pay', amount: netPayBreakdown.value.netPay, caption: 'Used as planned income', compact: true });
	}

	if (budget.plannedIncome > 0) {
		list.push(
			{ label: 'Allocated', amount: totalAllocated.value, caption: 'Planned across categories', compact: true },
			{
				label: 'Unallocated',
				amount: unallocatedIncome.value,
				caption: `${unallocatedPercent.value}% of planned income`,
				signed: true,
				compact: true,
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
		<TopBar><MonthSwitcher v-model="month" /></TopBar>

		<p v-if="budget.error" class="type-body-medium text-error" role="alert">{{ budget.error }}</p>

		<!-- Planned income: what a percentage-based budget is a share of. Pressing the figure edits it in place. -->
		<section class="card flex flex-col items-center p-5">
			<h2 class="type-label-medium pt-3 uppercase">Planned income</h2>

			<!-- The field and its buttons are drawn for an ordinary surface, so they sit on an inset panel of one. -->
			<form
				v-if="editingIncome"
				class="mt-3 w-full rounded-lg bg-surface p-3 [--surface-under:var(--color-surface)]"
				novalidate
				@submit.prevent="commitIncome"
			>
				<DenseField
					id="planned-income"
					v-model="incomeDraft"
					class="type-headline-small"
					label="Planned income"
					inputmode="decimal"
					autofocus
					clearable
					:placeholder="usingGross ? 'Gross 0.00' : '0.00'"
					:prefix="currencySymbol(usingGross ? 'PHP' : currency)"
					:disabled="savingIncome"
					@keydown.esc="cancelEditingIncome"
				/>
				<div class="flex gap-2 pt-2">
					<button type="button" class="btn-text flex-1" :disabled="savingIncome" @click="cancelEditingIncome">Cancel</button>
					<button type="submit" class="btn-primary flex-1" :disabled="savingIncome">Save</button>
				</div>
			</form>

			<button v-else type="button" class="btn-text type-headline-medium min-h-13 w-full" @click="startEditingIncome">
				{{ incomeLabel }}
			</button>

			<!-- PH-specific: what is typed is gross pay, and the take-home net is what actually gets budgeted from. -->
			<ConnectedButtonGroup
				v-if="isPhp"
				v-model="incomeMode"
				class="mt-2 w-full"
				label="How planned income is set"
				:options="[
					{ value: 'gross', label: 'Gross' },
					{ value: 'fixed', label: 'Fixed' },
				]"
			/>

			<p class="type-body-small pt-1 text-center whitespace-pre-line">{{ incomeHint }}</p>

			<template v-if="netPayBreakdown">
				<h3 class="type-label-medium w-full pt-4">Monthly contributions</h3>
				<ul class="type-body-medium w-full pt-1">
					<li v-for="line in netPayBreakdown.contributions" :key="line.label" class="flex justify-between gap-3 py-1">
						<span>{{ line.label }}</span>
						<MoneyText :amount="line.amount" currency="PHP" />
					</li>
				</ul>
			</template>

			<template v-if="budget.incomeBreakdown.length">
				<h3 class="type-label-medium w-full pt-4">Income</h3>
				<ul class="type-body-medium w-full pt-1">
					<li v-for="entry in budget.incomeBreakdown" :key="entry.categoryId" class="flex justify-between gap-3 py-1">
						<span>{{ entry.name }}</span>
						<MoneyText :amount="entry.actual" :currency="currency" />
					</li>
				</ul>
			</template>
		</section>

		<StatCarousel :stats="stats" :currency="currency" tone="tertiary" />

		<EmptyState v-if="!hasAnyCategories" title="No categories yet" description="Budgets are set per category, so create a few first." />

		<template v-if="budget.expenseBreakdown.length">
			<h2 class="type-title-small">Expense</h2>
			<BudgetCard v-for="entry in budget.expenseBreakdown" :key="entry.categoryId" :entry="entry" :currency="currency" />
		</template>

		<!-- Budgeted separately from expenses: a movement into savings or investments is planned, not spent. -->
		<template v-if="budget.cashflowBreakdown.length">
			<h2 class="type-title-small">Cashflow</h2>
			<BudgetCard v-for="entry in budget.cashflowBreakdown" :key="entry.categoryId" :entry="entry" :currency="currency" />
		</template>
	</div>
</template>
