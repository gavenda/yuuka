import { describe, expect, it } from 'vitest';
import {
	convertAtRate,
	currencySymbol,
	DEFAULT_CURRENCY,
	formatMoney,
	formatRate,
	parseMoney,
	parseRate,
	percentOf,
	rateBetween,
	toDecimalString,
} from './money';

describe('exchange rates', () => {
	it('converts to whole minor units', () => {
		// Wise: 70 USD received at 1 USD = 63.7796 PHP is 4,464.57 PHP converted.
		expect(convertAtRate(7_000, 63.7796)).toBe(446_457);
		expect(convertAtRate(100, 58.125)).toBe(5813);
	});

	it('works the rate out of two amounts, as what left for each one that arrived', () => {
		expect(rateBetween(446_457, 7_000)).toBeCloseTo(63.7796, 4);
		expect(rateBetween(0, 10_000)).toBeNull();
	});

	it('writes a rate in six significant figures, without trailing zeros or an exponent', () => {
		expect(formatRate(0.017241379)).toBe('0.0172414');
		expect(formatRate(58)).toBe('58');
		expect(formatRate(1.5)).toBe('1.5');
		expect(formatRate(0.0000001234)).toBe('0.0000001234');
		expect(formatRate(0.000000123456789)).toBe('0.000000123457');
	});

	it('reads only a positive number as a rate', () => {
		expect(parseRate(' 0.0172 ')).toBe(0.0172);
		expect(parseRate('1,000.5')).toBe(1000.5);
		expect(parseRate('.5')).toBe(0.5);
		expect(parseRate('0')).toBeNull();
		expect(parseRate('-1')).toBeNull();
		expect(parseRate('abc')).toBeNull();
	});
});

describe('currencySymbol', () => {
	it('writes a known currency with its symbol', () => {
		expect(currencySymbol('USD')).toContain('$');
		expect(currencySymbol('PHP')).toContain('₱');
	});

	it('degrades to the code for one the platform does not know, or a malformed one', () => {
		expect(currencySymbol('XYZ')).toBe('XYZ');
		expect(currencySymbol('x')).toBe('x');
	});
});

describe('parseMoney', () => {
	it('reads whole and fractional amounts as minor units', () => {
		expect(parseMoney('0')).toBe(0);
		expect(parseMoney('1')).toBe(100);
		expect(parseMoney('45.99')).toBe(4599);
		expect(parseMoney('45.9')).toBe(4590);
		expect(parseMoney('.5')).toBe(50);
		expect(parseMoney('1234.56')).toBe(123456);
	});

	it('stays exact where a float multiply would not', () => {
		// 45.99 * 100 is 4598.9999... in binary floating point.
		expect(parseMoney('45.99')).toBe(4599);
		expect(parseMoney('1.10')).toBe(110);
		expect(parseMoney('0.07')).toBe(7);
		expect(parseMoney('8.29')).toBe(829);
		expect(parseMoney('1000000.01')).toBe(100000001);
	});

	it('handles negatives', () => {
		expect(parseMoney('-45.99')).toBe(-4599);
		expect(parseMoney('-0.01')).toBe(-1);
	});

	it('tolerates separators and surrounding space', () => {
		expect(parseMoney(' 1,234.56 ')).toBe(123456);
		expect(parseMoney('1 234.56')).toBe(123456);
	});

	it('rejects anything that is not a number', () => {
		for (const input of ['', '   ', 'abc', '1.2.3', '--1', '1.234', '$5', '1e3', '-', '.']) {
			expect(parseMoney(input), input).toBeNull();
		}
	});
});

describe('toDecimalString', () => {
	it('round-trips through parseMoney', () => {
		for (const minor of [0, 1, 50, 100, 4599, -4599, 123456, -1]) {
			expect(parseMoney(toDecimalString(minor)), String(minor)).toBe(minor);
		}
	});

	it('pads the fractional part to two digits', () => {
		expect(toDecimalString(5)).toBe('0.05');
		expect(toDecimalString(50)).toBe('0.50');
		expect(toDecimalString(100)).toBe('1.00');
		expect(toDecimalString(-5)).toBe('-0.05');
	});
});

describe('formatMoney', () => {
	it('renders minor units as currency', () => {
		expect(formatMoney(123456, 'USD')).toContain('1,234.56');
		expect(formatMoney(0, 'USD')).toContain('0.00');
	});

	it('defaults to PHP', () => {
		expect(DEFAULT_CURRENCY).toBe('PHP');
		expect(formatMoney(123456)).toContain('1,234.56');
		expect(formatMoney(123456)).toBe(formatMoney(123456, 'PHP'));
	});

	it('renders an unknown but well-formed code as the code itself', () => {
		expect(formatMoney(123456, 'ZZZ')).toContain('1,234.56');
		expect(formatMoney(123456, 'ZZZ')).toContain('ZZZ');
	});

	it('falls back instead of throwing on a malformed code', () => {
		// Intl throws a RangeError on these. Every figure on every screen goes
		// through here, so it degrades to the amount plus the code.
		for (const bad of ['', 'US', 'USDD', '12']) {
			expect(() => formatMoney(123456, bad)).not.toThrow();
			expect(formatMoney(123456, bad)).toContain('1,234.56');
		}
	});
});

describe('percentOf', () => {
	it('reports the share of a planned amount', () => {
		expect(percentOf(5000, 10000)).toBe(50);
		expect(percentOf(10000, 10000)).toBe(100);
	});

	it('clamps rather than overflowing its track', () => {
		expect(percentOf(20000, 10000)).toBe(100);
		expect(percentOf(-500, 10000)).toBe(0);
	});

	it('treats spending with no budget as full', () => {
		expect(percentOf(100, 0)).toBe(100);
		expect(percentOf(0, 0)).toBe(0);
	});
});
