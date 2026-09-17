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

const setBudgetMode = (budgetMode: 'fixed' | 'monthly') => call('/settings', { method: 'PATCH', body: JSON.stringify({ budgetMode }) });

const setIncomePlan = (body: Record<string, unknown>) => call('/income-plan', { method: 'PUT', body: JSON.stringify(body) });

const addTransaction = (body: Record<string, unknown>) =>
	call('/transactions', { method: 'POST', body: JSON.stringify({ accountId, ...body }) });

// Mode-agnostic behaviour: validation and deletion work the same whichever
// stored row a budget actually lands on.
describe('budgets', () => {
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

	it('rejects a budget that gives neither or both an amount and a percent', async () => {
		expect((await setBudget({ categoryId: groceries, month: '2026-09' })).status).toBe(400);
		expect((await setBudget({ categoryId: groceries, month: '2026-09', amount: 100, percent: 10 })).status).toBe(400);
	});
});

describe('budgets in fixed mode (the default)', () => {
	it('reports no month, since the plan does not belong to just one', async () => {
		const { budget } = await json<{ budget: { month: string | null } }>(
			await setBudget({ categoryId: groceries, month: '2026-09', amount: 60_000 }),
		);
		expect(budget.month).toBeNull();
	});

	it('applies the same planned amount to every month', async () => {
		await setBudget({ categoryId: groceries, month: '2026-09', amount: 60_000 });

		const september = await json<{ budgets: { amount: number }[] }>(await call('/budgets?month=2026-09'));
		const november = await json<{ budgets: { amount: number }[] }>(await call('/budgets?month=2026-11'));

		expect(september.budgets[0].amount).toBe(60_000);
		expect(november.budgets[0].amount).toBe(60_000);
	});

	it('updates in place rather than creating a second row, whatever month is passed', async () => {
		const first = await json<{ budget: { id: string } }>(await setBudget({ categoryId: groceries, month: '2026-09', amount: 60_000 }));
		const second = await json<{ budget: { id: string; amount: number } }>(
			await setBudget({ categoryId: groceries, month: '2026-11', amount: 75_000 }),
		);

		expect(second.budget.id).toBe(first.budget.id);
		expect(second.budget.amount).toBe(75000);

		const { budgets } = await json<{ budgets: unknown[] }>(await call('/budgets?month=2026-09'));
		expect(budgets).toHaveLength(1);
	});

	it('feeds the same planned figure into every month’s summary', async () => {
		await setBudget({ categoryId: groceries, month: '2026-09', amount: 60_000 });

		const september = await json<{ categories: { categoryId: string; planned: number }[] }>(await call('/summary?month=2026-09'));
		const november = await json<{ categories: { categoryId: string; planned: number }[] }>(await call('/summary?month=2026-11'));

		expect(september.categories.find((entry) => entry.categoryId === groceries)!.planned).toBe(60_000);
		expect(november.categories.find((entry) => entry.categoryId === groceries)!.planned).toBe(60_000);
	});
});

describe('switching to monthly mode', () => {
	it('stops seeing what was budgeted while fixed, and lets each month diverge from there', async () => {
		await setBudget({ categoryId: groceries, month: '2026-09', amount: 60_000 });
		await setBudgetMode('monthly');

		expect((await json<{ budgets: unknown[] }>(await call('/budgets?month=2026-09'))).budgets).toHaveLength(0);

		await setBudget({ categoryId: groceries, month: '2026-09', amount: 45_000 });
		await setBudget({ categoryId: groceries, month: '2026-10', amount: 30_000 });

		expect((await json<{ budgets: { amount: number }[] }>(await call('/budgets?month=2026-09'))).budgets[0].amount).toBe(45_000);
		expect((await json<{ budgets: { amount: number }[] }>(await call('/budgets?month=2026-10'))).budgets[0].amount).toBe(30_000);
	});
});

describe('budgets in monthly mode', () => {
	beforeEach(async () => {
		await setBudgetMode('monthly');
	});

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
});

describe('income plan', () => {
	it('starts at zero for a month nothing was set for', async () => {
		const { incomePlan } = await json<{ incomePlan: { month: string | null; amount: number } }>(await call('/income-plan?month=2026-09'));
		// Fixed is the default budget mode, so an unset plan reports no month either.
		expect(incomePlan).toMatchObject({ month: null, amount: 0 });
	});

	it('sets and updates the planned income for a month', async () => {
		await setIncomePlan({ month: '2026-09', amount: 500_000 });
		expect((await json<{ incomePlan: { amount: number } }>(await call('/income-plan?month=2026-09'))).incomePlan.amount).toBe(500000);

		await setIncomePlan({ month: '2026-09', amount: 600_000 });
		expect((await json<{ incomePlan: { amount: number } }>(await call('/income-plan?month=2026-09'))).incomePlan.amount).toBe(600000);
	});

	it('rejects a negative amount', async () => {
		expect((await setIncomePlan({ month: '2026-09', amount: -1 })).status).toBe(400);
	});

	it('defaults to fixed mode with no gross amount', async () => {
		await setIncomePlan({ month: '2026-09', amount: 50_000 });
		const { incomePlan } = await json<{ incomePlan: { mode: string; grossAmount: number | null } }>(
			await call('/income-plan?month=2026-09'),
		);
		expect(incomePlan).toMatchObject({ mode: 'fixed', grossAmount: null });
	});

	it('remembers the gross salary a net figure was derived from', async () => {
		await setIncomePlan({ month: '2026-09', amount: 45_000, mode: 'gross', grossAmount: 50_000 });
		const { incomePlan } = await json<{ incomePlan: { amount: number; mode: string; grossAmount: number | null } }>(
			await call('/income-plan?month=2026-09'),
		);
		expect(incomePlan).toMatchObject({ amount: 45_000, mode: 'gross', grossAmount: 50_000 });
	});

	it('clears the gross amount when switching back to fixed', async () => {
		await setIncomePlan({ month: '2026-09', amount: 45_000, mode: 'gross', grossAmount: 50_000 });
		await setIncomePlan({ month: '2026-09', amount: 60_000, mode: 'fixed' });

		const { incomePlan } = await json<{ incomePlan: { amount: number; mode: string; grossAmount: number | null } }>(
			await call('/income-plan?month=2026-09'),
		);
		expect(incomePlan).toMatchObject({ amount: 60_000, mode: 'fixed', grossAmount: null });
	});

	it('rejects gross mode without a gross amount', async () => {
		expect((await setIncomePlan({ month: '2026-09', amount: 45_000, mode: 'gross' })).status).toBe(400);
	});
});

describe('income plan in fixed budget mode (the default)', () => {
	it('reports no month, since the plan does not belong to just one', async () => {
		const { incomePlan } = await json<{ incomePlan: { month: string | null } }>(await setIncomePlan({ month: '2026-09', amount: 500_000 }));
		expect(incomePlan.month).toBeNull();
	});

	it('applies the same planned income to every month', async () => {
		await setIncomePlan({ month: '2026-09', amount: 500_000 });

		expect((await json<{ incomePlan: { amount: number } }>(await call('/income-plan?month=2026-09'))).incomePlan.amount).toBe(500_000);
		expect((await json<{ incomePlan: { amount: number } }>(await call('/income-plan?month=2026-11'))).incomePlan.amount).toBe(500_000);
	});

	it('updates in place rather than creating a second row, whatever month is passed', async () => {
		await setIncomePlan({ month: '2026-09', amount: 500_000 });
		await setIncomePlan({ month: '2026-11', amount: 700_000 });

		expect((await json<{ incomePlan: { amount: number } }>(await call('/income-plan?month=2026-09'))).incomePlan.amount).toBe(700_000);
	});

	it('feeds the same planned figure into every month’s summary', async () => {
		await setIncomePlan({ month: '2026-09', amount: 500_000 });

		const september = await json<{ plannedIncome: number }>(await call('/summary?month=2026-09'));
		const november = await json<{ plannedIncome: number }>(await call('/summary?month=2026-11'));

		expect(september.plannedIncome).toBe(500_000);
		expect(november.plannedIncome).toBe(500_000);
	});
});

describe('switching income plan to monthly mode', () => {
	it('stops seeing what was planned while fixed, and lets each month diverge from there', async () => {
		await setIncomePlan({ month: '2026-09', amount: 500_000 });
		await setBudgetMode('monthly');

		expect((await json<{ incomePlan: { amount: number } }>(await call('/income-plan?month=2026-09'))).incomePlan.amount).toBe(0);

		await setIncomePlan({ month: '2026-09', amount: 450_000 });
		await setIncomePlan({ month: '2026-10', amount: 300_000 });

		expect((await json<{ incomePlan: { amount: number } }>(await call('/income-plan?month=2026-09'))).incomePlan.amount).toBe(450_000);
		expect((await json<{ incomePlan: { amount: number } }>(await call('/income-plan?month=2026-10'))).incomePlan.amount).toBe(300_000);
	});
});

describe('income plan in monthly mode', () => {
	beforeEach(async () => {
		await setBudgetMode('monthly');
	});

	it('keeps months independent', async () => {
		await setIncomePlan({ month: '2026-09', amount: 500_000 });
		await setIncomePlan({ month: '2026-10', amount: 700_000 });

		expect((await json<{ incomePlan: { amount: number } }>(await call('/income-plan?month=2026-09'))).incomePlan.amount).toBe(500000);
		expect((await json<{ incomePlan: { amount: number } }>(await call('/income-plan?month=2026-10'))).incomePlan.amount).toBe(700000);
	});
});

describe('percentage-based budgets', () => {
	beforeEach(async () => {
		await setIncomePlan({ month: '2026-09', amount: 500_000 });
	});

	it('derives the planned amount from the month’s planned income', async () => {
		const { budget } = await json<{ budget: { amount: number; percent: number } }>(
			await setBudget({ categoryId: groceries, month: '2026-09', percent: 20 }),
		);
		expect(budget.percent).toBe(20);

		const { categories } = await json<{ categories: { categoryId: string; planned: number; plannedPercent: number | null }[] }>(
			await call('/summary?month=2026-09'),
		);
		const row = categories.find((entry) => entry.categoryId === groceries)!;
		expect(row).toMatchObject({ planned: 100_000, plannedPercent: 20 });
	});

	it('recomputes as planned income changes, without editing the budget again', async () => {
		await setBudget({ categoryId: groceries, month: '2026-09', percent: 10 });
		await setIncomePlan({ month: '2026-09', amount: 1_000_000 });

		const { categories } = await json<{ categories: { categoryId: string; planned: number }[] }>(await call('/summary?month=2026-09'));
		expect(categories.find((entry) => entry.categoryId === groceries)!.planned).toBe(100_000);
	});

	it('switches a category back to a fixed amount, clearing the percent', async () => {
		await setBudget({ categoryId: groceries, month: '2026-09', percent: 20 });
		const { budget } = await json<{ budget: { amount: number; percent: number | null } }>(
			await setBudget({ categoryId: groceries, month: '2026-09', amount: 45_000 }),
		);
		expect(budget).toMatchObject({ amount: 45000, percent: null });
	});

	it('rejects a percent outside 0-100', async () => {
		expect((await setBudget({ categoryId: groceries, month: '2026-09', percent: -1 })).status).toBe(400);
		expect((await setBudget({ categoryId: groceries, month: '2026-09', percent: 101 })).status).toBe(400);
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
