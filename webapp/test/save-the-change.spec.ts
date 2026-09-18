import { beforeEach, describe, expect, it } from 'vitest';
import { authedClient, json, makeAccount, makeCategory, otherClient, type Call } from './helpers';

let call: Call;
let sourceId: string;
let destinationId: string;

beforeEach(async () => {
	call = await authedClient();
	sourceId = await makeAccount(call, { name: 'Checking', roundUpSource: true });
	destinationId = await makeAccount(call, { name: 'Savings' });
	await call('/round-up', {
		method: 'PATCH',
		body: JSON.stringify({ enabled: true, roundTo: 1000, destinationAccountId: destinationId }),
	});
});

function post(body: Record<string, unknown>) {
	return call('/transactions', { method: 'POST', body: JSON.stringify(body) });
}

interface CreateResponse {
	transaction: { id: string; transferId: string | null };
	roundUp: { id: string; amount: number; accountId: string; payee: string; categoryId: string | null; transferId: string | null } | null;
}

describe('save the change', () => {
	it('rounds an expense up to the nearest ₱10 and moves the gap to the destination', async () => {
		const response = await post({ accountId: sourceId, amount: -4599, occurredOn: '2026-09-03' });
		const { transaction, roundUp } = await json<CreateResponse>(response);

		expect(transaction.transferId).toBeNull();
		expect(roundUp).toMatchObject({ amount: 401, accountId: destinationId, payee: 'Save the Change', categoryId: null });
		expect(roundUp!.transferId).not.toBeNull();

		const { total } = await json<{ total: number }>(await call('/transactions'));
		expect(total).toBe(3); // the purchase, plus the two round-up legs
	});

	it("posts both legs under the rule's category when one is set", async () => {
		const categoryId = await makeCategory(call, { name: 'Cashflow', kind: 'expense', appliesTo: 'transfer' });
		await call('/round-up', { method: 'PATCH', body: JSON.stringify({ categoryId }) });

		const { roundUp } = await json<CreateResponse>(await post({ accountId: sourceId, amount: -4599, occurredOn: '2026-09-03' }));
		expect(roundUp).toMatchObject({ categoryId });
	});

	it('rounds up to the nearest ₱100 when configured', async () => {
		await call('/round-up', { method: 'PATCH', body: JSON.stringify({ roundTo: 10000 }) });

		const { roundUp } = await json<CreateResponse>(await post({ accountId: sourceId, amount: -4599, occurredOn: '2026-09-03' }));
		expect(roundUp).toMatchObject({ amount: 5401, accountId: destinationId });
	});

	it('does nothing when the amount is already an exact multiple', async () => {
		const { roundUp } = await json<CreateResponse>(await post({ accountId: sourceId, amount: -5000, occurredOn: '2026-09-03' }));
		expect(roundUp).toBeNull();

		const { total } = await json<{ total: number }>(await call('/transactions'));
		expect(total).toBe(1);
	});

	it('never triggers on income', async () => {
		const { roundUp } = await json<CreateResponse>(await post({ accountId: sourceId, amount: 4599, occurredOn: '2026-09-03' }));
		expect(roundUp).toBeNull();
	});

	it('never triggers on an account that has not opted in', async () => {
		const plainAccountId = await makeAccount(call, { name: 'Cash' });
		const { roundUp } = await json<CreateResponse>(await post({ accountId: plainAccountId, amount: -4599, occurredOn: '2026-09-03' }));
		expect(roundUp).toBeNull();
	});

	it('never triggers while the rule is disabled', async () => {
		await call('/round-up', { method: 'PATCH', body: JSON.stringify({ enabled: false }) });
		const { roundUp } = await json<CreateResponse>(await post({ accountId: sourceId, amount: -4599, occurredOn: '2026-09-03' }));
		expect(roundUp).toBeNull();
	});

	it('never triggers when no destination is set', async () => {
		const other = await otherClient();
		const otherSource = await makeAccount(other, { name: 'Checking', roundUpSource: true });
		await other('/round-up', { method: 'PATCH', body: JSON.stringify({ enabled: true }) });

		const { roundUp } = await json<CreateResponse>(
			await other('/transactions', {
				method: 'POST',
				body: JSON.stringify({ accountId: otherSource, amount: -4599, occurredOn: '2026-09-03' }),
			}),
		);
		expect(roundUp).toBeNull();
	});

	it('does not round up a purchase made on the destination account itself, but the purchase still posts', async () => {
		await call('/accounts/' + destinationId, { method: 'PATCH', body: JSON.stringify({ roundUpSource: true }) });

		const response = await post({ accountId: destinationId, amount: -4599, occurredOn: '2026-09-03' });
		expect(response.status).toBe(201);
		const { transaction, roundUp } = await json<CreateResponse>(response);
		expect(transaction.transferId).toBeNull();
		expect(roundUp).toBeNull();
	});

	it('never triggers on editing an existing transaction', async () => {
		const { transaction } = await json<CreateResponse>(await post({ accountId: sourceId, amount: -5000, occurredOn: '2026-09-03' }));

		const response = await call(`/transactions/${transaction.id}`, { method: 'PATCH', body: JSON.stringify({ amount: -4599 }) });
		expect(response.status).toBe(200);

		const { total } = await json<{ total: number }>(await call('/transactions'));
		expect(total).toBe(1);
	});

	it('never triggers on a transfer, even from an opted-in account', async () => {
		const response = await call('/transactions/transfer', {
			method: 'POST',
			body: JSON.stringify({ fromAccountId: sourceId, toAccountId: destinationId, amount: 4599, occurredOn: '2026-09-03' }),
		});
		expect(response.status).toBe(201);

		const { total } = await json<{ total: number }>(await call('/transactions'));
		expect(total).toBe(2); // just the transfer's own two legs
	});

	it('never triggers on a balance adjustment', async () => {
		const response = await call(`/accounts/${sourceId}/adjust`, {
			method: 'POST',
			body: JSON.stringify({ balance: -4599, occurredOn: '2026-09-03' }),
		});
		expect(response.status).toBe(201);

		const { total } = await json<{ total: number }>(await call('/transactions'));
		expect(total).toBe(1);
	});

	it('deleting the original purchase does not delete the round-up transfer', async () => {
		const { transaction } = await json<CreateResponse>(await post({ accountId: sourceId, amount: -4599, occurredOn: '2026-09-03' }));

		await call(`/transactions/${transaction.id}`, { method: 'DELETE' });

		// The round-up is its own independent transfer once posted, editable and
		// deletable like any other — deleting the purchase that triggered it
		// does not cascade.
		const { total } = await json<{ total: number }>(await call('/transactions'));
		expect(total).toBe(2);
	});
});
