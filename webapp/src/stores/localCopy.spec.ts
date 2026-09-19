import { ApiError } from '@/lib/api';
import { readCache, writeCache } from '@/lib/cache';
import { installLocalStorage, MemoryStorage } from '@/testing/memoryStorage';
import { createPinia, setActivePinia } from 'pinia';
import { beforeEach, describe, expect, it, vi } from 'vitest';

/**
 * The API is the source of truth and the browser's copy only what was last seen. These specs pin
 * how each store treats the two: paint from the copy at once, replace it with what the API
 * answers, and keep it on screen when the API cannot be reached — but not when the API answered
 * and said no.
 */
const api = vi.hoisted(() => ({
	listAccounts: vi.fn(),
	listAccountTypes: vi.fn(),
	listCategories: vi.fn(),
	settings: vi.fn(),
	getRoundUpRule: vi.fn(),
	summary: vi.fn(),
	listTransactions: vi.fn(),
	listSubscriptions: vi.fn(),
}));

vi.mock('@/lib/api', async (original) => ({ ...(await original<typeof import('@/lib/api')>()), api }));

const { useLedgerStore } = await import('./ledger');
const { useBudgetStore } = await import('./budget');
const { useTransactionStore } = await import('./transactions');
const { useSubscriptionStore } = await import('./subscriptions');

const offline = () => new ApiError(0, 'Network error. Check your connection.');
const serverError = () => new ApiError(500, 'Request failed (500).');

/** A promise settled by hand, so a spec can look at the store while the API is still "answering". */
function deferred<T>() {
	let resolve!: (value: T) => void;
	const promise = new Promise<T>((done) => (resolve = done));
	return { promise, resolve };
}

let storage: MemoryStorage;

beforeEach(() => {
	storage = new MemoryStorage();
	installLocalStorage(storage);
	setActivePinia(createPinia());
	for (const fn of Object.values(api)) fn.mockReset();
});

describe('ledger', () => {
	const snapshot = (id: string) => ({
		accounts: [{ id }],
		accountTypes: [],
		categories: [],
		settings: { displayCurrency: 'USD', budgetMode: 'fixed', defaultAccountId: null },
		roundUpRule: { enabled: false },
	});

	function answer(id: string) {
		const fresh = snapshot(id);
		api.listAccounts.mockResolvedValue({ accounts: fresh.accounts });
		api.listAccountTypes.mockResolvedValue({ accountTypes: fresh.accountTypes });
		api.listCategories.mockResolvedValue({ categories: fresh.categories });
		api.settings.mockResolvedValue({ settings: fresh.settings });
		api.getRoundUpRule.mockResolvedValue({ roundUpRule: fresh.roundUpRule });
	}

	it('paints from the local copy while the API is still answering, then replaces it', async () => {
		writeCache('ledger', snapshot('saved'));
		const accounts = deferred<{ accounts: unknown[] }>();
		answer('fresh');
		api.listAccounts.mockReturnValue(accounts.promise);

		const ledger = useLedgerStore();
		const loading = ledger.load();

		expect(ledger.accounts).toEqual([{ id: 'saved' }]);
		expect(ledger.displayCurrency).toBe('USD');
		expect(ledger.loaded).toBe(true);

		accounts.resolve({ accounts: [{ id: 'fresh' }] });
		await loading;

		expect(ledger.accounts).toEqual([{ id: 'fresh' }]);
	});

	it('writes what the API answered back to the local copy', async () => {
		answer('fresh');

		await useLedgerStore().load();

		expect(readCache<{ accounts: unknown[] }>('ledger')?.accounts).toEqual([{ id: 'fresh' }]);
	});

	it('keeps showing the local copy when the API cannot be reached, and asks again next time', async () => {
		writeCache('ledger', snapshot('saved'));
		api.listAccounts.mockRejectedValue(offline());
		api.listAccountTypes.mockRejectedValue(offline());
		api.listCategories.mockRejectedValue(offline());
		api.settings.mockRejectedValue(offline());
		api.getRoundUpRule.mockRejectedValue(offline());

		const ledger = useLedgerStore();
		await expect(ledger.load()).resolves.toBeUndefined();
		expect(ledger.accounts).toEqual([{ id: 'saved' }]);

		answer('fresh');
		await ledger.load();

		expect(api.listAccounts).toHaveBeenCalledTimes(2);
		expect(ledger.accounts).toEqual([{ id: 'fresh' }]);
	});

	it('fails when the API cannot be reached and there is no copy to show', async () => {
		api.listAccounts.mockRejectedValue(offline());
		api.listAccountTypes.mockRejectedValue(offline());
		api.listCategories.mockRejectedValue(offline());
		api.settings.mockRejectedValue(offline());
		api.getRoundUpRule.mockRejectedValue(offline());

		await expect(useLedgerStore().load()).rejects.toMatchObject({ status: 0 });
	});

	it('does not hide an answer from the API behind the local copy', async () => {
		writeCache('ledger', snapshot('saved'));
		api.listAccounts.mockRejectedValue(serverError());
		api.listAccountTypes.mockResolvedValue({ accountTypes: [] });
		api.listCategories.mockResolvedValue({ categories: [] });
		api.settings.mockResolvedValue({ settings: snapshot('x').settings });
		api.getRoundUpRule.mockResolvedValue({ roundUpRule: snapshot('x').roundUpRule });

		await expect(useLedgerStore().load()).rejects.toMatchObject({ status: 500 });
	});

	it('ignores a local copy that is not shaped like a ledger', async () => {
		writeCache('ledger', { accounts: 'nope' });
		answer('fresh');
		api.listAccounts.mockReturnValue(new Promise(() => {}));

		const ledger = useLedgerStore();
		void ledger.load();

		expect(ledger.loaded).toBe(false);
		expect(ledger.accounts).toEqual([]);
	});
});

describe('budget', () => {
	const summaryFor = (month: string, marker = 'x') => ({ month, categories: [], marker });

	it('shows a month seen before while the API answers', async () => {
		writeCache('summary:month=2026-09', summaryFor('2026-09', 'saved'));
		const answer = deferred<unknown>();
		api.summary.mockReturnValue(answer.promise);

		const budget = useBudgetStore();
		budget.month = '2026-09';
		const loading = budget.load();

		expect(budget.summary).toMatchObject({ marker: 'saved' });

		answer.resolve(summaryFor('2026-09', 'fresh'));
		await loading;

		expect(budget.summary).toMatchObject({ marker: 'fresh' });
		expect(readCache<{ marker: string }>('summary:month=2026-09')?.marker).toBe('fresh');
	});

	it('is quiet about an unreachable API while it has that month to show', async () => {
		writeCache('summary:month=2026-09', summaryFor('2026-09', 'saved'));
		api.summary.mockRejectedValue(offline());

		const budget = useBudgetStore();
		budget.month = '2026-09';

		await expect(budget.load()).resolves.toBe(false);
		expect(budget.error).toBeNull();
		expect(budget.summary).toMatchObject({ marker: 'saved' });
	});

	it('says so when the API cannot be reached and there is nothing to show', async () => {
		api.summary.mockRejectedValue(offline());

		const budget = useBudgetStore();
		budget.month = '2026-09';
		await budget.load();

		expect(budget.error).toBe('Network error. Check your connection.');
		expect(budget.summary).toBeNull();
	});

	it('reports an answer from the API even while it has a copy to show', async () => {
		writeCache('summary:month=2026-09', summaryFor('2026-09'));
		api.summary.mockRejectedValue(serverError());

		const budget = useBudgetStore();
		budget.month = '2026-09';
		await budget.load();

		expect(budget.error).toBe('Request failed (500).');
	});

	it('does not take a copy shown while offline for a current summary', async () => {
		writeCache('summary:month=2026-09', summaryFor('2026-09', 'saved'));
		api.summary.mockRejectedValueOnce(offline()).mockResolvedValueOnce(summaryFor('2026-09', 'fresh'));

		const budget = useBudgetStore();
		budget.month = '2026-09';
		await budget.load();
		await budget.load();

		expect(api.summary).toHaveBeenCalledTimes(2);
		expect(budget.summary).toMatchObject({ marker: 'fresh' });
	});

	it("drops other months' copies once a refresh has been answered, since a write may have moved them", async () => {
		writeCache('summary:month=2026-08', summaryFor('2026-08'));
		api.summary.mockResolvedValue(summaryFor('2026-09'));

		const budget = useBudgetStore();
		budget.month = '2026-09';
		await budget.refresh();

		expect(readCache('summary:month=2026-08')).toBeNull();
		expect(readCache('summary:month=2026-09')).not.toBeNull();
	});

	it("keeps other months' copies when the refresh could not be answered", async () => {
		writeCache('summary:month=2026-08', summaryFor('2026-08'));
		api.summary.mockRejectedValue(offline());

		const budget = useBudgetStore();
		budget.month = '2026-09';
		await budget.refresh();

		expect(readCache('summary:month=2026-08')).not.toBeNull();
	});
});

describe('transactions', () => {
	const page = (ids: string[], total = ids.length) => ({ transactions: ids.map((id) => ({ id, occurredOn: '2026-09-01' })), total });

	it("shows a filter set's first page from the local copy while the API answers", async () => {
		writeCache('transactions:month=2026-09', page(['saved']));
		const answer = deferred<unknown>();
		api.listTransactions.mockReturnValue(answer.promise);

		const store = useTransactionStore();
		const loading = store.load({ month: '2026-09' });

		expect(store.transactions).toMatchObject([{ id: 'saved' }]);
		expect(store.loaded).toBe(true);

		answer.resolve(page(['fresh']));
		await loading;

		expect(store.transactions).toMatchObject([{ id: 'fresh' }]);
		expect(readCache<{ transactions: { id: string }[] }>('transactions:month=2026-09')?.transactions[0]?.id).toBe('fresh');
	});

	it('is quiet about an unreachable API while it has that page to show', async () => {
		writeCache('transactions:month=2026-09', page(['saved']));
		api.listTransactions.mockRejectedValue(offline());

		const store = useTransactionStore();
		await store.load({ month: '2026-09' });

		expect(store.error).toBeNull();
		expect(store.transactions).toMatchObject([{ id: 'saved' }]);
	});

	it('says so when the API cannot be reached and there is nothing to show', async () => {
		api.listTransactions.mockRejectedValue(offline());

		const store = useTransactionStore();
		await store.load({ month: '2026-09' });

		expect(store.error).toBe('Network error. Check your connection.');
		expect(store.loaded).toBe(false);
	});

	it('keeps different filter sets apart', async () => {
		writeCache('transactions:month=2026-08', page(['august']));
		api.listTransactions.mockRejectedValue(offline());

		const store = useTransactionStore();
		await store.load({ month: '2026-09' });

		expect(store.transactions).toEqual([]);
		expect(store.error).not.toBeNull();
	});

	it('does not keep pages fetched by "load more", which would splice into the copy of the first', async () => {
		api.listTransactions.mockResolvedValueOnce(page(['a'], 2)).mockResolvedValueOnce(page(['b'], 2));

		const store = useTransactionStore();
		await store.load({ month: '2026-09' });
		await store.loadMore();

		expect(store.transactions).toHaveLength(2);
		expect(readCache<{ transactions: unknown[] }>('transactions:month=2026-09')?.transactions).toHaveLength(1);
	});

	it("drops other filter sets' copies once a refresh has been answered", async () => {
		writeCache('transactions:month=2026-08', page(['august']));
		api.listTransactions.mockResolvedValue(page(['a']));

		const store = useTransactionStore();
		await store.load({ month: '2026-09' });
		expect(readCache('transactions:month=2026-08')).not.toBeNull();

		await store.refresh();

		expect(readCache('transactions:month=2026-08')).toBeNull();
		expect(readCache('transactions:month=2026-09')).not.toBeNull();
	});
});

describe('subscriptions', () => {
	it('shows the local copy while the API answers, and keeps it when the API cannot be reached', async () => {
		writeCache('subscriptions', [{ id: 'saved' }]);
		api.listSubscriptions.mockRejectedValue(offline());

		const store = useSubscriptionStore();
		await store.load();

		expect(store.subscriptions).toEqual([{ id: 'saved' }]);
		expect(store.error).toBeNull();
	});

	it('asks again after coming back, rather than treating the copy as current', async () => {
		writeCache('subscriptions', [{ id: 'saved' }]);
		api.listSubscriptions.mockRejectedValueOnce(offline()).mockResolvedValueOnce({ subscriptions: [{ id: 'fresh' }] });

		const store = useSubscriptionStore();
		await store.load();
		await store.load();

		expect(api.listSubscriptions).toHaveBeenCalledTimes(2);
		expect(store.subscriptions).toEqual([{ id: 'fresh' }]);
	});

	it('says so when the API cannot be reached and there is nothing to show', async () => {
		api.listSubscriptions.mockRejectedValue(offline());

		const store = useSubscriptionStore();
		await store.load();

		expect(store.error).toBe('Network error. Check your connection.');
	});
});
