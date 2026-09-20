/** `YYYY-MM` */
export const MONTH_PATTERN = /^\d{4}-(0[1-9]|1[0-2])$/;
/** `YYYY-MM-DD` */
export const DATE_PATTERN = /^\d{4}-(0[1-9]|1[0-2])-(0[1-9]|[12]\d|3[01])$/;
/**
 * `YYYY-MM-DD`, optionally with a `THH:MM` time of day. A bare date sorts and
 * range-filters as if it were midnight, since it is a string prefix of any
 * timed value on the same day.
 */
export const DATE_TIME_PATTERN = /^\d{4}-(0[1-9]|1[0-2])-(0[1-9]|[12]\d|3[01])(T([01]\d|2[0-3]):[0-5]\d)?$/;

/**
 * Splits the single `occurredOn` field the API speaks into the two columns the
 * ledger stores. A row that names no time of day keeps NULL rather than a
 * stand-in midnight, so "no time" and "midnight" stay different things.
 */
export function splitOccurrence(value: string): { date: string; time: string | null } {
	return { date: value.slice(0, 10), time: value.length > 10 ? value.slice(11, 16) : null };
}

/** Rejoins them, so what goes out is shaped exactly as what came in. */
export function joinOccurrence(date: string, time: string | null): string {
	return time ? `${date}T${time}` : date;
}

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

/**
 * True when a `YYYY-MM-DD` names a day that exists. `DATE_PATTERN` only checks
 * the shape, so it lets `2026-02-31` through.
 */
export function isRealDate(date: string): boolean {
	if (!DATE_PATTERN.test(date)) return false;
	const parsed = new Date(`${date}T00:00:00Z`);
	return !Number.isNaN(parsed.getTime()) && parsed.toISOString().slice(0, 10) === date;
}

/** How many days a `YYYY-MM` has. */
export function daysInMonth(month: string): number {
	const [year, monthIndex] = month.split('-').map(Number);
	return new Date(Date.UTC(year, monthIndex, 0)).getUTCDate();
}

/** The date `day` falls on in a month, clamped to the month's last day (31 in February is the 28th or 29th). */
export function dateInMonth(month: string, day: number): string {
	return `${month}-${String(Math.min(day, daysInMonth(month))).padStart(2, '0')}`;
}

/** The day of the month a `YYYY-MM-DD` falls on. */
export function dayOf(date: string): number {
	return Number(date.slice(8, 10));
}

/** The occurrence of a monthly schedule anchored on `day` that follows `after`'s month. */
export function nextOccurrence(day: number, after: string): string {
	return dateInMonth(addMonths(monthOf(after), 1), day);
}

/** The first occurrence of a monthly schedule anchored on `day` that is `from` or later. */
export function firstOccurrenceOnOrAfter(day: number, from: string): string {
	const thisMonth = dateInMonth(monthOf(from), day);
	return thisMonth >= from ? thisMonth : dateInMonth(addMonths(monthOf(from), 1), day);
}
