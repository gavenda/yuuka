import { api, isNetworkError } from '@/lib/api';
import { readLedger, replaceAccounts, replaceCategories, replaceLedger, replaceTags } from '@/lib/cache';
import { DEFAULT_CURRENCY } from '@/lib/money';
import { defineStore } from 'pinia';
import { computed, ref } from 'vue';
import type { Account, AccountType, Category, RoundUpRule, Settings, Tag } from '@/types';

/**
 * Accounts and categories change rarely but are needed by nearly every view, so
 * they are loaded once and shared rather than refetched per screen.
 *
 * The first `load` paints from the browser's local copy, then replaces it with
 * what the API says: the API is the source of truth, and the copy is only what
 * was last seen. Every refresh that succeeds replaces the rows it fetched in the
 * copy; settings and the round-up rule are saved there by the API layer as they
 * change.
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
	const expenseCategories = computed(() => categories.value.filter((category) => category.kind === 'expense'));
	const incomeCategories = computed(() => categories.value.filter((category) => category.kind === 'income'));

	/** Categories usable on a transfer — the Cashflow tree. */
	const transferCategories = computed(() => categories.value.filter((category) => category.kind === 'transfer'));

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

	/** Bumped whenever what is on screen is replaced, so a copy still being read cannot land on top of it. */
	let revision = 0;

	/** Shows the local copy, if there is one and nothing has replaced what is on screen since it was asked for. */
	async function hydrate(): Promise<void> {
		const asked = revision;
		const copy = await readLedger();
		if (!copy || revision !== asked) return;

		revision++;
		accounts.value = copy.accounts;
		accountTypes.value = copy.accountTypes;
		categories.value = copy.categories;
		tags.value = copy.tags;
		settings.value = copy.settings;
		roundUpRule.value = copy.roundUpRule;
		loaded.value = true;
	}

	/**
	 * Resolves whether the API answered. An unreachable one with a copy on screen resolves false
	 * rather than throwing; anything else that went wrong throws.
	 */
	async function load(force = false): Promise<boolean> {
		if (synced && !force) return true;
		loading.value = true;
		// Read beside the request rather than before it: the copy only fills the wait for the answer.
		const copy = loaded.value ? null : hydrate();

		try {
			const [accountsResponse, typesResponse, categoriesResponse, tagsResponse, settingsResponse, roundUpResponse] = await Promise.all([
				api.listAccounts(true),
				api.listAccountTypes(true),
				api.listCategories(true),
				api.listTags(),
				api.settings(),
				api.getRoundUpRule(),
			]);
			revision++;
			accounts.value = accountsResponse.accounts;
			accountTypes.value = typesResponse.accountTypes;
			categories.value = categoriesResponse.categories;
			tags.value = tagsResponse.tags;
			settings.value = settingsResponse.settings;
			roundUpRule.value = roundUpResponse.roundUpRule;
			loaded.value = true;
			synced = true;
			void replaceLedger({
				accounts: accountsResponse.accounts,
				accountTypes: typesResponse.accountTypes,
				categories: categoriesResponse.categories,
				tags: tagsResponse.tags,
				settings: settingsResponse.settings,
				roundUpRule: roundUpResponse.roundUpRule,
			});
			return true;
		} catch (caught) {
			// Out of reach with a copy on screen: keep showing it. A later load tries again. A copy
			// still on its way counts — offline, the request can fail before the copy is read.
			await copy;
			if (!loaded.value || !isNetworkError(caught)) throw caught;
			return false;
		} finally {
			loading.value = false;
		}
	}

	async function refreshAccounts(): Promise<void> {
		// Types come along: renaming one changes what every account displays.
		const [accountsResponse, typesResponse] = await Promise.all([api.listAccounts(true), api.listAccountTypes(true)]);
		revision++;
		accounts.value = accountsResponse.accounts;
		accountTypes.value = typesResponse.accountTypes;
		void replaceAccounts(accountsResponse.accounts, typesResponse.accountTypes);
	}

	async function refreshCategories(): Promise<void> {
		const fresh = (await api.listCategories(true)).categories;
		revision++;
		categories.value = fresh;
		void replaceCategories(fresh);
	}

	async function refreshTags(): Promise<void> {
		const fresh = (await api.listTags()).tags;
		revision++;
		tags.value = fresh;
		void replaceTags(fresh);
	}

	// The API layer saves the row each of these answers with, as it does for every save.
	async function updateSettings(input: Partial<Pick<Settings, 'displayCurrency' | 'budgetMode' | 'defaultAccountId'>>): Promise<void> {
		const saved = (await api.updateSettings(input)).settings;
		revision++;
		settings.value = saved;
	}

	async function updateRoundUpRule(
		input: Partial<Pick<RoundUpRule, 'enabled' | 'roundTo' | 'destinationAccountId' | 'categoryId'>>,
	): Promise<void> {
		const saved = (await api.updateRoundUpRule(input)).roundUpRule;
		revision++;
		roundUpRule.value = saved;
	}

	function reset(): void {
		revision++;
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
		hydrate,
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
