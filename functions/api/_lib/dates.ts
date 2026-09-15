/** `YYYY-MM` */
export const MONTH_PATTERN = /^\d{4}-(0[1-9]|1[0-2])$/;
/** `YYYY-MM-DD` */
export const DATE_PATTERN = /^\d{4}-(0[1-9]|1[0-2])-(0[1-9]|[12]\d|3[01])$/;

export function isMonth(value: string): boolean {
	return MONTH_PATTERN.test(value);
}

export function isDate(value: string): boolean {
	return DATE_PATTERN.test(value);
}

/** The `YYYY-MM` the given instant falls in, in UTC. */
export function currentMonth(now: Date = new Date()): string {
	return now.toISOString().slice(0, 7);
}

/** The `YYYY-MM-DD` the given instant falls on, in UTC. */
export function today(now: Date = new Date()): string {
	return now.toISOString().slice(0, 10);
}

/** Shifts a `YYYY-MM` by a whole number of months. */
export function addMonths(month: string, delta: number): string {
	const [year, monthIndex] = month.split('-').map(Number);
	const total = year * 12 + (monthIndex - 1) + delta;
	const nextYear = Math.floor(total / 12);
	const nextMonth = (total % 12) + 1;
	return `${String(nextYear).padStart(4, '0')}-${String(nextMonth).padStart(2, '0')}`;
}

/**
 * Half-open date range covering a month: `start <= occurred_on < end`.
 * Keeps range scans on the `occurred_on` index instead of forcing a LIKE.
 */
export function monthRange(month: string): { start: string; end: string } {
	return { start: `${month}-01`, end: `${addMonths(month, 1)}-01` };
}

/** The `YYYY-MM` a `YYYY-MM-DD` belongs to. */
export function monthOf(date: string): string {
	return date.slice(0, 7);
}
