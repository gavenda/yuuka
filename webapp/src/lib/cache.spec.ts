import { IDBDatabase as FakeDatabase } from 'fake-indexeddb';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import * as row from '@/testing/fixtures';
import { blockIndexedDB, installIndexedDB } from '@/testing/indexedDb';
import { installLocalStorage, MemoryStorage } from '@/testing/memoryStorage';

type Cache = typeof import('./cache');

const OWNER = 'yuuka.cache.owner';
const DATABASE = 'yuuka-cache';
const SEPTEMBER = { month: '2026-09' };

/** A page load: the modules afresh, over whatever IndexedDB and `localStorage` already hold. */
async function reload(): Promise<Cache> {
	vi.resetModules();
	return import('./cache');
}

function openDatabase(version?: number, upgrade?: (db: IDBDatabase) => void): Promise<IDBDatabase> {
	return new Promise((resolve, reject) => {
		const request = indexedDB.open(DATABASE, version);
		request.onupgradeneeded = () => upgrade?.(request.result);
		request.onsuccess = () => resolve(request.result);
		request.onerror = () => reject(request.error);
	});
}

/** Runs [work] against one store directly, past the modules and their memory, as another tab or build would. */
async function inStore<T>(
	store: string,
	mode: IDBTransactionMode,
	work: (store: IDBObjectStore) => IDBRequest<T> | void,
): Promise<T | undefined> {
	const db = await openDatabase();
	try {
		return await new Promise<T | undefined>((resolve, reject) => {
			const transaction = db.transaction(store, mode);
			const request = work(transaction.objectStore(store));
			transaction.oncomplete = () => resolve(request?.result);
			transaction.onabort = () => reject(transaction.error);
		});
	} finally {
		db.close();
	}
}

const storedKeys = async (store: string) =>
	((await inStore(store, 'readonly', (target) => target.getAllKeys())) ?? []).map((key) => JSON.stringify(key)).sort();
const storedIds = async (store: string) => ((await inStore(store, 'readonly', (target) => target.getAllKeys())) ?? []).map(String).sort();
const stored = async <T>(store: string, key: IDBValidKey) => inStore<T>(store, 'readonly', (target) => target.get(key));

describe('local database', () => {
	let storage: MemoryStorage;
	let cache: Cache;

	beforeEach(async () => {
		storage = new MemoryStorage();
		installLocalStorage(storage);
		installIndexedDB();
		// Only the clock: IndexedDB's own scheduling has to keep running.
		vi.useFakeTimers({ toFake: ['Date'] });
		vi.setSystemTime(1_000);
		cache = await reload();
	});

	afterEach(() => {
		vi.useRealTimers();
	});

	describe('layout', () => {
		it("is Room's: a store per table, keyed and indexed the same way", async () => {
			await cache.warmCache();
			const db = await openDatabase();
			const transaction = db.transaction(Array.from(db.objectStoreNames));
			const store = (name: string) => transaction.objectStore(name);
			const indexes = (name: string) => Array.from(store(name).indexNames).sort();

			expect(Array.from(db.objectStoreNames).sort()).toEqual(
				[
					'account_types',
					'accounts',
					'budgets',
					'categories',
					'income_plans',
					'payees',
					'round_up_rule',
					'settings',
					'subscriptions',
					'summaries',
					'tags',
					'transaction_pages',
					'transaction_tags',
					'transactions',
				].sort(),
			);
			expect(store('transaction_tags').keyPath).toEqual(['transactionId', 'tagId']);
			expect(indexes('transaction_tags')).toEqual(['tagId']);
			expect(indexes('transactions')).toEqual(['accountId', 'categoryId', 'month', 'transferId']);
			expect(indexes('budgets')).toEqual(['categoryId', 'queriedMonth']);
			expect(store('income_plans').keyPath).toBe('queriedMonth');
			expect(store('summaries').keyPath).toBe('month');
			expect(store('settings').keyPath).toBe('id');
			db.close();
		});

		it('drops the old single-store layout on upgrade', async () => {
			const old = await openDatabase(1, (db) => db.createObjectStore('entries', { keyPath: 'key' }));
			old.close();

			await cache.warmCache();

			const db = await openDatabase();
			expect(db.version).toBe(2);
			expect(Array.from(db.objectStoreNames)).not.toContain('entries');
			db.close();
		});

		it('keeps rows, as rows, across a reload — and nothing in localStorage', async () => {
			await cache.replaceLedger(row.ledger('saved'));

			cache = await reload();

			expect((await cache.readLedger())?.accounts).toEqual([row.account('saved')]);
			expect(await stored('accounts', 'saved')).toMatchObject({ id: 'saved', name: 'saved', owner: null });
			expect(storage.length).toBe(0);
		});

		it('answers a read from memory once open, so a screen paints from it without a round trip to the disk', async () => {
			await cache.replaceLedger(row.ledger('saved'));
			const transaction = vi.spyOn(FakeDatabase.prototype, 'transaction');

			try {
				expect(await cache.readLedger()).not.toBeNull();
				expect(transaction).not.toHaveBeenCalled();
			} finally {
				transaction.mockRestore();
			}
		});
	});

	describe('ledger', () => {
		it('has no ledger to show until it has been saved whole', async () => {
			await cache.replaceAccounts([row.account('a')], []);
			expect(await cache.readLedger()).toBeNull();
		});

		it('reads lists back in the order the API sorts them in', async () => {
			await cache.replaceLedger({
				...row.ledger('x'),
				accounts: [
					row.account('z', { name: 'Zed', archived: true }),
					row.account('b', { name: 'beta' }),
					row.account('a', { name: 'Alpha' }),
				],
				categories: [
					row.category('income', { kind: 'income' }),
					row.category('child', { name: 'Child', parentId: 'p' }),
					row.category('p', { name: 'Parent', sortOrder: 1 }),
					row.category('q', { name: 'Other', sortOrder: 0 }),
				],
				tags: [row.tag('t2', { name: 'b' }), row.tag('t1', { name: 'A' })],
			});

			const ledger = await cache.readLedger();

			expect(row.ids(ledger!.accounts)).toEqual(['a', 'b', 'z']);
			expect(row.ids(ledger!.categories)).toEqual(['q', 'p', 'child', 'income']);
			expect(row.ids(ledger!.tags)).toEqual(['t1', 't2']);
		});

		it('replaces a list with what the API answered, so a row it no longer returns goes', async () => {
			await cache.replaceAccounts([row.account('a'), row.account('b')], []);
			await cache.replaceAccounts([row.account('b')], []);

			expect(await storedIds('accounts')).toEqual(['b']);
		});

		it('saves one row without touching the rest', async () => {
			await cache.replaceLedger(row.ledger('a'));
			await cache.saveAccount(row.account('b'));
			await cache.saveAccount(row.account('a', { name: 'Renamed' }));

			expect((await cache.readLedger())?.accounts.map((account) => account.name)).toEqual(['b', 'Renamed']);
		});
	});

	describe('transactions', () => {
		const tagged = (id: string, ...tags: string[]) =>
			row.transaction(id, { tags: tags.map((name) => ({ id: name, name, color: '#228833' })) });

		it("reads a filter set's first page back, newest first, with the tags each row wears", async () => {
			await cache.replaceTags([row.tag('food', { name: 'Food', color: '#aa3377' })]);
			await cache.writeTransactionPage(
				SEPTEMBER,
				[row.transaction('late', { occurredOn: '2026-09-20T09:00' }), tagged('early', 'food')],
				2,
				8,
			);

			const page = await cache.readTransactionPage(SEPTEMBER, 50);

			expect(row.ids(page!.transactions)).toEqual(['late', 'early']);
			// The tag as the tags table has it now, not as the row carried it.
			expect(page!.transactions[1]!.tags).toEqual([{ id: 'food', name: 'Food', color: '#aa3377' }]);
			expect(page!.total).toBe(2);
		});

		it('has no copy of a filter set it has never seen', async () => {
			await cache.writeTransactionPage(SEPTEMBER, [row.transaction('t')], 1, 8);
			expect(await cache.readTransactionPage({ ...SEPTEMBER, accountId: 'a' }, 50)).toBeNull();
		});

		it.each([
			['an account', { accountId: 'b' }, row.transaction('in', { accountId: 'b' }), row.transaction('out', { accountId: 'a' })],
			['uncategorised', { categoryId: 'none' }, row.transaction('in'), row.transaction('out', { categoryId: 'c' })],
			['a tag', { tagId: 'trip' }, tagged('in', 'trip'), tagged('out', 'home')],
			['a search over tag names', { search: 'TRI' }, tagged('in', 'trip'), tagged('out', 'home')],
			['a search over notes', { search: 'lunch' }, row.transaction('in', { notes: 'Lunch out' }), row.transaction('out')],
		])("answers a filter set by %s from the rows it holds, as the API's filters would", async (_, filter, matching, other) => {
			const filters = { ...SEPTEMBER, ...filter };
			await cache.writeTransactionPage(filters, [], 0, 8);
			await cache.saveTransactions([matching, other]);

			expect(row.ids((await cache.readTransactionPage(filters, 50))!.transactions)).toEqual(['in']);
		});

		it('shows a row saved since, in its place, in a filter set seen before', async () => {
			await cache.writeTransactionPage(SEPTEMBER, [row.transaction('old', { occurredOn: '2026-09-01' })], 1, 8);
			await cache.saveTransactions([row.transaction('new', { occurredOn: '2026-09-15' })]);

			expect(row.ids((await cache.readTransactionPage(SEPTEMBER, 50))!.transactions)).toEqual(['new', 'old']);
		});

		it('replaces the slice the page covers: a row the API no longer returns goes', async () => {
			const rows = ['3', '2', '1'].map((day) => row.transaction(`t${day}`, { occurredOn: `2026-09-0${day}` }));
			await cache.writeTransactionPage(SEPTEMBER, rows, 3, 8);

			await cache.writeTransactionPage(SEPTEMBER, [rows[0]!, rows[2]!], 2, 8);

			expect(await storedIds('transactions')).toEqual(['t1', 't3']);
		});

		it("leaves rows beyond the page's last one alone", async () => {
			const page = [row.transaction('t9', { occurredOn: '2026-09-09' }), row.transaction('t8', { occurredOn: '2026-09-08' })];
			await cache.writeTransactionPage(SEPTEMBER, page, 5, 8);
			await cache.saveTransactions([row.transaction('t1', { occurredOn: '2026-09-01' })]);

			await cache.writeTransactionPage(SEPTEMBER, page, 5, 8);

			expect(await storedIds('transactions')).toEqual(['t1', 't8', 't9']);
		});

		it('keeps the newest filter sets, and takes the rows only a trimmed one held', async () => {
			const filterSet = (accountId: string) => ({ ...SEPTEMBER, accountId });
			const shared = row.transaction('shared', { accountId: 'b' });

			vi.setSystemTime(1_000);
			await cache.writeTransactionPage(filterSet('a'), [row.transaction('only-a', { accountId: 'a' })], 1, 2);
			vi.setSystemTime(2_000);
			await cache.writeTransactionPage(filterSet('b'), [shared], 1, 2);
			vi.setSystemTime(3_000);
			await cache.writeTransactionPage({ ...SEPTEMBER, accountId: 'b,c' }, [shared], 1, 2);

			expect(await cache.readTransactionPage(filterSet('a'), 50)).toBeNull();
			expect(await storedIds('transactions')).toEqual(['shared']);
			expect(await storedIds('transaction_pages')).toHaveLength(2);
		});

		it('drops other filter sets, with the rows only they held', async () => {
			await cache.writeTransactionPage({ month: '2026-08' }, [row.transaction('august', { occurredOn: '2026-08-10' })], 1, 8);
			await cache.writeTransactionPage(SEPTEMBER, [row.transaction('september')], 1, 8);

			await cache.dropTransactionPages(SEPTEMBER);

			expect(await cache.readTransactionPage({ month: '2026-08' }, 50)).toBeNull();
			expect(await storedIds('transactions')).toEqual(['september']);
		});

		it('drops the tags a row no longer wears', async () => {
			await cache.saveTransactions([tagged('t', 'trip', 'food')]);
			await cache.saveTransactions([tagged('t', 'food')]);

			expect(await storedKeys('transaction_tags')).toEqual([JSON.stringify(['t', 'food'])]);
		});

		it('deletes both legs of a transfer, with their tags', async () => {
			await cache.saveTransactions([
				row.transaction('from', { transferId: 'x', amount: -500 }),
				{ ...tagged('to', 'trip'), transferId: 'x', accountId: 'b', amount: 500 },
				row.transaction('other'),
			]);

			await cache.deleteTransaction('from');

			expect(await storedIds('transactions')).toEqual(['other']);
			expect(await storedKeys('transaction_tags')).toEqual([]);
		});
	});

	describe('cascades', () => {
		it('a deleted category leaves its transactions and subscriptions uncategorised, and takes its budgets', async () => {
			const filed = { categoryId: 'c', categoryName: 'Food', categoryColor: '#aa3377' };
			await cache.writeTransactionPage(SEPTEMBER, [row.transaction('t', filed)], 1, 8);
			await cache.replaceSubscriptions([row.subscription('s', filed)]);
			await cache.saveBudget(
				{ id: 'b', categoryId: 'c', month: null, amount: 100, percent: null, createdAt: '', updatedAt: '' },
				'2026-09',
			);

			await cache.deleteCategory('c');

			expect((await cache.readTransactionPage(SEPTEMBER, 50))!.transactions[0]).toMatchObject({ categoryId: null, categoryName: null });
			expect((await cache.readSubscriptions())![0]).toMatchObject({ categoryId: null, categoryColor: null });
			expect(await storedIds('budgets')).toEqual([]);
			// Patched in the store as well as in memory.
			expect(await stored('transactions', 't')).toMatchObject({ categoryId: null });
		});

		it('a deleted tag only comes off', async () => {
			await cache.replaceTags([row.tag('trip')]);
			await cache.saveTransactions([row.transaction('t', { tags: [{ id: 'trip', name: 'trip', color: '#228833' }] })]);

			await cache.deleteTag('trip');

			expect(await storedIds('transactions')).toEqual(['t']);
			expect(await storedKeys('transaction_tags')).toEqual([]);
		});

		it('a deleted account takes its transactions, the other legs of its transfers, and its subscriptions', async () => {
			await cache.replaceAccounts([row.account('a'), row.account('b')], []);
			await cache.saveTransactions([
				row.transaction('on-a'),
				row.transaction('from-a', { transferId: 'x' }),
				row.transaction('to-b', { transferId: 'x', accountId: 'b' }),
				row.transaction('on-b', { accountId: 'b' }),
			]);
			await cache.replaceSubscriptions([row.subscription('s-a'), row.subscription('s-b', { accountId: 'b' })]);

			await cache.deleteAccount('a');

			expect(await storedIds('accounts')).toEqual(['b']);
			expect(await storedIds('transactions')).toEqual(['on-b']);
			expect(await storedIds('subscriptions')).toEqual(['s-b']);
		});

		it('a budget set again for the same month and category replaces the one before', async () => {
			const budget = (id: string, amount: number) => ({
				id,
				categoryId: 'c',
				month: '2026-09',
				amount,
				percent: null,
				createdAt: '',
				updatedAt: '',
			});
			await cache.saveBudget(budget('first', 100), '2026-09');
			await cache.saveBudget(budget('second', 200), '2026-09');
			await cache.saveBudget(budget('october', 300), '2026-10');

			expect(await storedIds('budgets')).toEqual(['october', 'second']);
		});
	});

	describe('per month', () => {
		it("keeps the newest months' summaries, as structured rows", async () => {
			for (const [index, month] of ['2026-06', '2026-07', '2026-08', '2026-09'].entries()) {
				vi.setSystemTime(1_000 + index);
				await cache.writeSummary(row.summary(month), 2);
			}

			expect(await storedIds('summaries')).toEqual(['2026-08', '2026-09']);
			expect(await cache.readSummary('2026-09')).toMatchObject({ month: '2026-09', marker: 'x' });
			expect(typeof (await stored<{ summary: unknown }>('summaries', '2026-09'))?.summary).toBe('object');
		});

		it('drops every month but the one asked to keep', async () => {
			await cache.writeSummary(row.summary('2026-08'), 6);
			await cache.writeSummary(row.summary('2026-09'), 6);

			await cache.dropSummaries('2026-09');

			expect(await storedIds('summaries')).toEqual(['2026-09']);
		});

		it('discards what is held per month and per page, and nothing else', async () => {
			await cache.replaceLedger(row.ledger('kept'));
			await cache.replaceSubscriptions([row.subscription('kept')]);
			await cache.writeTransactionPage(SEPTEMBER, [row.transaction('t', { tags: [{ id: 'g', name: 'g', color: '#228833' }] })], 1, 8);
			await cache.writeSummary(row.summary('2026-09'), 6);
			await cache.saveBudget({ id: 'b', categoryId: 'c', month: null, amount: 1, percent: null, createdAt: '', updatedAt: '' }, '2026-09');
			await cache.saveIncomePlan({ month: null, amount: 1, mode: 'fixed', grossAmount: null, createdAt: null, updatedAt: null }, '2026-09');

			await cache.discardMonthly(['transactions', 'budgets', 'incomePlans', 'summaries']);

			for (const store of ['transactions', 'transaction_tags', 'transaction_pages', 'budgets', 'income_plans', 'summaries']) {
				expect(await storedKeys(store), store).toEqual([]);
			}
			expect((await cache.readLedger())?.accounts).toHaveLength(1);
			expect(await cache.readSubscriptions()).toHaveLength(1);
		});
	});

	describe('ownership', () => {
		it('keeps the copy for the same person', async () => {
			await cache.adoptCacheFor('auth0|a');
			await cache.replaceLedger(row.ledger('mine'));

			expect(await cache.adoptCacheFor('auth0|a')).toBe(false);

			expect(await cache.readLedger()).not.toBeNull();
		});

		it('discards every store when a different person signs in', async () => {
			await cache.adoptCacheFor('auth0|a');
			await cache.replaceLedger(row.ledger('theirs'));
			await cache.saveTransactions([row.transaction('theirs')]);

			expect(await cache.adoptCacheFor('auth0|b')).toBe(true);

			expect(await cache.readLedger()).toBeNull();
			expect(await storedIds('accounts')).toEqual([]);
			expect(await storedIds('transactions')).toEqual([]);
			expect(storage.getItem(OWNER)).toBe('auth0|b');
		});

		it('discards a copy that has no recorded owner', async () => {
			await cache.replaceLedger(row.ledger('unknown'));
			expect(await cache.adoptCacheFor('auth0|a')).toBe(true);
			expect(await cache.readLedger()).toBeNull();
		});

		it('discards the copy and records no owner when the person cannot be identified', async () => {
			await cache.adoptCacheFor('auth0|a');
			await cache.replaceLedger(row.ledger('theirs'));

			expect(await cache.adoptCacheFor(undefined)).toBe(true);

			expect(await cache.readLedger()).toBeNull();
			expect(storage.getItem(OWNER)).toBeNull();
		});

		it('never reads back what was written for someone else, even before it is cleared', async () => {
			await cache.adoptCacheFor('auth0|a');
			await cache.replaceLedger(row.ledger('theirs'));

			// Another tab has signed someone else in.
			storage.setItem(OWNER, 'auth0|b');

			expect(await cache.readLedger()).toBeNull();
		});

		it('keeps the owner through a clear, so an expired session is not taken for someone else', async () => {
			await cache.adoptCacheFor('auth0|a');
			await cache.replaceLedger(row.ledger('mine'));
			await cache.clearCache();

			expect(await cache.readLedger()).toBeNull();
			expect(await cache.adoptCacheFor('auth0|a')).toBe(false);
		});

		it('still reports a change of owner when the new one cannot be recorded', async () => {
			await cache.adoptCacheFor('auth0|a');
			await cache.replaceLedger(row.ledger('theirs'));
			storage.full = true;

			expect(await cache.adoptCacheFor('auth0|b')).toBe(true);

			expect(await cache.readLedger()).toBeNull();
			expect(storage.getItem(OWNER)).toBeNull();
		});
	});

	describe('when storage is out of reach', () => {
		it('keeps no copy, and throws nothing, when IndexedDB is refused', async () => {
			blockIndexedDB();
			cache = await reload();

			await expect(cache.replaceLedger(row.ledger('x'))).resolves.toBeUndefined();
			expect(await cache.readLedger()).toBeNull();
			await expect(cache.writeTransactionPage(SEPTEMBER, [row.transaction('t')], 1, 8)).resolves.toBeUndefined();
			expect(await cache.readTransactionPage(SEPTEMBER, 50)).toBeNull();
			await expect(cache.deleteAccount('a')).resolves.toBeUndefined();
			await expect(cache.clearCache()).resolves.toBeUndefined();
			// Whose books these are does not depend on the copy: a change of owner still reaches the queue.
			expect(await cache.adoptCacheFor('auth0|a')).toBe(true);
			expect(storage.getItem(OWNER)).toBe('auth0|a');
		});

		it('keeps no copy when localStorage is blocked, since nothing could say whose it was', async () => {
			installLocalStorage(() => {
				throw new DOMException('blocked', 'SecurityError');
			});

			await cache.replaceLedger(row.ledger('x'));
			expect(await cache.readLedger()).toBeNull();
			expect(await cache.adoptCacheFor('auth0|a')).toBe(false);
		});

		it('reads a row it cannot use as no copy, rather than throwing', async () => {
			await cache.replaceLedger(row.ledger('x'));
			await inStore('accounts', 'readwrite', (store) => store.put({ id: 'broken', owner: null }));
			cache = await reload();

			expect(await cache.readLedger()).toBeNull();
		});

		it('discards the copy it used to keep in localStorage on first open, and nothing else', async () => {
			storage.setItem('yuuka.cache.ledger', JSON.stringify({ v: 2, at: 1, data: { accounts: [] } }));
			storage.setItem('yuuka.cache.summary:month=2026-09', '{}');
			storage.setItem(OWNER, 'auth0|a');
			storage.setItem('yuuka.theme', 'dark');
			storage.setItem('yuuka.device-id', 'web-1');

			await cache.warmCache();

			expect(storage.getItem('yuuka.cache.ledger')).toBeNull();
			expect(storage.getItem('yuuka.cache.summary:month=2026-09')).toBeNull();
			expect(storage.getItem('yuuka.theme')).toBe('dark');
			expect(storage.getItem('yuuka.device-id')).toBe('web-1');
			// The marker stays where it was, so updating the app is not mistaken for someone else signing in.
			expect(await cache.adoptCacheFor('auth0|a')).toBe(false);
		});
	});

	describe('snapshotKey', () => {
		it('gives the same key however the filters were ordered', () => {
			expect(cache.snapshotKey('transactions', { month: '2026-09', accountId: 'a' })).toBe(
				cache.snapshotKey('transactions', { accountId: 'a', month: '2026-09' }),
			);
		});

		it('leaves out filters that are not set', () => {
			expect(cache.snapshotKey('transactions', { month: '2026-09', search: '', accountId: undefined })).toBe('transactions:month=2026-09');
		});

		it('tells different filters apart', () => {
			expect(cache.snapshotKey('transactions', { month: '2026-09' })).not.toBe(cache.snapshotKey('transactions', { month: '2026-08' }));
			expect(cache.snapshotKey('transactions', {})).toBe('transactions:');
		});
	});
});
