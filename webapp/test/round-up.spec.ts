import { beforeEach, describe, expect, it } from 'vitest';
import { authedClient, json, makeAccount, makeCategory, otherClient, type Call } from './helpers';

let call: Call;

beforeEach(async () => {
	call = await authedClient();
});

const patch = (body: Record<string, unknown>) => call('/round-up', { method: 'PATCH', body: JSON.stringify(body) });

interface RoundUpRuleBody {
	enabled: boolean;
	roundTo: number;
	destinationAccountId: string | null;
	categoryId: string | null;
}

describe('round-up rule', () => {
	it('starts disabled with no destination or category', async () => {
		const { roundUpRule } = await json<{ roundUpRule: RoundUpRuleBody }>(await call('/round-up'));
		expect(roundUpRule).toMatchObject({ enabled: false, roundTo: 1000, destinationAccountId: null, categoryId: null });
	});

	it('accepts a transfer-scope category', async () => {
		const categoryId = await makeCategory(call, { name: 'Cashflow', kind: 'transfer' });

		const { roundUpRule } = await json<{ roundUpRule: RoundUpRuleBody }>(await patch({ categoryId }));
		expect(roundUpRule.categoryId).toBe(categoryId);
	});

	it('rejects a standard-scope category', async () => {
		const categoryId = await makeCategory(call, { name: 'Groceries', kind: 'expense' });

		expect((await patch({ categoryId })).status).toBe(400);
	});

	it('rejects a category belonging to another user', async () => {
		const theirs = await otherClient();
		const theirCategoryId = await makeCategory(theirs, { name: 'Cashflow', kind: 'transfer' });

		expect((await patch({ categoryId: theirCategoryId })).status).toBe(400);
	});

	it('clears the category back to uncategorised', async () => {
		const categoryId = await makeCategory(call, { name: 'Cashflow', kind: 'transfer' });
		await patch({ categoryId });

		const { roundUpRule } = await json<{ roundUpRule: RoundUpRuleBody }>(await patch({ categoryId: null }));
		expect(roundUpRule.categoryId).toBeNull();
	});

	it('sets each field independently', async () => {
		const destinationId = await makeAccount(call, { name: 'Savings' });

		await patch({ enabled: true });
		await patch({ roundTo: 10000 });
		const response = await patch({ destinationAccountId: destinationId });

		const { roundUpRule } = await json<{ roundUpRule: { enabled: boolean; roundTo: number; destinationAccountId: string | null } }>(
			response,
		);
		expect(roundUpRule).toMatchObject({ enabled: true, roundTo: 10000, destinationAccountId: destinationId });
	});

	it('a later partial patch does not reset earlier fields', async () => {
		const destinationId = await makeAccount(call, { name: 'Savings' });
		await patch({ enabled: true, roundTo: 10000, destinationAccountId: destinationId });

		// Only touches `enabled` this time.
		const response = await patch({ enabled: false });
		const { roundUpRule } = await json<{ roundUpRule: { enabled: boolean; roundTo: number; destinationAccountId: string | null } }>(
			response,
		);
		expect(roundUpRule).toMatchObject({ enabled: false, roundTo: 10000, destinationAccountId: destinationId });
	});

	it('a first-ever patch that only sets `enabled` defaults the rest rather than erroring', async () => {
		const response = await patch({ enabled: true });
		expect(response.status).toBe(200);

		const { roundUpRule } = await json<{ roundUpRule: { enabled: boolean; roundTo: number; destinationAccountId: string | null } }>(
			response,
		);
		expect(roundUpRule).toMatchObject({ enabled: true, roundTo: 1000, destinationAccountId: null });
	});

	it('rejects a destination account belonging to another user', async () => {
		const theirs = await otherClient();
		const theirAccountId = await makeAccount(theirs, { name: 'Not yours' });

		expect((await patch({ destinationAccountId: theirAccountId })).status).toBe(400);
	});

	it('rejects an unknown destination account id', async () => {
		expect((await patch({ destinationAccountId: 'acc_nope' })).status).toBe(400);
	});

	it('rejects a roundTo outside 1000/10000', async () => {
		expect((await patch({ roundTo: 500 })).status).toBe(400);
	});

	it('rejects an empty patch', async () => {
		expect((await call('/round-up', { method: 'PATCH', body: '{}' })).status).toBe(400);
	});

	it('needs a token', async () => {
		const { request } = await import('./helpers');
		expect((await request('/round-up')).status).toBe(401);
	});
});
