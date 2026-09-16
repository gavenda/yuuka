import { api } from '@/lib/api';
import { DEFAULT_CURRENCY } from '@/lib/money';
import { defineStore } from 'pinia';
import { computed, ref } from 'vue';
import type { Account, AccountType, Category, Settings } from '@/types';

/**
 * Accounts and categories change rarely but are needed by nearly every view, so
 * they are loaded once and shared rather than refetched per screen.
 */
export const useLedgerStore = defineStore('ledger', () => {
	const accounts = ref<Account[]>([]);
	const accountTypes = ref<AccountType[]>([]);
	const categories = ref<Category[]>([]);
	const settings = ref<Settings | null>(null);
	const loaded = ref(false);
	const loading = ref(false);

	const activeAccounts = computed(() => accounts.value.filter((account) => !account.archived));
	const activeAccountTypes = computed(() => accountTypes.value.filter((type) => !type.archived));

	/**
	 * The currency every aggregate figure is shown in. A user preference, not a
	 * guess at the first account's currency — those describe what each account
	 * holds, which need not be what the reader wants totals in.
	 */
	const displayCurrency = computed(() => settings.value?.displayCurrency ?? DEFAULT_CURRENCY);
	/** Whether a budgeted amount applies to every month or is set per month. Fixed is the default. */
	const budgetMode = computed(() => settings.value?.budgetMode ?? 'fixed');
	/**
	 * Categories usable on a plain transaction, by direction. Transfer-scope
	 * categories are excluded: a transaction carries one category, and these two
	 * sets never mix on the same row.
	 */
	const expenseCategories = computed(() =>
		categories.value.filter((category) => category.appliesTo === 'standard' && category.kind === 'expense'),
	);
	const incomeCategories = computed(() =>
		categories.value.filter((category) => category.appliesTo === 'standard' && category.kind === 'income'),
	);

	/** Categories usable on a transfer — the Cashflow tree. */
	const transferCategories = computed(() => categories.value.filter((category) => category.appliesTo === 'transfer'));

	/** Top-level categories only; budgets are set on these. */
	const parentCategories = computed(() => categories.value.filter((category) => category.parentId === null));

	/**
	 * Groups a flat list into pickable options: each parent followed by its own
	 * children. Choosing a parent or a child are both single choices, which is
	 * what keeps a transaction from carrying two categories at once.
	 */
	function groupForPicker(list: Category[]) {
		const parents = list.filter((category) => category.parentId === null && !category.archived);

		return parents.map((parent) => ({
			parent,
			children: list.filter((category) => category.parentId === parent.id && !category.archived),
		}));
	}

	const accountsById = computed(() => new Map(accounts.value.map((account) => [account.id, account])));
	const categoriesById = computed(() => new Map(categories.value.map((category) => [category.id, category])));

	const netWorth = computed(() => activeAccounts.value.reduce((total, account) => total + account.balance, 0));

	async function load(force = false): Promise<void> {
		if (loaded.value && !force) return;
		loading.value = true;

		try {
			const [accountsResponse, typesResponse, categoriesResponse, settingsResponse] = await Promise.all([
				api.listAccounts(true),
				api.listAccountTypes(true),
				api.listCategories(true),
				api.settings(),
			]);
			accounts.value = accountsResponse.accounts;
			accountTypes.value = typesResponse.accountTypes;
			categories.value = categoriesResponse.categories;
			settings.value = settingsResponse.settings;
			loaded.value = true;
		} finally {
			loading.value = false;
		}
	}

	async function refreshAccounts(): Promise<void> {
		// Types come along: renaming one changes what every account displays.
		const [accountsResponse, typesResponse] = await Promise.all([api.listAccounts(true), api.listAccountTypes(true)]);
		accounts.value = accountsResponse.accounts;
		accountTypes.value = typesResponse.accountTypes;
	}

	async function refreshCategories(): Promise<void> {
		categories.value = (await api.listCategories(true)).categories;
	}

	async function updateSettings(input: Partial<Pick<Settings, 'displayCurrency' | 'budgetMode'>>): Promise<void> {
		settings.value = (await api.updateSettings(input)).settings;
	}

	function reset(): void {
		settings.value = null;
		accounts.value = [];
		accountTypes.value = [];
		categories.value = [];
		loaded.value = false;
	}

	return {
		accounts,
		accountTypes,
		activeAccountTypes,
		settings,
		displayCurrency,
		budgetMode,
		updateSettings,
		categories,
		loaded,
		loading,
		activeAccounts,
		expenseCategories,
		incomeCategories,
		transferCategories,
		parentCategories,
		groupForPicker,
		accountsById,
		categoriesById,
		netWorth,
		load,
		refreshAccounts,
		refreshCategories,
		reset,
	};
});
