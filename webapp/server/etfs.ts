/**
 * ETFs bought through Interactive Brokers: US-listed ones (VOO, VT) and UCITS ones on the London Stock
 * Exchange (VWRA, CSPX), priced from Yahoo Finance.
 *
 * Yahoo has no published API, but two endpoints answer plain JSON with no key:
 * `v1/finance/search?q=` lists what matches a query, and `v8/finance/chart/<SYMBOL>` gives a security's
 * name, currency and daily closes over a range. There are thousands of ETFs, so none are listed ahead of
 * time: one is added to `securities` the first time someone looks it up (`resolveEtf`), with a year of its
 * closes from that one call, and the hourly tick keeps the ones in `securities` current a few at a time.
 *
 * An ETF is a security like a stock: `securities` (kind `etf`, symbol `ETF-<Yahoo symbol>`, its own
 * `currency`, `exchange` `US` or `LSE`) and a row a trading day in `stock_prices`. A price is kept in
 * ten-thousandths of its currency. The LSE quotes some ETFs in pence (`GBp`); those are kept in pounds.
 */

import { invalidateSummaries } from './cache';
import { currentMonth } from './dates';

const YAHOO = 'https://query1.finance.yahoo.com';

/** An ETF's symbol: the prefix and its Yahoo symbol. No PSE symbol has a hyphen. */
export const ETF_PREFIX = 'ETF-';
export const etfSymbol = (yahooSymbol: string) => `${ETF_PREFIX}${yahooSymbol}`;

/** What Yahoo calls an ETF: a US ticker, or an LSE one with its `.L` suffix. */
const YAHOO_SYMBOL = /^[A-Z0-9]{1,6}(?:\.L)?$/;

/** The Yahoo exchange codes of the US markets an ETF lists on. */
const US_EXCHANGES = new Set(['NYQ', 'NMS', 'NGM', 'NCM', 'NAS', 'PCX', 'ASE', 'BTS', 'NYSEArca']);

/** How many ETFs one hourly tick may bring up to date: each is one request. */
const ETFS_PER_TICK = 10;

/** How long an ETF's price stands before the tick asks again; its market closes once a day. */
const STALE_HOURS = 6;

export type EtfExchange = 'US' | 'LSE';

/** Where an ETF trades, from its Yahoo symbol: the LSE's carry `.L`, a US one carries nothing. */
export const exchangeOf = (yahooSymbol: string): EtfExchange => (yahooSymbol.endsWith('.L') ? 'LSE' : 'US');

export interface EtfMatch {
	symbol: string;
	name: string;
	exchange: EtfExchange;
}

interface YahooQuote {
	symbol?: string;
	quoteType?: string;
	exchange?: string;
	shortname?: string;
	longname?: string;
}

/** The ETFs in a Yahoo search answer that trade in the US or on the LSE, in Yahoo's order. */
export function parseSearch(body: unknown): EtfMatch[] {
	const quotes = (body as { quotes?: YahooQuote[] } | null)?.quotes ?? [];
	const matches: EtfMatch[] = [];
	for (const quote of quotes) {
		if (quote.quoteType !== 'ETF' || !quote.symbol || !YAHOO_SYMBOL.test(quote.symbol)) continue;
		const exchange = exchangeOf(quote.symbol);
		if (exchange === 'LSE' ? quote.exchange !== 'LSE' : !US_EXCHANGES.has(quote.exchange ?? '')) continue;
		const name = (quote.longname ?? quote.shortname ?? quote.symbol).trim().slice(0, 200);
		matches.push({ symbol: etfSymbol(quote.symbol), name, exchange });
	}
	return matches;
}

export interface EtfChart {
	name: string;
	currency: string;
	/** The latest close, in ten-thousandths of `currency`. */
	priceE4: number;
	/** The move from the close before it, in hundredths of a percent; null with only one close. */
	changeBp: number | null;
	asOf: string;
	/** Oldest first, one a trading day. */
	closes: { day: string; closeE4: number }[];
}

interface YahooChart {
	chart?: {
		result?: {
			meta?: {
				currency?: string;
				instrumentType?: string;
				longName?: string;
				shortName?: string;
				gmtoffset?: number;
				priceHint?: number;
			};
			timestamp?: number[];
			indicators?: { quote?: { close?: (number | null)[] }[] };
		}[];
	};
}

/**
 * A chart answer's closes, in ten-thousandths. Yahoo sends a close as a float (`178.89999389648438`), so it
 * is rounded to the decimals the market quotes in (`priceHint`) first; a price in pence is made pounds.
 * The day is the trading day in the exchange's own time. Null for anything but an ETF in a currency.
 */
export function parseChart(body: unknown): EtfChart | null {
	const result = (body as YahooChart | null)?.chart?.result?.[0];
	const meta = result?.meta;
	if (!meta || meta.instrumentType !== 'ETF' || !meta.currency) return null;
	const pence = meta.currency === 'GBp';
	const currency = pence ? 'GBP' : meta.currency.toUpperCase();
	if (!/^[A-Z]{3}$/.test(currency)) return null;

	const hint = Math.min(Math.max(meta.priceHint ?? 2, 0), 4);
	const toE4 = (close: number) => {
		const quoted = Math.round(close * 10 ** hint);
		return pence ? Math.round((quoted * 100) / 10 ** hint) : Math.round((quoted * 10_000) / 10 ** hint);
	};

	const stamps = result.timestamp ?? [];
	const prices = result.indicators?.quote?.[0]?.close ?? [];
	const byDay = new Map<string, number>();
	stamps.forEach((stamp, index) => {
		const close = prices[index];
		if (typeof close !== 'number' || !(close > 0)) return;
		const day = new Date((stamp + (meta.gmtoffset ?? 0)) * 1000).toISOString().slice(0, 10);
		byDay.set(day, toE4(close));
	});
	const closes = [...byDay.entries()].map(([day, closeE4]) => ({ day, closeE4 })).sort((a, b) => a.day.localeCompare(b.day));
	const last = closes.at(-1);
	if (!last) return null;
	const before = closes.at(-2);

	return {
		name: (meta.longName ?? meta.shortName ?? '').trim().slice(0, 200),
		currency,
		priceE4: last.closeE4,
		changeBp: before ? Math.round(((last.closeE4 - before.closeE4) * 10_000) / before.closeE4) : null,
		asOf: last.day,
		closes,
	};
}

async function fetchYahoo(path: string): Promise<unknown> {
	const response = await fetch(`${YAHOO}${path}`, {
		headers: { Accept: 'application/json', 'User-Agent': 'Mozilla/5.0 (compatible; Yuuka)' },
	});
	if (!response.ok) throw new Error(`Yahoo Finance answered ${response.status} for ${path}.`);
	return response.json();
}

/** The US and LSE ETFs Yahoo finds for `query`, at most eight. */
export async function searchEtfs(query: string): Promise<EtfMatch[]> {
	const body = await fetchYahoo(`/v1/finance/search?q=${encodeURIComponent(query)}&quotesCount=20&newsCount=0&listsCount=0`);
	return parseSearch(body).slice(0, 8);
}

/**
 * Reads an ETF's chart and keeps it: its row in `securities` and a close a trading day in `stock_prices`,
 * a year of them the first time (`range`). Answers false when Yahoo knows no ETF by that symbol.
 */
export async function resolveEtf(env: Env, symbol: string, range: '1y' | '5d' = '1y'): Promise<boolean> {
	const yahoo = symbol.slice(ETF_PREFIX.length);
	if (!symbol.startsWith(ETF_PREFIX) || !YAHOO_SYMBOL.test(yahoo)) return false;
	let body: unknown;
	try {
		body = await fetchYahoo(`/v8/finance/chart/${encodeURIComponent(yahoo)}?range=${range}&interval=1d`);
	} catch (error) {
		// A symbol Yahoo does not know is a 404; anything else is Yahoo being unwell, and is the caller's to report.
		if (error instanceof Error && error.message.includes(' 404 ')) return false;
		throw error;
	}
	const chart = parseChart(body);
	if (!chart) return false;

	const statements: D1PreparedStatement[] = [
		env.DB.prepare(
			`INSERT INTO securities (symbol, name, price_e4, change_bp, as_of, kind, currency, exchange) VALUES (?, ?, ?, ?, ?, 'etf', ?, ?)
			 ON CONFLICT (symbol) DO UPDATE SET name = excluded.name, price_e4 = excluded.price_e4, change_bp = excluded.change_bp,
			   as_of = excluded.as_of, currency = excluded.currency, exchange = excluded.exchange,
			   updated_at = strftime('%Y-%m-%dT%H:%M:%fZ', 'now')`,
		).bind(symbol, chart.name || yahoo, chart.priceE4, chart.changeBp, chart.asOf, chart.currency, exchangeOf(yahoo)),
	];
	for (const close of chart.closes) {
		statements.push(
			env.DB.prepare(
				'INSERT INTO stock_prices (symbol, day, close_e4) VALUES (?, ?, ?) ON CONFLICT (symbol, day) DO UPDATE SET close_e4 = excluded.close_e4',
			).bind(symbol, close.day, close.closeE4),
		);
	}
	for (let index = 0; index < statements.length; index += 200) {
		await env.DB.batch(statements.slice(index, index + 200));
	}
	return true;
}

/**
 * Brings up to `budget` ETFs whose price is more than `STALE_HOURS` old up to date, the held ones first and
 * then the longest unread, and purges this month's summary for whoever holds one. Answers how many it read.
 */
export async function refreshEtfs(env: Env, now: Date, budget = ETFS_PER_TICK): Promise<number> {
	const stale = new Date(now.getTime() - STALE_HOURS * 3600_000).toISOString();
	const held = 'EXISTS (SELECT 1 FROM trades t WHERE t.symbol = s.symbol)';
	const { results } = await env.DB.prepare(
		`SELECT s.symbol FROM securities s WHERE s.kind = 'etf' AND s.updated_at < ? ORDER BY ${held} DESC, s.updated_at LIMIT ?`,
	)
		.bind(stale, budget)
		.all<{ symbol: string }>();

	let read = 0;
	for (const { symbol } of results) {
		try {
			if (await resolveEtf(env, symbol, '5d')) read++;
			// One Yahoo no longer knows waits its turn rather than being asked first on every tick.
			else await env.DB.prepare(`UPDATE securities SET updated_at = ? WHERE symbol = ?`).bind(now.toISOString(), symbol).run();
		} catch (error) {
			console.error(`Refreshing the ETF ${symbol} failed`, error);
		}
	}
	if (read === 0) return 0;

	const { results: holders } = await env.DB.prepare(
		"SELECT DISTINCT t.user_id FROM trades t JOIN securities s ON s.symbol = t.symbol WHERE s.kind = 'etf'",
	).all<{ user_id: string }>();
	const month = currentMonth();
	await Promise.all(holders.map((row) => invalidateSummaries(env.CACHE, row.user_id, [month]).catch(() => {})));
	return read;
}
