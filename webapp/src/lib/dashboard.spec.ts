import { describe, expect, it } from 'vitest';
import type { CategoryBreakdown } from '@/types';
import { account } from '@/testing/fixtures';
import {
	accountGroups,
	budgetPace,
	categoryChanges,
	incomeProgress,
	mixedCurrencies,
	overBudget,
	paceHealth,
	savingsRate,
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
