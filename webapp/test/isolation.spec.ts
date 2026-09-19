import { env } from 'cloudflare:test';
import { beforeEach, describe, expect, it } from 'vitest';
import { runDueSubscriptions } from '../server/subscriptions';
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

	it('cannot be balance-adjusted by another user', async () => {
		const response = await theirs(`/accounts/${accountId}/adjust`, {
			method: 'POST',
			body: JSON.stringify({ balance: 999_00, occurredOn: '2026-09-04' }),
		});
		expect(response.status).toBe(404);

		const { account } = await json<{ account: { balance: number } }>(await mine(`/accounts/${accountId}`));
		expect(account.balance).toBe(500_00);
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

	it('cannot be edited by another user', async () => {
		const from = await makeAccount(mine, { name: 'From', startingBalance: 50_000 });
		const to = await makeAccount(mine, { name: 'To' });

		const created = await json<{ transferId: string }>(
			await mine('/transactions/transfer', {
				method: 'POST',
				body: JSON.stringify({ fromAccountId: from, toAccountId: to, amount: 10_000, occurredOn: '2026-09-05' }),
			}),
		);

		const response = await theirs(`/transactions/transfer/${created.transferId}`, {
			method: 'PATCH',
			body: JSON.stringify({ fromAccountId: from, toAccountId: to, amount: 50_000, occurredOn: '2026-09-05' }),
		});

		expect(response.status).toBe(404);

		const { account } = await json<{ account: { balance: number } }>(await mine(`/accounts/${from}`));
		expect(account.balance).toBe(40000);
	});

	it('cannot be re-pointed onto another user’s account by patching', async () => {
		const from = await makeAccount(mine, { name: 'From', startingBalance: 50_000 });
		const to = await makeAccount(mine, { name: 'To' });
		const theirAccount = await makeAccount(theirs, { name: 'Theirs' });

		const created = await json<{ transferId: string }>(
			await mine('/transactions/transfer', {
				method: 'POST',
				body: JSON.stringify({ fromAccountId: from, toAccountId: to, amount: 10_000, occurredOn: '2026-09-05' }),
			}),
		);

		const response = await mine(`/transactions/transfer/${created.transferId}`, {
			method: 'PATCH',
			body: JSON.stringify({ fromAccountId: from, toAccountId: theirAccount, amount: 10_000, occurredOn: '2026-09-05' }),
		});

		expect(response.status).toBe(400);
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

describe('income plans are private', () => {
	beforeEach(async () => {
		await mine('/income-plan', {
			method: 'PUT',
			body: JSON.stringify({ month: '2026-09', amount: 500_000, mode: 'gross', grossAmount: 550_000 }),
		});
	});

	it('are not shown to another user', async () => {
		const { incomePlan } = await json<{ incomePlan: { amount: number; mode: string; grossAmount: number | null } }>(
			await theirs('/income-plan?month=2026-09'),
		);
		expect(incomePlan).toMatchObject({ amount: 0, mode: 'fixed', grossAmount: null });
	});

	it('setting one does not change another user’s figure', async () => {
		await theirs('/income-plan', { method: 'PUT', body: JSON.stringify({ month: '2026-09', amount: 999_000 }) });

		const { incomePlan } = await json<{ incomePlan: { amount: number } }>(await mine('/income-plan?month=2026-09'));
		expect(incomePlan.amount).toBe(500000);
	});
});

describe('categories are private', () => {
	it('cannot be edited or deleted by another user', async () => {
		const categoryId = await makeCategory(mine, { name: 'Mine Only' });

		expect((await theirs(`/categories/${categoryId}`, { method: 'PATCH', body: JSON.stringify({ name: 'Stolen' }) })).status).toBe(404);
		expect((await theirs(`/categories/${categoryId}`, { method: 'DELETE' })).status).toBe(404);
	});
});

describe('default account setting is private', () => {
	it('cannot be pointed at another user’s account', async () => {
		const theirAccountId = await makeAccount(theirs, { name: 'Not yours' });

		const response = await mine('/settings', { method: 'PATCH', body: JSON.stringify({ defaultAccountId: theirAccountId }) });
		expect(response.status).toBe(400);

		const { settings } = await json<{ settings: { defaultAccountId: string | null } }>(await mine('/settings'));
		expect(settings.defaultAccountId).toBeNull();
	});
});

describe('round-up rule is private', () => {
	it('cannot be pointed at another user’s account', async () => {
		const theirAccountId = await makeAccount(theirs, { name: 'Not yours' });

		const response = await mine('/round-up', { method: 'PATCH', body: JSON.stringify({ destinationAccountId: theirAccountId }) });
		expect(response.status).toBe(400);

		const { roundUpRule } = await json<{ roundUpRule: { destinationAccountId: string | null } }>(await mine('/round-up'));
		expect(roundUpRule.destinationAccountId).toBeNull();
	});

	it('one user changing theirs does not touch another’s', async () => {
		const destinationId = await makeAccount(mine, { name: 'Savings' });
		await mine('/round-up', { method: 'PATCH', body: JSON.stringify({ enabled: true, destinationAccountId: destinationId }) });

		const { roundUpRule } = await json<{ roundUpRule: { enabled: boolean } }>(await theirs('/round-up'));
		expect(roundUpRule.enabled).toBe(false);
	});

	it('cannot be pointed at another user’s category', async () => {
		const theirCategoryId = await makeCategory(theirs, { name: 'Cashflow', kind: 'expense', appliesTo: 'transfer' });

		const response = await mine('/round-up', { method: 'PATCH', body: JSON.stringify({ categoryId: theirCategoryId }) });
		expect(response.status).toBe(400);

		const { roundUpRule } = await json<{ roundUpRule: { categoryId: string | null } }>(await mine('/round-up'));
		expect(roundUpRule.categoryId).toBeNull();
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

describe('subscriptions are private', () => {
	let subscriptionId: string;
	const startOn = new Date(Date.now() + 3 * 86_400_000).toISOString().slice(0, 10);

	beforeEach(async () => {
		const accountId = await makeAccount(mine, { name: 'Private' });
		const response = await mine('/subscriptions', {
			method: 'POST',
			body: JSON.stringify({ accountId, amount: -1500, payee: 'Netflix', startOn }),
		});
		subscriptionId = (await json<{ subscription: { id: string } }>(response)).subscription.id;
	});

	it('are not listed for another user', async () => {
		const body = await json<{ subscriptions: unknown[] }>(await theirs('/subscriptions'));
		expect(body.subscriptions).toEqual([]);
	});

	it('cannot be changed or deleted by another user, and answer 404 not 403', async () => {
		const patched = await theirs(`/subscriptions/${subscriptionId}`, { method: 'PATCH', body: JSON.stringify({ amount: -1 }) });
		const deleted = await theirs(`/subscriptions/${subscriptionId}`, { method: 'DELETE' });
		expect(patched.status).toBe(404);
		expect(deleted.status).toBe(404);

		const { subscriptions } = await json<{ subscriptions: { amount: number }[] }>(await mine('/subscriptions'));
		expect(subscriptions).toMatchObject([{ amount: -1500 }]);
	});

	it('cannot be pointed at another user’s account or category', async () => {
		const theirAccountId = await makeAccount(theirs, { name: 'Not yours' });
		const theirCategoryId = await makeCategory(theirs, { name: 'Not yours', kind: 'expense' });
		const patch = (body: Record<string, unknown>) =>
			mine(`/subscriptions/${subscriptionId}`, { method: 'PATCH', body: JSON.stringify(body) });

		expect((await patch({ accountId: theirAccountId })).status).toBe(400);
		expect((await patch({ categoryId: theirCategoryId })).status).toBe(400);
	});

	it('post only to their owner’s account', async () => {
		await runDueSubscriptions(env, new Date(`${startOn}T00:00:00Z`));

		const mineRows = await json<{ total: number }>(await mine('/transactions'));
		const theirRows = await json<{ total: number }>(await theirs('/transactions'));
		expect(mineRows.total).toBe(1);
		expect(theirRows.total).toBe(0);
	});
});
