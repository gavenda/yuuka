import { beforeEach, describe, expect, it } from 'vitest';
import { accountTypeId, authedClient, json, makeAccount, type Call } from './helpers';

let call: Call;
let accountId: string;

beforeEach(async () => {
	call = await authedClient();
	accountId = await makeAccount(call, { typeId: await accountTypeId(call) });
});

interface Category {
	id: string;
	name: string;
	kind: string;
	parentId: string | null;
	appliesTo: string;
}

async function categories(): Promise<Category[]> {
	return (await json<{ categories: Category[] }>(await call('/categories'))).categories;
}

const byName = async (name: string) => (await categories()).find((category) => category.name === name)!;

const addCategory = (body: Record<string, unknown>) => call('/categories', { method: 'POST', body: JSON.stringify(body) });

describe('subcategories', () => {
	it('are provisioned under their parents', async () => {
		const all = await categories();
		const groceries = all.find((category) => category.name === 'Groceries')!;
		const supermarket = all.find((category) => category.name === 'Supermarket')!;

		expect(groceries.parentId).toBeNull();
		expect(supermarket.parentId).toBe(groceries.id);
	});

	it('inherit their parent’s kind and scope', async () => {
		const cashflow = await byName('Cashflow');
		const investments = await byName('Investments');

		expect(investments.parentId).toBe(cashflow.id);
		expect(investments.kind).toBe(cashflow.kind);
		expect(investments.appliesTo).toBe('transfer');
	});

	it('can be created under an existing parent', async () => {
		const parent = await byName('Health');
		const response = await addCategory({ name: 'Dental', kind: 'income', parentId: parent.id });

		expect(response.status).toBe(201);
		const { category } = await json<{ category: Category }>(response);
		expect(category.parentId).toBe(parent.id);
		// The requested kind is ignored in favour of the parent's, so the two
		// can never drift apart.
		expect(category.kind).toBe('expense');
	});

	it('stop at one level deep', async () => {
		const child = await byName('Supermarket');
		const response = await addCategory({ name: 'Too deep', kind: 'expense', parentId: child.id });

		expect(response.status).toBe(400);
		expect((await json<{ error: string }>(response)).error).toContain('one level');
	});

	it('reject an unknown parent', async () => {
		expect((await addCategory({ name: 'Orphan', kind: 'expense', parentId: 'cat_nope' })).status).toBe(400);
	});

	it('allow the same name under different parents', async () => {
		const groceries = await byName('Groceries');
		const transport = await byName('Transport');

		expect((await addCategory({ name: 'Other', kind: 'expense', parentId: groceries.id })).status).toBe(201);
		expect((await addCategory({ name: 'Other', kind: 'expense', parentId: transport.id })).status).toBe(201);
	});

	it('still refuse a duplicate name among siblings', async () => {
		const groceries = await byName('Groceries');
		expect((await addCategory({ name: 'Supermarket', kind: 'expense', parentId: groceries.id })).status).toBe(409);
	});

	it('still refuse a duplicate top-level name', async () => {
		expect((await addCategory({ name: 'Groceries', kind: 'expense' })).status).toBe(409);
	});

	it('disappear with their parent, leaving transactions intact', async () => {
		const groceries = await byName('Groceries');
		const supermarket = await byName('Supermarket');

		await call('/transactions', {
			method: 'POST',
			body: JSON.stringify({ accountId, categoryId: supermarket.id, amount: -2500, occurredOn: '2026-09-04' }),
		});

		expect((await call(`/categories/${groceries.id}`, { method: 'DELETE' })).status).toBe(204);

		const remaining = await categories();
		expect(remaining.some((category) => category.id === supermarket.id)).toBe(false);

		const { transactions, total } = await json<{ transactions: { categoryId: string | null }[]; total: number }>(
			await call('/transactions'),
		);
		expect(total).toBe(1);
		expect(transactions[0].categoryId).toBeNull();
	});
});

describe('a transaction carries one category, never both', () => {
	it('accepts a subcategory', async () => {
		const supermarket = await byName('Supermarket');
		const response = await call('/transactions', {
			method: 'POST',
			body: JSON.stringify({ accountId, categoryId: supermarket.id, amount: -2500, occurredOn: '2026-09-04' }),
		});

		expect(response.status).toBe(201);
		const { transaction } = await json<{ transaction: { categoryId: string; categoryName: string } }>(response);
		expect(transaction.categoryId).toBe(supermarket.id);
		expect(transaction.categoryName).toBe('Supermarket');
	});

	it('accepts the parent instead', async () => {
		const groceries = await byName('Groceries');
		const response = await call('/transactions', {
			method: 'POST',
			body: JSON.stringify({ accountId, categoryId: groceries.id, amount: -2500, occurredOn: '2026-09-04' }),
		});

		expect(response.status).toBe(201);
		const { transaction } = await json<{ transaction: { categoryId: string } }>(response);
		expect(transaction.categoryId).toBe(groceries.id);
	});
});

describe('budgets roll subcategories into the parent', () => {
	it('counts a child’s spending against the parent’s plan', async () => {
		const groceries = await byName('Groceries');
		const supermarket = await byName('Supermarket');
		const market = await byName('Market');

		await call('/budgets', { method: 'PUT', body: JSON.stringify({ categoryId: groceries.id, month: '2026-09', amount: 60_000 }) });

		const spend = (categoryId: string, amount: number) =>
			call('/transactions', { method: 'POST', body: JSON.stringify({ accountId, categoryId, amount, occurredOn: '2026-09-04' }) });

		await spend(groceries.id, -10_000);
		await spend(supermarket.id, -15_000);
		await spend(market.id, -5_000);

		const { categories: breakdown } = await json<{
			categories: {
				categoryId: string;
				planned: number;
				actual: number;
				remaining: number;
				children: { name: string; actual: number }[];
			}[];
		}>(await call('/summary?month=2026-09'));

		const row = breakdown.find((entry) => entry.categoryId === groceries.id)!;
		expect(row.planned).toBe(60000);
		// The parent's own 100 plus both children.
		expect(row.actual).toBe(30000);
		expect(row.remaining).toBe(30000);

		const children = Object.fromEntries(row.children.map((child) => [child.name, child.actual]));
		expect(children).toEqual({ Supermarket: 15000, Market: 5000 });
	});

	it('lists only parents at the top level, so the list cannot double-count', async () => {
		const supermarket = await byName('Supermarket');
		await call('/transactions', {
			method: 'POST',
			body: JSON.stringify({ accountId, categoryId: supermarket.id, amount: -15_000, occurredOn: '2026-09-04' }),
		});

		const { categories: breakdown, expenses } = await json<{
			categories: { categoryId: string; actual: number }[];
			expenses: number;
		}>(await call('/summary?month=2026-09'));

		expect(breakdown.some((entry) => entry.categoryId === supermarket.id)).toBe(false);
		expect(breakdown.reduce((sum, entry) => sum + entry.actual, 0)).toBe(expenses);
	});

	it('refuses a budget on a subcategory', async () => {
		const supermarket = await byName('Supermarket');
		const response = await call('/budgets', {
			method: 'PUT',
			body: JSON.stringify({ categoryId: supermarket.id, month: '2026-09', amount: 1000 }),
		});

		expect(response.status).toBe(400);
		expect((await json<{ error: string }>(response)).error).toContain('parent');
	});
});
