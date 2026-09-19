/**
 * Money is handled as integer minor units (cents) everywhere, so arithmetic is
 * exact. Conversion to and from a decimal string happens only at the edges.
 */

/** The currency used when none is known. */
export const DEFAULT_CURRENCY = 'PHP';

/**
 * Formats minor units as a localised currency string, e.g. `-4599` -> `-₱45.99`.
 *
 * `Intl.NumberFormat` throws on a malformed currency code, and this function is
 * called for every figure on every screen — so a bad value, from a stale cache
 * or an unexpected response, falls back to the amount plus the code rather than
 * blanking the page. An unknown but well-formed code needs no fallback: Intl
 * renders it as the code itself.
 */
export function formatMoney(minor: number, currency = DEFAULT_CURRENCY): string {
	try {
		return new Intl.NumberFormat(undefined, {
			style: 'currency',
			currency,
			minimumFractionDigits: 2,
			maximumFractionDigits: 2,
		}).format(minor / 100);
	} catch {
		return `${formatAmount(minor)} ${currency}`.trim();
	}
}

/** Formats without the currency symbol, for tables that label the currency once. */
/**
 * The symbol a currency is written with (₱, $, €), or the code itself when the platform does not know it — the
 * same fallback `formatMoney` takes, so an unfamiliar code degrades instead of throwing.
 */
export function currencySymbol(currency = DEFAULT_CURRENCY): string {
	try {
		const parts = new Intl.NumberFormat(undefined, { style: 'currency', currency, currencyDisplay: 'narrowSymbol' }).formatToParts(0);
		return parts.find((part) => part.type === 'currency')?.value ?? currency;
	} catch {
		return currency;
	}
}

export function formatAmount(minor: number): string {
	return new Intl.NumberFormat(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 }).format(minor / 100);
}

/** Renders minor units as an editable decimal string, e.g. `4599` -> `45.99`. */
export function toDecimalString(minor: number): string {
	const negative = minor < 0;
	const absolute = Math.abs(minor);
	const whole = Math.floor(absolute / 100);
	const cents = absolute % 100;
	return `${negative ? '-' : ''}${whole}.${String(cents).padStart(2, '0')}`;
}

/**
 * Parses a decimal string into minor units, or returns null if it is not a
 * number. Splitting on the decimal point rather than multiplying by 100 avoids
 * the float rounding that turns 45.99 into 4598.999…
 */
export function parseMoney(input: string): number | null {
	const normalised = input.trim().replace(/[\s,_]/g, '');
	if (!normalised) return null;

	const match = /^(-)?(\d*)(?:\.(\d{1,2}))?$/.exec(normalised);
	if (!match) return null;

	const [, sign, whole = '', fraction = ''] = match;
	if (!whole && !fraction) return null;

	const minor = Number(whole || '0') * 100 + Number(fraction.padEnd(2, '0'));
	return sign ? -minor : minor;
}

/** Parses a percentage input like "12.5" into 0-100, or null if it is not a valid share. */
export function parsePercent(input: string): number | null {
	const trimmed = input.trim();
	if (!trimmed) return null;

	const value = Number(trimmed);
	if (!Number.isFinite(value) || value < 0 || value > 100) return null;
	return value;
}

/** Percentage of `planned` consumed by `actual`, clamped for use as a bar width. */
export function percentOf(actual: number, planned: number): number {
	if (planned <= 0) return actual > 0 ? 100 : 0;
	return Math.min(100, Math.max(0, Math.round((actual / planned) * 100)));
}
