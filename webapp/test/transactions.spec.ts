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

	it('accepts an optional time of day on the date', async () => {
		const response = await post({ accountId, amount: -100, occurredOn: '2026-09-03T14:30' });
		expect(response.status).toBe(201);
		const { transaction } = await json<{ transaction: { occurredOn: string } }>(response);
		expect(transaction.occurredOn).toBe('2026-09-03T14:30');
	});

	it('rejects a malformed time of day', async () => {
		expect((await post({ accountId, amount: -100, occurredOn: '2026-09-03T25:00' })).status).toBe(400);
		expect((await post({ accountId, amount: -100, occurredOn: '2026-09-03T14:3' })).status).toBe(400);
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

		// `to` names a day, and a transaction that happened at some point during
		// that day happened on it. While the date and the time shared one column,
		// '2026-09-10T14:30' sorted after '2026-09-10' and the row fell out of its
		// own range — which is nearly every row, since most carry a time.
		it('includes a timed transaction on the last day of the range', async () => {
			await post({ accountId, amount: -700, occurredOn: '2026-09-10T14:30', payee: 'Late in the day' });

			const { transactions } = await json<{ transactions: { payee: string }[] }>(await call('/transactions?from=2026-09-01&to=2026-09-10'));

			expect(transactions.map((entry) => entry.payee)).toContain('Late in the day');
		});

		it('filters by category, including the uncategorised', async () => {
			expect((await json<{ total: number }>(await call(`/transactions?categoryId=${categoryId}`))).total).toBe(2);
			expect((await json<{ total: number }>(await call('/transactions?categoryId=none'))).total).toBe(1);
		});

		it('takes several categories at once, the uncategorised among them', async () => {
			expect((await json<{ total: number }>(await call(`/transactions?categoryId=${categoryId},none`))).total).toBe(3);
			expect((await json<{ total: number }>(await call('/transactions?categoryId=none,none'))).total).toBe(1);
		});

		it('takes several accounts at once', async () => {
			expect((await json<{ total: number }>(await call(`/transactions?accountId=${accountId},acc_elsewhere`))).total).toBe(3);
			expect((await json<{ total: number }>(await call('/transactions?accountId=acc_elsewhere'))).total).toBe(0);
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

	describe('running balance', () => {
		it('accrues from the starting balance in date order, regardless of insert order', async () => {
			// Posted out of date order, to prove the total follows occurredOn rather
			// than insertion order.
			await post({ accountId, amount: -3000, occurredOn: '2026-09-20' });
			await post({ accountId, amount: -1000, occurredOn: '2026-08-28' });
			await post({ accountId, amount: 5000, occurredOn: '2026-09-02' });

			const { transactions } = await json<{ transactions: { occurredOn: string; runningBalance: number }[] }>(await call('/transactions'));
			// Newest first: 0 - 1000 + 5000 - 3000, read backwards from the last leg.
			expect(transactions.map((entry) => [entry.occurredOn, entry.runningBalance])).toEqual([
				['2026-09-20', 1000],
				['2026-09-02', 4000],
				['2026-08-28', -1000],
			]);
		});

		it('does not mix two accounts’ figures', async () => {
			const other = await makeAccount(call, { name: 'Savings', startingBalance: 10_000 });
			await post({ accountId, amount: -500, occurredOn: '2026-09-04' });
			await post({ accountId: other, amount: 2000, occurredOn: '2026-09-04' });

			const { transactions } = await json<{ transactions: { accountId: string; runningBalance: number }[] }>(await call('/transactions'));
			expect(transactions.find((entry) => entry.accountId === accountId)?.runningBalance).toBe(-500);
			expect(transactions.find((entry) => entry.accountId === other)?.runningBalance).toBe(12_000);
		});

		it('still reflects the full account history when a filter narrows the list', async () => {
			await post({ accountId, categoryId, amount: -1000, occurredOn: '2026-09-01', payee: 'Earlier' });
			await post({ accountId, amount: -500, occurredOn: '2026-09-05', payee: 'Findable' });

			// The search only surfaces the second row, but its balance must still
			// account for the first — narrowing the list must not change what "the
			// balance after this one" means.
			const { transactions } = await json<{ transactions: { payee: string; runningBalance: number }[] }>(
				await call('/transactions?search=Findable'),
			);
			expect(transactions).toEqual([expect.objectContaining({ payee: 'Findable', runningBalance: -1500 })]);
		});

		it('reflects both legs of a transfer', async () => {
			const savingsId = await makeAccount(call, { name: 'Savings', startingBalance: 0 });
			await post({ accountId, amount: 1000, occurredOn: '2026-09-01' });

			await call('/transactions/transfer', {
				method: 'POST',
				body: JSON.stringify({ fromAccountId: accountId, toAccountId: savingsId, amount: 400, occurredOn: '2026-09-02' }),
			});

			const { transactions } = await json<{ transactions: { accountId: string; amount: number; runningBalance: number }[] }>(
				await call('/transactions'),
			);
			expect(transactions.find((entry) => entry.accountId === accountId && entry.amount === -400)?.runningBalance).toBe(600);
			expect(transactions.find((entry) => entry.accountId === savingsId)?.runningBalance).toBe(400);
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

		describe('editing', () => {
			let transferId: string;

			beforeEach(async () => {
				const created = await json<{ transferId: string }>(
					await call('/transactions/transfer', {
						method: 'POST',
						body: JSON.stringify({ fromAccountId: accountId, toAccountId: savingsId, amount: 25_000, occurredOn: '2026-09-05' }),
					}),
				);
				transferId = created.transferId;
			});

			it('updates both legs together, keeping them linked', async () => {
				const response = await call(`/transactions/transfer/${transferId}`, {
					method: 'PATCH',
					body: JSON.stringify({
						fromAccountId: accountId,
						toAccountId: savingsId,
						amount: 10_000,
						occurredOn: '2026-09-06',
						payee: 'Rebalance',
					}),
				});

				expect(response.status).toBe(200);
				const { transactions } = await json<{ transactions: { accountId: string; amount: number; transferId: string; payee: string }[] }>(
					response,
				);

				expect(transactions).toHaveLength(2);
				expect(transactions[0]).toMatchObject({ accountId, amount: -10000, payee: 'Rebalance', transferId });
				expect(transactions[1]).toMatchObject({ accountId: savingsId, amount: 10000, payee: 'Rebalance', transferId });

				const { accounts } = await json<{ accounts: { id: string; balance: number }[] }>(await call('/accounts'));
				const balances = Object.fromEntries(accounts.map((entry) => [entry.id, entry.balance]));
				expect(balances[accountId]).toBe(-10000);
				expect(balances[savingsId]).toBe(10000);
			});

			it('can reverse direction between the same two accounts', async () => {
				const response = await call(`/transactions/transfer/${transferId}`, {
					method: 'PATCH',
					body: JSON.stringify({ fromAccountId: savingsId, toAccountId: accountId, amount: 25_000, occurredOn: '2026-09-05' }),
				});

				expect(response.status).toBe(200);
				const { accounts } = await json<{ accounts: { id: string; balance: number }[] }>(await call('/accounts'));
				const balances = Object.fromEntries(accounts.map((entry) => [entry.id, entry.balance]));
				expect(balances[accountId]).toBe(25000);
				expect(balances[savingsId]).toBe(-25000);
			});

			it('rejects a non-positive amount', async () => {
				const response = await call(`/transactions/transfer/${transferId}`, {
					method: 'PATCH',
					body: JSON.stringify({ fromAccountId: accountId, toAccountId: savingsId, amount: 0, occurredOn: '2026-09-05' }),
				});
				expect(response.status).toBe(400);
			});

			it('404s for an unknown transfer', async () => {
				const response = await call('/transactions/transfer/tfr_nope', {
					method: 'PATCH',
					body: JSON.stringify({ fromAccountId: accountId, toAccountId: savingsId, amount: 100, occurredOn: '2026-09-05' }),
				});
				expect(response.status).toBe(404);
			});
		});
	});
});
