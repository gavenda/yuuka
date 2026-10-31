/**
 * The banks an account can be filed under without a logo URL of its own. Only the key is stored on the
 * account; the name, the logo and how it shows in dark mode are here, so changing one reaches every
 * account that names it. The server checks only a key's shape, so adding a bank is a release, not a
 * migration — but the same key must be added to `ui/common/Banks.kt` on Android, or that app shows the
 * account's initial for it. Logos live in `public/banks/` (and, as vector drawables, in the app's `res/drawable/bank_*.xml`).
 */
export interface Bank {
	/** What the account stores: lowercase letters, digits and hyphens, at most 40. */
	key: string;
	name: string;
	/** A path under `public/`; the light-mode logo when there is a `logoDark`. */
	logo: string;
	/** A logo drawn for dark mode, where the bank has one. */
	logoDark?: string;
	/** Whether `logo` inverts in dark mode, for a dark mark with no dark-mode logo of its own. */
	invertDark?: boolean;
}

/** In the order the form lists them; Custom always follows. */
export const BANKS: readonly Bank[] = [
	{ key: 'bpi', name: 'BPI', logo: '/banks/bpi.svg' },
	{ key: 'dragonfi', name: 'DragonFi', logo: '/banks/dragonfi.svg' },
	{ key: 'gcash', name: 'GCash', logo: '/banks/gcash.svg' },
	{ key: 'gotyme', name: 'GoTyme', logo: '/banks/gotyme_light.svg', logoDark: '/banks/gotyme_dark.svg' },
	{ key: 'maya', name: 'Maya', logo: '/banks/maya_light.svg', logoDark: '/banks/maya_dark.svg' },
	{ key: 'metrobank', name: 'Metrobank', logo: '/banks/metrobank.svg' },
	{ key: 'tonik', name: 'Tonik', logo: '/banks/tonik.svg' },
];

export function bankByKey(key: string | null | undefined): Bank | null {
	return key ? (BANKS.find((bank) => bank.key === key) ?? null) : null;
}
