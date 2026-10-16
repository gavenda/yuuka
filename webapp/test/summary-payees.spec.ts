import { beforeEach, describe, expect, it } from 'vitest';
import { authedClient, json, makeAccount, type Call } from './helpers';

let call: Call;
let checking: string;
let savings: string;

interface Summary {
	topPayees: { payee: string; amount: number; count: number }[];
	roundUps: number;
}

beforeEach(async () => {
	call = await authedClient();
	checking = await makeAccount(call, { name: 'Checking', startingBalance: 1_000_000, roundUpSource: true });
	savings = await makeAccount(call, { name: 'Savings' });
});

const post = (body: Record<string, unknown>) =>
	call('/transactions', { method: 'POST', body: JSON.stringify({ accountId: checking, occurredOn: '2025-03-04', ...body }) });

const summary = async (month = '2025-03') => json<Summary>(await call(`/summary?month=${month}`));

describe("the summary's top payees", () => {
	it('names who the spending went to, the most first, matching names whatever the case', async () => {
		await post({ payee: 'Grocer', amount: -3000 });
		await post({ payee: 'grocer', amount: -2000 });
		await post({ payee: 'Cafe', amount: -4000 });
		await post({ payee: 'Employer', amount: 900_000 });
		await post({ payee: '', amount: -9999 });
		await post({ payee: 'Grocer', amount: -100, occurredOn: '2025-04-01' });

		const { topPayees } = await summary();
		expect(topPayees.map(({ amount, count }) => ({ amount, count }))).toEqual([
			{ amount: 5000, count: 2 },
			{ amount: 4000, count: 1 },
		]);
		expect(topPayees[0].payee.toLowerCase()).toBe('grocer');
		expect(topPayees[1].payee).toBe('Cafe');
	});

	it('leaves transfers out, whatever they are called', async () => {
		await call('/transactions/transfer', {
			method: 'POST',
			body: JSON.stringify({ fromAccountId: checking, toAccountId: savings, amount: 50_000, occurredOn: '2025-03-05', payee: 'Rent pot' }),
		});

		expect((await summary()).topPayees).toEqual([]);
	});

	it('names five at most', async () => {
		for (let index = 1; index <= 7; index++) await post({ payee: `Shop ${index}`, amount: -100 * index });

		const { topPayees } = await summary();
		expect(topPayees.map((row) => row.payee)).toEqual(['Shop 7', 'Shop 6', 'Shop 5', 'Shop 4', 'Shop 3']);
	});
});

describe("the summary's round-ups", () => {
	it('is what Save the Change moved in the month, and none of its payee shows as spending', async () => {
		await call('/round-up', { method: 'PATCH', body: JSON.stringify({ enabled: true, roundTo: 1000, destinationAccountId: savings }) });
		await post({ payee: 'Cafe', amount: -4599 });
		await post({ payee: 'Cafe', amount: -1250 });

		const { roundUps, topPayees } = await summary();
		expect(roundUps).toBe(401 + 750);
		expect(topPayees.map((row) => row.payee)).toEqual(['Cafe']);
	});

	it('is nothing in a month without any', async () => {
		expect((await summary()).roundUps).toBe(0);
	});
});
