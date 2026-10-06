<script setup lang="ts">
import AppIcon from '@/components/AppIcon.vue';
import ConnectedButtonGroup from '@/components/ConnectedButtonGroup.vue';
import FormDialog from '@/components/FormDialog.vue';
import SelectionDialog from '@/components/SelectionDialog.vue';
import SelectionItem from '@/components/SelectionItem.vue';
import { ApiError } from '@/lib/api';
import { ACCOUNT_CIRCLE, ATTACH_MONEY, CLOSE, CONTRACT_EDIT, DARK_MODE, INFO, NOTE_ALT, SEARCH, SUPERVISOR_ACCOUNT } from '@/lib/icons';
import { currencyName, currencySymbol } from '@/lib/money';
import { showSnackbar } from '@/lib/snackbar';
import { useTheme, type ThemeMode } from '@/lib/theme';
import { useBudgetStore } from '@/stores/budget';
import { useLedgerStore } from '@/stores/ledger';
import type { BudgetMode, Settings } from '@/types';
import { computed, onMounted, ref, watch } from 'vue';

const ledger = useLedgerStore();
const budget = useBudgetStore();
const { mode: themeMode, setMode: setThemeMode } = useTheme();

const appVersion = __APP_VERSION__;

const THEME_MODES: { value: ThemeMode; label: string }[] = [
	{ value: 'system', label: 'System' },
	{ value: 'light', label: 'Light' },
	{ value: 'dark', label: 'Dark' },
];

const BUDGET_MODES: { value: BudgetMode; label: string }[] = [
	{ value: 'fixed', label: 'Fixed' },
	{ value: 'monthly', label: 'Monthly' },
];

/**
 * Every choice here is made from a list, so there is nothing to get wrong and nothing to confirm: a pick is
 * written as it is made, the way the theme has always behaved. The write is local first and cannot fail for
 * want of a network, so only a rejection has anything to say.
 */
async function persist(change: Partial<Pick<Settings, 'displayCurrency' | 'budgetMode' | 'defaultAccountId'>>): Promise<void> {
	try {
		await ledger.updateSettings(change);
		// The summary carries formatted figures nowhere, but refreshing keeps the
		// dashboard consistent with anything a setting touched.
		await budget.refresh();
	} catch (caught) {
		showSnackbar(caught instanceof ApiError ? caught.message : 'Could not save the setting.');
	}
}

const defaultAccountName = computed(
	() => ledger.accounts.find((account) => account.id === ledger.defaultAccountId)?.name ?? 'First active account',
);
const accountsByType = computed(() => [...ledger.activeAccounts].sort((a, b) => (a.typeName ?? '').localeCompare(b.typeName ?? '')));
const accountSheetOpen = ref(false);

function pickDefaultAccount(id: string): void {
	accountSheetOpen.value = false;
	if (id !== ledger.defaultAccountId) void persist({ defaultAccountId: id });
}

/** The default currency and nine of the most traded: what the display-currency list offers before a search. */
const COMMON_CURRENCIES = ['PHP', 'USD', 'EUR', 'JPY', 'GBP', 'CNY', 'AUD', 'CAD', 'SGD', 'HKD'];

/** Every currency the platform knows, for the search to reach. An old browser that cannot list them still has the common ten. */
const allCurrencies: string[] = (() => {
	try {
		return Intl.supportedValuesOf('currency');
	} catch {
		return COMMON_CURRENCIES;
	}
})();

const currencyDialogOpen = ref(false);
const currencySearch = ref('');
/** Pressing a row only marks it. Changing the currency relabels every figure in the app, so it waits for Save. */
const pickedCurrency = ref(ledger.displayCurrency);

watch(currencyDialogOpen, (open) => {
	if (!open) return;
	currencySearch.value = '';
	pickedCurrency.value = ledger.displayCurrency;
});

/**
 * Before anything is typed the list is the ten currencies most people would pick from, not the hundreds the
 * platform knows; a search still reaches every one of them. The currency in use leads either list — and joins
 * the short one if it is not among the ten — so it is the first thing seen.
 */
const shownCurrencies = computed(() => {
	const current = ledger.displayCurrency;
	const query = currencySearch.value.trim().toLowerCase();
	const codes = query
		? allCurrencies.filter((code) => code.toLowerCase().includes(query) || currencyName(code).toLowerCase().includes(query))
		: [...new Set([current, ...COMMON_CURRENCIES])];

	return [...codes]
		.sort((a, b) => Number(b === current) - Number(a === current))
		.map((code) => ({ code, name: currencyName(code), symbol: currencySymbol(code) }));
});

function saveCurrency(): void {
	currencyDialogOpen.value = false;
	if (pickedCurrency.value !== ledger.displayCurrency) void persist({ displayCurrency: pickedCurrency.value });
}

onMounted(() => ledger.load());
</script>

<template>
	<div class="flex flex-col gap-4 p-4">
		<section class="flex flex-col gap-4">
			<h2 class="settings-header">Currency</h2>
			<div class="settings-group">
				<button type="button" class="settings-row" aria-haspopup="dialog" @click="currencyDialogOpen = true">
					<AppIcon :icon="ATTACH_MONEY" />
					<span class="min-w-0 flex-1">
						<span class="type-title-medium block">Display currency</span>
						<span class="type-body-medium block">{{ ledger.displayCurrency }}</span>
					</span>
					<span class="type-headline-small" aria-hidden="true">{{ currencySymbol(ledger.displayCurrency) }}</span>
				</button>
			</div>
		</section>

		<section class="flex flex-col gap-4">
			<h2 class="settings-header">Appearance</h2>
			<div class="settings-group">
				<div class="settings-row flex-col items-stretch">
					<div class="flex items-center gap-4">
						<AppIcon :icon="DARK_MODE" />
						<span class="min-w-0 flex-1">
							<span class="type-title-medium block">App theme</span>
							<span class="type-body-medium block">Choose how your app looks</span>
						</span>
					</div>
					<ConnectedButtonGroup :model-value="themeMode" label="App theme" :options="THEME_MODES" @update:model-value="setThemeMode" />
				</div>
			</div>
		</section>

		<section class="flex flex-col gap-4">
			<h2 class="settings-header">Budgeting</h2>
			<div class="settings-group">
				<div class="settings-row flex-col items-stretch">
					<div class="flex items-center gap-4">
						<AppIcon :icon="NOTE_ALT" />
						<span class="min-w-0 flex-1">
							<span class="type-title-medium block">Mode</span>
							<span class="type-body-medium block">Choose whether you budget monthly or not</span>
						</span>
					</div>
					<ConnectedButtonGroup
						:model-value="ledger.budgetMode"
						label="Budget mode"
						:options="BUDGET_MODES"
						@update:model-value="(mode) => mode !== ledger.budgetMode && persist({ budgetMode: mode })"
					/>
				</div>
			</div>
		</section>

		<section class="flex flex-col gap-4">
			<h2 class="settings-header">Accounts</h2>
			<div class="settings-group">
				<!-- A list to manage rather than a value to pick, so it is a screen of its own. -->
				<RouterLink to="/settings/account-types" class="settings-row state-layer focus-ring">
					<AppIcon :icon="CONTRACT_EDIT" />
					<span class="min-w-0 flex-1">
						<span class="type-title-medium block">Account types</span>
						<span class="type-body-medium block">Add, rename or retire the labels your accounts are grouped under</span>
					</span>
				</RouterLink>
			</div>
		</section>

		<section class="flex flex-col gap-4">
			<h2 class="settings-header">Transactions</h2>
			<div class="settings-group">
				<button type="button" class="settings-row" aria-haspopup="dialog" @click="accountSheetOpen = true">
					<AppIcon :icon="SUPERVISOR_ACCOUNT" />
					<span class="min-w-0 flex-1">
						<span class="type-title-medium block">Default account</span>
						<span class="type-body-medium block">{{ defaultAccountName }}</span>
					</span>
				</button>
			</div>
		</section>

		<section class="flex flex-col gap-4">
			<h2 class="settings-header">About</h2>
			<div class="settings-group">
				<div class="settings-row">
					<AppIcon :icon="INFO" />
					<span class="min-w-0 flex-1">
						<span class="type-title-medium block">Version</span>
						<span class="type-body-medium block">{{ appVersion }}</span>
					</span>
				</div>
			</div>
		</section>

		<!-- Every currency there is, behind a search. Its first row is drawn like a search bar. -->
		<FormDialog
			:open="currencyDialogOpen"
			title="Display currency"
			:save-enabled="pickedCurrency !== ledger.displayCurrency"
			:dirty="pickedCurrency !== ledger.displayCurrency"
			@close="currencyDialogOpen = false"
			@save="saveCurrency"
		>
			<label class="search-field flex-none">
				<AppIcon :icon="SEARCH" />
				<input
					v-model="currencySearch"
					type="search"
					autofocus
					aria-label="Search currencies"
					placeholder="Search currency name or code..."
				/>
				<button v-if="currencySearch" type="button" class="btn-icon -mr-2" aria-label="Clear" @click="currencySearch = ''">
					<AppIcon :icon="CLOSE" />
				</button>
			</label>

			<div class="selection-list">
				<SelectionItem
					v-for="currency in shownCurrencies"
					:key="currency.code"
					:title="currency.code"
					:subtitle="currency.name"
					:badge="currency.symbol"
					:selected="currency.code === pickedCurrency"
					@click="pickedCurrency = currency.code"
				/>
			</div>
		</FormDialog>

		<SelectionDialog :open="accountSheetOpen" title="Select default account" @close="accountSheetOpen = false">
			<SelectionItem
				v-for="account in accountsByType"
				:key="account.id"
				:icon="ACCOUNT_CIRCLE"
				:title="account.name"
				:subtitle="account.typeName"
				:selected="account.id === ledger.defaultAccountId"
				@click="pickDefaultAccount(account.id)"
			/>
		</SelectionDialog>
	</div>
</template>
