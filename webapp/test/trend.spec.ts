import { beforeEach, describe, expect, it } from 'vitest';
import { authedClient, json, makeAccount, type Call } from './helpers';

let call: Call;
let checking: string;
let savings: string;

interface Trend {
	month: string;
	months: { month: string; income: number; expenses: number; net: number; netWorth: number }[];
}

beforeEach(async () => {
	call = await authedClient();
	checking = await makeAccount(call, { name: 'Checking', startingBalance: 100_000 });
	savings = await makeAccount(call, { name: 'Savings', startingBalance: 50_000 });
});

const post = (body: Record<string, unknown>) =>
	call('/transactions', { method: 'POST', body: JSON.stringify({ accountId: checking, ...body }) });
const trend = async (query: string) => json<Trend>(await call(`/trend?${query}`));

describe('the trend', () => {
	it('runs month by month, oldest first, ending at the month asked for', async () => {
		const { month, months } = await trend('month=2025-03&months=3');

		expect(month).toBe('2025-03');
		expect(months.map((entry) => entry.month)).toEqual(['2025-01', '2025-02', '2025-03']);
	});

	it('crosses a year', async () => {
		const { months } = await trend('month=2025-02&months=4');
		expect(months.map((entry) => entry.month)).toEqual(['2024-11', '2024-12', '2025-01', '2025-02']);
	});

	it('counts income and spending as the summary does, transfers left out', async () => {
		await post({ amount: 300_000, occurredOn: '2025-02-01' });
		await post({ amount: -40_000, occurredOn: '2025-02-10' });
		await post({ amount: -5_000, occurredOn: '2025-03-02' });
		await call('/transactions/transfer', {
			method: 'POST',
			body: JSON.stringify({ fromAccountId: checking, toAccountId: savings, amount: 20_000, occurredOn: '2025-02-15' }),
		});

		const { months } = await trend('month=2025-03&months=2');
		expect(months).toMatchObject([
			{ month: '2025-02', income: 300_000, expenses: 40_000, net: 260_000 },
			{ month: '2025-03', income: 0, expenses: 5_000, net: -5_000 },
		]);

		const summary = await json<{ income: number; expenses: number }>(await call('/summary?month=2025-02'));
		expect(summary).toMatchObject({ income: 300_000, expenses: 40_000 });
	});

	it('works net worth back from today, so its last point is the summary’s', async () => {
		await post({ amount: -10_000, occurredOn: '2025-01-15' });
		await post({ amount: -20_000, occurredOn: '2025-02-15' });
		await post({ amount: -30_000, occurredOn: '2025-04-15' });

		const { months } = await trend('month=2025-03&months=3');
		// Today the two accounts hold 150,000 less 60,000. At January's end February's and April's spending had
		// not happened, and at March's end April's had not.
		expect(months.map((entry) => entry.netWorth)).toEqual([140_000, 120_000, 120_000]);

		const latest = await trend('month=2025-04&months=2');
		const summary = await json<{ netWorth: number }>(await call('/summary?month=2025-04'));
		expect(latest.months.at(-1)!.netWorth).toBe(summary.netWorth);
	});

	it('defaults to twelve months, and refuses fewer than two or more than twenty-four', async () => {
		expect((await trend('month=2025-03')).months).toHaveLength(12);
		expect((await call('/trend?months=1')).status).toBe(400);
		expect((await call('/trend?months=25')).status).toBe(400);
		expect((await call('/trend?month=2025-13')).status).toBe(400);
	});
});
