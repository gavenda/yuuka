import { describe, expect, it } from 'vitest';
import { budgetStatus, forMode, nextColor, PALETTE } from './palette';

describe('palette', () => {
	it('ships eight distinct slots in both modes', () => {
		expect(PALETTE).toHaveLength(8);
		expect(new Set(PALETTE.map((slot) => slot.light)).size).toBe(8);
		expect(new Set(PALETTE.map((slot) => slot.dark)).size).toBe(8);
	});

	it('maps a light slot onto its dark step, and leaves custom colours alone', () => {
		expect(forMode('#2a78d6', true)).toBe('#3987e5');
		expect(forMode('#2a78d6', false)).toBe('#2a78d6');
		expect(forMode('#ff00ff', true)).toBe('#ff00ff');
	});

	it('assigns slots in order and wraps rather than inventing a hue', () => {
		expect(nextColor(0)).toBe(PALETTE[0].light);
		expect(nextColor(7)).toBe(PALETTE[7].light);
		expect(nextColor(8)).toBe(PALETTE[0].light);
	});
});

describe('budgetStatus', () => {
	it('escalates as a budget is consumed', () => {
		expect(budgetStatus(0, 10000)).toBe('good');
		expect(budgetStatus(5000, 10000)).toBe('good');
		expect(budgetStatus(8500, 10000)).toBe('warning');
		expect(budgetStatus(10000, 10000)).toBe('warning');
		expect(budgetStatus(10001, 10000)).toBe('critical');
	});

	it('flags spending against no budget, but not an untouched category', () => {
		expect(budgetStatus(500, 0)).toBe('warning');
		expect(budgetStatus(0, 0)).toBe('good');
	});
});
