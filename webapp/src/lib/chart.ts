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

/** The average spent on a day that had any spending, or null when no day did. Zero days would only drag it down. */
export function dailyAverage(days: { amount: number }[]): number | null {
	const spent = days.filter((entry) => entry.amount > 0);
	if (spent.length === 0) return null;
	return Math.floor(spent.reduce((sum, entry) => sum + entry.amount, 0) / spent.length);
}

/** A value axis reaching `top` in steps of `step`, both in minor units, with every labelled value from zero to `top`. */
export interface AxisScale {
	top: number;
	step: number;
	ticks: number[];
}

const AXIS_INTERVALS = 3;
const NICE_STEPS = [1, 2, 2.5, 5, 10];

/**
 * An axis for a chart whose tallest bar is `max`, with about three intervals: the smallest round number that
 * clears the bar, so the axis reads 0, 5k, 10k rather than 0, 4.6k, 9.2k. Mirrors `Charts.kt`.
 */
export function axisScale(max: number): AxisScale {
	if (max <= 0) return { top: 100, step: 100, ticks: [0, 100] };

	const raw = max / AXIS_INTERVALS;
	const magnitude = 10 ** Math.floor(Math.log10(raw));
	const step = Math.max(1, Math.floor(NICE_STEPS.map((factor) => factor * magnitude).find((candidate) => candidate >= raw) ?? raw));
	const top = Math.ceil(max / step) * step;

	return { top, step, ticks: Array.from({ length: top / step + 1 }, (_, index) => index * step) };
}

/**
 * An axis label for `minor`, short enough for the narrow strip beside a chart: 1500000 is `15k`. The currency is
 * left out, since the chart says it once elsewhere.
 */
export function compactAmount(minor: number, locale?: string): string {
	const format = new Intl.NumberFormat(locale, { maximumFractionDigits: 1, minimumFractionDigits: 0 });
	const major = minor / 100;

	if (major >= 1_000_000) return `${format.format(major / 1_000_000)}M`;
	if (major >= 1_000) return `${format.format(major / 1_000)}k`;
	return format.format(major);
}

/**
 * The outline of a scalloped disc — the badge's shape — as an SVG path: `lobes` soft bumps around a circle of
 * `radius`, dipping by `depth` (a fraction of the radius) between them. A stand-in for Material's expressive
 * "cookie" shapes, which have no web counterpart.
 */
export function scallopPath(cx: number, cy: number, radius: number, lobes = 12, depth = 0.14, stepsPerLobe = 10): string {
	const steps = lobes * stepsPerLobe;

	const points = Array.from({ length: steps }, (_, index) => {
		const angle = (index / steps) * Math.PI * 2;
		const reach = radius * (1 - (depth * (1 - Math.cos(lobes * angle))) / 2);
		return `${(cx + reach * Math.cos(angle)).toFixed(2)} ${(cy + reach * Math.sin(angle)).toFixed(2)}`;
	});

	return `M${points.join('L')}Z`;
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
