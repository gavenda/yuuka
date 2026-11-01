import { Hono } from 'hono';
import { badRequest, conflict, notFound } from '../errors';
import { alreadyOwned, newId } from '../ids';
import { invalidateSummaries } from '../cache';
import { currentMonth, joinOccurrence, splitOccurrence } from '../dates';
import { NOW_SQL } from '../sql';
import { parseJson, parseQuery } from '../validate';
import { requireAuth } from '../middleware/auth';
import type { SystemCategory } from '../defaults';
import { positions, refreshSecurities, tradeCash, type TradeKind, type TradeLike } from '../investments';
import { pricesQuerySchema, tradeCreateSchema, tradeUpdateSchema } from '../schemas';
import type { AppEnv } from '../types';

/**
 * A trade's cash is filed under a system category: a buy's or a sell's under the transfer Investments,
 * a dividend's under the income Investments. Bound as the owner, then `systemCategoryFor(kind)`.
 */
const SYSTEM_CATEGORY_ID = 'SELECT id FROM categories WHERE user_id = ? AND system = ?';
export const systemCategoryFor = (kind: TradeKind): SystemCategory => (kind === 'dividend' ? 'investment_income' : 'investments');

interface SecurityRow {
	symbol: string;
	name: string;
	price_e4: number;
	change_bp: number | null;
	as_of: string;
	logo_url: string | null;
}

interface TradeRow {
	id: string;
	account_id: string;
	account_name: string | null;
	symbol: string;
	name: string | null;
	kind: TradeKind;
	occurred_on: string;
	occurred_time: string | null;
	shares: number;
	price_e4: number;
	fees: number;
	transaction_id: string;
	created_at: string;
	updated_at: string;
}

const toSecurity = (row: SecurityRow) => ({
	symbol: row.symbol,
	name: row.name,
	price: row.price_e4,
	changeBp: row.change_bp,
	asOf: row.as_of,
	logoUrl: row.logo_url,
});

const toTrade = (row: TradeRow) => ({
	id: row.id,
	accountId: row.account_id,
	accountName: row.account_name,
	symbol: row.symbol,
	name: row.name,
	kind: row.kind,
	occurredOn: joinOccurrence(row.occurred_on, row.occurred_time),
	shares: row.shares,
	price: row.price_e4,
	fees: row.fees,
	amount: tradeCash(row),
	transactionId: row.transaction_id,
	createdAt: row.created_at,
	updatedAt: row.updated_at,
});

const SELECT_TRADE = `
	SELECT tr.*, a.name AS account_name, s.name AS name
	FROM trades tr
	LEFT JOIN accounts a ON a.id = tr.account_id AND a.user_id = tr.user_id
	LEFT JOIN securities s ON s.symbol = tr.symbol
`;

const monthOf = (day: string) => day.slice(0, 7);

/** Every PSE stock at its latest close. Brought up to date first if it is behind, so the first person to look needs no cron. */
export const securityRoutes = new Hono<AppEnv>()
	.use('*', requireAuth)
	.get('/', async (c) => {
		try {
			await refreshSecurities(c.env, new Date());
		} catch (error) {
			// PHISIX being down leaves the last prices standing rather than failing the read.
			console.warn('PSE prices could not be refreshed', error);
		}
		const { results } = await c.env.DB.prepare('SELECT * FROM securities ORDER BY symbol').all<SecurityRow>();
		return c.json({ securities: results.map(toSecurity) });
	})
	/** A stock's daily closes from `from` on, oldest first. */
	.get('/:symbol/prices', async (c) => {
		const { from = '0000-00-00' } = parseQuery(c, pricesQuerySchema);
		const { results } = await c.env.DB.prepare('SELECT day, close_e4 FROM stock_prices WHERE symbol = ? AND day >= ? ORDER BY day ASC')
			.bind(c.req.param('symbol'), from)
			.all<{ day: string; close_e4: number }>();
		return c.json({ prices: results.map((row) => ({ day: row.day, close: row.close_e4 })) });
	});

async function userTrades(db: D1Database, userId: string): Promise<TradeLike[]> {
	const { results } = await db
		.prepare(
			'SELECT id, account_id, symbol, kind, occurred_on, occurred_time, created_at, shares, price_e4, fees FROM trades WHERE user_id = ?',
		)
		.bind(userId)
		.all<TradeLike & { id: string }>();
	return results;
}

const POSITION_MESSAGE = 'That would sell more shares than are held at that point.';

/** What the cash leg's notes say the trade was: `Buy 1,000 BDO`. */
const legNotes = (kind: TradeKind, shares: number, symbol: string) =>
	`${kind[0]!.toUpperCase()}${kind.slice(1)} ${shares.toLocaleString('en-US')} ${symbol}`;

/** Refuses fees that eat the whole trade: a buy always pays out, a sell and a dividend always bring something in. */
function checkCash(kind: TradeKind, amount: number): void {
	if (amount === 0 || (kind !== 'buy' && amount < 0)) throw badRequest('The fees cannot be as much as the trade.');
}

/**
 * A trade is created with its cash leg, an ordinary transaction in the broker account, and edited and
 * deleted with it: the leg is never edited on its own, so the two cannot disagree.
 */
export const tradeRoutes = new Hono<AppEnv>()
	.use('*', requireAuth)
	.get('/', async (c) => {
		const { results } = await c.env.DB.prepare(
			`${SELECT_TRADE} WHERE tr.user_id = ? ORDER BY tr.occurred_on DESC, tr.occurred_time DESC, tr.created_at DESC`,
		)
			.bind(c.get('userId'))
			.all<TradeRow>();
		return c.json({ trades: results.map(toTrade) });
	})
	.post('/', async (c) => {
		const input = await parseJson(c, tradeCreateSchema);
		const userId = c.get('userId');
		const id = input.id ?? newId('trd');

		if (input.id && (await alreadyOwned(c.env.DB, 'trades', id, userId))) {
			const existing = await c.env.DB.prepare(`${SELECT_TRADE} WHERE tr.id = ? AND tr.user_id = ?`).bind(id, userId).first<TradeRow>();
			return c.json({ trade: toTrade(existing!) });
		}

		const security = await c.env.DB.prepare('SELECT symbol FROM securities WHERE symbol = ?').bind(input.symbol).first();
		if (!security) throw notFound('Stock not found.');

		const { date, time } = splitOccurrence(input.occurredOn);
		const trade: TradeLike = {
			account_id: input.accountId,
			symbol: input.symbol,
			kind: input.kind,
			occurred_on: date,
			occurred_time: time,
			created_at: new Date().toISOString(),
			shares: input.shares,
			price_e4: input.price,
			fees: input.fees,
		};
		const amount = tradeCash(trade);
		checkCash(input.kind, amount);

		if (input.kind === 'sell' && !positions([...(await userTrades(c.env.DB, userId)), trade])) throw conflict(POSITION_MESSAGE);

		const transactionId = input.transactionId ?? newId('txn');
		const notes = legNotes(input.kind, input.shares, input.symbol);
		const [inserted] = await c.env.DB.batch([
			// The account is checked in the statement that writes, as everywhere else.
			c.env.DB.prepare(
				`INSERT INTO transactions (id, user_id, account_id, category_id, amount, occurred_on, occurred_time, payee, notes)
				 SELECT ?, ?, ?, (${SYSTEM_CATEGORY_ID}), ?, ?, ?, ?, ? WHERE EXISTS (SELECT 1 FROM accounts WHERE id = ? AND user_id = ?)`,
			).bind(
				transactionId,
				userId,
				input.accountId,
				userId,
				systemCategoryFor(input.kind),
				amount,
				date,
				time,
				input.symbol,
				notes,
				input.accountId,
				userId,
			),
			c.env.DB.prepare(
				`INSERT INTO trades (id, user_id, account_id, symbol, kind, occurred_on, occurred_time, shares, price_e4, fees, transaction_id)
				 SELECT ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ? WHERE EXISTS (SELECT 1 FROM transactions WHERE id = ? AND user_id = ?)`,
			).bind(
				id,
				userId,
				input.accountId,
				input.symbol,
				input.kind,
				date,
				time,
				input.shares,
				input.price,
				input.fees,
				transactionId,
				transactionId,
				userId,
			),
		]);
		if (!inserted.meta.changes) throw notFound('Account not found.');

		await invalidateSummaries(c.env.CACHE, userId, [monthOf(date), currentMonth()]);

		const row = await c.env.DB.prepare(`${SELECT_TRADE} WHERE tr.id = ? AND tr.user_id = ?`).bind(id, userId).first<TradeRow>();
		return c.json({ trade: toTrade(row!) }, 201);
	})
	/** Puts a trade right, whole: what it is, where, when, and the cash leg worked out again from it. */
	.patch('/:id', async (c) => {
		const id = c.req.param('id');
		const userId = c.get('userId');
		const input = await parseJson(c, tradeUpdateSchema);

		const existing = await c.env.DB.prepare('SELECT occurred_on, created_at, transaction_id FROM trades WHERE id = ? AND user_id = ?')
			.bind(id, userId)
			.first<{ occurred_on: string; created_at: string; transaction_id: string }>();
		if (!existing) throw notFound('Trade not found.');

		const security = await c.env.DB.prepare('SELECT symbol FROM securities WHERE symbol = ?').bind(input.symbol).first();
		if (!security) throw notFound('Stock not found.');

		const { date, time } = splitOccurrence(input.occurredOn);
		const trade: TradeLike = {
			account_id: input.accountId,
			symbol: input.symbol,
			kind: input.kind,
			occurred_on: date,
			occurred_time: time,
			// It keeps its place among trades made at the same moment.
			created_at: existing.created_at,
			shares: input.shares,
			price_e4: input.price,
			fees: input.fees,
		};
		const amount = tradeCash(trade);
		checkCash(input.kind, amount);

		// Any edit can strand a later sell: fewer shares bought, a buy moved after the sell, another account.
		const rest = (await userTrades(c.env.DB, userId)).filter((row) => (row as TradeLike & { id: string }).id !== id);
		if (!positions([...rest, trade])) throw conflict(POSITION_MESSAGE);

		const notes = legNotes(input.kind, input.shares, input.symbol);
		const owned = 'EXISTS (SELECT 1 FROM accounts WHERE id = ? AND user_id = ?)';
		const [moved] = await c.env.DB.batch([
			// The account is checked in the statements that write, as everywhere else.
			c.env.DB.prepare(
				`UPDATE transactions
				 SET account_id = ?, category_id = (${SYSTEM_CATEGORY_ID}), amount = ?, occurred_on = ?, occurred_time = ?, payee = ?, notes = ?,
				     updated_at = ${NOW_SQL}
				 WHERE id = ? AND user_id = ? AND ${owned}`,
			).bind(
				input.accountId,
				userId,
				systemCategoryFor(input.kind),
				amount,
				date,
				time,
				input.symbol,
				notes,
				existing.transaction_id,
				userId,
				input.accountId,
				userId,
			),
			c.env.DB.prepare(
				`UPDATE trades
				 SET account_id = ?, symbol = ?, kind = ?, occurred_on = ?, occurred_time = ?, shares = ?, price_e4 = ?, fees = ?, updated_at = ${NOW_SQL}
				 WHERE id = ? AND user_id = ? AND ${owned}`,
			).bind(
				input.accountId,
				input.symbol,
				input.kind,
				date,
				time,
				input.shares,
				input.price,
				input.fees,
				id,
				userId,
				input.accountId,
				userId,
			),
		]);
		if (!moved.meta.changes) throw notFound('Account not found.');

		await invalidateSummaries(c.env.CACHE, userId, [monthOf(existing.occurred_on), monthOf(date), currentMonth()]);

		const row = await c.env.DB.prepare(`${SELECT_TRADE} WHERE tr.id = ? AND tr.user_id = ?`).bind(id, userId).first<TradeRow>();
		return c.json({ trade: toTrade(row!) });
	})
	.delete('/:id', async (c) => {
		const id = c.req.param('id');
		const userId = c.get('userId');
		const existing = await c.env.DB.prepare('SELECT occurred_on, transaction_id FROM trades WHERE id = ? AND user_id = ?')
			.bind(id, userId)
			.first<{ occurred_on: string; transaction_id: string }>();
		// Already gone is what a replayed delete finds, and it is success.
		if (!existing) return c.body(null, 204);

		const rest = (await userTrades(c.env.DB, userId)).filter((trade) => (trade as TradeLike & { id: string }).id !== id);
		if (!positions(rest)) throw conflict(POSITION_MESSAGE);

		// The trade goes with its cash leg.
		await c.env.DB.prepare('DELETE FROM transactions WHERE id = ? AND user_id = ?').bind(existing.transaction_id, userId).run();
		await invalidateSummaries(c.env.CACHE, userId, [monthOf(existing.occurred_on), currentMonth()]);
		return c.body(null, 204);
	});

/**
 * For the transaction routes: refuses to edit a trade's cash leg, which only the trade decides, and
 * to delete one whose trade a later sell depends on.
 */
export async function guardTradeLeg(db: D1Database, userId: string, transactionId: string, action: 'edit' | 'delete'): Promise<void> {
	const trade = await db
		.prepare('SELECT id FROM trades WHERE transaction_id = ? AND user_id = ?')
		.bind(transactionId, userId)
		.first<{ id: string }>();
	if (!trade) return;
	if (action === 'edit') throw conflict('This is a trade’s cash. Change it from Investments.');
	const rest = (await userTrades(db, userId)).filter((row) => (row as TradeLike & { id: string }).id !== trade.id);
	if (!positions(rest)) throw conflict(POSITION_MESSAGE);
}
