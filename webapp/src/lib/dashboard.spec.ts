import { describe, expect, it } from 'vitest';
import { savingsRate } from './dashboard';

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
