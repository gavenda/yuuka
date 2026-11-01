import { env } from 'cloudflare:test';
import { beforeEach, describe, expect, it } from 'vitest';
import { companyIdOf, latestCloseDay, logoUrlOf, positions, tradeCash, valueOf } from '../server/investments';
import { authedClient, json, makeAccount, otherClient, type Call } from './helpers';

let call: Call;
let broker: string;

interface Trade {
	id: string;
	accountId: string;
	symbol: string;
	kind: string;
	occurredOn: string;
	shares: number;
	price: number;
	fees: number;
	amount: number;
	transactionId: string;
}

const MONTH = new Date().toISOString().slice(0, 7);
const TODAY = new Date().toISOString().slice(0, 10);

beforeEach(async () => {
	// Market data is shared and outlives each test's wipe. An `as_of` far ahead keeps the
	// routes from asking PHISIX for anything.
	await env.DB.batch([
		env.DB.prepare('DELETE FROM stock_prices'),
		env.DB.prepare(
			`INSERT OR REPLACE INTO securities (symbol, name, price_e4, change_bp, as_of) VALUES
			 ('BDO', 'BDO Unibank, Inc.', 1200000, 51, '2999-12-31'),
			 ('ALI', 'Ayala Land, Inc.', 153600, 13, '2999-12-31')`,
		),
	]);
	call = await authedClient();
	broker = await makeAccount(call, { name: 'Dragonfi', startingBalance: 10_000_000 });
});

const trade = (body: Record<string, unknown>, caller: Call = call) =>
	caller('/trades', {
		method: 'POST',
		body: JSON.stringify({
			accountId: broker,
			symbol: 'BDO',
			kind: 'buy',
			occurredOn: TODAY,
			shares: 100,
			price: 1_180_000,
			fees: 3_000,
			...body,
		}),
	});

const edit = (id: string, body: Record<string, unknown>, caller: Call = call) =>
	caller(`/trades/${id}`, {
		method: 'PATCH',
		body: JSON.stringify({
			accountId: broker,
			symbol: 'BDO',
			kind: 'buy',
			occurredOn: TODAY,
			shares: 100,
			price: 1_180_000,
			fees: 3_000,
			...body,
		}),
	});

const balance = async () => {
	const { accounts } = await json<{ accounts: { id: string; balance: number }[] }>(await call('/accounts'));
	return accounts.find((account) => account.id === broker)!.balance;
};

describe('a trade', () => {
	it('writes its cash leg in the broker account', async () => {
		const response = await trade({});
		expect(response.status).toBe(201);
		const { trade: created } = await json<{ trade: Trade }>(response);

		// 100 × ₱118.00 = ₱11,800.00, plus ₱30.00 of fees.
		expect(created).toMatchObject({ symbol: 'BDO', kind: 'buy', shares: 100, amount: -1_183_000 });
		expect(await balance()).toBe(10_000_000 - 1_183_000);

		const { transactions } = await json<{ transactions: { id: string; amount: number; payee: string }[] }>(
			await call(`/transactions?month=${MONTH}&limit=50&offset=0`),
		);
		expect(transactions.find((row) => row.id === created.transactionId)).toMatchObject({ amount: -1_183_000, payee: 'BDO' });
	});

	it('is neither income nor spending when bought or sold, and its shares count in net worth', async () => {
		await trade({});
		await trade({ kind: 'sell', shares: 40, price: 1_200_000, fees: 2_500 });

		const summary = await json<{ income: number; expenses: number; netWorth: number; holdings: number; topPayees: unknown[] }>(
			await call(`/summary?month=${MONTH}`),
		);
		expect(summary.income).toBe(0);
		expect(summary.expenses).toBe(0);
		expect(summary.topPayees).toEqual([]);
		// 60 shares at the latest close of ₱120.00.
		expect(summary.holdings).toBe(720_000);
		expect(summary.netWorth).toBe((await balance()) + 720_000);
	});

	it('counts a dividend as income', async () => {
		await trade({});
		const { trade: dividend } = await json<{ trade: Trade }>(await trade({ kind: 'dividend', shares: 100, price: 15_000, fees: 1_500 }));
		// ₱1.50 a share on 100 shares, less 10% final tax.
		expect(dividend.amount).toBe(13_500);

		const summary = await json<{ income: number }>(await call(`/summary?month=${MONTH}`));
		expect(summary.income).toBe(13_500);
	});

	it('cannot sell more than is held, then or before', async () => {
		await trade({});
		expect((await trade({ kind: 'sell', shares: 101 })).status).toBe(409);
		expect((await trade({ kind: 'sell', shares: 10, occurredOn: '2020-01-02' })).status).toBe(409);
	});

	it('refuses a stock the PSE does not list', async () => {
		expect((await trade({ symbol: 'NOPE' })).status).toBe(404);
	});

	it('answers a replay with the trade it already made', async () => {
		const ids = { id: 'trd_0123456789abcdef', transactionId: 'txn_0123456789abcdef' };
		expect((await trade(ids)).status).toBe(201);
		expect((await trade(ids)).status).toBe(200);

		const { trades } = await json<{ trades: Trade[] }>(await call('/trades'));
		expect(trades).toHaveLength(1);
		expect(await balance()).toBe(10_000_000 - 1_183_000);
	});

	it('goes with its cash leg, unless a later sell needs it', async () => {
		const { trade: buy } = await json<{ trade: Trade }>(await trade({}));
		const { trade: sell } = await json<{ trade: Trade }>(await trade({ kind: 'sell', shares: 50 }));

		expect((await call(`/transactions/${buy.transactionId}`, { method: 'DELETE' })).status).toBe(409);
		expect((await call(`/trades/${buy.id}`, { method: 'DELETE' })).status).toBe(409);

		expect((await call(`/transactions/${sell.transactionId}`, { method: 'DELETE' })).status).toBe(204);
		expect((await call(`/trades/${buy.id}`, { method: 'DELETE' })).status).toBe(204);

		const { trades } = await json<{ trades: Trade[] }>(await call('/trades'));
		expect(trades).toEqual([]);
		expect(await balance()).toBe(10_000_000);
		// Already gone reads as done, so a replayed delete is harmless.
		expect((await call(`/trades/${buy.id}`, { method: 'DELETE' })).status).toBe(204);
	});

	it('records the time it was made, on itself and its cash leg', async () => {
		const { trade: created } = await json<{ trade: Trade }>(await trade({ occurredOn: `${TODAY}T09:45` }));
		expect(created.occurredOn).toBe(`${TODAY}T09:45`);

		const { transactions } = await json<{ transactions: { id: string; occurredOn: string }[] }>(
			await call(`/transactions?month=${MONTH}&limit=50&offset=0`),
		);
		expect(transactions.find((row) => row.id === created.transactionId)?.occurredOn).toBe(`${TODAY}T09:45`);
	});

	it('sells in time order within a day', async () => {
		await trade({ occurredOn: `${TODAY}T10:00` });
		// A sell before the buy that day has nothing to sell yet.
		expect((await trade({ kind: 'sell', shares: 10, occurredOn: `${TODAY}T09:30` })).status).toBe(409);
		expect((await trade({ kind: 'sell', shares: 10, occurredOn: `${TODAY}T10:30` })).status).toBe(201);
	});

	it('is edited whole, its cash leg worked out again', async () => {
		const { trade: buy } = await json<{ trade: Trade }>(await trade({}));
		const second = await makeAccount(call, { name: 'COL', startingBalance: 5_000_000 });

		const response = await edit(buy.id, { accountId: second, shares: 200, fees: 6_000, occurredOn: `${TODAY}T14:05` });
		expect(response.status).toBe(200);
		const { trade: edited } = await json<{ trade: Trade }>(response);
		// 200 × ₱118.00 = ₱23,600.00, plus ₱60.00 of fees, now out of the second account.
		expect(edited).toMatchObject({ id: buy.id, accountId: second, shares: 200, amount: -2_366_000, occurredOn: `${TODAY}T14:05` });
		expect(edited.transactionId).toBe(buy.transactionId);
		expect(await balance()).toBe(10_000_000);

		const { transactions } = await json<{ transactions: { id: string; accountId: string; amount: number; notes: string }[] }>(
			await call(`/transactions?month=${MONTH}&limit=50&offset=0`),
		);
		expect(transactions.find((row) => row.id === buy.transactionId)).toMatchObject({
			accountId: second,
			amount: -2_366_000,
			notes: 'Buy 200 BDO',
		});
	});

	it('cannot be edited into a history that sells more than it holds', async () => {
		const { trade: buy } = await json<{ trade: Trade }>(await trade({}));
		await trade({ kind: 'sell', shares: 80 });
		expect((await edit(buy.id, { shares: 50 })).status).toBe(409);
		expect((await edit(buy.id, { shares: 80 })).status).toBe(200);
	});

	it('answers an edit of a trade that is not there with 404', async () => {
		expect((await edit('trd_0123456789abcdef', {})).status).toBe(404);
	});

	it('keeps its cash leg from being edited as a transaction', async () => {
		const { trade: buy } = await json<{ trade: Trade }>(await trade({}));
		const response = await call(`/transactions/${buy.transactionId}`, { method: 'PATCH', body: JSON.stringify({ amount: -1 }) });
		expect(response.status).toBe(409);
	});
});

describe('prices', () => {
	it('lists every stock at its latest close', async () => {
		const { securities } = await json<{ securities: { symbol: string; price: number }[] }>(await call('/securities'));
		expect(securities).toEqual(
			expect.arrayContaining([
				expect.objectContaining({ symbol: 'BDO', price: 1_200_000, changeBp: 51, asOf: '2999-12-31', logoUrl: null }),
			]),
		);
	});

	it('gives a stock’s closes oldest first, from a day on', async () => {
		await env.DB.prepare(
			`INSERT INTO stock_prices (symbol, day, close_e4) VALUES ('BDO', '2026-10-28', 1170000), ('BDO', '2026-10-29', 1174000), ('BDO', '2026-10-30', 1180000)`,
		).run();
		const { prices } = await json<{ prices: { day: string; close: number }[] }>(await call('/securities/BDO/prices?from=2026-10-29'));
		expect(prices).toEqual([
			{ day: '2026-10-29', close: 1_174_000 },
			{ day: '2026-10-30', close: 1_180_000 },
		]);
	});
});

describe('trades are private', () => {
	it('cannot be made against another user’s account', async () => {
		const other = await otherClient();
		expect((await trade({}, other)).status).toBe(404);
		expect(await balance()).toBe(10_000_000);
	});

	it('are not listed or deletable by another user', async () => {
		const { trade: buy } = await json<{ trade: Trade }>(await trade({}));
		const other = await otherClient();

		const { trades } = await json<{ trades: Trade[] }>(await other('/trades'));
		expect(trades).toEqual([]);

		await other(`/trades/${buy.id}`, { method: 'DELETE' });
		const mine = await json<{ trades: Trade[] }>(await call('/trades'));
		expect(mine.trades).toHaveLength(1);
	});

	it('cannot be edited by another user, nor moved into another user’s account', async () => {
		const { trade: buy } = await json<{ trade: Trade }>(await trade({}));
		const other = await otherClient();
		expect((await edit(buy.id, { shares: 1 }, other)).status).toBe(404);

		const theirs = await makeAccount(other, { name: 'Theirs', startingBalance: 0 });
		expect((await edit(buy.id, { accountId: theirs })).status).toBe(404);

		const mine = await json<{ trades: Trade[] }>(await call('/trades'));
		expect(mine.trades[0]).toMatchObject({ shares: 100, accountId: broker });
		expect(await balance()).toBe(10_000_000 - 1_183_000);
	});
});

describe('the arithmetic', () => {
	it('values shares to the centavo, rounding half up', () => {
		expect(valueOf(100, 1_180_000)).toBe(1_180_000);
		expect(valueOf(3, 5)).toBe(0);
		expect(valueOf(10, 5)).toBe(1);
	});

	it('signs a trade’s cash by what it does', () => {
		expect(tradeCash({ kind: 'buy', shares: 10, price_e4: 1_000_000, fees: 2_000 })).toBe(-102_000);
		expect(tradeCash({ kind: 'sell', shares: 10, price_e4: 1_000_000, fees: 2_000 })).toBe(98_000);
		expect(tradeCash({ kind: 'dividend', shares: 10, price_e4: 10_000, fees: 100 })).toBe(900);
	});

	it('takes shares out at the average cost and realises the rest', () => {
		const base = { account_id: 'a', symbol: 'X', fees: 0 };
		const result = positions([
			{ ...base, kind: 'buy', occurred_on: '2026-01-01', shares: 100, price_e4: 100_000 },
			{ ...base, kind: 'buy', occurred_on: '2026-02-01', shares: 100, price_e4: 200_000 },
			{ ...base, kind: 'sell', occurred_on: '2026-03-01', shares: 50, price_e4: 300_000 },
		]);
		// Average cost ₱15.00; 50 sold at ₱30.00 realises ₱750.00.
		expect(result).toEqual([{ accountId: 'a', symbol: 'X', shares: 150, costBasis: 225_000, realised: 75_000, dividends: 0 }]);
	});

	it('finds the latest close: today after 15:30 in Manila, the weekday before otherwise', () => {
		// Friday 30 October 2026, 16:00 in Manila.
		expect(latestCloseDay(new Date('2026-10-30T08:00:00Z'))).toBe('2026-10-30');
		// The same Friday at 10:00, mid-session.
		expect(latestCloseDay(new Date('2026-10-30T02:00:00Z'))).toBe('2026-10-29');
		// Sunday and Monday morning both look back to Friday.
		expect(latestCloseDay(new Date('2026-11-01T04:00:00Z'))).toBe('2026-10-30');
		expect(latestCloseDay(new Date('2026-11-02T01:00:00Z'))).toBe('2026-10-30');
	});
});

describe('a stock’s logo', () => {
	// Cut from what PSE Edge answered: its directory search, then a company page.
	const search = `<tr>
		<td><a href="#company" onclick="cmDetail('260','468');return false;">BDO Unibank, Inc.</a></td>
		<td><a href="#company" onclick="cmDetail('260','468');return false;">BDO</a></td>
	</tr><tr>
		<td><a href="#company" onclick="cmDetail('612','901');return false;">BDO Leasing</a></td>
		<td><a href="#company" onclick="cmDetail('612','901');return false;">BDOL</a></td>
	</tr>`;

	it('is found under the company id the directory lists for that exact symbol', () => {
		expect(companyIdOf(search, 'BDO')).toBe('260');
		expect(companyIdOf(search, 'BDOL')).toBe('612');
		expect(companyIdOf(search, 'BD')).toBeNull();
	});

	it('is read off the company page as an address on PSE Edge', () => {
		expect(logoUrlOf('<div><img src="/clogo/180/cl53741ab0r275.JPG" alt="Logo" height="85" /></div>')).toBe(
			'https://edge.pse.com.ph/clogo/180/cl53741ab0r275.JPG',
		);
		expect(logoUrlOf('<img src="/images/common/top_logo.png" alt="PSE Edge"/>')).toBeNull();
	});
});
