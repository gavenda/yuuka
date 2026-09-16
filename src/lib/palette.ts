/**
 * Categorical palette for category colours.
 *
 * The eight slots are assigned in fixed order and never cycled: the ordering is
 * what keeps adjacent series apart under colour-vision deficiency, so a ninth
 * category reuses a slot rather than inventing a hue. Each slot carries its own
 * dark-mode step — the dark column is these hues re-stepped for a dark surface,
 * not an automatic lightening of the light one.
 *
 * Validated against this app's own surfaces (white cards in light mode,
 * slate-900 in dark): every slot clears the lightness band, chroma floor,
 * CVD separation and normal-vision floor. Three light-mode slots sit below 3:1
 * contrast, so anything painted with them is always accompanied by a visible
 * text label — never colour alone.
 */
export interface PaletteSlot {
	name: string;
	light: string;
	dark: string;
}

export const PALETTE: PaletteSlot[] = [
	{ name: 'Blue', light: '#2a78d6', dark: '#3987e5' },
	{ name: 'Orange', light: '#eb6834', dark: '#d95926' },
	{ name: 'Aqua', light: '#1baf7a', dark: '#199e70' },
	{ name: 'Yellow', light: '#eda100', dark: '#c98500' },
	{ name: 'Magenta', light: '#e87ba4', dark: '#d55181' },
	{ name: 'Green', light: '#008300', dark: '#008300' },
	{ name: 'Violet', light: '#4a3aa7', dark: '#9085e9' },
	{ name: 'Red', light: '#e34948', dark: '#e66767' },
];

const DARK_BY_LIGHT = new Map(PALETTE.map((slot) => [slot.light.toLowerCase(), slot.dark]));

/** Maps a stored (light) colour onto its dark-mode step, leaving custom hexes alone. */
export function forMode(color: string, dark: boolean): string {
	if (!dark) return color;
	return DARK_BY_LIGHT.get(color.toLowerCase()) ?? color;
}

/** The slot a new category should take, so defaults spread across the palette. */
export function nextColor(existingCount: number): string {
	return PALETTE[existingCount % PALETTE.length].light;
}

/**
 * Status colours are reserved for state and never used as a series colour.
 * Each is paired with a visible label wherever it appears.
 */
export const STATUS = {
	good: '#0ca30c',
	warning: '#fab219',
	critical: '#d03b3b',
} as const;

/** Budget health from the share of a planned amount already spent. An unbudgeted
 *  category reads as neutral, not a warning — there is no plan to be off track from. */
export function budgetStatus(actual: number, planned: number): keyof typeof STATUS {
	if (planned <= 0) return 'good';
	const ratio = actual / planned;
	if (ratio > 1) return 'critical';
	if (ratio >= 0.85) return 'warning';
	return 'good';
}
