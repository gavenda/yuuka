import type { Subscription } from '@/types';

/** `1` -> `1st`, `22` -> `22nd`, `13` -> `13th`. */
export function ordinal(day: number): string {
	const teens = day % 100;
	if (teens >= 11 && teens <= 13) return `${day}th`;

	switch (day % 10) {
		case 1:
			return `${day}st`;
		case 2:
			return `${day}nd`;
		case 3:
			return `${day}rd`;
		default:
			return `${day}th`;
	}
}

/** How often a subscription posts, as a sentence fragment: `Monthly on the 15th`. */
export function scheduleLabel(dayOfMonth: number): string {
	return `Monthly on the ${ordinal(dayOfMonth)}`;
}

/**
 * Today's date in UTC. Subscriptions run at 00:00 UTC, so the API measures "not in
 * the past" against this rather than the browser's own calendar day.
 */
export function utcToday(now: Date = new Date()): string {
	return now.toISOString().slice(0, 10);
}

/** The day after `date`, as `YYYY-MM-DD`. Steps in UTC so a daylight-saving change cannot skip or repeat a day. */
export function nextDay(date: string): string {
	const [year, month, day] = date.split('-').map(Number);
	return new Date(Date.UTC(year, month - 1, day + 1)).toISOString().slice(0, 10);
}

/**
 * What the active subscriptions come to in a month, signed like the amounts themselves: a net
 * outflow is negative. Paused ones post nothing, so they are left out. Like every aggregate figure
 * it is added up as it stands and shown in the display currency; amounts are not converted between
 * currencies.
 */
export function monthlyTotal(subscriptions: readonly Pick<Subscription, 'amount' | 'enabled'>[]): number {
	return subscriptions.reduce((total, subscription) => (subscription.enabled ? total + subscription.amount : total), 0);
}
