import { env } from 'cloudflare:test';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { parseChart, parseSearch, refreshEtfs } from '../server/etfs';
import { authedClient, json, makeAccount, type Call } from './helpers';

/** A Yahoo chart answer of the shape `v8/finance/chart` gives. */
const chart = (symbol: string, currency: string, closes: (number | null)[], extra: Record<string, unknown> = {}) => ({
	chart: {
		result: [
			{
				meta: {
					currency,
					symbol,
					instrumentType: 'ETF',
					longName: `${symbol} Fund`,
					gmtoffset: symbol.endsWith('.L') ? 0 : -18_000,
					priceHint: 2,
					...extra,
				},
				// Weekdays from Mon 16 Nov 2026, at the US open (14:30 UTC).
				timestamp: closes.map((_, index) => Date.UTC(2026, 10, 16 + index, 14, 30) / 1000),
				indicators: { quote: [{ close: closes }] },
			},
		],
	},
});

describe('reading Yahoo Finance', () => {
	it('keeps only US-listed and LSE ETFs from a search', () => {
		const matches = parseSearch({
			quotes: [
				{ symbol: 'VWRA.L', quoteType: 'ETF', exchange: 'LSE', longname: 'Vanguard FTSE All-World UCITS ETF USD Accumulation' },
				{ symbol: 'VWRA.SW', quoteType: 'ETF', exchange: 'EBS', longname: 'Swiss listing' },
				{ symbol: 'VOO', quoteType: 'ETF', exchange: 'PCX', shortname: 'Vanguard S&P 500 ETF' },
				{ symbol: 'AAPL', quoteType: 'EQUITY', exchange: 'NMS', longname: 'Apple Inc.' },
			],
		});
		expect(matches).toEqual([
			{ symbol: 'ETF-VWRA.L', name: 'Vanguard FTSE All-World UCITS ETF USD Accumulation', exchange: 'LSE' },
			{ symbol: 'ETF-VOO', name: 'Vanguard S&P 500 ETF', exchange: 'US' },
		]);
	});

	it('rounds a close to the market’s ticks, in ten-thousandths, on the exchange’s own day', () => {
		const read = parseChart(chart('VOO', 'USD', [610.25, null, 612.4699951171875]))!;
		expect(read).toMatchObject({ currency: 'USD', priceE4: 6_124_700, asOf: '2026-11-18', name: 'VOO Fund' });
		expect(read.closes).toEqual([
			{ day: '2026-11-16', closeE4: 6_102_500 },
			{ day: '2026-11-18', closeE4: 6_124_700 },
		]);
		// (612.47 − 610.25) / 610.25 is 0.36%.
		expect(read.changeBp).toBe(36);
	});

	it('keeps a price quoted in pence in pounds', () => {
		expect(parseChart(chart('VUSA.L', 'GBp', [8523.5]))).toMatchObject({ currency: 'GBP', priceE4: 852_350 });
	});

	it('reads nothing but an ETF', () => {
		expect(parseChart(chart('AAPL', 'USD', [100], { instrumentType: 'EQUITY' }))).toBeNull();
		expect(parseChart({ chart: { result: [] } })).toBeNull();
	});
});

describe('an ETF', () => {
	let call: Call;
	let ibkr: string;
	let fetched: string[];

	beforeEach(async () => {
		await env.DB.batch([
			env.DB.prepare("DELETE FROM stock_prices WHERE symbol LIKE 'ETF-%'"),
			env.DB.prepare("DELETE FROM securities WHERE symbol LIKE 'ETF-%'"),
		]);
		fetched = [];
		vi.spyOn(globalThis, 'fetch').mockImplementation(async (input) => {
			const url = String(input);
			if (!url.startsWith('https://query1.finance.yahoo.com/')) throw new Error(`Unexpected fetch of ${url}`);
			fetched.push(url);
			if (url.includes('/chart/VOO')) return Response.json(chart('VOO', 'USD', [610.25, 611, 612.47]));
			if (url.includes('/chart/DOWN')) return new Response('unwell', { status: 502 });
			if (url.includes('/chart/CSPX.L')) return Response.json(chart('CSPX.L', 'USD', [700.1]));
			if (url.includes('/search'))
				return Response.json({ quotes: [{ symbol: 'VOO', quoteType: 'ETF', exchange: 'PCX', longname: 'VOO' }] });
			return new Response('{}', { status: 404 });
		});
		call = await authedClient();
		ibkr = await makeAccount(call, { name: 'IBKR', currency: 'USD', bank: 'ibkr', startingBalance: 1_000_000 });
	});

	afterEach(() => vi.restoreAllMocks());

	it('is found by a search, and kept with a year of closes the first time it is looked up', async () => {
		const search = await json<{ results: { symbol: string }[] }>(await call('/securities/search?q=voo'));
		expect(search.results.map((result) => result.symbol)).toEqual(['ETF-VOO']);

		const response = await call('/securities/ETF-VOO');
		expect(response.status).toBe(200);
		const { security } = await json<{ security: Record<string, unknown> }>(response);
		expect(security).toMatchObject({ symbol: 'ETF-VOO', kind: 'etf', currency: 'USD', exchange: 'US', price: 6_124_700 });
		expect(fetched.some((url) => url.includes('/chart/VOO?range=1y'))).toBe(true);
		expect(await env.DB.prepare("SELECT COUNT(*) AS n FROM stock_prices WHERE symbol = 'ETF-VOO'").first('n')).toBe(3);

		// Kept: a second look asks Yahoo nothing.
		fetched = [];
		expect((await call('/securities/ETF-VOO')).status).toBe(200);
		expect(fetched).toEqual([]);
	});

	it('answers 404 for one Yahoo does not know', async () => {
		expect((await call('/securities/ETF-NOPE')).status).toBe(404);
	});

	it('is bought in fractional shares, from an account in its currency, under its ticker', async () => {
		await call('/securities/ETF-CSPX.L');
		const buy = (body: Record<string, unknown>) =>
			call('/trades', {
				method: 'POST',
				body: JSON.stringify({
					accountId: ibkr,
					symbol: 'ETF-CSPX.L',
					kind: 'buy',
					occurredOn: '2026-11-18',
					units: 15_000,
					price: 7_001_000,
					fees: 400,
					...body,
				}),
			});

		const response = await buy({});
		expect(response.status).toBe(201);
		const { trade } = await json<{ trade: { amount: number; transactionId: string } }>(response);
		// 1.5 shares at $700.10 is $1,050.15, and $4.00 of commission.
		expect(trade.amount).toBe(-105_415);
		const { transactions } = await json<{ transactions: { id: string; payee: string; notes: string }[] }>(
			await call('/transactions?month=2026-11&limit=50&offset=0'),
		);
		expect(transactions.find((row) => row.id === trade.transactionId)).toMatchObject({ payee: 'CSPX', notes: 'Buy 1.5 CSPX' });

		// Its cash is worked out, never given; and a peso account holds no dollars.
		expect((await buy({ gross: 105_015 })).status).toBe(400);
		const pesos = await makeAccount(call, { name: 'BPI', currency: 'PHP' });
		expect((await buy({ accountId: pesos })).status).toBe(400);
	});

	it('is looked up when a trade names it before anyone has', async () => {
		const response = await call('/trades', {
			method: 'POST',
			body: JSON.stringify({ accountId: ibkr, symbol: 'ETF-VOO', kind: 'buy', occurredOn: '2026-11-18', units: 10_000, price: 6_124_700 }),
		});
		expect(response.status).toBe(201);
	});

	it('answers a trade 503, not 404, while Yahoo is unwell, so a queued one is sent again', async () => {
		const response = await call('/trades', {
			method: 'POST',
			body: JSON.stringify({ accountId: ibkr, symbol: 'ETF-DOWN', kind: 'buy', occurredOn: '2026-11-18', units: 10_000, price: 1_000_000 }),
		});
		expect(response.status).toBe(503);
	});

	it('is brought up to date by the tick once its price is old', async () => {
		await call('/securities/ETF-VOO');
		const later = new Date(Date.now() + 7 * 3600_000);
		fetched = [];
		expect(await refreshEtfs(env, later)).toBe(1);
		expect(fetched).toEqual(['https://query1.finance.yahoo.com/v8/finance/chart/VOO?range=5d&interval=1d']);
		// Just read: nothing is due.
		expect(await refreshEtfs(env, new Date())).toBe(0);
	});
});
