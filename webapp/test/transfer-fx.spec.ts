import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { authedClient, json, makeAccount, makeCategory, otherClient, type Call } from './helpers';

interface Row {
	id: string;
	accountId: string;
	amount: number;
	categoryId: string | null;
	transferId: string | null;
	feeTransferId: string | null;
	payee: string;
	occurredOn: string;
}

let call: Call;
let pesoId: string;
let dollarId: string;
let feesId: string;

beforeEach(async () => {
	call = await authedClient();
	pesoId = await makeAccount(call, { name: 'Peso', currency: 'PHP', startingBalance: 0 });
	dollarId = await makeAccount(call, { name: 'Dollar', currency: 'USD', startingBalance: 0 });
	feesId = await makeCategory(call, { name: 'Bank fees', kind: 'expense' });
});

function transfer(body: Record<string, unknown>) {
	return call('/transactions/transfer', {
		method: 'POST',
		body: JSON.stringify({ fromAccountId: pesoId, toAccountId: dollarId, occurredOn: '2026-09-05', ...body }),
	});
}

async function balances(): Promise<Record<string, number>> {
	const { accounts } = await json<{ accounts: { id: string; balance: number }[] }>(await call('/accounts'));
	return Object.fromEntries(accounts.map((entry) => [entry.id, entry.balance]));
}

describe('a transfer between currencies', () => {
	it('lands what arrived, not what left', async () => {
		const response = await transfer({ amount: 580_000, toAmount: 10_000 });
		expect(response.status).toBe(201);

		const { transactions } = await json<{ transactions: Row[] }>(response);
		expect(transactions).toHaveLength(2);
		expect(transactions[0]).toMatchObject({ accountId: pesoId, amount: -580_000 });
		expect(transactions[1]).toMatchObject({ accountId: dollarId, amount: 10_000 });

		expect(await balances()).toMatchObject({ [pesoId]: -580_000, [dollarId]: 10_000 });
	});

	it('keeps what arrived when an edit names no received amount and leaves what left alone', async () => {
		const { transferId } = await json<{ transferId: string }>(await transfer({ amount: 580_000, toAmount: 10_000 }));

		const renamed = await call(`/transactions/transfer/${transferId}`, {
			method: 'PATCH',
			body: JSON.stringify({ fromAccountId: pesoId, toAccountId: dollarId, amount: 580_000, occurredOn: '2026-09-05', payee: 'Wise' }),
		});
		const { transactions } = await json<{ transactions: Row[] }>(renamed);
		expect(transactions[1]).toMatchObject({ amount: 10_000, payee: 'Wise' });
	});

	it('changes what arrived when an edit names it', async () => {
		const { transferId } = await json<{ transferId: string }>(await transfer({ amount: 580_000, toAmount: 10_000 }));

		const edited = await call(`/transactions/transfer/${transferId}`, {
			method: 'PATCH',
			body: JSON.stringify({ fromAccountId: pesoId, toAccountId: dollarId, amount: 580_000, toAmount: 9_950, occurredOn: '2026-09-05' }),
		});
		const { transactions } = await json<{ transactions: Row[] }>(edited);
		expect(transactions[1]).toMatchObject({ amount: 9_950 });
	});
});

describe('reading one transfer whole', () => {
	it('answers its outflow, inflow and fee, for a list that held only one leg', async () => {
		const { transferId } = await json<{ transferId: string }>(
			await transfer({ amount: 446_457, toAmount: 7_000, fee: { amount: 9_622, categoryId: feesId } }),
		);

		const response = await call(`/transactions/transfer/${transferId}`);
		expect(response.status).toBe(200);
		const { transactions } = await json<{ transactions: Row[] }>(response);
		expect(transactions.map((row) => [row.accountId, row.amount])).toEqual([
			[pesoId, -446_457],
			[dollarId, 7_000],
			[pesoId, -9_622],
		]);
	});

	it("is not found for an unknown id or someone else's transfer", async () => {
		const { transferId } = await json<{ transferId: string }>(await transfer({ amount: 1_000 }));

		expect((await call('/transactions/transfer/nope')).status).toBe(404);
		expect((await (await otherClient())(`/transactions/transfer/${transferId}`)).status).toBe(404);
	});
});

describe("a transfer's fee", () => {
	it('posts an expense on the source account, linked to the transfer', async () => {
		const response = await transfer({ amount: 580_000, toAmount: 10_000, fee: { amount: 5_000, categoryId: feesId } });
		expect(response.status).toBe(201);

		const { transferId, transactions } = await json<{ transferId: string; transactions: Row[] }>(response);
		expect(transactions).toHaveLength(3);
		expect(transactions[2]).toMatchObject({
			accountId: pesoId,
			amount: -5_000,
			categoryId: feesId,
			transferId: null,
			feeTransferId: transferId,
			payee: 'Transfer fee',
		});

		expect(await balances()).toMatchObject({ [pesoId]: -585_000, [dollarId]: 10_000 });
	});

	it('counts as spending while the transfer does not', async () => {
		await transfer({ amount: 580_000, toAmount: 10_000, fee: { amount: 5_000, categoryId: feesId } });

		const { expenses } = await json<{ expenses: number }>(await call('/summary?month=2026-09'));
		expect(expenses).toBe(5_000);
	});

	it('goes with its transfer', async () => {
		const { transactions } = await json<{ transactions: Row[] }>(
			await transfer({ amount: 100_000, fee: { amount: 1_500, categoryId: null } }),
		);

		expect((await call(`/transactions/${transactions[1].id}`, { method: 'DELETE' })).status).toBeLessThan(300);
		expect(await balances()).toMatchObject({ [pesoId]: 0, [dollarId]: 0 });
	});

	it('is never remembered as a payee, even when edited on its own', async () => {
		const { transactions } = await json<{ transactions: Row[] }>(
			await transfer({ amount: 100_000, fee: { amount: 1_500, categoryId: null } }),
		);

		const edited = await call(`/transactions/${transactions[2].id}`, { method: 'PATCH', body: JSON.stringify({ amount: -2_000 }) });
		expect(edited.status).toBe(200);
		const { payees } = await json<{ payees: { payee: string }[] }>(await call('/payees'));
		expect(payees.map((entry) => entry.payee)).not.toContain('Transfer fee');
	});

	it('purges the month a fee was moved to when its transfer goes', async () => {
		const { transactions } = await json<{ transactions: Row[] }>(
			await transfer({ amount: 100_000, fee: { amount: 1_500, categoryId: null } }),
		);
		await call(`/transactions/${transactions[2].id}`, { method: 'PATCH', body: JSON.stringify({ occurredOn: '2026-10-03' }) });

		// Read once, so October's summary is cached with the fee in it.
		expect((await json<{ expenses: number }>(await call('/summary?month=2026-10'))).expenses).toBe(1_500);

		await call(`/transactions/${transactions[0].id}`, { method: 'DELETE' });
		expect((await json<{ expenses: number }>(await call('/summary?month=2026-10'))).expenses).toBe(0);
	});

	it('refuses an edit whose new fee names an id already taken, leaving the legs as they were', async () => {
		const taken = (await json<{ transactions: Row[] }>(await transfer({ amount: 1_000 }))).transactions[0].id;
		const { transferId } = await json<{ transferId: string }>(await transfer({ amount: 100_000, toAmount: 1_700 }));

		const response = await call(`/transactions/transfer/${transferId}`, {
			method: 'PATCH',
			body: JSON.stringify({
				fromAccountId: pesoId,
				toAccountId: dollarId,
				amount: 200_000,
				toAmount: 3_400,
				occurredOn: '2026-09-05',
				fee: { id: taken, amount: 500, categoryId: null },
			}),
		});
		expect(response.status).toBe(400);
		expect(await balances()).toMatchObject({ [pesoId]: -101_000, [dollarId]: 2_700 });
	});

	it('can be deleted on its own, leaving the transfer', async () => {
		const { transactions } = await json<{ transactions: Row[] }>(
			await transfer({ amount: 100_000, fee: { amount: 1_500, categoryId: null } }),
		);

		await call(`/transactions/${transactions[2].id}`, { method: 'DELETE' });
		expect(await balances()).toMatchObject({ [pesoId]: -100_000, [dollarId]: 100_000 });
	});

	it('refuses a Cashflow category, leaving nothing behind', async () => {
		const cashflow = await makeCategory(call, { name: 'Moves', kind: 'transfer' });
		const response = await transfer({ amount: 100_000, fee: { amount: 1_500, categoryId: cashflow } });

		expect(response.status).toBe(400);
		expect(await balances()).toMatchObject({ [pesoId]: 0, [dollarId]: 0 });
	});

	describe('on an edit', () => {
		let transferId: string;

		beforeEach(async () => {
			({ transferId } = await json<{ transferId: string }>(await transfer({ amount: 100_000, toAmount: 1_700 })));
		});

		const patch = (body: Record<string, unknown>) =>
			call(`/transactions/transfer/${transferId}`, {
				method: 'PATCH',
				body: JSON.stringify({
					fromAccountId: pesoId,
					toAccountId: dollarId,
					amount: 100_000,
					toAmount: 1_700,
					occurredOn: '2026-09-05',
					...body,
				}),
			});

		it('is added, changed, kept and taken off', async () => {
			let rows = (await json<{ transactions: Row[] }>(await patch({ fee: { amount: 2_000, categoryId: feesId } }))).transactions;
			expect(rows).toHaveLength(3);
			expect(rows[2]).toMatchObject({ amount: -2_000, feeTransferId: transferId });

			rows = (await json<{ transactions: Row[] }>(await patch({ fee: { amount: 2_500, categoryId: null } }))).transactions;
			expect(rows[2]).toMatchObject({ amount: -2_500, categoryId: null });

			// Left out, the fee stays, and follows the transfer's day.
			rows = (await json<{ transactions: Row[] }>(await patch({ occurredOn: '2026-09-07' }))).transactions;
			expect(rows[2]).toMatchObject({ amount: -2_500, occurredOn: '2026-09-07' });

			rows = (await json<{ transactions: Row[] }>(await patch({ fee: null }))).transactions;
			expect(rows).toHaveLength(2);
			expect(await balances()).toMatchObject({ [pesoId]: -100_000 });
		});

		it('refuses a Cashflow category and leaves the legs as they were', async () => {
			const cashflow = await makeCategory(call, { name: 'Moves', kind: 'transfer' });
			const response = await patch({ amount: 200_000, fee: { amount: 2_000, categoryId: cashflow } });

			expect(response.status).toBe(400);
			expect(await balances()).toMatchObject({ [pesoId]: -100_000, [dollarId]: 1_700 });
		});
	});

	it("cannot be filed under someone else's category", async () => {
		const other = await otherClient();
		const theirs = await makeCategory(other, { name: 'Theirs', kind: 'expense' });

		const response = await transfer({ amount: 100_000, fee: { amount: 1_500, categoryId: theirs } });
		expect(response.status).toBe(400);
		expect(await balances()).toMatchObject({ [pesoId]: 0, [dollarId]: 0 });
	});
});

describe('GET /api/fx', () => {
	let fetched: string[];

	beforeEach(() => {
		fetched = [];
		vi.spyOn(globalThis, 'fetch').mockImplementation(async (input) => {
			const url = String(input instanceof Request ? input.url : input);
			fetched.push(url);
			if (url.startsWith('https://api.frankfurter.dev/')) {
				if (url.includes('symbols=XAF')) return new Response('{"message":"not found"}', { status: 404 });
				return Response.json({ amount: 1, base: 'PHP', date: '2026-09-04', rates: { USD: 0.01724 } });
			}
			if (url.startsWith('https://open.er-api.com/')) return Response.json({ result: 'success', rates: { XAF: 10.5 } });
			throw new Error(`Unexpected fetch of ${url}`);
		});
	});

	afterEach(() => vi.restoreAllMocks());

	it("answers a past day's rate from the ECB, and keeps it", async () => {
		const response = await call('/fx?from=php&to=usd&date=2026-09-05');
		expect(response.status).toBe(200);
		expect(await json(response)).toEqual({ rate: 0.01724, date: '2026-09-04', source: 'ecb' });
		expect(fetched).toEqual(['https://api.frankfurter.dev/v1/2026-09-05?base=PHP&symbols=USD']);

		await call('/fx?from=PHP&to=USD&date=2026-09-05');
		expect(fetched).toHaveLength(1);
	});

	it('falls back to the wider source for a pair the ECB does not quote, today only', async () => {
		const today = await json<{ rate: number; source: string }>(await call('/fx?from=PHP&to=XAF'));
		expect(today).toMatchObject({ rate: 10.5, source: 'open-er-api' });

		const past = await call('/fx?from=PHP&to=XAF&date=2020-01-02');
		expect(past.status).toBe(503);
	});

	it('answers one for the same currency without asking anyone', async () => {
		expect(await json(await call('/fx?from=PHP&to=PHP&date=2026-09-05'))).toMatchObject({ rate: 1, source: 'identity' });
		expect(fetched).toEqual([]);
	});

	it('refuses a malformed code', async () => {
		expect((await call('/fx?from=PESO&to=USD')).status).toBe(400);
	});
});
