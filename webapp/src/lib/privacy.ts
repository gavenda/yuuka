import { ref, watchEffect } from 'vue';
import { DEFAULT_CURRENCY, formatMoney } from './money';

const STORAGE_KEY = 'yuuka.amounts-hidden';

/** What stands in for a figure while amounts are hidden. */
export const MASK = '••••';

/**
 * Whether money is masked across the whole app — the "someone is looking over
 * my shoulder" switch.
 *
 * It is deliberately a local display preference rather than a server-side
 * setting: it protects the current screen, not the data, so it should take
 * effect instantly and not follow the account onto someone else's device.
 */
const hidden = ref(readStored());

function readStored(): boolean {
	try {
		return localStorage.getItem(STORAGE_KEY) === 'true';
	} catch {
		// Private windows and blocked storage simply start visible.
		return false;
	}
}

watchEffect(() => {
	try {
		localStorage.setItem(STORAGE_KEY, String(hidden.value));
	} catch {
		// Remembering the choice is a convenience, not a requirement.
	}
});

export function useAmountVisibility() {
	return {
		hidden,
		toggle: () => {
			hidden.value = !hidden.value;
		},
	};
}

/** The parts of a formatted amount that carry the number itself. */
const NUMERIC_PARTS = new Set(['integer', 'group', 'decimal', 'fraction']);

/**
 * Formats an amount with its digits replaced by the mask, keeping everything
 * else the formatter produced — currency symbol or code, sign, and spacing.
 *
 * Built from `formatToParts` rather than a regex over the finished string so it
 * holds wherever the symbol sits: `₱••••` for a leading symbol, `•••• €` for a
 * trailing one, `ZZZ ••••` for a code Intl does not recognise.
 */
function maskedMoney(minor: number, currency: string): string {
	try {
		const parts = new Intl.NumberFormat(undefined, {
			style: 'currency',
			currency,
			minimumFractionDigits: 2,
			maximumFractionDigits: 2,
		}).formatToParts(minor / 100);

		// All the numeric parts collapse into one mask, so its width never
		// depends on the amount it is hiding.
		let placed = false;

		return parts
			.map((part) => {
				if (!NUMERIC_PARTS.has(part.type)) return part.value;
				if (placed) return '';
				placed = true;
				return MASK;
			})
			.join('');
	} catch {
		// Malformed currency code: `formatMoney` has the same fallback shape.
		return `${MASK} ${currency}`.trim();
	}
}

/**
 * `formatMoney`, with the figure masked while amounts are hidden.
 *
 * Every amount the user sees renders through here rather than `formatMoney`
 * directly, so the toggle cannot miss one. The currency stays visible: hiding
 * it as well would leave a column of anonymous dots with no indication of what
 * is even being measured.
 */
export function displayMoney(minor: number, currency: string = DEFAULT_CURRENCY): string {
	return hidden.value ? maskedMoney(minor, currency) : formatMoney(minor, currency);
}
