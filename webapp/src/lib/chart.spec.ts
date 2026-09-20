import { describe, expect, it } from 'vitest';
import { axisScale, compactAmount, dailyAverage, monthSeries, rankAndFold, scallopPath } from './chart';

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

describe('dailyAverage', () => {
	it('leaves out days with nothing spent', () => {
		expect(dailyAverage([{ amount: 2000 }, { amount: 0 }, { amount: 4000 }, { amount: 0 }])).toBe(3000);
	});

	it('is null when nothing was spent', () => {
		expect(dailyAverage([{ amount: 0 }, { amount: 0 }])).toBeNull();
		expect(dailyAverage([])).toBeNull();
	});
});

describe('axisScale', () => {
	it('reaches a round number above the tallest bar', () => {
		expect(axisScale(4599)).toEqual({ top: 6000, step: 2000, ticks: [0, 2000, 4000, 6000] });
	});

	it('never falls below the tallest bar and stays within a few steps', () => {
		for (const max of [1, 99, 100, 4599, 12_345, 250_000, 9_999_999, 123_456_789]) {
			const scale = axisScale(max);

			expect(scale.top).toBeGreaterThanOrEqual(max);
			expect(scale.top % scale.step).toBe(0);
			expect(scale.ticks.length).toBeGreaterThanOrEqual(2);
			expect(scale.ticks.length).toBeLessThanOrEqual(5);
			expect(scale.ticks.at(-1)).toBe(scale.top);
		}
	});

	it('still has a usable axis for an empty chart', () => {
		const scale = axisScale(0);

		expect(scale.top).toBeGreaterThan(0);
		expect(scale.step).toBeGreaterThan(0);
	});
});

describe('compactAmount', () => {
	it('shortens the figure for the axis', () => {
		expect(compactAmount(0, 'en-US')).toBe('0');
		expect(compactAmount(250, 'en-US')).toBe('2.5');
		expect(compactAmount(1_500_000, 'en-US')).toBe('15k');
		expect(compactAmount(150_000, 'en-US')).toBe('1.5k');
		expect(compactAmount(230_000_000, 'en-US')).toBe('2.3M');
	});
});

describe('scallopPath', () => {
	it('draws one closed outline that stays within its radius', () => {
		const path = scallopPath(50, 50, 10);
		const points = [...path.matchAll(/(-?\d+\.\d+) (-?\d+\.\d+)/g)].map((match) => [Number(match[1]), Number(match[2])]);

		expect(path.startsWith('M')).toBe(true);
		expect(path.endsWith('Z')).toBe(true);
		expect(points).toHaveLength(12 * 10);
		for (const [x, y] of points) {
			expect(Math.hypot(x - 50, y - 50)).toBeLessThanOrEqual(10.01);
			expect(Math.hypot(x - 50, y - 50)).toBeGreaterThan(8);
		}
	});
});
