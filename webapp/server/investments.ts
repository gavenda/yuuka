/**
 * PSE investments: market data from PHISIX, and what a person's trades come to.
 *
 * PHISIX (https://phisix-api3.appspot.com) answers `stocks.json` with the latest
 * price of every PSE stock, and `stocks/<SYMBOL>.<YYYY-MM-DD>.json` with one
 * stock's close on a past weekday (a weekend is an error, a holiday repeats the
 * last close). It is free and has no key, so it is asked politely: the latest
 * prices once a trading day, and past closes only for stocks someone holds, a
 * few dozen an hour, newest first.
 *
 * Prices are in ten-thousandths of a peso (`_e4`) and money in centavos, both
 * integers. A PHISIX amount arrives as a JSON number with at most four decimal
 * places; rounding it to the nearest ten-thousandth is exact for those.
 */

import { invalidateSummaries } from './cache';
import { currentMonth } from './dates';

const PHISIX = 'https://phisix-api3.appspot.com';

/** The hourly PSE price tick; must match `triggers.crons` in `wrangler.jsonc`. */
export const INVESTMENT_CRON = '20 * * * *';

/** How many past closes one cron tick may ask PHISIX for. */
export const BACKFILL_PER_TICK = 40;

/** How far back a held stock's closes are backfilled, in days. */
const BACKFILL_DAYS = 366;

interface PhisixStock {
	symbol: string;
	name: string;
	price: { currency: string; amount: number };
	percentChange?: number;
}

interface PhisixResponse {
	stocks: PhisixStock[];
	as_of: string;
}

export const toE4 = (amount: number) => Math.round(amount * 10_000);

/** The value in centavos of `shares` at `priceE4`, rounded half up. */
export const valueOf = (shares: number, priceE4: number) => Math.floor((shares * priceE4 + 50) / 100);

/** Manila is UTC+8 all year. */
function manila(now: Date): Date {
	return new Date(now.getTime() + 8 * 3600_000);
}

const isoDay = (date: Date) => date.toISOString().slice(0, 10);

function isWeekend(day: string): boolean {
	const weekday = new Date(`${day}T00:00:00Z`).getUTCDay();
	return weekday === 0 || weekday === 6;
}

function previousDay(day: string): string {
	const date = new Date(`${day}T00:00:00Z`);
	date.setUTCDate(date.getUTCDate() - 1);
	return isoDay(date);
}

/**
 * The trading day whose close is the latest there can be at `now`: today in
 * Manila once the market has closed (15:00, with half an hour for PHISIX to
 * catch up), otherwise the weekday before.
 */
export function latestCloseDay(now: Date): string {
	const local = manila(now);
	let day = isoDay(local);
	const minutes = local.getUTCHours() * 60 + local.getUTCMinutes();
	if (minutes < 15 * 60 + 30) day = previousDay(day);
	while (isWeekend(day)) day = previousDay(day);
	return day;
}

async function fetchJson(url: string): Promise<PhisixResponse | null> {
	const response = await fetch(url, { headers: { Accept: 'application/json' } });
	if (!response.ok) return null;
	const body = (await response.json()) as Partial<PhisixResponse>;
	return Array.isArray(body.stocks) ? (body as PhisixResponse) : null;
}

/**
 * Brings `securities` up to the latest close and records it in `stock_prices`, unless it already
 * is. Answers whether it fetched. A day PHISIX repeats (a holiday) is stored as that day's close.
 */
export async function refreshSecurities(env: Env, now: Date, force = false): Promise<boolean> {
	const day = latestCloseDay(now);
	if (!force) {
		const latest = await env.DB.prepare('SELECT MAX(as_of) AS as_of FROM securities').first<{ as_of: string | null }>();
		if (latest?.as_of && latest.as_of >= day) return false;
	}

	const body = await fetchJson(`${PHISIX}/stocks.json`);
	if (!body) throw new Error('PHISIX did not answer with stocks.');

	const statements: D1PreparedStatement[] = [];
	for (const stock of body.stocks) {
		if (!stock.symbol || stock.price?.currency !== 'PHP') continue;
		const price = toE4(stock.price.amount);
		const change = stock.percentChange === undefined ? null : Math.round(stock.percentChange * 100);
		statements.push(
			env.DB.prepare(
				`INSERT INTO securities (symbol, name, price_e4, change_bp, as_of) VALUES (?, ?, ?, ?, ?)
				 ON CONFLICT (symbol) DO UPDATE SET name = excluded.name, price_e4 = excluded.price_e4,
				   change_bp = excluded.change_bp, as_of = excluded.as_of,
				   updated_at = strftime('%Y-%m-%dT%H:%M:%fZ', 'now')`,
			).bind(stock.symbol, stock.name, price, change, day),
			env.DB.prepare(
				'INSERT INTO stock_prices (symbol, day, close_e4) VALUES (?, ?, ?) ON CONFLICT (symbol, day) DO UPDATE SET close_e4 = excluded.close_e4',
			).bind(stock.symbol, day, price),
		);
	}
	// D1 takes a batch whole; a few hundred statements at a time keeps each one small.
	for (let index = 0; index < statements.length; index += 200) {
		await env.DB.batch(statements.slice(index, index + 200));
	}

	// Net worth counts what holdings are worth at the latest close, so this month's cached summary
	// is out of date for everyone who holds a stock.
	const { results } = await env.DB.prepare('SELECT DISTINCT user_id FROM trades').all<{ user_id: string }>();
	const month = currentMonth();
	await Promise.all(results.map((row) => invalidateSummaries(env.CACHE, row.user_id, [month]).catch(() => {})));
	return true;
}

/**
 * Asks PHISIX for up to `budget` past closes of the stocks someone holds, the most recent missing
 * weekdays first, so the last month fills before the year. A weekday PHISIX has nothing for is
 * remembered in `stock_price_gaps` and not asked again.
 */
export async function backfillPrices(env: Env, now: Date, budget = BACKFILL_PER_TICK): Promise<number> {
	const { results: held } = await env.DB.prepare('SELECT DISTINCT symbol FROM trades ORDER BY symbol').all<{ symbol: string }>();
	if (held.length === 0) return 0;

	const end = latestCloseDay(now);
	const start = new Date(`${end}T00:00:00Z`);
	start.setUTCDate(start.getUTCDate() - BACKFILL_DAYS);
	const from = isoDay(start);

	const wanted: { symbol: string; day: string }[] = [];
	for (const { symbol } of held) {
		const [known, gaps] = await env.DB.batch<{ day: string }>([
			env.DB.prepare('SELECT day FROM stock_prices WHERE symbol = ? AND day >= ?').bind(symbol, from),
			env.DB.prepare('SELECT day FROM stock_price_gaps WHERE symbol = ? AND day >= ?').bind(symbol, from),
		]);
		const have = new Set([...known.results, ...gaps.results].map((row) => row.day));
		for (let day = end; day >= from; day = previousDay(day)) {
			if (!isWeekend(day) && !have.has(day)) wanted.push({ symbol, day });
		}
	}

	// Newest first across every stock, so each one's recent closes arrive before anyone's old ones.
	wanted.sort((a, b) => (a.day === b.day ? a.symbol.localeCompare(b.symbol) : b.day.localeCompare(a.day)));

	let fetched = 0;
	for (const { symbol, day } of wanted.slice(0, budget)) {
		const body = await fetchJson(`${PHISIX}/stocks/${encodeURIComponent(symbol)}.${day}.json`).catch(() => null);
		const stock = body?.stocks.find((entry) => entry.symbol === symbol);
		if (stock) {
			await env.DB.prepare('INSERT OR IGNORE INTO stock_prices (symbol, day, close_e4) VALUES (?, ?, ?)')
				.bind(symbol, day, toE4(stock.price.amount))
				.run();
			fetched++;
		} else {
			await env.DB.prepare('INSERT OR IGNORE INTO stock_price_gaps (symbol, day) VALUES (?, ?)').bind(symbol, day).run();
		}
	}
	return fetched;
}

/** The hourly tick: the latest close once a trading day, then a slice of history. */
export async function runInvestmentTick(env: Env, now: Date): Promise<void> {
	try {
		await refreshSecurities(env, now);
	} catch (error) {
		console.error('Refreshing PSE prices failed', error);
	}
	try {
		await backfillPrices(env, now);
	} catch (error) {
		console.error('Backfilling PSE prices failed', error);
	}
}

export type TradeKind = 'buy' | 'sell' | 'dividend';

export interface TradeLike {
	account_id: string;
	symbol: string;
	kind: TradeKind;
	occurred_on: string;
	created_at?: string;
	shares: number;
	price_e4: number;
	fees: number;
}

/** The cash a trade moves, signed: a buy pays the gross and the fees, a sell and a dividend bring in the gross less them. */
export function tradeCash(trade: Pick<TradeLike, 'kind' | 'shares' | 'price_e4' | 'fees'>): number {
	const gross = valueOf(trade.shares, trade.price_e4);
	return trade.kind === 'buy' ? -(gross + trade.fees) : gross - trade.fees;
}

export interface Position {
	accountId: string;
	symbol: string;
	shares: number;
	/** What the shares held cost, buy fees included. */
	costBasis: number;
	realised: number;
	dividends: number;
}

/**
 * Works a set of trades through in date order, average cost: a buy adds its shares and its cost
 * with fees, a sell takes its shares out at the average cost so far and realises the rest of its
 * proceeds, a dividend adds its net cash. Answers null when a sell would take more than is held at
 * that point — a back-dated sell, or a deleted buy, that the history cannot carry.
 */
export function positions(trades: TradeLike[]): Position[] | null {
	const ordered = [...trades].sort(
		(a, b) => a.occurred_on.localeCompare(b.occurred_on) || (a.created_at ?? '').localeCompare(b.created_at ?? ''),
	);
	const byKey = new Map<string, Position>();
	for (const trade of ordered) {
		const key = `${trade.account_id}\u0000${trade.symbol}`;
		let position = byKey.get(key);
		if (!position) {
			position = { accountId: trade.account_id, symbol: trade.symbol, shares: 0, costBasis: 0, realised: 0, dividends: 0 };
			byKey.set(key, position);
		}
		const gross = valueOf(trade.shares, trade.price_e4);
		if (trade.kind === 'buy') {
			position.shares += trade.shares;
			position.costBasis += gross + trade.fees;
		} else if (trade.kind === 'sell') {
			if (trade.shares > position.shares) return null;
			const cost = Math.round((position.costBasis * trade.shares) / position.shares);
			position.realised += gross - trade.fees - cost;
			position.costBasis -= cost;
			position.shares -= trade.shares;
		} else {
			position.dividends += gross - trade.fees;
		}
	}
	return [...byKey.values()];
}

/**
 * What a person's holdings are worth, by the latest close — or, with `onOrBefore`, by each stock's
 * close on that day or the last before it. A stock with no close that early is counted at what it
 * cost, which is all that is known of it.
 */
export async function holdingsValue(db: D1Database, userId: string, onOrBefore?: string): Promise<number> {
	const { results } = await db
		.prepare(
			`SELECT account_id, symbol, kind, occurred_on, created_at, shares, price_e4, fees FROM trades
			 WHERE user_id = ? ${onOrBefore ? 'AND occurred_on <= ?' : ''}`,
		)
		.bind(...(onOrBefore ? [userId, onOrBefore] : [userId]))
		.all<TradeLike>();
	if (results.length === 0) return 0;

	const held = (positions(results) ?? []).filter((position) => position.shares > 0);
	let total = 0;
	for (const position of held) {
		const row = onOrBefore
			? await db
					.prepare('SELECT close_e4 AS price FROM stock_prices WHERE symbol = ? AND day <= ? ORDER BY day DESC LIMIT 1')
					.bind(position.symbol, onOrBefore)
					.first<{ price: number }>()
			: await db.prepare('SELECT price_e4 AS price FROM securities WHERE symbol = ?').bind(position.symbol).first<{ price: number }>();
		total += row ? valueOf(position.shares, row.price) : position.costBasis;
	}
	return total;
}

/**
 * A SQL condition true of a transaction that is not the cash of a buy or a sell. Those only turn
 * cash into shares and back, so income, spending, the daily chart and the payees leave them out, as
 * they leave out transfers. A dividend's cash is income and stays in. `column` is the transaction's
 * id as the query names it — never caller input.
 */
export const notTradeCash = (column: string) =>
	`NOT EXISTS (SELECT 1 FROM trades tr WHERE tr.transaction_id = ${column} AND tr.kind <> 'dividend')`;
