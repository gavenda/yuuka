import { ApiError } from '@/lib/api';
import {
	clearCache,
	readLedger,
	readSummary,
	readTransactionPage,
	replaceAccounts,
	replaceLedger,
	replaceSubscriptions,
	writeSummary,
	writeTransactionPage,
} from '@/lib/cache';
import * as row from '@/testing/fixtures';
import { installIndexedDB } from '@/testing/indexedDb';
import { installLocalStorage, MemoryStorage } from '@/testing/memoryStorage';
import { createPinia, setActivePinia } from 'pinia';
import { beforeEach, describe, expect, it, vi } from 'vitest';

/**
 * The API is the source of truth and the browser's copy only what was last seen. These specs pin
 * how each store treats the two: paint from the copy at once, replace it with what the API
 * answers, and keep it on screen when the API cannot be reached — but not when the API answered
 * and said no. Reading the copy takes time of its own, so they also pin that a copy arriving late
 * never lands on top of something newer.
 */
const api = vi.hoisted(() => ({
	listAccounts: vi.fn(),
	listAccountTypes: vi.fn(),
	listCategories: vi.fn(),
	listTags: vi.fn(),
	settings: vi.fn(),
	getRoundUpRule: vi.fn(),
	summary: vi.fn(),
	listTransactions: vi.fn(),
	listSubscriptions: vi.fn(),
}));

vi.mock('@/lib/api', async (original) => ({ ...(await original<typeof import('@/lib/api')>()), api }));

/** While [held] is set, reading the local copy waits on it, as a slow disk would. */
const disk = vi.hoisted(() => ({ held: null as Promise<void> | null }));

vi.mock('@/lib/cache', async (original) => {
	const actual = await original<typeof import('@/lib/cache')>();
	const slow =
		<A extends unknown[], R>(read: (...args: A) => Promise<R>) =>
		async (...args: A): Promise<R> => {
			if (disk.held) await disk.held;
			return read(...args);
		};

	return {
		...actual,
		readLedger: slow(actual.readLedger),
		readSummary: slow(actual.readSummary),
		readTransactionPage: slow(actual.readTransactionPage),
		readSubscriptions: slow(actual.readSubscriptions),
	};
});

installIndexedDB();

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

/** Holds every read of the local copy back until the returned function lets them through. */
function holdReads(): () => void {
	const gate = deferred<void>();
	disk.held = gate.promise;
	return () => {
		disk.held = null;
		gate.resolve();
	};
}

/** The next task: whatever the copy needed has happened by then. */
const nextTask = () => new Promise<void>((resolve) => setTimeout(resolve));

beforeEach(async () => {
	installLocalStorage(new MemoryStorage());
	disk.held = null;
	await clearCache();
	setActivePinia(createPinia());
	for (const fn of Object.values(api)) fn.mockReset();
});

describe('ledger', () => {
	function answer(id: string) {
		const fresh = row.ledger(id);
		api.listAccounts.mockResolvedValue({ accounts: fresh.accounts });
		api.listAccountTypes.mockResolvedValue({ accountTypes: fresh.accountTypes });
		api.listCategories.mockResolvedValue({ categories: fresh.categories });
		api.listTags.mockResolvedValue({ tags: fresh.tags });
		api.settings.mockResolvedValue({ settings: fresh.settings });
		api.getRoundUpRule.mockResolvedValue({ roundUpRule: fresh.roundUpRule });
	}

	function unreachable() {
		for (const call of [api.listAccounts, api.listAccountTypes, api.listCategories, api.listTags, api.settings, api.getRoundUpRule]) {
			call.mockRejectedValue(offline());
		}
	}

	it('paints from the local copy while the API is still answering, then replaces it', async () => {
		await replaceLedger(row.ledger('saved'));
		const accounts = deferred<{ accounts: unknown[] }>();
		answer('fresh');
		api.listAccounts.mockReturnValue(accounts.promise);

		const ledger = useLedgerStore();
		const loading = ledger.load();
		await nextTask();

		expect(row.ids(ledger.accounts)).toEqual(['saved']);
		expect(ledger.displayCurrency).toBe('USD');
		expect(ledger.loaded).toBe(true);

		accounts.resolve({ accounts: [row.account('fresh')] });
		await loading;

		expect(row.ids(ledger.accounts)).toEqual(['fresh']);
	});

	it('replaces the rows in the local copy with what the API answered', async () => {
		await replaceLedger(row.ledger('saved'));
		answer('fresh');

		await useLedgerStore().load();

		expect(row.ids((await readLedger())!.accounts)).toEqual(['fresh']);
	});

	it('keeps showing the local copy when the API cannot be reached, and asks again next time', async () => {
		await replaceLedger(row.ledger('saved'));
		unreachable();

		const ledger = useLedgerStore();
		await expect(ledger.load()).resolves.toBe(false);
		expect(row.ids(ledger.accounts)).toEqual(['saved']);

		answer('fresh');
		await expect(ledger.load()).resolves.toBe(true);

		expect(api.listAccounts).toHaveBeenCalledTimes(2);
		expect(row.ids(ledger.accounts)).toEqual(['fresh']);
	});

	it('fails when the API cannot be reached and there is no copy to show', async () => {
		unreachable();
		await expect(useLedgerStore().load()).rejects.toMatchObject({ status: 0 });
	});

	it('does not hide an answer from the API behind the local copy', async () => {
		await replaceLedger(row.ledger('saved'));
		answer('x');
		api.listAccounts.mockRejectedValue(serverError());

		await expect(useLedgerStore().load()).rejects.toMatchObject({ status: 500 });
	});

	it('does not paint a ledger the copy holds only part of', async () => {
		await replaceAccounts([row.account('saved')], []);
		answer('fresh');
		api.listAccounts.mockReturnValue(new Promise(() => {}));

		const ledger = useLedgerStore();
		void ledger.load();
		await nextTask();

		expect(ledger.loaded).toBe(false);
		expect(ledger.accounts).toEqual([]);
	});

	it('does not let a copy read after the API answered replace the answer', async () => {
		await replaceLedger(row.ledger('saved'));
		answer('fresh');
		const release = holdReads();

		const ledger = useLedgerStore();
		await ledger.load();
		release();
		await nextTask();

		expect(row.ids(ledger.accounts)).toEqual(['fresh']);
	});

	it('shows the copy, not an error, when the API fails before the copy has been read', async () => {
		await replaceLedger(row.ledger('saved'));
		unreachable();
		const release = holdReads();

		const ledger = useLedgerStore();
		const loading = ledger.load();
		await nextTask();
		release();

		await expect(loading).resolves.toBe(false);
		expect(row.ids(ledger.accounts)).toEqual(['saved']);
	});

	it('does not bring a copy back after a reset', async () => {
		await replaceLedger(row.ledger('saved'));
		api.listAccounts.mockReturnValue(new Promise(() => {}));
		const release = holdReads();

		const ledger = useLedgerStore();
		void ledger.load();
		ledger.reset();
		release();
		await nextTask();

		expect(ledger.loaded).toBe(false);
		expect(ledger.accounts).toEqual([]);
	});
});

describe('budget', () => {
	it('shows a month seen before while the API answers', async () => {
		await writeSummary(row.summary('2026-09', 'saved'), 6);
		const answer = deferred<unknown>();
		api.summary.mockReturnValue(answer.promise);

		const budget = useBudgetStore();
		budget.month = '2026-09';
		const loading = budget.load();
		await nextTask();

		expect(budget.summary).toMatchObject({ marker: 'saved' });

		answer.resolve(row.summary('2026-09', 'fresh'));
		await loading;

		expect(budget.summary).toMatchObject({ marker: 'fresh' });
		expect(await readSummary('2026-09')).toMatchObject({ marker: 'fresh' });
	});

	it('is quiet about an unreachable API while it has that month to show', async () => {
		await writeSummary(row.summary('2026-09', 'saved'), 6);
		api.summary.mockRejectedValue(offline());

		const budget = useBudgetStore();
		budget.month = '2026-09';

		await expect(budget.load()).resolves.toBe(false);
		expect(budget.error).toBeNull();
		expect(budget.summary).toMatchObject({ marker: 'saved' });
	});

	it('does not let a copy read after the API answered replace the answer', async () => {
		await writeSummary(row.summary('2026-09', 'saved'), 6);
		api.summary.mockResolvedValue(row.summary('2026-09', 'fresh'));
		const release = holdReads();

		const budget = useBudgetStore();
		budget.month = '2026-09';
		await budget.load();
		release();
		await nextTask();

		expect(budget.summary).toMatchObject({ marker: 'fresh' });
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
		await writeSummary(row.summary('2026-09'), 6);
		api.summary.mockRejectedValue(serverError());

		const budget = useBudgetStore();
		budget.month = '2026-09';
		await budget.load();

		expect(budget.error).toBe('Request failed (500).');
	});

	it('does not take a copy shown while offline for a current summary', async () => {
		await writeSummary(row.summary('2026-09', 'saved'), 6);
		api.summary.mockRejectedValueOnce(offline()).mockResolvedValueOnce(row.summary('2026-09', 'fresh'));

		const budget = useBudgetStore();
		budget.month = '2026-09';
		await budget.load();
		await budget.load();

		expect(api.summary).toHaveBeenCalledTimes(2);
		expect(budget.summary).toMatchObject({ marker: 'fresh' });
	});

	it("drops other months' copies once a refresh has been answered, since a write may have moved them", async () => {
		await writeSummary(row.summary('2026-08'), 6);
		api.summary.mockResolvedValue(row.summary('2026-09'));

		const budget = useBudgetStore();
		budget.month = '2026-09';
		await budget.refresh();

		expect(await readSummary('2026-08')).toBeNull();
		expect(await readSummary('2026-09')).not.toBeNull();
	});

	it("keeps other months' copies when the refresh could not be answered", async () => {
		await writeSummary(row.summary('2026-08'), 6);
		api.summary.mockRejectedValue(offline());

		const budget = useBudgetStore();
		budget.month = '2026-09';
		await budget.refresh();

		expect(await readSummary('2026-08')).not.toBeNull();
	});
});

describe('transactions', () => {
	const page = (ids: string[], total = ids.length) => ({ transactions: ids.map((id) => row.transaction(id)), total });
	const SEPTEMBER = { month: '2026-09' };

	it("shows a filter set's first page from the local copy while the API answers", async () => {
		await writeTransactionPage(SEPTEMBER, page(['saved']).transactions, 1, 8);
		const answer = deferred<unknown>();
		api.listTransactions.mockReturnValue(answer.promise);

		const store = useTransactionStore();
		const loading = store.load(SEPTEMBER);
		await nextTask();

		expect(row.ids(store.transactions)).toEqual(['saved']);
		expect(store.loaded).toBe(true);

		answer.resolve(page(['fresh']));
		await loading;

		expect(row.ids(store.transactions)).toEqual(['fresh']);
		// The answer replaced the slice: the row it no longer returns is gone from the copy too.
		expect(row.ids((await readTransactionPage(SEPTEMBER, 50))!.transactions)).toEqual(['fresh']);
	});

	it('is quiet about an unreachable API while it has that page to show', async () => {
		await writeTransactionPage(SEPTEMBER, page(['saved']).transactions, 1, 8);
		api.listTransactions.mockRejectedValue(offline());

		const store = useTransactionStore();
		await store.load(SEPTEMBER);

		expect(store.error).toBeNull();
		expect(row.ids(store.transactions)).toEqual(['saved']);
	});

	it('says so when the API cannot be reached and there is nothing to show', async () => {
		api.listTransactions.mockRejectedValue(offline());

		const store = useTransactionStore();
		await store.load(SEPTEMBER);

		expect(store.error).toBe('Network error. Check your connection.');
		expect(store.loaded).toBe(false);
	});

	it('does not land the copy of a filter set left behind on the one chosen since', async () => {
		await writeTransactionPage({ month: '2026-08' }, [row.transaction('august', { occurredOn: '2026-08-10' })], 1, 8);
		api.listTransactions.mockReturnValueOnce(new Promise(() => {})).mockResolvedValueOnce(page(['september']));
		const release = holdReads();

		const store = useTransactionStore();
		void store.load({ month: '2026-08' });
		await store.load(SEPTEMBER);
		release();
		await nextTask();

		expect(row.ids(store.transactions)).toEqual(['september']);
	});

	it('keeps different filter sets apart', async () => {
		await writeTransactionPage({ month: '2026-08' }, [row.transaction('august', { occurredOn: '2026-08-10' })], 1, 8);
		api.listTransactions.mockRejectedValue(offline());

		const store = useTransactionStore();
		await store.load(SEPTEMBER);

		expect(store.transactions).toEqual([]);
		expect(store.error).not.toBeNull();
	});

	it('does not keep the pages scrolling on fetched, which only the first page is held for', async () => {
		api.listTransactions.mockResolvedValueOnce(page(['a'], 2)).mockResolvedValueOnce(page(['b'], 2));

		const store = useTransactionStore();
		await store.load(SEPTEMBER);
		await store.loadMore();

		expect(store.transactions).toHaveLength(2);
		expect(row.ids((await readTransactionPage(SEPTEMBER, 50))!.transactions)).toEqual(['a']);
	});

	it('keeps the rows on show when a further page cannot be had, and says so apart from the screen', async () => {
		api.listTransactions
			.mockResolvedValueOnce(page(['a'], 2))
			.mockRejectedValueOnce(offline())
			.mockResolvedValueOnce(page(['b'], 2));

		const store = useTransactionStore();
		await store.load(SEPTEMBER);
		await store.loadMore();

		expect(row.ids(store.transactions)).toEqual(['a']);
		expect(store.error).toBeNull();
		expect(store.moreError).toBe('Network error. Check your connection.');
		expect(store.hasMore).toBe(true);

		// Asked for again, it carries on from where the list stopped.
		await store.loadMore();

		expect(row.ids(store.transactions)).toEqual(['a', 'b']);
		expect(store.moreError).toBeNull();
		expect(api.listTransactions).toHaveBeenLastCalledWith({ ...SEPTEMBER, limit: 50, offset: 1 });
	});

	it('does not add a further page to the filter set chosen while it was on its way', async () => {
		const further = deferred<unknown>();
		api.listTransactions
			.mockResolvedValueOnce(page(['a'], 2))
			.mockReturnValueOnce(further.promise)
			.mockResolvedValueOnce({ transactions: [row.transaction('august', { occurredOn: '2026-08-10' })], total: 1 });

		const store = useTransactionStore();
		await store.load(SEPTEMBER);
		const more = store.loadMore();
		await store.load({ month: '2026-08' });
		further.resolve(page(['b'], 2));
		await more;

		expect(row.ids(store.transactions)).toEqual(['august']);
		expect(store.total).toBe(1);
		expect(store.loadingMore).toBe(false);
	});

	it('asks for one further page at a time', async () => {
		const further = deferred<unknown>();
		api.listTransactions.mockResolvedValueOnce(page(['a'], 3)).mockReturnValueOnce(further.promise);

		const store = useTransactionStore();
		await store.load(SEPTEMBER);
		const more = store.loadMore();
		void store.loadMore();
		further.resolve(page(['b'], 3));
		await more;

		expect(api.listTransactions).toHaveBeenCalledTimes(2);
		expect(row.ids(store.transactions)).toEqual(['a', 'b']);
	});

	it("drops other filter sets' copies once a refresh has been answered", async () => {
		await writeTransactionPage({ month: '2026-08' }, [row.transaction('august', { occurredOn: '2026-08-10' })], 1, 8);
		api.listTransactions.mockResolvedValue(page(['a']));

		const store = useTransactionStore();
		await store.load(SEPTEMBER);
		expect(await readTransactionPage({ month: '2026-08' }, 50)).not.toBeNull();

		await store.refresh();

		expect(await readTransactionPage({ month: '2026-08' }, 50)).toBeNull();
		expect(await readTransactionPage(SEPTEMBER, 50)).not.toBeNull();
	});
});

describe('subscriptions', () => {
	it('shows the local copy while the API answers, and keeps it when the API cannot be reached', async () => {
		await replaceSubscriptions([row.subscription('saved')]);
		api.listSubscriptions.mockRejectedValue(offline());

		const store = useSubscriptionStore();
		await expect(store.load()).resolves.toBe(false);

		expect(row.ids(store.subscriptions)).toEqual(['saved']);
		expect(store.error).toBeNull();
	});

	it('does not let a copy read after the API answered replace the answer', async () => {
		await replaceSubscriptions([row.subscription('saved')]);
		api.listSubscriptions.mockResolvedValue({ subscriptions: [row.subscription('fresh')] });
		const release = holdReads();

		const store = useSubscriptionStore();
		await store.load();
		release();
		await nextTask();

		expect(row.ids(store.subscriptions)).toEqual(['fresh']);
	});

	it('asks again after coming back, rather than treating the copy as current', async () => {
		await replaceSubscriptions([row.subscription('saved')]);
		api.listSubscriptions.mockRejectedValueOnce(offline()).mockResolvedValueOnce({ subscriptions: [row.subscription('fresh')] });

		const store = useSubscriptionStore();
		await store.load();
		await store.load();

		expect(api.listSubscriptions).toHaveBeenCalledTimes(2);
		expect(row.ids(store.subscriptions)).toEqual(['fresh']);
	});

	it('says so when the API cannot be reached and there is nothing to show', async () => {
		api.listSubscriptions.mockRejectedValue(offline());

		const store = useSubscriptionStore();
		await store.load();

		expect(store.error).toBe('Network error. Check your connection.');
	});
});
