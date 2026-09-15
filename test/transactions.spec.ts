import { beforeEach, describe, expect, it } from 'vitest';
import { authedClient, json, makeAccount, makeCategory, type Call } from './helpers';

let call: Call;
let accountId: string;
let categoryId: string;

beforeEach(async () => {
	call = await authedClient();
	accountId = await makeAccount(call, { startingBalance: 0 });
	categoryId = await makeCategory(call, { name: 'Groceries' });
});

function post(body: Record<string, unknown>) {
	return call('/transactions', { method: 'POST', body: JSON.stringify(body) });
}

describe('transactions', () => {
	it('records an outflow with its joined labels', async () => {
		const response = await post({ accountId, categoryId, amount: -4599, occurredOn: '2026-09-03', payee: 'Corner Market' });

		expect(response.status).toBe(201);
		const { transaction } = await json<{ transaction: Record<string, unknown> }>(response);
		expect(transaction).toMatchObject({
			accountId,
			accountName: 'Checking',
			categoryId,
			categoryName: 'Groceries',
			amount: -4599,
			occurredOn: '2026-09-03',
			payee: 'Corner Market',
			transferId: null,
		});
	});

	it('allows an uncategorised transaction', async () => {
		const response = await post({ accountId, amount: -100, occurredOn: '2026-09-03' });
		expect(response.status).toBe(201);
		const { transaction } = await json<{ transaction: { categoryId: string | null } }>(response);
		expect(transaction.categoryId).toBeNull();
	});

	it('rejects a zero amount', async () => {
		expect((await post({ accountId, amount: 0, occurredOn: '2026-09-03' })).status).toBe(400);
	});

	it('rejects a fractional amount, since amounts are minor units', async () => {
		expect((await post({ accountId, amount: -10.5, occurredOn: '2026-09-03' })).status).toBe(400);
	});

	it('rejects a malformed date', async () => {
		expect((await post({ accountId, amount: -100, occurredOn: '2026-9-3' })).status).toBe(400);
		expect((await post({ accountId, amount: -100, occurredOn: '2026-13-01' })).status).toBe(400);
	});

	it('rejects an unknown account or category', async () => {
		expect((await post({ accountId: 'acc_nope', amount: -100, occurredOn: '2026-09-03' })).status).toBe(400);
		expect((await post({ accountId, categoryId: 'cat_nope', amount: -100, occurredOn: '2026-09-03' })).status).toBe(400);
	});

	it('rejects a malformed JSON body', async () => {
		const response = await call('/transactions', { method: 'POST', body: 'not json' });
		expect(response.status).toBe(400);
	});

	describe('listing', () => {
		beforeEach(async () => {
			await post({ accountId, categoryId, amount: -1000, occurredOn: '2026-08-28', payee: 'August Shop' });
			await post({ accountId, categoryId, amount: -2000, occurredOn: '2026-09-02', payee: 'Corner Market' });
			await post({ accountId, amount: -3000, occurredOn: '2026-09-20', payee: 'Unfiled', notes: 'cash withdrawal' });
		});

		it('returns newest first', async () => {
			const { transactions } = await json<{ transactions: { occurredOn: string }[] }>(await call('/transactions'));
			expect(transactions.map((entry) => entry.occurredOn)).toEqual(['2026-09-20', '2026-09-02', '2026-08-28']);
		});

		it('filters by month', async () => {
			const { total } = await json<{ total: number }>(await call('/transactions?month=2026-09'));
			expect(total).toBe(2);
		});

		it('filters by an explicit date range', async () => {
			const { total } = await json<{ total: number }>(await call('/transactions?from=2026-09-01&to=2026-09-10'));
			expect(total).toBe(1);
		});

		it('filters by category, including the uncategorised', async () => {
			expect((await json<{ total: number }>(await call(`/transactions?categoryId=${categoryId}`))).total).toBe(2);
			expect((await json<{ total: number }>(await call('/transactions?categoryId=none'))).total).toBe(1);
		});

		it('searches payee and notes', async () => {
			expect((await json<{ total: number }>(await call('/transactions?search=Corner'))).total).toBe(1);
			expect((await json<{ total: number }>(await call('/transactions?search=withdrawal'))).total).toBe(1);
			expect((await json<{ total: number }>(await call('/transactions?search=nothing'))).total).toBe(0);
		});

		it('reports the unpaged total alongside a page', async () => {
			const body = await json<{ transactions: unknown[]; total: number; limit: number; offset: number }>(
				await call('/transactions?limit=2&offset=1'),
			);
			expect(body.transactions).toHaveLength(2);
			expect(body).toMatchObject({ total: 3, limit: 2, offset: 1 });
		});

		it('rejects an out-of-range limit', async () => {
			expect((await call('/transactions?limit=500')).status).toBe(400);
			expect((await call('/transactions?limit=0')).status).toBe(400);
		});

		it('rejects a malformed month filter', async () => {
			expect((await call('/transactions?month=2026-99')).status).toBe(400);
		});
	});

	describe('updating', () => {
		it('patches only the given fields', async () => {
			const created = await json<{ transaction: { id: string } }>(
				await post({ accountId, categoryId, amount: -500, occurredOn: '2026-09-03' }),
			);

			const response = await call(`/transactions/${created.transaction.id}`, {
				method: 'PATCH',
				body: JSON.stringify({ payee: 'Renamed' }),
			});

			expect(response.status).toBe(200);
			const { transaction } = await json<{ transaction: Record<string, unknown> }>(response);
			expect(transaction).toMatchObject({ payee: 'Renamed', amount: -500, occurredOn: '2026-09-03' });
		});

		it('can clear a category by setting it to null', async () => {
			const created = await json<{ transaction: { id: string } }>(
				await post({ accountId, categoryId, amount: -500, occurredOn: '2026-09-03' }),
			);

			const response = await call(`/transactions/${created.transaction.id}`, {
				method: 'PATCH',
				body: JSON.stringify({ categoryId: null }),
			});

			const { transaction } = await json<{ transaction: { categoryId: string | null } }>(response);
			expect(transaction.categoryId).toBeNull();
		});

		it('404s for an unknown transaction', async () => {
			expect((await call('/transactions/txn_nope', { method: 'PATCH', body: JSON.stringify({ payee: 'x' }) })).status).toBe(404);
			expect((await call('/transactions/txn_nope', { method: 'DELETE' })).status).toBe(404);
		});
	});

	describe('transfers', () => {
		let savingsId: string;

		beforeEach(async () => {
			savingsId = await makeAccount(call, { name: 'Savings', startingBalance: 0 });
		});

		it('writes a matched pair of legs', async () => {
			const response = await call('/transactions/transfer', {
				method: 'POST',
				body: JSON.stringify({ fromAccountId: accountId, toAccountId: savingsId, amount: 25_000, occurredOn: '2026-09-05' }),
			});

			expect(response.status).toBe(201);
			const { transactions } = await json<{ transactions: { accountId: string; amount: number; transferId: string }[] }>(response);

			expect(transactions).toHaveLength(2);
			expect(transactions[0]).toMatchObject({ accountId, amount: -25000 });
			expect(transactions[1]).toMatchObject({ accountId: savingsId, amount: 25000 });
			expect(transactions[0].transferId).toBe(transactions[1].transferId);
		});

		it('moves the balance between accounts without changing the total', async () => {
			await call('/transactions/transfer', {
				method: 'POST',
				body: JSON.stringify({ fromAccountId: accountId, toAccountId: savingsId, amount: 25_000, occurredOn: '2026-09-05' }),
			});

			const { accounts } = await json<{ accounts: { id: string; balance: number }[] }>(await call('/accounts'));
			const balances = Object.fromEntries(accounts.map((entry) => [entry.id, entry.balance]));

			expect(balances[accountId]).toBe(-25000);
			expect(balances[savingsId]).toBe(25000);
		});

		it('rejects a transfer to the same account', async () => {
			const response = await call('/transactions/transfer', {
				method: 'POST',
				body: JSON.stringify({ fromAccountId: accountId, toAccountId: accountId, amount: 100, occurredOn: '2026-09-05' }),
			});
			expect(response.status).toBe(400);
		});

		it('rejects a non-positive transfer amount', async () => {
			for (const amount of [0, -100]) {
				const response = await call('/transactions/transfer', {
					method: 'POST',
					body: JSON.stringify({ fromAccountId: accountId, toAccountId: savingsId, amount, occurredOn: '2026-09-05' }),
				});
				expect(response.status, `amount ${amount}`).toBe(400);
			}
		});

		it('deletes both legs when either one is deleted', async () => {
			const created = await json<{ transactions: { id: string }[] }>(
				await call('/transactions/transfer', {
					method: 'POST',
					body: JSON.stringify({ fromAccountId: accountId, toAccountId: savingsId, amount: 500, occurredOn: '2026-09-05' }),
				}),
			);

			expect((await call(`/transactions/${created.transactions[1].id}`, { method: 'DELETE' })).status).toBe(204);
			expect((await json<{ total: number }>(await call('/transactions'))).total).toBe(0);
		});
	});
});
