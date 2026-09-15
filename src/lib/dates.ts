/** Month/date helpers mirroring the API's `YYYY-MM` and `YYYY-MM-DD` formats. */

export function currentMonth(now: Date = new Date()): string {
	return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}`;
}

export function today(now: Date = new Date()): string {
	return `${currentMonth(now)}-${String(now.getDate()).padStart(2, '0')}`;
}

export function addMonths(month: string, delta: number): string {
	const [year, monthIndex] = month.split('-').map(Number);
	const total = year * 12 + (monthIndex - 1) + delta;
	return `${String(Math.floor(total / 12)).padStart(4, '0')}-${String((total % 12) + 1).padStart(2, '0')}`;
}

/** `2026-09` -> `September 2026`. */
export function formatMonth(month: string): string {
	const [year, monthIndex] = month.split('-').map(Number);
	return new Intl.DateTimeFormat(undefined, { month: 'long', year: 'numeric' }).format(new Date(year, monthIndex - 1, 1));
}

/** `2026-09-15` -> `15 Sep`, rendered from the parts to dodge timezone shifts. */
export function formatDate(date: string): string {
	const [year, month, day] = date.split('-').map(Number);
	return new Intl.DateTimeFormat(undefined, { day: 'numeric', month: 'short' }).format(new Date(year, month - 1, day));
}

export function formatLongDate(date: string): string {
	const [year, month, day] = date.split('-').map(Number);
	return new Intl.DateTimeFormat(undefined, { dateStyle: 'medium' }).format(new Date(year, month - 1, day));
}
