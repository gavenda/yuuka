import { describe, expect, it } from 'vitest';
import type { CategoryBreakdown } from '@/types';
import { account, subscription } from '@/testing/fixtures';
import { holdingOf } from '@/lib/investments';
import {
	aheadOfPace,
	accountGroups,
	budgetPace,
	categoryChanges,
	incomeProgress,
	investmentSummary,
	mixedCurrencies,
	overBudget,
	paceHealth,
	safeToSpend,
	savingsRate,
	stillToPost,
} from './dashboard';

const entry = (categoryId: string, extra: Partial<CategoryBreakdown>): CategoryBreakdown => ({
	categoryId,
	name: categoryId,
	kind: 'expense',
	color: '#2a78d6',
	planned: 0,
	plannedPercent: null,
	actual: 0,
	remaining: 0,
	children: [],
	...extra,
});

describe('savingsRate', () => {
	it('is the share of income the month ended with', () => {
		expect(savingsRate(2500000, 10000000)).toBe(25);
		expect(savingsRate(-1000000, 10000000)).toBe(-10);
	});

	it('has nothing to say without income', () => {
		expect(savingsRate(0, 0)).toBeNull();
		expect(savingsRate(-500, 0)).toBeNull();
	});
});

describe('overBudget', () => {
	it('lists the planned expense categories past their plan, the furthest over first', () => {
		const list = overBudget([
			entry('food', { planned: 1000, actual: 1200, remaining: -200 }),
			entry('fun', { planned: 1000, actual: 1500, remaining: -500 }),
			entry('rent', { planned: 1000, actual: 900, remaining: 100 }),
		]);

		expect(list.map((row) => row.categoryId)).toEqual(['fun', 'food']);
	});

	it('leaves out what had no plan, and what is not spending', () => {
		const list = overBudget([
			entry('unplanned', { planned: 0, actual: 500, remaining: -500 }),
			entry('salary', { kind: 'income', planned: 1000, actual: 1200, remaining: -200 }),
			entry('savings', { kind: 'transfer', planned: 1000, actual: 1200, remaining: -200 }),
		]);

		expect(list).toEqual([]);
	});
});

describe('budgetPace', () => {
	const plan = [
		entry('food', { planned: 6000, actual: 3000 }),
		entry('rent', { planned: 4000, actual: 4000 }),
		entry('salary', { kind: 'income', planned: 9999, actual: 9999 }),
	];

	it('totals the expense categories as the Budget screen does', () => {
		expect(budgetPace(plan, '2026-10', '2026-10-16')).toEqual({ spent: 7000, planned: 10000, percent: 70, day: 16, days: 31 });
	});

	it('names the day only while the month is under way', () => {
		expect(budgetPace(plan, '2026-09', '2026-10-16')?.day).toBeNull();
		expect(budgetPace(plan, '2026-11', '2026-10-16')?.day).toBeNull();
	});

	it('has nothing to say with nothing planned', () => {
		expect(budgetPace([entry('food', { actual: 3000 })], '2026-10', '2026-10-16')).toBeNull();
	});

	it('is ahead of pace when more of the plan is gone than of the month, and critical past the plan', () => {
		expect(paceHealth({ spent: 7000, planned: 10000, percent: 70, day: 16, days: 31 })).toBe('warning');
		expect(paceHealth({ spent: 4000, planned: 10000, percent: 40, day: 16, days: 31 })).toBe('good');
		expect(paceHealth({ spent: 7000, planned: 10000, percent: 70, day: null, days: 30 })).toBe('good');
		expect(paceHealth({ spent: 10001, planned: 10000, percent: 100, day: null, days: 30 })).toBe('critical');
	});
});

describe('incomeProgress', () => {
	it('is unclamped, and absent without a plan', () => {
		expect(incomeProgress(12000, 10000)).toBe(120);
		expect(incomeProgress(5000, 0)).toBeNull();
	});
});

describe('accountGroups', () => {
	it('groups the accounts in use under their types, the largest balance first', () => {
		const groups = accountGroups([
			account('a', { typeName: 'Savings', balance: 100 }),
			account('b', { typeName: 'checking', balance: 50 }),
			account('c', { typeName: 'Savings', balance: 900 }),
			account('d', { typeName: 'Savings', balance: 5000, archived: true }),
		]);

		expect(groups.map((group) => [group.type, group.accounts.map((row) => row.id)])).toEqual([
			['checking', ['b']],
			['Savings', ['c', 'a']],
		]);
	});
});

describe('mixedCurrencies', () => {
	it('notices a second currency among the accounts in use only', () => {
		expect(mixedCurrencies([account('a', { currency: 'PHP' }), account('b', { currency: 'php' })])).toBe(false);
		expect(mixedCurrencies([account('a', { currency: 'PHP' }), account('b', { currency: 'USD' })])).toBe(true);
		expect(mixedCurrencies([account('a', { currency: 'PHP' }), account('b', { currency: 'USD', archived: true })])).toBe(false);
	});
});

describe('categoryChanges', () => {
	it('is how far each category moved from the other month, from nothing for one it never had', () => {
		const changes = categoryChanges(
			[entry('food', { actual: 1200 }), entry('fun', { actual: 300 }), entry('new', { actual: 50 })],
			[entry('food', { actual: 1000 }), entry('fun', { actual: 500 }), entry('gone', { actual: 999 })],
		);

		expect(Object.fromEntries(changes)).toEqual({ food: 200, fun: -200, new: 50 });
	});
});

describe('stillToPost', () => {
	it('lists the active subscriptions next running in the month, the soonest first', () => {
		const list = stillToPost(
			[
				subscription('late', { nextRunOn: '2026-10-28' }),
				subscription('soon', { nextRunOn: '2026-10-20' }),
				subscription('paused', { nextRunOn: '2026-10-21', enabled: false }),
				subscription('next', { nextRunOn: '2026-11-02' }),
			],
			'2026-10',
		);

		expect(list.map((row) => row.id)).toEqual(['soon', 'late']);
	});
});

describe('safeToSpend', () => {
	it('shares what is left of the plan over the days left, today included', () => {
		// Day 20 of 31 leaves 12 days, today among them; 10,000 of 22,000 spent leaves 12,000.
		expect(safeToSpend({ spent: 10_000, planned: 22_000, percent: 45, day: 20, days: 31 })).toEqual({
			perDay: 1000,
			left: 12_000,
			daysLeft: 12,
		});
		// Rounds down, so keeping to it never runs past the plan.
		expect(safeToSpend({ spent: 0, planned: 1000, percent: 0, day: 28, days: 30 })).toEqual({ perDay: 333, left: 1000, daysLeft: 3 });
	});

	it('has nothing to say past the plan or outside the month', () => {
		expect(safeToSpend({ spent: 1000, planned: 1000, percent: 100, day: 10, days: 30 })).toBeNull();
		expect(safeToSpend({ spent: 500, planned: 1000, percent: 50, day: null, days: 30 })).toBeNull();
		expect(safeToSpend(null)).toBeNull();
	});
});

describe('aheadOfPace', () => {
	it('lists plans spent faster than the month is passing, the furthest first', () => {
		// Day 10 of 31 is about a third of the month gone.
		const list = aheadOfPace(
			[
				entry('food', { planned: 1000, actual: 500, remaining: 500 }),
				entry('fun', { planned: 1000, actual: 900, remaining: 100 }),
				entry('rent', { planned: 1000, actual: 200, remaining: 800 }),
				entry('over', { planned: 1000, actual: 1200, remaining: -200 }),
				entry('unplanned', { actual: 500, remaining: -500 }),
				entry('salary', { planned: 1000, actual: 900, remaining: 100, kind: 'income' }),
			],
			'2026-10',
			'2026-10-10',
		);

		expect(list.map((row) => row.categoryId)).toEqual(['fun', 'food']);
		expect(aheadOfPace([entry('food', { planned: 1000, actual: 900 })], '2026-09', '2026-10-10')).toEqual([]);
	});
});

describe('investmentSummary', () => {
	const holding = (symbol: string, accountId: string, shares: number, costBasis: number, priceE4: number, asOf: string | null) =>
		holdingOf(
			{ accountId, symbol, shares, costBasis, realised: 0, dividends: 0 },
			{ name: symbol, accountName: accountId, priceE4, changeBp: null, asOf },
		);

	it('adds the held positions and names the largest stocks', () => {
		const summary = investmentSummary(
			[
				holding('BDO', 'acc-a', 100, 1_400_000, 1_500_000, '2026-10-30'),
				holding('BDO', 'acc-b', 100, 1_450_000, 1_500_000, '2026-10-30'),
				holding('ALI', 'acc-a', 1000, 3_000_000, 280_000, '2026-10-31'),
				holding('JFC', 'acc-a', 10, 250_000, 2_000_000, null),
				holding('SM', 'acc-a', 0, 0, 9_000_000, '2026-10-31'),
			],
			2,
		);

		// ₱150 × 200 BDO, ₱28 × 1,000 ALI and ₱200 × 10 JFC.
		expect(summary?.marketValue).toBe(3_000_000 + 2_800_000 + 200_000);
		expect(summary?.costBasis).toBe(6_100_000);
		expect(summary?.unrealised).toBe(-100_000);
		expect(summary?.asOf).toBe('2026-10-31');
		expect(summary?.largest.map((stock) => [stock.symbol, stock.marketValue])).toEqual([
			['BDO', 3_000_000],
			['ALI', 2_800_000],
		]);
	});

	it('has nothing to say with nothing held', () => {
		expect(investmentSummary([])).toBeNull();
		expect(investmentSummary([holding('SM', 'acc', 0, 0, 9_000_000, null)])).toBeNull();
	});
});
