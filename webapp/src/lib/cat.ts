import type { IconPath } from '@/lib/icons';

/*
 * The print on Yuuka's pyjamas, and the backdrop to the brand (`CatPattern.kt`): a round head with two pointed
 * ears, drawn as an outline with its eyes and the insides of its ears filled — in the tertiary colour, which is
 * the pink they are on the fabric. It goes behind the screens that have nothing else on them — signing in,
 * loading — and as a faint mark on the rail and the card a screen leads with, never behind a list, a form or a
 * chart. Where a list is empty it is Yuuka herself who says so (`YuukaPortrait`). The paths are in a 100 by 80
 * box, the same strings Android parses.
 */
export const CAT_HEAD =
	'M 14 36 L 13 14 Q 13 6 21 10 L 38 20 Q 50 16 62 20 L 79 10 Q 87 6 87 14 L 86 36 C 100 46 98 74 50 74 C 2 74 0 46 14 36 Z';
export const CAT_EYES = 'M 32 52 a 4 5 0 1 0 8 0 a 4 5 0 1 0 -8 0 Z M 60 52 a 4 5 0 1 0 8 0 a 4 5 0 1 0 -8 0 Z';
export const CAT_EARS = 'M 20 18 L 31 24 L 21 31 Z M 80 18 L 69 24 L 79 31 Z';

/** The left ear wound the other way, so that with the eyes and the right ear it cuts a hole in a filled head. */
const EAR_HOLES = 'M 20 18 L 21 31 L 31 24 Z M 80 18 L 69 24 L 79 31 Z';

/** The motif as an icon (`CatIcon`): the head filled, with the eyes and ears cut out of it, so it takes a colour like any other. */
export const CAT: IconPath = { d: `${CAT_HEAD} ${CAT_EYES} ${EAR_HOLES}`, viewBox: '0 -10 100 100' };
