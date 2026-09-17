import { beforeEach, describe, expect, it } from 'vitest';
import { DEFAULT_CURRENCY } from '../functions/api/_lib/defaults';
import { accountTypeId, authedClient, json, makeAccount, type Call } from './helpers';

let call: Call;

beforeEach(async () => {
	call = await authedClient();
});

describe('accounts', () => {
	it('starts empty', async () => {
		const body = await json<{ accounts: unknown[] }>(await call('/accounts'));
		expect(body.accounts).toEqual([]);
	});

	it('creates an account and defaults its currency', async () => {
		const typeId = await accountTypeId(call, 'Checking');
		const response = await call('/accounts', {
			method: 'POST',
			body: JSON.stringify({ name: 'Everyday', typeId, startingBalance: 125_00 }),
		});

		expect(response.status).toBe(201);
		const { account } = await json<{ account: Record<string, unknown> }>(response);
		expect(account).toMatchObject({
			name: 'Everyday',
			typeId,
			typeName: 'Checking',
			currency: DEFAULT_CURRENCY,
			startingBalance: 12500,
			balance: 12500,
			archived: false,
		});
	});

	it('upper-cases the currency code', async () => {
		const id = await makeAccount(call, { currency: 'eur' });
		const { account } = await json<{ account: { currency: string } }>(await call(`/accounts/${id}`));
		expect(account.currency).toBe('EUR');
	});

	it('rejects an account type that does not exist', async () => {
		const response = await call('/accounts', { method: 'POST', body: JSON.stringify({ name: 'X', typeId: 'atp_nope' }) });
		expect(response.status).toBe(400);
	});

	it('rejects a blank name', async () => {
		const response = await call('/accounts', { method: 'POST', body: JSON.stringify({ name: '   ', typeId: await accountTypeId(call) }) });
		expect(response.status).toBe(400);
	});

	it('rejects a non-integer starting balance', async () => {
		const response = await call('/accounts', {
			method: 'POST',
			body: JSON.stringify({ name: 'X', typeId: await accountTypeId(call), startingBalance: 10.5 }),
		});
		expect(response.status).toBe(400);
	});

	it('folds transactions into the reported balance', async () => {
		const id = await makeAccount(call, { startingBalance: 100_00 });
		const post = (amount: number) =>
			call('/transactions', { method: 'POST', body: JSON.stringify({ accountId: id, amount, occurredOn: '2026-09-04' }) });

		await post(-2500);
		await post(-1000);
		await post(500);

		const { account } = await json<{ account: { balance: number } }>(await call(`/accounts/${id}`));
		expect(account.balance).toBe(10000 - 2500 - 1000 + 500);
	});

	it('updates only the fields it is given', async () => {
		const typeId = await accountTypeId(call, 'Savings');
		const id = await makeAccount(call, { name: 'Old', typeId, startingBalance: 7000 });
		const response = await call(`/accounts/${id}`, { method: 'PATCH', body: JSON.stringify({ name: 'New' }) });

		expect(response.status).toBe(200);
		const { account } = await json<{ account: Record<string, unknown> }>(response);
		expect(account).toMatchObject({ name: 'New', typeName: 'Savings', startingBalance: 7000 });
	});

	it('rejects an empty patch', async () => {
		const id = await makeAccount(call);
		expect((await call(`/accounts/${id}`, { method: 'PATCH', body: '{}' })).status).toBe(400);
	});

	it('hides archived accounts unless asked', async () => {
		const id = await makeAccount(call, { name: 'Dormant' });
		await call(`/accounts/${id}`, { method: 'PATCH', body: JSON.stringify({ archived: true }) });

		const hidden = await json<{ accounts: unknown[] }>(await call('/accounts'));
		expect(hidden.accounts).toHaveLength(0);

		const shown = await json<{ accounts: unknown[] }>(await call('/accounts?includeArchived=true'));
		expect(shown.accounts).toHaveLength(1);
	});

	it('404s for an unknown account', async () => {
		expect((await call('/accounts/acc_nope')).status).toBe(404);
		expect((await call('/accounts/acc_nope', { method: 'PATCH', body: JSON.stringify({ name: 'x' }) })).status).toBe(404);
		expect((await call('/accounts/acc_nope', { method: 'DELETE' })).status).toBe(404);
	});

	it('deletes an account that has no history', async () => {
		const id = await makeAccount(call);
		expect((await call(`/accounts/${id}`, { method: 'DELETE' })).status).toBe(204);
		expect((await call(`/accounts/${id}`)).status).toBe(404);
	});

	it('refuses to delete an account with history unless told to', async () => {
		const id = await makeAccount(call);
		await call('/transactions', { method: 'POST', body: JSON.stringify({ accountId: id, amount: -100, occurredOn: '2026-09-04' }) });

		const blocked = await call(`/accounts/${id}`, { method: 'DELETE' });
		expect(blocked.status).toBe(409);

		const forced = await call(`/accounts/${id}?includeTransactions=true`, { method: 'DELETE' });
		expect(forced.status).toBe(204);

		const { total } = await json<{ total: number }>(await call('/transactions'));
		expect(total).toBe(0);
	});
});
