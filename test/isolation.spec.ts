import { beforeEach, describe, expect, it } from 'vitest';
import { authedClient, json, makeAccount, makeCategory, otherClient, type Call } from './helpers';

/**
 * Multi-user means the interesting failures are no longer "does this work" but
 * "can one account reach another's money". Every route is checked from the
 * wrong side of the fence.
 */
let mine: Call;
let theirs: Call;

beforeEach(async () => {
	mine = await authedClient();
	theirs = await otherClient();
});

describe('new accounts', () => {
	it('start with default categories and no accounts', async () => {
		const categories = await json<{ categories: { name: string }[] }>(await mine('/categories'));
		const accounts = await json<{ accounts: unknown[] }>(await mine('/accounts'));

		expect(categories.categories.length).toBeGreaterThan(0);
		expect(categories.categories.some((entry) => entry.name === 'Groceries')).toBe(true);
		expect(accounts.accounts).toEqual([]);
	});

	it('provision each user their own copy, not a shared set', async () => {
		const ours = await json<{ categories: { id: string }[] }>(await mine('/categories'));
		const others = await json<{ categories: { id: string }[] }>(await theirs('/categories'));

		expect(ours.categories.length).toBe(others.categories.length);
		const overlap = ours.categories.filter((entry) => others.categories.some((other) => other.id === entry.id));
		expect(overlap).toEqual([]);
	});

	it('let two users hold categories of the same name', async () => {
		expect((await mine('/categories', { method: 'POST', body: JSON.stringify({ name: 'Hobbies', kind: 'expense' }) })).status).toBe(201);
		expect((await theirs('/categories', { method: 'POST', body: JSON.stringify({ name: 'Hobbies', kind: 'expense' }) })).status).toBe(201);
	});
});

describe('accounts are private', () => {
	let accountId: string;

	beforeEach(async () => {
		accountId = await makeAccount(mine, { name: 'Private', startingBalance: 500_00 });
	});

	it('are not listed for another user', async () => {
		const body = await json<{ accounts: unknown[] }>(await theirs('/accounts'));
		expect(body.accounts).toEqual([]);
	});

	it('read as missing rather than forbidden, so ids are not confirmed', async () => {
		expect((await theirs(`/accounts/${accountId}`)).status).toBe(404);
	});

	it('cannot be edited by another user', async () => {
		expect((await theirs(`/accounts/${accountId}`, { method: 'PATCH', body: JSON.stringify({ name: 'Stolen' }) })).status).toBe(404);

		const { account } = await json<{ account: { name: string } }>(await mine(`/accounts/${accountId}`));
		expect(account.name).toBe('Private');
	});

	it('cannot be deleted by another user', async () => {
		expect((await theirs(`/accounts/${accountId}`, { method: 'DELETE' })).status).toBe(404);
		expect((await mine(`/accounts/${accountId}`)).status).toBe(200);
	});
});

describe('transactions are private', () => {
	let accountId: string;
	let categoryId: string;
	let transactionId: string;

	beforeEach(async () => {
		accountId = await makeAccount(mine);
		categoryId = await makeCategory(mine, { name: 'Private Spending' });

		const created = await json<{ transaction: { id: string } }>(
			await mine('/transactions', {
				method: 'POST',
				body: JSON.stringify({ accountId, categoryId, amount: -1234, occurredOn: '2026-09-04', payee: 'Secret' }),
			}),
		);
		transactionId = created.transaction.id;
	});

	it('are not listed for another user', async () => {
		const body = await json<{ total: number }>(await theirs('/transactions'));
		expect(body.total).toBe(0);
	});

	it('are not reachable by filtering on the owner’s account id', async () => {
		const body = await json<{ total: number }>(await theirs(`/transactions?accountId=${accountId}`));
		expect(body.total).toBe(0);
	});

	it('cannot be edited or deleted by another user', async () => {
		expect((await theirs(`/transactions/${transactionId}`, { method: 'PATCH', body: JSON.stringify({ payee: 'Stolen' }) })).status).toBe(
			404,
		);
		expect((await theirs(`/transactions/${transactionId}`, { method: 'DELETE' })).status).toBe(404);

		const { total } = await json<{ total: number }>(await mine('/transactions'));
		expect(total).toBe(1);
	});

	it('cannot be written against another user’s account', async () => {
		const response = await theirs('/transactions', {
			method: 'POST',
			body: JSON.stringify({ accountId, amount: -500, occurredOn: '2026-09-04' }),
		});

		expect(response.status).toBe(400);
		expect((await json<{ total: number }>(await mine('/transactions'))).total).toBe(1);
	});

	it('cannot be written against another user’s category', async () => {
		const ownAccount = await makeAccount(theirs, { name: 'Theirs' });
		const response = await theirs('/transactions', {
			method: 'POST',
			body: JSON.stringify({ accountId: ownAccount, categoryId, amount: -500, occurredOn: '2026-09-04' }),
		});

		expect(response.status).toBe(400);
	});

	it('cannot be moved onto another user’s account by patching', async () => {
		const ownAccount = await makeAccount(theirs, { name: 'Theirs' });
		const own = await json<{ transaction: { id: string } }>(
			await theirs('/transactions', {
				method: 'POST',
				body: JSON.stringify({ accountId: ownAccount, amount: -100, occurredOn: '2026-09-04' }),
			}),
		);

		const response = await theirs(`/transactions/${own.transaction.id}`, {
			method: 'PATCH',
			body: JSON.stringify({ accountId }),
		});

		expect(response.status).toBe(400);
	});
});

describe('transfers are private', () => {
	it('cannot move money out of another user’s account', async () => {
		const victim = await makeAccount(mine, { name: 'Victim', startingBalance: 100_000 });
		const attacker = await makeAccount(theirs, { name: 'Attacker' });

		const response = await theirs('/transactions/transfer', {
			method: 'POST',
			body: JSON.stringify({ fromAccountId: victim, toAccountId: attacker, amount: 100_000, occurredOn: '2026-09-05' }),
		});

		expect(response.status).toBe(400);

		// Neither side may be left holding half a transfer.
		const { account } = await json<{ account: { balance: number } }>(await mine(`/accounts/${victim}`));
		expect(account.balance).toBe(100000);
		expect((await json<{ total: number }>(await theirs('/transactions'))).total).toBe(0);
	});
});

describe('budgets are private', () => {
	let categoryId: string;

	beforeEach(async () => {
		categoryId = await makeCategory(mine, { name: 'Private Budget' });
		await mine('/budgets', { method: 'PUT', body: JSON.stringify({ categoryId, month: '2026-09', amount: 50_000 }) });
	});

	it('are not listed for another user', async () => {
		const body = await json<{ budgets: unknown[] }>(await theirs('/budgets?month=2026-09'));
		expect(body.budgets).toEqual([]);
	});

	it('cannot be set against another user’s category', async () => {
		const response = await theirs('/budgets', {
			method: 'PUT',
			body: JSON.stringify({ categoryId, month: '2026-09', amount: 1 }),
		});
		expect(response.status).toBe(400);

		const { budgets } = await json<{ budgets: { amount: number }[] }>(await mine('/budgets?month=2026-09'));
		expect(budgets[0].amount).toBe(50000);
	});

	it('cannot be deleted by another user', async () => {
		const { budgets } = await json<{ budgets: { id: string }[] }>(await mine('/budgets?month=2026-09'));
		expect((await theirs(`/budgets/${budgets[0].id}`, { method: 'DELETE' })).status).toBe(404);
	});
});

describe('categories are private', () => {
	it('cannot be edited or deleted by another user', async () => {
		const categoryId = await makeCategory(mine, { name: 'Mine Only' });

		expect((await theirs(`/categories/${categoryId}`, { method: 'PATCH', body: JSON.stringify({ name: 'Stolen' }) })).status).toBe(404);
		expect((await theirs(`/categories/${categoryId}`, { method: 'DELETE' })).status).toBe(404);
	});
});

describe('summaries are private', () => {
	it('never mix two users’ figures, cached or not', async () => {
		const accountId = await makeAccount(mine, { startingBalance: 100_000 });
		await mine('/transactions', {
			method: 'POST',
			body: JSON.stringify({ accountId, amount: -25_000, occurredOn: '2026-09-04' }),
		});

		const ours = await json<{ netWorth: number; expenses: number }>(await mine('/summary?month=2026-09'));
		expect(ours).toMatchObject({ netWorth: 75000, expenses: 25000 });

		// Read twice: the second read is served from cache, which is the step that
		// would leak if the cache key were not scoped by user.
		const theirsFirst = await json<{ netWorth: number; expenses: number; cached: boolean }>(await theirs('/summary?month=2026-09'));
		const theirsSecond = await json<{ netWorth: number; cached: boolean }>(await theirs('/summary?month=2026-09'));

		expect(theirsFirst).toMatchObject({ netWorth: 0, expenses: 0, cached: false });
		expect(theirsSecond).toMatchObject({ netWorth: 0, cached: true });

		// And ours is still ours after theirs was cached.
		expect((await json<{ netWorth: number }>(await mine('/summary?month=2026-09'))).netWorth).toBe(75000);
	});
});
