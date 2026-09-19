import { describe, expect, it } from 'vitest';
import { formatCount } from './count';

describe('formatCount', () => {
	it('leaves anything under a thousand as it is', () => {
		expect([0, 1, 12, 999].map(formatCount)).toEqual(['0', '1', '12', '999']);
	});

	it('shortens thousands, millions and billions with k, m and b', () => {
		expect(formatCount(1_000)).toBe('1k');
		expect(formatCount(12_000)).toBe('12k');
		expect(formatCount(1_000_000)).toBe('1m');
		expect(formatCount(3_000_000_000)).toBe('3b');
	});

	it('keeps one decimal and drops a trailing zero', () => {
		expect(formatCount(1_200)).toBe('1.2k');
		expect(formatCount(1_300)).toBe('1.3k');
		expect(formatCount(1_050)).toBe('1k');
		expect(formatCount(3_400_000)).toBe('3.4m');
		expect(formatCount(2_500_000_000)).toBe('2.5b');
	});

	it('truncates instead of rounding up, so it never overstates', () => {
		expect(formatCount(1_999)).toBe('1.9k');
		expect(formatCount(999_999)).toBe('999.9k');
		expect(formatCount(999_999_999)).toBe('999.9m');
	});

	it('reads nonsense as zero', () => {
		expect(formatCount(-5)).toBe('0');
		expect(formatCount(Number.NaN)).toBe('0');
	});
});
