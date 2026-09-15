import { beforeEach, describe, expect, it } from 'vitest';
import { authedClient, json, makeAccount, makeCategory, type Call } from './helpers';

let call: Call;
let accountId: string;
let groceries: string;
let salary: string;

beforeEach(async () => {
	call = await authedClient();
	accountId = await makeAccount(call, { startingBalance: 100_000 });
	groceries = await makeCategory(call, { name: 'Groceries', kind: 'expense' });
	salary = await makeCategory(call, { name: 'Salary', kind: 'income' });
});

const setBudget = (body: Record<string, unknown>) => call('/budgets', { method: 'PUT', body: JSON.stringify(body) });

const addTransaction = (body: Record<string, unknown>) =>
	call('/transactions', { method: 'POST', body: JSON.stringify({ accountId, ...body }) });

describe('budgets', () => {
	it('sets a planned amount for a category and month', async () => {
		const response = await setBudget({ categoryId: groceries, month: '2026-09', amount: 60_000 });
		expect(response.status).toBe(200);

		const { budget } = await json<{ budget: Record<string, unknown> }>(response);
		expect(budget).toMatchObject({ categoryId: groceries, month: '2026-09', amount: 60000 });
	});

	it('updates in place rather than creating a second row', async () => {
		const first = await json<{ budget: { id: string } }>(await setBudget({ categoryId: groceries, month: '2026-09', amount: 60_000 }));
		const second = await json<{ budget: { id: string; amount: number } }>(
			await setBudget({ categoryId: groceries, month: '2026-09', amount: 75_000 }),
		);

		expect(second.budget.id).toBe(first.budget.id);
		expect(second.budget.amount).toBe(75000);

		const { budgets } = await json<{ budgets: unknown[] }>(await call('/budgets?month=2026-09'));
		expect(budgets).toHaveLength(1);
	});

	it('keeps months independent', async () => {
		await setBudget({ categoryId: groceries, month: '2026-09', amount: 60_000 });
		await setBudget({ categoryId: groceries, month: '2026-10', amount: 40_000 });

		expect((await json<{ budgets: unknown[] }>(await call('/budgets?month=2026-09'))).budgets).toHaveLength(1);
		expect((await json<{ budgets: { amount: number }[] }>(await call('/budgets?month=2026-10'))).budgets[0].amount).toBe(40000);
	});

	it('rejects a negative amount, an unknown category and a bad month', async () => {
		expect((await setBudget({ categoryId: groceries, month: '2026-09', amount: -1 })).status).toBe(400);
		expect((await setBudget({ categoryId: 'cat_nope', month: '2026-09', amount: 100 })).status).toBe(400);
		expect((await setBudget({ categoryId: groceries, month: 'September', amount: 100 })).status).toBe(400);
	});

	it('deletes a budget', async () => {
		const { budget } = await json<{ budget: { id: string } }>(await setBudget({ categoryId: groceries, month: '2026-09', amount: 60_000 }));

		expect((await call(`/budgets/${budget.id}`, { method: 'DELETE' })).status).toBe(204);
		expect((await json<{ budgets: unknown[] }>(await call('/budgets?month=2026-09'))).budgets).toHaveLength(0);
		expect((await call(`/budgets/${budget.id}`, { method: 'DELETE' })).status).toBe(404);
	});

	it('disappears with its category', async () => {
		await setBudget({ categoryId: groceries, month: '2026-09', amount: 60_000 });
		await call(`/categories/${groceries}`, { method: 'DELETE' });

		expect((await json<{ budgets: unknown[] }>(await call('/budgets?month=2026-09'))).budgets).toHaveLength(0);
	});
});

describe('summary', () => {
	beforeEach(async () => {
		await addTransaction({ categoryId: salary, amount: 450_000, occurredOn: '2026-09-01' });
		await addTransaction({ categoryId: groceries, amount: -8_450, occurredOn: '2026-09-03' });
		await addTransaction({ categoryId: groceries, amount: -12_000, occurredOn: '2026-09-11' });
		// Outside the month under test, so it must not appear in the totals.
		await addTransaction({ categoryId: groceries, amount: -99_999, occurredOn: '2026-08-30' });
	});

	it('totals income and spending for the month only', async () => {
		const body = await json<{ income: number; expenses: number; net: number }>(await call('/summary?month=2026-09'));

		expect(body.income).toBe(450000);
		expect(body.expenses).toBe(8450 + 12000);
		expect(body.net).toBe(450000 - 20450);
	});

	it('reports net worth across all time, not just the month', async () => {
		const body = await json<{ netWorth: number }>(await call('/summary?month=2026-09'));
		expect(body.netWorth).toBe(100000 + 450000 - 8450 - 12000 - 99999);
	});

	it('compares planned against actual as positive magnitudes', async () => {
		await setBudget({ categoryId: groceries, month: '2026-09', amount: 60_000 });

		const { categories } = await json<{ categories: { categoryId: string; planned: number; actual: number; remaining: number }[] }>(
			await call('/summary?month=2026-09'),
		);

		const row = categories.find((entry) => entry.categoryId === groceries)!;
		expect(row).toMatchObject({ planned: 60000, actual: 20450, remaining: 39550 });

		const income = categories.find((entry) => entry.categoryId === salary)!;
		expect(income.actual).toBe(450000);
	});

	it('shows a negative remainder when a category is overspent', async () => {
		await setBudget({ categoryId: groceries, month: '2026-09', amount: 10_000 });

		const { categories } = await json<{ categories: { categoryId: string; remaining: number }[] }>(await call('/summary?month=2026-09'));
		expect(categories.find((entry) => entry.categoryId === groceries)!.remaining).toBe(10000 - 20450);
	});

	it('breaks spending down by day', async () => {
		const { dailySpend } = await json<{ dailySpend: { date: string; amount: number }[] }>(await call('/summary?month=2026-09'));
		expect(dailySpend).toEqual([
			{ date: '2026-09-03', amount: 8450 },
			{ date: '2026-09-11', amount: 12000 },
		]);
	});

	it('excludes transfers from income and spending', async () => {
		const savings = await makeAccount(call, { name: 'Savings' });
		await call('/transactions/transfer', {
			method: 'POST',
			body: JSON.stringify({ fromAccountId: accountId, toAccountId: savings, amount: 200_000, occurredOn: '2026-09-15' }),
		});

		const body = await json<{ income: number; expenses: number; netWorth: number }>(await call('/summary?month=2026-09'));
		expect(body.income).toBe(450000);
		expect(body.expenses).toBe(20450);
		// The money moved, so net worth is unchanged.
		expect(body.netWorth).toBe(100000 + 450000 - 8450 - 12000 - 99999);
	});

	it('serves the second read from cache, and refreshes it after a write', async () => {
		expect((await json<{ cached: boolean }>(await call('/summary?month=2026-09'))).cached).toBe(false);
		expect((await json<{ cached: boolean }>(await call('/summary?month=2026-09'))).cached).toBe(true);

		await addTransaction({ categoryId: groceries, amount: -500, occurredOn: '2026-09-18' });

		const refreshed = await json<{ cached: boolean; expenses: number }>(await call('/summary?month=2026-09'));
		expect(refreshed.cached).toBe(false);
		expect(refreshed.expenses).toBe(20450 + 500);
	});

	it('invalidates the cache when a budget changes', async () => {
		await call('/summary?month=2026-09');
		await setBudget({ categoryId: groceries, month: '2026-09', amount: 60_000 });

		expect((await json<{ cached: boolean }>(await call('/summary?month=2026-09'))).cached).toBe(false);
	});

	it('invalidates both months when a transaction moves across a boundary', async () => {
		const created = await json<{ transaction: { id: string } }>(
			await addTransaction({ categoryId: groceries, amount: -777, occurredOn: '2026-09-05' }),
		);

		await call('/summary?month=2026-09');
		await call('/summary?month=2026-10');

		await call(`/transactions/${created.transaction.id}`, { method: 'PATCH', body: JSON.stringify({ occurredOn: '2026-10-05' }) });

		expect((await json<{ cached: boolean }>(await call('/summary?month=2026-09'))).cached).toBe(false);
		expect((await json<{ cached: boolean }>(await call('/summary?month=2026-10'))).cached).toBe(false);
	});

	it('rejects a malformed month', async () => {
		expect((await call('/summary?month=nope')).status).toBe(400);
	});
});
