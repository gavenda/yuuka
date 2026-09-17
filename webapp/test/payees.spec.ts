import { beforeEach, describe, expect, it } from 'vitest';
import { authedClient, json, makeAccount, makeCategory, otherClient, type Call } from './helpers';

let call: Call;
let checking: string;
let savings: string;
let groceries: string;

interface Payee {
	id: string;
	payee: string;
	kind: string;
	accountId: string | null;
	toAccountId: string | null;
	categoryId: string | null;
	notes: string;
	usedCount: number;
}

beforeEach(async () => {
	call = await authedClient();
	checking = await makeAccount(call, { name: 'Checking', startingBalance: 500_000 });
	savings = await makeAccount(call, { name: 'Savings' });
	groceries = await makeCategory(call, { name: 'Groceries' });
});

const post = (body: Record<string, unknown>) =>
	call('/transactions', {
		method: 'POST',
		body: JSON.stringify({ accountId: checking, amount: -2500, occurredOn: '2026-09-04', ...body }),
	});

const transfer = (body: Record<string, unknown>) =>
	call('/transactions/transfer', {
		method: 'POST',
		body: JSON.stringify({ fromAccountId: checking, toAccountId: savings, amount: 50_000, occurredOn: '2026-09-05', ...body }),
	});

const payees = async (query = ''): Promise<Payee[]> => (await json<{ payees: Payee[] }>(await call(`/payees${query}`))).payees;

describe('remembering payees', () => {
	it('starts empty', async () => {
		expect(await payees()).toEqual([]);
	});

	it('records the details a named transaction was filed under', async () => {
		await post({ payee: 'Corner Market', categoryId: groceries, notes: 'weekly shop' });

		const [entry] = await payees();
		expect(entry).toMatchObject({
			payee: 'Corner Market',
			kind: 'expense',
			accountId: checking,
			categoryId: groceries,
			notes: 'weekly shop',
			usedCount: 1,
		});
	});

	it('remembers nothing for a blank payee', async () => {
		await post({ categoryId: groceries });
		expect(await payees()).toEqual([]);
	});

	it('records income as income', async () => {
		const salary = await makeCategory(call, { name: 'Salary', kind: 'income' });
		await post({ payee: 'Employer', amount: 450_000, categoryId: salary });

		const [entry] = await payees();
		expect(entry).toMatchObject({ payee: 'Employer', kind: 'income', categoryId: salary });
	});

	it('keeps one row per payee, counting uses', async () => {
		await post({ payee: 'Corner Market', categoryId: groceries });
		await post({ payee: 'Corner Market', categoryId: groceries });
		await post({ payee: 'Corner Market', categoryId: groceries });

		const all = await payees();
		expect(all).toHaveLength(1);
		expect(all[0].usedCount).toBe(3);
	});

	it('treats different casing as the same payee, keeping the newest spelling', async () => {
		await post({ payee: 'corner market', categoryId: groceries });
		await post({ payee: 'Corner Market', categoryId: groceries });

		const all = await payees();
		expect(all).toHaveLength(1);
		expect(all[0].payee).toBe('Corner Market');
		expect(all[0].usedCount).toBe(2);
	});

	it('lets the newest use win, rather than merging', async () => {
		const dining = await makeCategory(call, { name: 'Dining Out' });
		await post({ payee: 'Cafe', categoryId: groceries, notes: 'first' });
		await post({ payee: 'Cafe', categoryId: dining, notes: 'second' });

		// "What did I do last time" has one answer; a merged row would answer wrongly.
		const [entry] = await payees();
		expect(entry).toMatchObject({ categoryId: dining, notes: 'second' });
	});

	it('records an edit too', async () => {
		const created = await json<{ transaction: { id: string } }>(await post({ payee: 'Typo', categoryId: groceries }));
		await call(`/transactions/${created.transaction.id}`, { method: 'PATCH', body: JSON.stringify({ payee: 'Corrected' }) });

		expect((await payees()).map((entry) => entry.payee).sort()).toEqual(['Corrected', 'Typo']);
	});
});

describe('suggestions', () => {
	beforeEach(async () => {
		await post({ payee: 'Corner Market', categoryId: groceries });
		await post({ payee: 'Corner Market', categoryId: groceries });
		await post({ payee: 'Tesco Corner', categoryId: groceries });
		await post({ payee: 'Baker Street', categoryId: groceries });
	});

	it('matches anywhere in the name', async () => {
		const names = (await payees('?search=corner')).map((entry) => entry.payee);
		expect(names).toContain('Corner Market');
		expect(names).toContain('Tesco Corner');
		expect(names).not.toContain('Baker Street');
	});

	it('floats a prefix match above a mid-string one', async () => {
		const names = (await payees('?search=corner')).map((entry) => entry.payee);
		expect(names[0]).toBe('Corner Market');
	});

	it('is case-insensitive', async () => {
		expect((await payees('?search=BAKER')).map((entry) => entry.payee)).toEqual(['Baker Street']);
	});

	it('orders by use when nothing is typed', async () => {
		expect((await payees())[0].payee).toBe('Corner Market');
	});

	it('honours a limit', async () => {
		expect(await payees('?limit=2')).toHaveLength(2);
		expect((await call('/payees?limit=0')).status).toBe(400);
	});

	it('can be forgotten', async () => {
		const [entry] = await payees();
		expect((await call(`/payees/${entry.id}`, { method: 'DELETE' })).status).toBe(204);
		expect((await payees()).some((row) => row.id === entry.id)).toBe(false);
		expect((await call(`/payees/${entry.id}`, { method: 'DELETE' })).status).toBe(404);
	});
});

describe('transfers', () => {
	it('name themselves when left blank', async () => {
		const response = await transfer({});
		const { transactions } = await json<{ transactions: { payee: string }[] }>(response);

		expect(transactions[0].payee).toBe('Checking → Savings');
		expect(transactions[1].payee).toBe('Checking → Savings');
	});

	it('do not remember a name they composed themselves', async () => {
		await transfer({});
		// The derived name is not something the user typed, so offering it back
		// would be noise.
		expect(await payees()).toEqual([]);
	});

	it('keep a name the user gave, on both legs', async () => {
		const { transactions } = await json<{ transactions: { payee: string }[] }>(await transfer({ payee: 'Monthly investing' }));
		expect(transactions.map((leg) => leg.payee)).toEqual(['Monthly investing', 'Monthly investing']);
	});

	it('remember a typed name with both accounts', async () => {
		await transfer({ payee: 'Monthly investing', notes: 'index fund' });

		const [entry] = await payees();
		expect(entry).toMatchObject({
			payee: 'Monthly investing',
			kind: 'transfer',
			accountId: checking,
			toAccountId: savings,
			notes: 'index fund',
		});
	});
});

describe('payee history is private', () => {
	it('is not visible to another user', async () => {
		await post({ payee: 'Corner Market', categoryId: groceries });

		const theirs = await otherClient();
		expect((await json<{ payees: unknown[] }>(await theirs('/payees'))).payees).toEqual([]);
	});

	it('cannot be deleted by another user', async () => {
		await post({ payee: 'Corner Market', categoryId: groceries });
		const [entry] = await payees();

		const theirs = await otherClient();
		expect((await theirs(`/payees/${entry.id}`, { method: 'DELETE' })).status).toBe(404);
	});
});
