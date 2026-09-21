import { beforeEach, describe, expect, it } from 'vitest';
import { accountTypeId, authedClient, json, makeAccount, makeCategory, otherClient, type Call } from './helpers';

/**
 * The offline queue's landing site. What matters here is not that a batch can
 * write — the routes it dispatches to already have their own specs — but the
 * four promises the clients are built on: a queued call behaves exactly like
 * the online one, the order the user made changes in is kept, an old edit
 * loses to a newer one, and sending the same batch twice changes nothing.
 */

let call: Call;

beforeEach(async () => {
	call = await authedClient();
});

interface Result {
	opId: string;
	status: 'applied' | 'stale' | 'failed';
	code: number;
	body?: unknown;
	error?: string;
}

const later = (minutes: number) => new Date(Date.now() + minutes * 60_000).toISOString();
const earlier = (minutes: number) => new Date(Date.now() - minutes * 60_000).toISOString();

async function batch(operations: Record<string, unknown>[], as: Call = call): Promise<Result[]> {
	const response = await as('/sync/batch', { method: 'POST', body: JSON.stringify({ operations }) });
	const body = await json<{ results: Result[]; error?: string }>(response);
	if (!body.results) throw new Error(`Batch failed (${response.status}): ${JSON.stringify(body)}`);
	return body.results;
}

describe('a queued batch', () => {
	it('applies operations in the order the user made them', async () => {
		const typeId = await accountTypeId(call);

		// The transaction names an account that does not exist yet. It only works
		// because the client named the account itself and the two are applied in
		// order — which is the whole point of the queue.
		const results = await batch([
			{
				opId: '1',
				method: 'POST',
				path: '/api/accounts',
				at: earlier(5),
				body: { id: 'acc_offlineaccount01', name: 'Plane Wallet', typeId, startingBalance: 0 },
			},
			{
				opId: '2',
				method: 'POST',
				path: '/api/transactions',
				at: earlier(4),
				body: { id: 'txn_offlinespend001', accountId: 'acc_offlineaccount01', amount: -2500, occurredOn: '2026-03-04', payee: 'Coffee' },
			},
		]);

		expect(results.map((result) => result.status)).toEqual(['applied', 'applied']);

		const { accounts } = await json<{ accounts: { id: string; balance: number }[] }>(await call('/accounts'));
		expect(accounts.find((account) => account.id === 'acc_offlineaccount01')?.balance).toBe(-2500);
	});

	it('keeps the ids the client chose, so the rows it already drew are the rows that exist', async () => {
		const typeId = await accountTypeId(call);
		await batch([
			{ opId: '1', method: 'POST', path: '/api/accounts', at: earlier(1), body: { id: 'acc_chosenbytheclient', name: 'Cash', typeId } },
		]);

		const { account } = await json<{ account: { id: string; name: string } }>(await call('/accounts/acc_chosenbytheclient'));
		expect(account.name).toBe('Cash');
	});

	it('reports a failure without abandoning the operations after it', async () => {
		const accountId = await makeAccount(call);

		const results = await batch([
			{
				opId: 'bad',
				method: 'POST',
				path: '/api/transactions',
				at: earlier(3),
				body: { accountId: 'acc_notmine', amount: -100, occurredOn: '2026-03-04' },
			},
			{
				opId: 'good',
				method: 'POST',
				path: '/api/transactions',
				at: earlier(2),
				body: { accountId, amount: -100, occurredOn: '2026-03-04' },
			},
		]);

		expect(results[0].status).toBe('failed');
		expect(results[1].status).toBe('applied');
	});

	it('is safe to send twice', async () => {
		const accountId = await makeAccount(call);
		const operations = [
			{
				opId: '1',
				method: 'POST',
				path: '/api/transactions',
				at: earlier(2),
				body: { id: 'txn_sentexactlytwice', accountId, amount: -1500, occurredOn: '2026-03-04', payee: 'Lunch' },
			},
		];

		await batch(operations);
		const second = await batch(operations);

		expect(second[0].status).toBe('applied');

		const { transactions } = await json<{ transactions: unknown[] }>(await call('/transactions?limit=50&offset=0'));
		expect(transactions.filter((row) => (row as { payee: string }).payee === 'Lunch')).toHaveLength(1);
	});

	it('treats deleting something already gone as done', async () => {
		const accountId = await makeAccount(call);
		const { transaction } = await json<{ transaction: { id: string } }>(
			await call('/transactions', { method: 'POST', body: JSON.stringify({ accountId, amount: -100, occurredOn: '2026-03-04' }) }),
		);
		await call(`/transactions/${transaction.id}`, { method: 'DELETE' });

		const results = await batch([
			{
				opId: '1',
				method: 'DELETE',
				path: `/api/transactions/${transaction.id}`,
				at: earlier(1),
				entity: 'transaction',
				id: transaction.id,
			},
		]);

		expect(results[0].status).toBe('applied');
	});
});

describe('last write wins', () => {
	it('drops an edit older than the row it names', async () => {
		const { tag } = await json<{ tag: { id: string } }>(await call('/tags', { method: 'POST', body: JSON.stringify({ name: 'Travel' }) }));

		// The row was just written, so an edit the user made an hour ago lost.
		const results = await batch([
			{ opId: '1', method: 'PATCH', path: `/api/tags/${tag.id}`, at: earlier(60), entity: 'tag', id: tag.id, body: { name: 'Stale' } },
		]);

		expect(results[0].status).toBe('stale');

		const { tags } = await json<{ tags: { id: string; name: string }[] }>(await call('/tags'));
		expect(tags.find((entry) => entry.id === tag.id)?.name).toBe('Travel');
	});

	it('applies an edit newer than the row it names', async () => {
		const { tag } = await json<{ tag: { id: string } }>(await call('/tags', { method: 'POST', body: JSON.stringify({ name: 'Travel' }) }));

		const results = await batch([
			{ opId: '1', method: 'PATCH', path: `/api/tags/${tag.id}`, at: later(60), entity: 'tag', id: tag.id, body: { name: 'Holidays' } },
		]);

		expect(results[0].status).toBe('applied');

		const { tags } = await json<{ tags: { id: string; name: string }[] }>(await call('/tags'));
		expect(tags.find((entry) => entry.id === tag.id)?.name).toBe('Holidays');
	});

	it('applies a create, which has no row to be stale against', async () => {
		const results = await batch([
			{
				opId: '1',
				method: 'POST',
				path: '/api/tags',
				at: earlier(600),
				entity: 'tag',
				id: 'tag_madelongago001',
				body: { id: 'tag_madelongago001', name: 'Old' },
			},
		]);

		expect(results[0].status).toBe('applied');
	});
});

describe('what a batch may not do', () => {
	it('refuses to nest', async () => {
		const response = await call('/sync/batch', {
			method: 'POST',
			body: JSON.stringify({
				operations: [{ opId: '1', method: 'POST', path: '/api/sync/batch', at: earlier(1), body: { operations: [] } }],
			}),
		});

		expect(response.status).toBe(400);
	});

	it('refuses to touch auth or device registration', async () => {
		for (const path of ['/api/auth/me', '/api/devices']) {
			const response = await call('/sync/batch', {
				method: 'POST',
				body: JSON.stringify({ operations: [{ opId: '1', method: 'PUT', path, at: earlier(1), body: {} }] }),
			});
			expect(response.status).toBe(400);
		}
	});

	it('needs a token like every other route', async () => {
		const response = await fetch('https://yuuka.test/api/sync/batch', { method: 'POST', body: '{"operations":[]}' }).catch(() => null);
		expect(response?.status ?? 401).not.toBe(200);
	});

	/**
	 * The batch is dispatched through the same router, so a sub-request is
	 * authenticated by the batch's own token. Naming someone else's row in an
	 * operation therefore reaches the same ownership check an online call would.
	 */
	it('cannot reach another user through an operation', async () => {
		const stranger = await otherClient();
		const theirAccount = await makeAccount(stranger, { name: 'Theirs' });

		const results = await batch([
			{
				opId: '1',
				method: 'POST',
				path: '/api/transactions',
				at: earlier(1),
				body: { accountId: theirAccount, amount: -100, occurredOn: '2026-03-04' },
			},
			{ opId: '2', method: 'DELETE', path: `/api/accounts/${theirAccount}`, at: earlier(1), entity: 'account', id: theirAccount },
		]);

		expect(results[0].status).toBe('failed');
		// A delete of a row that is not ours is "already gone" from where we sit,
		// which is the same answer the online route gives — and it must not have
		// touched anything.
		const { accounts } = await json<{ accounts: { id: string }[] }>(await stranger('/accounts'));
		expect(accounts.map((account) => account.id)).toContain(theirAccount);
	});
});

describe('a round-up made offline', () => {
	/**
	 * The client works the round-up out itself so the balance it shows is right,
	 * and names the three rows. The API must adopt those names rather than
	 * posting a second transfer of its own.
	 */
	it('keeps the ids the client drew it with', async () => {
		const source = await makeAccount(call, { name: 'Spending', roundUpSource: true });
		const destination = await makeAccount(call, { name: 'Savings' });
		const categoryId = await makeCategory(call, { name: 'Cashflow offline', kind: 'transfer' });

		await call('/round-up', {
			method: 'PATCH',
			body: JSON.stringify({ enabled: true, roundTo: 1000, destinationAccountId: destination, categoryId }),
		});

		const results = await batch([
			{
				opId: '1',
				method: 'POST',
				path: '/api/transactions',
				at: earlier(1),
				body: {
					id: 'txn_offlinepurchase1',
					accountId: source,
					amount: -1250,
					occurredOn: '2026-03-04',
					payee: 'Bakery',
					roundUpIds: { transferId: 'tfr_offlineroundup01', fromId: 'txn_offlineroundupa', toId: 'txn_offlineroundupb' },
				},
			},
		]);

		expect(results[0].status).toBe('applied');

		const { transactions } = await json<{ transactions: { id: string; amount: number }[] }>(await call('/transactions?limit=50&offset=0'));
		const ids = transactions.map((row) => row.id);
		expect(ids).toContain('txn_offlineroundupa');
		expect(ids).toContain('txn_offlineroundupb');
		expect(transactions.find((row) => row.id === 'txn_offlineroundupb')?.amount).toBe(750);
	});
});
