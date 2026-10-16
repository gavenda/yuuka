import { Hono } from 'hono';
import { addMonths, currentMonth } from '../dates';
import { parseQuery } from '../validate';
import { requireAuth } from '../middleware/auth';
import { trendQuerySchema } from '../schemas';
import type { AppEnv } from '../types';

interface MonthTotalsRow {
	month: string;
	income: number;
	expenses: number;
}

interface MonthMovementRow {
	month: string;
	moved: number;
}

export interface TrendMonth {
	month: string;
	income: number;
	expenses: number;
	net: number;
	/** What every account added up to at the end of the month, worked back from today's balances. */
	netWorth: number;
}

/**
 * Income, spending and net worth month by month, oldest first, ending at `month`. It is its own route
 * rather than part of `/api/summary` because it spans months: the summary is cached per month and purged
 * only for the months a write touches, so a back-dated transaction would leave a later month's trend
 * stale. This is not cached at all — two grouped scans of one user's rows are cheap.
 *
 * Income and spending are counted as the summary counts them, transfers left out. Net worth at a month's
 * end is today's figure less everything dated after it, which treats every account as always having held
 * its starting balance: an account opened since counts from the start. That keeps the last point equal to
 * the summary's net worth for the current month, which is the figure people check it against.
 */
export const trendRoutes = new Hono<AppEnv>().use('*', requireAuth).get('/', async (c) => {
	const { month = currentMonth(), months: count } = parseQuery(c, trendQuerySchema);
	const userId = c.get('userId');

	const first = addMonths(month, -(count - 1));
	const start = `${first}-01`;
	const end = `${addMonths(month, 1)}-01`;

	const [totals, movement, worth] = await c.env.DB.batch([
		c.env.DB.prepare(
			`SELECT substr(occurred_on, 1, 7) AS month,
			        COALESCE(SUM(CASE WHEN amount > 0 THEN amount END), 0) AS income,
			        COALESCE(-SUM(CASE WHEN amount < 0 THEN amount END), 0) AS expenses
			 FROM transactions
			 WHERE user_id = ? AND transfer_id IS NULL AND occurred_on >= ? AND occurred_on < ?
			 GROUP BY month`,
		).bind(userId, start, end),
		// Everything that moved a balance from the first month on, the months after `month` included:
		// net worth at a month's end is today's less all of it dated later.
		c.env.DB.prepare(
			`SELECT substr(occurred_on, 1, 7) AS month, SUM(amount) AS moved
			 FROM transactions
			 WHERE user_id = ? AND occurred_on >= ?
			 GROUP BY month`,
		).bind(userId, start),
		c.env.DB.prepare(
			`SELECT COALESCE((SELECT SUM(starting_balance) FROM accounts WHERE user_id = ?1), 0)
			      + COALESCE((SELECT SUM(amount) FROM transactions WHERE user_id = ?1), 0) AS total`,
		).bind(userId),
	]);

	const totalsByMonth = new Map((totals.results as MonthTotalsRow[]).map((row) => [row.month, row]));
	const movementRows = movement.results as MonthMovementRow[];
	const now = (worth.results as { total: number }[])[0]?.total ?? 0;

	const trend: TrendMonth[] = Array.from({ length: count }, (_, index) => {
		const key = addMonths(first, index);
		const row = totalsByMonth.get(key);
		const income = row?.income ?? 0;
		const expenses = row?.expenses ?? 0;
		const later = movementRows.filter((entry) => entry.month > key).reduce((sum, entry) => sum + entry.moved, 0);
		return { month: key, income, expenses, net: income - expenses, netWorth: now - later };
	});

	return c.json({ month, months: trend });
});
