import { describe, expect, it } from 'vitest';
import type { CategoryBreakdown } from '@/types';
import { overBudget, savingsRate } from './dashboard';

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
