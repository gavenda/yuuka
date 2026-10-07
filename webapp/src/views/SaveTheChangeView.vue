<script setup lang="ts">
import AppIcon from '@/components/AppIcon.vue';
import ConnectedButtonGroup from '@/components/ConnectedButtonGroup.vue';
import SelectionDialog from '@/components/SelectionDialog.vue';
import SelectionItem from '@/components/SelectionItem.vue';
import ToggleSwitch from '@/components/ToggleSwitch.vue';
import { api, ApiError } from '@/lib/api';
import { ACCOUNT_BALANCE_WALLET, ACCOUNT_CIRCLE, BLOCK, CALCULATE, CATEGORY, ERROR, SAVINGS, WALLET } from '@/lib/icons';
import { showSnackbar } from '@/lib/snackbar';
import { useBudgetStore } from '@/stores/budget';
import { useLedgerStore } from '@/stores/ledger';
import type { Account, RoundUpRule } from '@/types';
import { computed, onMounted, ref, watch } from 'vue';
import { t } from '@/i18n';

const ledger = useLedgerStore();
const budget = useBudgetStore();

const ROUND_TO: { value: 1000 | 10000; label: string }[] = [
	{ value: 1000, label: '₱10' },
	{ value: 10000, label: '₱100' },
];

/**
 * What the screen shows. It is what is saved, with one exception: a rule switched on before a destination
 * is chosen cannot be saved, so it waits here, on, until an account is picked.
 */
const enabled = ref(ledger.roundUpRule?.enabled ?? false);
const roundTo = ref<1000 | 10000>(ledger.roundUpRule?.roundTo ?? 1000);
const destinationId = ref(ledger.roundUpRule?.destinationAccountId ?? null);
const categoryId = ref(ledger.roundUpRule?.categoryId ?? null);

// The screen follows the rule: on arrival, once it has loaded, and whenever a save or another device changes it.
watch(
	() => ledger.roundUpRule,
	(rule) => {
		enabled.value = rule?.enabled ?? false;
		roundTo.value = rule?.roundTo ?? 1000;
		destinationId.value = rule?.destinationAccountId ?? null;
		categoryId.value = rule?.categoryId ?? null;
	},
	{ deep: true },
);

const hasDestination = computed(() => ledger.activeAccounts.some((account) => account.id === destinationId.value));
/** The destination's problem is shown once the switch has been thrown or the picker opened, not on arrival. */
const destinationTouched = ref(false);
const destinationError = computed(() =>
	destinationTouched.value && enabled.value && !hasDestination.value ? t('saveTheChange.chooseDestination') : null,
);

/**
 * Every choice here is picked from a list, so it is written as it is made. The one thing that cannot be
 * written is a rule that is on with nowhere for the change to go, so that waits for its destination.
 */
async function persist(): Promise<void> {
	if (enabled.value && !hasDestination.value) return;

	const saved = ledger.roundUpRule;
	const change: Partial<RoundUpRule> = {
		...(enabled.value !== (saved?.enabled ?? false) ? { enabled: enabled.value } : {}),
		...(roundTo.value !== (saved?.roundTo ?? 1000) ? { roundTo: roundTo.value } : {}),
		...(destinationId.value !== (saved?.destinationAccountId ?? null) ? { destinationAccountId: destinationId.value } : {}),
		...(categoryId.value !== (saved?.categoryId ?? null) ? { categoryId: categoryId.value } : {}),
	};
	if (!Object.keys(change).length) return;

	try {
		await ledger.updateRoundUpRule(change);
		await budget.refresh();
	} catch (caught) {
		showSnackbar(caught instanceof ApiError ? caught.message : t('common.couldNotSave'));
	}
}

function setEnabled(next: boolean): void {
	enabled.value = next;
	// Turning the rule on without a destination is the mistake, so it is said the moment the switch is thrown.
	if (next) destinationTouched.value = true;
	void persist();
}

function setRoundTo(next: 1000 | 10000): void {
	roundTo.value = next;
	void persist();
}

const sourceSheetOpen = ref(false);
const destinationSheetOpen = ref(false);
const categorySheetOpen = ref(false);

const sourceAccounts = computed(() => ledger.activeAccounts.filter((account) => account.roundUpSource));
const sourceSummary = computed(() => {
	const sources = sourceAccounts.value;
	if (!sources.length) return t('saveTheChange.noSources');
	return sources.length <= 2 ? sources.map((account) => account.name).join(', ') : t('saveTheChange.sourceCount', sources.length);
});

/**
 * Which accounts round up is part of the rule to the person setting it, but it is kept on each account, and
 * set one account at a time with the narrowest possible change, so an edit of an account's name can never
 * quietly take it off.
 */
async function toggleSource(account: Account): Promise<void> {
	try {
		await api.updateAccount(account.id, { roundUpSource: !account.roundUpSource });
		await ledger.refreshAccounts();
	} catch (caught) {
		showSnackbar(caught instanceof ApiError ? caught.message : t('common.couldNotSave'));
	}
}

const destinationName = computed(
	() => ledger.activeAccounts.find((account) => account.id === destinationId.value)?.name ?? t('saveTheChange.chooseAccount'),
);

function openDestination(): void {
	destinationTouched.value = true;
	destinationSheetOpen.value = true;
}

function pickDestination(id: string): void {
	destinationSheetOpen.value = false;
	destinationId.value = id;
	void persist();
}

/**
 * A round-up posts as an ordinary transfer, so it takes the same Cashflow tree a plain transfer does. A
 * child carries its parent's name beneath it, standing in for the indent a list would give it.
 */
const categoryRows = computed(() => [
	{ id: null as string | null, name: t('common.uncategorized'), parentName: null as string | null },
	...ledger
		.groupForPicker(ledger.transferCategories)
		.flatMap((group) => [
			{ id: group.parent.id, name: group.parent.name, parentName: null },
			...group.children.map((child) => ({ id: child.id, name: child.name, parentName: group.parent.name })),
		]),
]);
const categoryName = computed(() => categoryRows.value.find((row) => row.id === categoryId.value)?.name ?? t('common.uncategorized'));

function pickCategory(id: string | null): void {
	categorySheetOpen.value = false;
	categoryId.value = id;
	void persist();
}

onMounted(() => ledger.load());
</script>

<template>
	<div class="flex flex-col gap-4 p-4">
		<section class="flex flex-col gap-4">
			<h2 class="settings-header">{{ t('saveTheChange.roundUps') }}</h2>
			<div class="settings-group">
				<!-- The whole row throws the switch, as a settings row does. -->
				<div class="settings-row state-layer cursor-pointer" @click="setEnabled(!enabled)">
					<AppIcon :icon="SAVINGS" />
					<span class="min-w-0 flex-1">
						<span class="type-title-medium block">{{ t('saveTheChange.roundUpPurchases') }}</span>
						<span class="type-body-medium block">{{ t('saveTheChange.description') }}</span>
					</span>
					<ToggleSwitch
						:model-value="enabled"
						:label="t('saveTheChange.roundUpPurchases')"
						:icon="false"
						@update:model-value="setEnabled"
					/>
				</div>

				<div class="settings-row flex-col items-stretch">
					<div class="flex items-center gap-4">
						<AppIcon :icon="CALCULATE" />
						<span class="min-w-0 flex-1">
							<span class="type-title-medium block">{{ t('saveTheChange.roundTo') }}</span>
							<span class="type-body-medium block">{{ t('saveTheChange.roundToHint') }}</span>
						</span>
					</div>
					<ConnectedButtonGroup
						:model-value="roundTo"
						:label="t('saveTheChange.roundTo')"
						:options="ROUND_TO"
						@update:model-value="setRoundTo"
					/>
				</div>
			</div>
		</section>

		<section class="flex flex-col gap-4">
			<h2 class="settings-header">{{ t('saveTheChange.accounts') }}</h2>
			<div class="settings-group">
				<button type="button" class="settings-row" aria-haspopup="dialog" @click="sourceSheetOpen = true">
					<AppIcon :icon="WALLET" />
					<span class="min-w-0 flex-1">
						<span class="type-title-medium block">{{ t('saveTheChange.accountsThatRoundUp') }}</span>
						<span class="type-body-medium block">{{ sourceSummary }}</span>
					</span>
				</button>

				<button
					type="button"
					class="settings-row"
					aria-haspopup="dialog"
					:aria-invalid="destinationError ? 'true' : undefined"
					aria-describedby="destination-error"
					@click="openDestination"
				>
					<AppIcon :icon="ACCOUNT_BALANCE_WALLET" />
					<span class="min-w-0 flex-1">
						<span class="type-title-medium block">{{ t('saveTheChange.destination') }}</span>
						<span class="type-body-medium block">{{ destinationName }}</span>
					</span>
				</button>
			</div>

			<!-- Drawn where a text field would draw its supporting text: in the error colour, led by the error icon. -->
			<p v-if="destinationError" id="destination-error" class="type-body-small -mt-3 flex items-center gap-2 px-2 text-error" role="alert">
				<AppIcon :icon="ERROR" :size="16" />
				{{ destinationError }}
			</p>
		</section>

		<section class="flex flex-col gap-4">
			<h2 class="settings-header">{{ t('saveTheChange.category') }}</h2>
			<div class="settings-group">
				<button type="button" class="settings-row" aria-haspopup="dialog" @click="categorySheetOpen = true">
					<AppIcon :icon="CATEGORY" />
					<span class="min-w-0 flex-1">
						<span class="type-title-medium block">{{ t('saveTheChange.cashflowCategory') }}</span>
						<span class="type-body-medium block">{{ categoryName }}</span>
					</span>
				</button>
			</div>
		</section>

		<!-- Any number of accounts: each press opts one in or out, and the dialog stays open. -->
		<SelectionDialog
			:open="sourceSheetOpen"
			:title="t('saveTheChange.chooseAccounts')"
			@close="sourceSheetOpen = false"
			:dismiss-label="t('saveTheChange.done')"
		>
			<SelectionItem
				v-for="account in ledger.activeAccounts"
				:key="account.id"
				:icon="ACCOUNT_CIRCLE"
				:title="account.name"
				:subtitle="account.typeName"
				:selected="account.roundUpSource"
				@click="toggleSource(account)"
			/>
		</SelectionDialog>

		<SelectionDialog :open="destinationSheetOpen" :title="t('saveTheChange.destination')" @close="destinationSheetOpen = false">
			<SelectionItem
				v-for="account in ledger.activeAccounts"
				:key="account.id"
				:icon="ACCOUNT_CIRCLE"
				:title="account.name"
				:subtitle="account.typeName"
				:selected="account.id === destinationId"
				@click="pickDestination(account.id)"
			/>
		</SelectionDialog>

		<SelectionDialog :open="categorySheetOpen" :title="t('saveTheChange.cashflowCategory')" @close="categorySheetOpen = false">
			<SelectionItem
				v-for="row in categoryRows"
				:key="row.id ?? 'none'"
				:icon="row.id === null ? BLOCK : CATEGORY"
				:title="row.name"
				:subtitle="row.parentName"
				:selected="row.id === categoryId"
				@click="pickCategory(row.id)"
			/>
		</SelectionDialog>
	</div>
</template>
