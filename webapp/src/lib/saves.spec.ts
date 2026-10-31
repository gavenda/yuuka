import * as row from '@/testing/fixtures';
import { installIndexedDB } from '@/testing/indexedDb';
import { installLocalStorage, MemoryStorage } from '@/testing/memoryStorage';
import { beforeEach, describe, expect, it, vi } from 'vitest';

/**
 * A save writes its row into the local copy before the server has seen it, as Android writes the
 * provisional row into Room — so the next page load paints it, offline or not. These pin that
 * the API layer does so, with the cascades the server would apply.
 */
const queue = vi.hoisted(() => ({ queued: vi.fn() }));

vi.mock('./queue', () => queue);

installIndexedDB();

const cache = await import('./cache');
const { api } = await import('./api');

const SEPTEMBER = { month: '2026-09' };

/** Lets the saves' writes, which nothing waits on, reach the copy. */
const settled = () => new Promise<void>((resolve) => setTimeout(resolve));

beforeEach(async () => {
	installLocalStorage(new MemoryStorage());
	await cache.clearCache();
	await cache.replaceLedger(row.ledger('a'));
	queue.queued.mockReset();
	// Queued, and answered with the provisional result, as an offline save is.
	queue.queued.mockImplementation(async (call: { optimistic: unknown }) => call.optimistic);
});

describe('saves', () => {
	it('write their row into the local copy', async () => {
		await api.createAccount({ name: 'Wallet', typeId: 'type', currency: 'PHP' });
		await settled();

		expect((await cache.readLedger())!.accounts.map((account) => account.name)).toContain('Wallet');
	});

	it('show a new transaction in a filter set seen before', async () => {
		await cache.writeTransactionPage(SEPTEMBER, [], 0, 8);

		await api.createTransaction({ accountId: 'a', amount: -500, occurredOn: '2026-09-12', payee: 'Coffee' });
		await settled();

		expect((await cache.readTransactionPage(SEPTEMBER, 50))!.transactions.map((transaction) => transaction.payee)).toEqual(['Coffee']);
	});

	it('move the balances of the accounts they touch', async () => {
		const balances = async () => Object.fromEntries((await cache.readLedger())!.accounts.map((account) => [account.id, account.balance]));
		await cache.saveAccount(row.account('a', { balance: 10_000 }));
		await cache.saveAccount(row.account('b', { balance: 0 }));
		await cache.writeTransactionPage(SEPTEMBER, [], 0, 8);

		const { transaction } = await api.createTransaction({ accountId: 'a', amount: -500, occurredOn: '2026-09-12', payee: 'Coffee' });
		await settled();
		expect(await balances()).toEqual({ a: 9_500, b: 0 });

		// An edit moves it by the difference, onto whichever account the row now names.
		await api.updateTransaction(transaction.id, { accountId: 'b', amount: -800 });
		await settled();
		expect(await balances()).toEqual({ a: 10_000, b: -800 });

		await api.deleteTransaction(transaction.id);
		await settled();
		expect(await balances()).toEqual({ a: 10_000, b: 0 });

		// A transfer moves both, and deleting one leg takes both back.
		const { transactions } = await api.createTransfer({ fromAccountId: 'a', toAccountId: 'b', amount: 2_000, occurredOn: '2026-09-13' });
		await settled();
		expect(await balances()).toEqual({ a: 8_000, b: 2_000 });

		await api.deleteTransaction(transactions[0].id);
		await settled();
		expect(await balances()).toEqual({ a: 10_000, b: 0 });
	});

	it('move the balance with the starting balance', async () => {
		const account = row.account('a', { startingBalance: 1_000, balance: 4_000 });
		await cache.saveAccount(account);
		// The edit works from the account as the screen holds it.
		const { ledgerView, setLedgerView } = await import('./provisional');
		const empty = ledgerView();
		setLedgerView(() => ({ ...empty, accounts: [account] }));

		await api.updateAccount('a', { startingBalance: 3_000 });
		await settled();
		setLedgerView(() => empty);

		expect((await cache.readLedger())!.accounts.find((account) => account.id === 'a')).toMatchObject({
			startingBalance: 3_000,
			balance: 6_000,
		});
	});

	it('apply the cascade the server would when they delete', async () => {
		await cache.saveCategory(row.category('c'));
		await cache.writeTransactionPage(SEPTEMBER, [row.transaction('t', { categoryId: 'c', categoryName: 'c' })], 1, 8);

		await api.deleteCategory('c');
		await settled();

		expect(row.ids((await cache.readLedger())!.categories)).toEqual([]);
		expect((await cache.readTransactionPage(SEPTEMBER, 50))!.transactions[0]).toMatchObject({ id: 't', categoryId: null });
	});

	it('write nothing when they fail', async () => {
		queue.queued.mockRejectedValueOnce(new Error('No queue, and no connection.'));

		await expect(api.createTag({ name: 'Trip' })).rejects.toThrow();
		await settled();

		expect((await cache.readLedger())!.tags).toEqual([]);
	});
});
