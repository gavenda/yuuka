import { Hono } from 'hono';
import { readSummary, writeSummary } from '../cache';
import { currentMonth, monthRange } from '../dates';
import { parseQuery } from '../validate';
import { requireAuth } from '../middleware/auth';
import { toAccount, type AccountRow, type CategoryKind, type CategoryRow, type CategoryScope } from '../mappers';
import { monthQuerySchema } from '../schemas';
import type { AppEnv } from '../types';

interface TotalsRow {
	income: number;
	expenses: number;
}

interface CategoryTotalRow {
	category_id: string;
	total: number;
}

interface BudgetAmountRow {
	category_id: string;
	amount: number;
}

interface DailyRow {
	date: string;
	spent: number;
}

export interface SubcategoryBreakdown {
	categoryId: string;
	name: string;
	color: string;
	/** This subcategory's own activity, already counted in the parent's `actual`. */
	actual: number;
}

export interface CategoryBreakdown {
	categoryId: string;
	name: string;
	kind: CategoryKind;
	color: string;
	appliesTo: CategoryScope;
	/** Planned amount for the month, as a positive number. Budgets live on parents only. */
	planned: number;
	/** What actually happened, including everything filed under this category's children. */
	actual: number;
	/** Planned minus actual; negative means over budget. */
	remaining: number;
	children: SubcategoryBreakdown[];
}

export const summaryRoutes = new Hono<AppEnv>().use('*', requireAuth).get('/', async (c) => {
	const { month = currentMonth() } = parseQuery(c, monthQuerySchema);
	const userId = c.get('userId');

	// Cache entries are per user; see `summaryKey`.
	const cached = await readSummary(c.env.CACHE, userId, month);
	if (cached) return c.json({ ...cached, cached: true });

	const { start, end } = monthRange(month);

	// Income and spending exclude transfers: moving money between your own
	// accounts is neither. Transfers are measured separately, below.
	const [accounts, totals, categories, categoryTotals, budgets, daily] = await c.env.DB.batch([
		c.env.DB.prepare(
			`SELECT a.id, a.name, a.type_id, ty.name AS type_name, a.currency, a.logo_url, a.starting_balance, a.archived,
			        a.created_at, a.updated_at,
			        a.starting_balance + COALESCE(SUM(t.amount), 0) AS balance
			 FROM accounts a
			 JOIN account_types ty ON ty.id = a.type_id
			 LEFT JOIN transactions t ON t.account_id = a.id AND t.user_id = a.user_id
			 WHERE a.user_id = ?
			 GROUP BY a.id
			 ORDER BY a.archived ASC, a.name COLLATE NOCASE ASC`,
		).bind(userId),
		c.env.DB.prepare(
			`SELECT COALESCE(SUM(CASE WHEN amount > 0 THEN amount END), 0) AS income,
			        COALESCE(-SUM(CASE WHEN amount < 0 THEN amount END), 0) AS expenses
			 FROM transactions
			 WHERE user_id = ? AND transfer_id IS NULL AND occurred_on >= ? AND occurred_on < ?`,
		).bind(userId, start, end),
		c.env.DB.prepare(
			'SELECT * FROM categories WHERE user_id = ? AND archived = 0 ORDER BY kind ASC, sort_order ASC, name COLLATE NOCASE ASC',
		).bind(userId),
		// Standard categories measure their signed activity; transfer categories
		// measure the outflow leg only. A transfer writes a matching pair, so
		// summing both sides would always come to zero.
		c.env.DB.prepare(
			`SELECT t.category_id,
			        SUM(CASE WHEN c.applies_to = 'transfer'
			                 THEN (CASE WHEN t.amount < 0 THEN -t.amount ELSE 0 END)
			                 ELSE t.amount END) AS total
			 FROM transactions t
			 JOIN categories c ON c.id = t.category_id AND c.user_id = t.user_id
			 WHERE t.user_id = ? AND t.occurred_on >= ? AND t.occurred_on < ?
			   AND ((c.applies_to = 'standard' AND t.transfer_id IS NULL)
			     OR (c.applies_to = 'transfer' AND t.transfer_id IS NOT NULL))
			 GROUP BY t.category_id`,
		).bind(userId, start, end),
		c.env.DB.prepare('SELECT category_id, amount FROM budgets WHERE user_id = ? AND month = ?').bind(userId, month),
		c.env.DB.prepare(
			`SELECT occurred_on AS date, -SUM(amount) AS spent
			 FROM transactions
			 WHERE user_id = ? AND transfer_id IS NULL AND amount < 0 AND occurred_on >= ? AND occurred_on < ?
			 GROUP BY occurred_on
			 ORDER BY occurred_on ASC`,
		).bind(userId, start, end),
	]);

	const accountRows = accounts.results as AccountRow[];
	const categoryRows = categories.results as CategoryRow[];
	const monthTotals = (totals.results as TotalsRow[])[0] ?? { income: 0, expenses: 0 };

	const signedByCategory = new Map((categoryTotals.results as CategoryTotalRow[]).map((row) => [row.category_id, row.total]));
	const plannedByCategory = new Map((budgets.results as BudgetAmountRow[]).map((row) => [row.category_id, row.amount]));

	/**
	 * Reports every category as a positive magnitude in its own natural
	 * direction, so "planned vs actual" compares like with like: expenses are
	 * stored negative, income positive, and transfer totals already arrive as
	 * the outflow magnitude.
	 */
	function ownActual(category: CategoryRow): number {
		const signed = signedByCategory.get(category.id) ?? 0;
		if (category.applies_to === 'transfer') return signed;
		return category.kind === 'expense' ? -signed : signed;
	}

	const parents = categoryRows.filter((category) => category.parent_id === null);
	const childrenByParent = new Map<string, CategoryRow[]>();

	for (const category of categoryRows) {
		if (!category.parent_id) continue;
		const siblings = childrenByParent.get(category.parent_id);
		if (siblings) siblings.push(category);
		else childrenByParent.set(category.parent_id, [category]);
	}

	// Budgets are set on parents, so a child's activity counts towards its
	// parent's plan. Only parents appear at the top level, which also means
	// summing this list cannot double-count a subcategory.
	const breakdown: CategoryBreakdown[] = parents.map((parent) => {
		const children = (childrenByParent.get(parent.id) ?? []).map((child) => ({
			categoryId: child.id,
			name: child.name,
			color: child.color,
			actual: ownActual(child),
		}));

		const actual = ownActual(parent) + children.reduce((sum, child) => sum + child.actual, 0);
		const planned = plannedByCategory.get(parent.id) ?? 0;

		return {
			categoryId: parent.id,
			name: parent.name,
			kind: parent.kind,
			color: parent.color,
			appliesTo: parent.applies_to,
			planned,
			actual,
			remaining: planned - actual,
			children: children.sort((a, b) => b.actual - a.actual),
		};
	});

	const payload = {
		month,
		income: monthTotals.income,
		expenses: monthTotals.expenses,
		net: monthTotals.income - monthTotals.expenses,
		netWorth: accountRows.reduce((sum, row) => sum + (row.balance ?? row.starting_balance), 0),
		/** Money moved into transfer-categorised destinations, e.g. investments. */
		cashflow: breakdown.filter((entry) => entry.appliesTo === 'transfer').reduce((sum, entry) => sum + entry.actual, 0),
		totalBudgeted: breakdown
			.filter((entry) => entry.appliesTo === 'standard' && entry.kind === 'expense')
			.reduce((sum, entry) => sum + entry.planned, 0),
		accounts: accountRows.map(toAccount),
		categories: breakdown,
		dailySpend: (daily.results as DailyRow[]).map((row) => ({ date: row.date, amount: row.spent })),
		generatedAt: new Date().toISOString(),
	};

	// Best-effort cache: a failed write should never fail the request.
	c.executionCtx.waitUntil(writeSummary(c.env.CACHE, userId, month, payload).catch(() => {}));

	return c.json({ ...payload, cached: false });
});
