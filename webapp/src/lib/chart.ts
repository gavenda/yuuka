import { BarController, BarElement, CategoryScale, Chart, LinearScale, Tooltip } from 'chart.js';

/**
 * Registered once, and only the pieces these charts actually draw — Chart.js is
 * tree-shakeable, so importing from `chart.js/auto` would pull in the line, pie
 * and radar controllers nobody renders.
 */
Chart.register(BarController, BarElement, CategoryScale, LinearScale, Tooltip);

export { Chart };
export type { ChartConfiguration } from 'chart.js';

/**
 * Chart chrome for each theme, from the Material 3 roles in `src/style.css`
 * (a canvas cannot read CSS variables, so the values are repeated here): the
 * grid is `outline-variant` and the ticks `outline`, so both stay recessive and
 * the data is the only loud thing. The tooltip is an inverse surface.
 */
export function chartInk(dark: boolean) {
	return {
		grid: dark ? '#43474e' : '#c4c6cf',
		tick: dark ? '#8e9099' : '#74777f',
		tooltipBackground: dark ? '#e3e2e6' : '#2f3033',
		tooltipText: dark ? '#2f3033' : '#f1f0f4',
		tooltipMuted: dark ? '#43474e' : '#c4c6cf',
	};
}

/** The single hue used when a chart plots one series, per theme: the theme's `primary`. */
export const SERIES_ONE = { light: '#005eb4', dark: '#a8c8ff' } as const;

/** Mark specs shared by every bar chart here. */
export const BAR_SPEC = {
	/** Cap the thickness; the band's leftover is air, not fill. */
	maxBarThickness: 24,
	/** Rounded data-end, square at the baseline. */
	borderRadius: 4,
	borderSkipped: false as const,
};

/** One entry per day of the month, including the days with no spending. */
export function monthSeries(month: string, days: { date: string; amount: number }[]) {
	const [year, monthIndex] = month.split('-').map(Number);
	const dayCount = new Date(year, monthIndex, 0).getDate();
	const byDate = new Map(days.map((entry) => [entry.date, entry.amount]));

	return Array.from({ length: dayCount }, (_, index) => {
		const day = index + 1;
		const date = `${month}-${String(day).padStart(2, '0')}`;
		return { day, date, amount: byDate.get(date) ?? 0 };
	});
}

export interface RankableEntry {
	categoryId: string;
	name: string;
	color: string;
	actual: number;
}

/**
 * Ranks entries by amount and folds the tail into a single "Other" row.
 *
 * The cap exists because the categorical palette has a fixed number of slots
 * and is never cycled: a ninth colour would repeat one already on screen, so
 * the ninth entry becomes part of "Other" instead.
 */
export function rankAndFold<T extends RankableEntry>(entries: T[], limit: number): (T | RankableEntry)[] {
	const ranked = [...entries].filter((entry) => entry.actual > 0).sort((a, b) => b.actual - a.actual);
	if (ranked.length <= limit) return ranked;

	const tail = ranked.slice(limit - 1);

	return [
		...ranked.slice(0, limit - 1),
		{
			categoryId: '__other__',
			name: `Other (${tail.length})`,
			color: '#898781',
			actual: tail.reduce((sum, entry) => sum + entry.actual, 0),
		},
	];
}
