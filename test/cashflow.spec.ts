import { beforeEach, describe, expect, it } from 'vitest';
import { authedClient, json, makeAccount, type Call } from './helpers';

let call: Call;
let from: string;
let to: string;

interface Category {
	id: string;
	name: string;
	parentId: string | null;
	appliesTo: string;
}

beforeEach(async () => {
	call = await authedClient();
	from = await makeAccount(call, { name: 'Checking', startingBalance: 500_000 });
	to = await makeAccount(call, { name: 'Brokerage' });
});

const categories = async () => (await json<{ categories: Category[] }>(await call('/categories'))).categories;
const byName = async (name: string) => (await categories()).find((category) => category.name === name)!;

const transfer = (body: Record<string, unknown>) =>
	call('/transactions/transfer', {
		method: 'POST',
		body: JSON.stringify({ fromAccountId: from, toAccountId: to, amount: 100_000, occurredOn: '2026-09-05', ...body }),
	});

describe('the Cashflow category', () => {
	it('is provisioned with its subcategories', async () => {
		const cashflow = await byName('Cashflow');
		expect(cashflow.appliesTo).toBe('transfer');
		expect(cashflow.parentId).toBeNull();

		const children = (await categories()).filter((category) => category.parentId === cashflow.id);
		expect(children.map((child) => child.name).sort()).toEqual(['Debt Repayment', 'Investments', 'Savings']);
	});

	it('is not offered to spending or income', async () => {
		// The category exists, but a plain transaction may not use it — the scope
		// is what keeps the two sets apart.
		const cashflow = await byName('Cashflow');
		const response = await call('/transactions', {
			method: 'POST',
			body: JSON.stringify({ accountId: from, categoryId: cashflow.id, amount: -1000, occurredOn: '2026-09-04' }),
		});

		expect(response.status).toBe(400);
		expect((await json<{ error: string }>(response)).error).toContain('cannot be used here');
	});

	it('categorises a transfer, on both legs', async () => {
		const investments = await byName('Investments');
		const response = await transfer({ categoryId: investments.id });

		expect(response.status).toBe(201);
		const { transactions } = await json<{ transactions: { categoryId: string; categoryName: string; amount: number }[] }>(response);

		expect(transactions).toHaveLength(2);
		for (const leg of transactions) {
			expect(leg.categoryId).toBe(investments.id);
			expect(leg.categoryName).toBe('Investments');
		}
	});

	it('still allows an uncategorised transfer', async () => {
		const response = await transfer({});
		expect(response.status).toBe(201);

		const { transactions } = await json<{ transactions: { categoryId: string | null }[] }>(response);
		expect(transactions[0].categoryId).toBeNull();
	});

	it('refuses a standard category on a transfer', async () => {
		const groceries = await byName('Groceries');
		const response = await transfer({ categoryId: groceries.id });

		expect(response.status).toBe(400);
		// A rejected transfer must not leave half a movement behind.
		expect((await json<{ total: number }>(await call('/transactions'))).total).toBe(0);
	});
});

describe('cashflow in the summary', () => {
	it('measures the outflow leg, not both sides netting to zero', async () => {
		const investments = await byName('Investments');
		await transfer({ categoryId: investments.id });

		const body = await json<{
			cashflow: number;
			income: number;
			expenses: number;
			categories: { name: string; actual: number; appliesTo: string; children: { name: string; actual: number }[] }[];
		}>(await call('/summary?month=2026-09'));

		expect(body.cashflow).toBe(100000);

		const cashflow = body.categories.find((entry) => entry.name === 'Cashflow')!;
		expect(cashflow.appliesTo).toBe('transfer');
		expect(cashflow.actual).toBe(100000);
		expect(cashflow.children.find((child) => child.name === 'Investments')!.actual).toBe(100000);
	});

	it('stays out of income and spending', async () => {
		const investments = await byName('Investments');
		await transfer({ categoryId: investments.id });

		const body = await json<{ income: number; expenses: number; netWorth: number }>(await call('/summary?month=2026-09'));

		// Moving your own money is neither earning nor spending, and net worth
		// is unchanged by it.
		expect(body.income).toBe(0);
		expect(body.expenses).toBe(0);
		expect(body.netWorth).toBe(500000);
	});

	it('can be budgeted like any other parent', async () => {
		const cashflow = await byName('Cashflow');
		const investments = await byName('Investments');

		expect(
			(await call('/budgets', { method: 'PUT', body: JSON.stringify({ categoryId: cashflow.id, month: '2026-09', amount: 150_000 }) }))
				.status,
		).toBe(200);

		await transfer({ categoryId: investments.id });

		const { categories: breakdown } = await json<{
			categories: { name: string; planned: number; actual: number; remaining: number }[];
		}>(await call('/summary?month=2026-09'));

		const row = breakdown.find((entry) => entry.name === 'Cashflow')!;
		expect(row).toMatchObject({ planned: 150000, actual: 100000, remaining: 50000 });
	});

	it('is excluded from the expense budget total', async () => {
		const cashflow = await byName('Cashflow');
		await call('/budgets', { method: 'PUT', body: JSON.stringify({ categoryId: cashflow.id, month: '2026-09', amount: 150_000 }) });

		const { totalBudgeted } = await json<{ totalBudgeted: number }>(await call('/summary?month=2026-09'));
		// Budgeting a movement is not budgeting spending.
		expect(totalBudgeted).toBe(0);
	});

	it('does not appear in the daily spend chart', async () => {
		const investments = await byName('Investments');
		await transfer({ categoryId: investments.id });

		const { dailySpend } = await json<{ dailySpend: unknown[] }>(await call('/summary?month=2026-09'));
		expect(dailySpend).toEqual([]);
	});
});
