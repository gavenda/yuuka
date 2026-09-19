const UNITS = [
	{ from: 1_000_000_000, suffix: 'b' },
	{ from: 1_000_000, suffix: 'm' },
	{ from: 1_000, suffix: 'k' },
];

/**
 * A count short enough to sit at the end of a row: 950, 1.2k, 3.4m, 2b.
 *
 * It truncates to one decimal rather than rounding, so a count never reads as
 * more than it is (999,999 is 999.9k, not 1000k), and drops a ".0". Whole
 * numbers below a thousand are shown as they are.
 */
export function formatCount(count: number): string {
	const whole = Number.isFinite(count) ? Math.max(0, Math.trunc(count)) : 0;
	const unit = UNITS.find((candidate) => whole >= candidate.from);
	if (!unit) return String(whole);

	// Integer tenths, so 1,300 is exactly 13 and never 12.999…
	const tenths = Math.floor(whole / (unit.from / 10));
	const fraction = tenths % 10;
	return `${Math.floor(tenths / 10)}${fraction ? `.${fraction}` : ''}${unit.suffix}`;
}
