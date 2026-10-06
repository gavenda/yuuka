import { argbFromHex, Blend, hexFromArgb } from '@material/material-color-utilities';
import { useTheme } from './theme';

/** The theme's `primary`, light and dark: the same values as `--color-primary` in `style.css`. Keep the two in step. */
const PRIMARY = { light: '#4e5d8c', dark: '#bac5ee' } as const;

const cache = new Map<string, string>();

/**
 * A colour the user picked for a category or tag, nudged toward the theme's primary hue — at most 15° —
 * so a mark of it sits in the palette instead of fighting it (Material's "harmonize", and the twin of
 * `HarmonisedColor.kt`). Only marks that are drawn beside the theme go through this; a picker shows the
 * value as it was chosen. Anything that is not a `#rrggbb` comes back as it was given.
 */
export function harmonise(hex: string, dark: boolean): string {
	const key = `${dark ? 'd' : 'l'}${hex}`;
	const known = cache.get(key);
	if (known) return known;

	let result = hex;
	if (/^#[0-9a-fA-F]{6}$/.test(hex)) {
		result = hexFromArgb(Blend.harmonize(argbFromHex(hex), argbFromHex(dark ? PRIMARY.dark : PRIMARY.light)));
	}
	cache.set(key, result);
	return result;
}

/** `harmonise` for the scheme on screen, so a mark follows a change of theme. Null stays null: no colour, no mark. */
export function useHarmonised() {
	const { theme } = useTheme();
	return (hex: string | null | undefined): string | undefined => (hex ? harmonise(hex, theme.value === 'dark') : undefined);
}
