import { api, isNetworkError } from '@/lib/api';
import { readCache, writeCache } from '@/lib/cache';
import { DEFAULT_CURRENCY } from '@/lib/money';
import { defineStore } from 'pinia';
import { computed, ref } from 'vue';
import type { Account, AccountType, Category, RoundUpRule, Settings, Tag } from '@/types';

const CACHE_KEY = 'ledger';

/** Everything the store holds, as kept in the browser's local copy. */
interface LedgerSnapshot {
	accounts: Account[];
	accountTypes: AccountType[];
	categories: Category[];
	tags: Tag[];
	settings: Settings;
	roundUpRule: RoundUpRule;
}

function isLedgerSnapshot(data: unknown): data is LedgerSnapshot {
	const snapshot = data as Partial<LedgerSnapshot> | null;
	return (
		Array.isArray(snapshot?.accounts) &&
		Array.isArray(snapshot.accountTypes) &&
		Array.isArray(snapshot.categories) &&
		Array.isArray(snapshot.tags) &&
		typeof snapshot.settings === 'object' &&
		snapshot.settings !== null &&
		typeof snapshot.roundUpRule === 'object' &&
		snapshot.roundUpRule !== null
	);
}

/**
 * Accounts and categories change rarely but are needed by nearly every view, so
 * they are loaded once and shared rather than refetched per screen.
 *
 * The first `load` paints from the browser's local copy, then replaces it with
 * what the API says: the API is the source of truth, and the copy is only what
 * was last seen. Every refresh that succeeds rewrites the copy.
 */
export const useLedgerStore = defineStore('ledger', () => {
	const accounts = ref<Account[]>([]);
	const accountTypes = ref<AccountType[]>([]);
	const categories = ref<Category[]>([]);
	const tags = ref<Tag[]>([]);
	const settings = ref<Settings | null>(null);
	const roundUpRule = ref<RoundUpRule | null>(null);
	/** Whether there is a ledger to show — from the API, or from the local copy while it is out of reach. */
	const loaded = ref(false);
	const loading = ref(false);
	/** Whether the API has answered since this page opened, which is what stops `load` fetching again. */
	let synced = false;

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
	/** Which account a new transaction should open on. Null falls back to the first active account. */
	const defaultAccountId = computed(() => settings.value?.defaultAccountId ?? null);
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

	/** Shows the local copy, if there is one. */
	function hydrate(): void {
		const snapshot = readCache(CACHE_KEY, isLedgerSnapshot);
		if (!snapshot) return;

		accounts.value = snapshot.accounts;
		accountTypes.value = snapshot.accountTypes;
		categories.value = snapshot.categories;
		tags.value = snapshot.tags;
		settings.value = snapshot.settings;
		roundUpRule.value = snapshot.roundUpRule;
		loaded.value = true;
	}

	/** Rewrites the local copy from what is on screen. Every assignment from the API ends here. */
	function persist(): void {
		if (!loaded.value || !settings.value || !roundUpRule.value) return;

		writeCache(CACHE_KEY, {
			accounts: accounts.value,
			accountTypes: accountTypes.value,
			categories: categories.value,
			tags: tags.value,
			settings: settings.value,
			roundUpRule: roundUpRule.value,
		} satisfies LedgerSnapshot);
	}

	async function load(force = false): Promise<void> {
		if (!loaded.value) hydrate();
		if (synced && !force) return;
		loading.value = true;

		try {
			const [accountsResponse, typesResponse, categoriesResponse, tagsResponse, settingsResponse, roundUpResponse] = await Promise.all([
				api.listAccounts(true),
				api.listAccountTypes(true),
				api.listCategories(true),
				api.listTags(),
				api.settings(),
				api.getRoundUpRule(),
			]);
			accounts.value = accountsResponse.accounts;
			accountTypes.value = typesResponse.accountTypes;
			categories.value = categoriesResponse.categories;
			tags.value = tagsResponse.tags;
			settings.value = settingsResponse.settings;
			roundUpRule.value = roundUpResponse.roundUpRule;
			loaded.value = true;
			synced = true;
			persist();
		} catch (caught) {
			// Out of reach with a copy on screen: keep showing it. A later load tries again.
			if (!loaded.value || !isNetworkError(caught)) throw caught;
		} finally {
			loading.value = false;
		}
	}

	async function refreshAccounts(): Promise<void> {
		// Types come along: renaming one changes what every account displays.
		const [accountsResponse, typesResponse] = await Promise.all([api.listAccounts(true), api.listAccountTypes(true)]);
		accounts.value = accountsResponse.accounts;
		accountTypes.value = typesResponse.accountTypes;
		persist();
	}

	async function refreshCategories(): Promise<void> {
		categories.value = (await api.listCategories(true)).categories;
		persist();
	}

	async function refreshTags(): Promise<void> {
		tags.value = (await api.listTags()).tags;
		persist();
	}

	async function updateSettings(input: Partial<Pick<Settings, 'displayCurrency' | 'budgetMode' | 'defaultAccountId'>>): Promise<void> {
		settings.value = (await api.updateSettings(input)).settings;
		persist();
	}

	async function updateRoundUpRule(
		input: Partial<Pick<RoundUpRule, 'enabled' | 'roundTo' | 'destinationAccountId' | 'categoryId'>>,
	): Promise<void> {
		roundUpRule.value = (await api.updateRoundUpRule(input)).roundUpRule;
		persist();
	}

	function reset(): void {
		settings.value = null;
		roundUpRule.value = null;
		accounts.value = [];
		accountTypes.value = [];
		categories.value = [];
		tags.value = [];
		loaded.value = false;
		synced = false;
	}

	return {
		accounts,
		accountTypes,
		activeAccountTypes,
		settings,
		roundUpRule,
		displayCurrency,
		budgetMode,
		defaultAccountId,
		updateSettings,
		updateRoundUpRule,
		categories,
		tags,
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
		refreshTags,
		reset,
	};
});
