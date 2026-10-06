import { shallowRef } from 'vue';
import type { IconPath } from './icons';

/** One entry of a FAB's menu, for a screen whose leading action is really a few. */
export interface FabAction {
	label: string;
	icon: IconPath;
	run: () => void;
}

/** What the current screen's floating action button does, for whichever surface is showing it. */
export interface FabEntry {
	label: string;
	icon: IconPath;
	disabled: boolean;
	run: () => void;
	/** When there are any, pressing the button lists them in a menu instead of calling `run`. */
	actions: FabAction[];
}

const current = shallowRef<FabEntry | null>(null);

/** The action the current screen leads with, if it has one. */
export const currentFab = current;

/**
 * Puts a screen's action forward. Returns the function that takes it back, which only clears the slot
 * if it is still this entry's: a page fading in registers before the one leaving has been torn down.
 */
export function registerFab(entry: FabEntry): () => void {
	current.value = entry;

	return () => {
		if (current.value === entry) current.value = null;
	};
}
