import { beforeEach, describe, expect, it } from 'vitest';
import { authedClient, categoryCount, json, makeAccount, makeCategory, type Call } from './helpers';

let call: Call;

beforeEach(async () => {
	call = await authedClient();
});

describe('categories', () => {
	it('creates a category with defaults', async () => {
		const response = await call('/categories', { method: 'POST', body: JSON.stringify({ name: 'Bicycle', kind: 'expense' }) });

		expect(response.status).toBe(201);
		const { category } = await json<{ category: Record<string, unknown> }>(response);
		expect(category).toMatchObject({ name: 'Bicycle', kind: 'expense', color: '#64748b', sortOrder: 0, archived: false });
	});

	it('rejects an invalid colour', async () => {
		const response = await call('/categories', {
			method: 'POST',
			body: JSON.stringify({ name: 'Bicycle', kind: 'expense', color: 'red' }),
		});
		expect(response.status).toBe(400);
	});

	it('rejects an unknown kind', async () => {
		const response = await call('/categories', { method: 'POST', body: JSON.stringify({ name: 'Bicycle', kind: 'savings' }) });
		expect(response.status).toBe(400);
	});

	it('refuses a duplicate name within the same kind', async () => {
		await makeCategory(call, { name: 'Travel', kind: 'expense' });
		const response = await call('/categories', { method: 'POST', body: JSON.stringify({ name: 'Travel', kind: 'expense' }) });
		expect(response.status).toBe(409);
	});

	it('allows the same name across different kinds', async () => {
		await makeCategory(call, { name: 'Refunds', kind: 'expense' });
		const response = await call('/categories', { method: 'POST', body: JSON.stringify({ name: 'Refunds', kind: 'income' }) });
		expect(response.status).toBe(201);
	});

	it('refuses to rename onto an existing name', async () => {
		await makeCategory(call, { name: 'Food', kind: 'expense' });
		const other = await makeCategory(call, { name: 'Fuel', kind: 'expense' });

		const response = await call(`/categories/${other}`, { method: 'PATCH', body: JSON.stringify({ name: 'Food' }) });
		expect(response.status).toBe(409);
	});

	it('orders expense before income, then by sort order', async () => {
		await makeCategory(call, { name: 'Zeta', kind: 'expense', sortOrder: 98 });
		await makeCategory(call, { name: 'Alpha', kind: 'expense', sortOrder: 99 });

		const { categories } = await json<{ categories: { name: string; kind: string }[] }>(await call('/categories'));

		// The provisioned defaults share this listing, so compare positions rather
		// than the whole array.
		const names = categories.map((entry) => entry.name);
		expect(names.indexOf('Zeta')).toBeLessThan(names.indexOf('Alpha'));

		const firstIncome = categories.findIndex((entry) => entry.kind === 'income');
		const lastExpense = categories.map((entry) => entry.kind).lastIndexOf('expense');
		expect(lastExpense).toBeLessThan(firstIncome);
	});

	it('hides archived categories unless asked', async () => {
		const before = await categoryCount(call);
		const id = await makeCategory(call, { name: 'Obsolete' });
		await call(`/categories/${id}`, { method: 'PATCH', body: JSON.stringify({ archived: true }) });

		expect(await categoryCount(call)).toBe(before);
		expect(await categoryCount(call, true)).toBe(before + 1);
	});

	it('keeps transactions when a category is deleted, but unlinks them', async () => {
		const accountId = await makeAccount(call);
		const categoryId = await makeCategory(call, { name: 'Temporary' });

		await call('/transactions', {
			method: 'POST',
			body: JSON.stringify({ accountId, categoryId, amount: -1500, occurredOn: '2026-09-07' }),
		});

		expect((await call(`/categories/${categoryId}`, { method: 'DELETE' })).status).toBe(204);

		const { transactions, total } = await json<{ transactions: { categoryId: string | null }[]; total: number }>(
			await call('/transactions'),
		);
		expect(total).toBe(1);
		expect(transactions[0].categoryId).toBeNull();
	});

	it('404s for an unknown category', async () => {
		expect((await call('/categories/cat_nope', { method: 'DELETE' })).status).toBe(404);
		expect((await call('/categories/cat_nope', { method: 'PATCH', body: JSON.stringify({ name: 'x' }) })).status).toBe(404);
	});
});
