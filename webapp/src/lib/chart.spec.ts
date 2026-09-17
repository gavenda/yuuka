import { describe, expect, it } from 'vitest';
import { BAR_SPEC, chartInk, monthSeries, rankAndFold, SERIES_ONE } from './chart';

describe('monthSeries', () => {
	it('fills every day of the month, zeroing the quiet ones', () => {
		const series = monthSeries('2026-09', [{ date: '2026-09-03', amount: 8450 }]);

		expect(series).toHaveLength(30);
		expect(series[0]).toEqual({ day: 1, date: '2026-09-01', amount: 0 });
		expect(series[2]).toEqual({ day: 3, date: '2026-09-03', amount: 8450 });
	});

	it('knows how long each month is, February included', () => {
		expect(monthSeries('2026-02', [])).toHaveLength(28);
		expect(monthSeries('2024-02', [])).toHaveLength(29);
		expect(monthSeries('2026-01', [])).toHaveLength(31);
		expect(monthSeries('2026-04', [])).toHaveLength(30);
		expect(monthSeries('2026-12', [])).toHaveLength(31);
	});

	it('ignores amounts dated outside the month', () => {
		const series = monthSeries('2026-09', [
			{ date: '2026-08-31', amount: 999 },
			{ date: '2026-10-01', amount: 999 },
		]);

		expect(series.reduce((sum, entry) => sum + entry.amount, 0)).toBe(0);
	});
});

describe('rankAndFold', () => {
	const entry = (name: string, actual: number) => ({ categoryId: name, name, color: '#2a78d6', actual });

	it('ranks by amount, largest first', () => {
		const rows = rankAndFold([entry('a', 100), entry('b', 300), entry('c', 200)], 8);
		expect(rows.map((row) => row.name)).toEqual(['b', 'c', 'a']);
	});

	it('drops entries with nothing spent', () => {
		const rows = rankAndFold([entry('a', 100), entry('zero', 0)], 8);
		expect(rows.map((row) => row.name)).toEqual(['a']);
	});

	it('leaves a list at the cap alone', () => {
		const rows = rankAndFold(
			Array.from({ length: 8 }, (_, i) => entry(`c${i}`, i + 1)),
			8,
		);
		expect(rows).toHaveLength(8);
		expect(rows.some((row) => row.categoryId === '__other__')).toBe(false);
	});

	it('folds the tail into one Other row past the cap', () => {
		// The palette is never cycled, so a ninth entry joins "Other" rather than
		// repeating a colour already on screen.
		const rows = rankAndFold(
			Array.from({ length: 12 }, (_, i) => entry(`c${i}`, (i + 1) * 100)),
			8,
		);

		expect(rows).toHaveLength(8);
		const other = rows.at(-1)!;
		expect(other.categoryId).toBe('__other__');
		expect(other.name).toBe('Other (5)');
		// c0..c4 are the five smallest: 100+200+300+400+500.
		expect(other.actual).toBe(1500);
	});

	it('conserves the total when folding', () => {
		const entries = Array.from({ length: 20 }, (_, i) => entry(`c${i}`, (i + 1) * 7));
		const expected = entries.reduce((sum, e) => sum + e.actual, 0);

		expect(rankAndFold(entries, 8).reduce((sum, row) => sum + row.actual, 0)).toBe(expected);
	});
});

describe('chart chrome', () => {
	it('gives each theme its own recessive grid and tick colours', () => {
		const light = chartInk(false);
		const dark = chartInk(true);

		expect(light.grid).not.toBe(dark.grid);
		expect(light.tick).toBeTruthy();
		expect(dark.tooltipBackground).toBeTruthy();
	});

	it('keeps the single-series hue stepped per theme', () => {
		expect(SERIES_ONE.light).not.toBe(SERIES_ONE.dark);
	});

	it('caps bar thickness and rounds only the data-end', () => {
		expect(BAR_SPEC.maxBarThickness).toBeLessThanOrEqual(24);
		expect(BAR_SPEC.borderRadius).toBe(4);
		// `borderSkipped: false` is what keeps the baseline square.
		expect(BAR_SPEC.borderSkipped).toBe(false);
	});
});
