import { shallowRef } from 'vue';

/** The month the screen on show is looking at, for whichever surface draws the button that changes it. */
export interface MonthActionEntry {
	/** `YYYY-MM`. */
	month: string;
	/** Opens the screen's own month picker. */
	open: () => void;
}

const current = shallowRef<MonthActionEntry | null>(null);

/** The current screen's month, if it is looking at one. */
export const currentMonthAction = current;

/**
 * Puts a screen's month forward. Returns the function that takes it back, which only clears the slot
 * if it is still this entry's: a page fading in registers before the one leaving has been torn down.
 */
export function registerMonthAction(entry: MonthActionEntry): () => void {
	current.value = entry;

	return () => {
		if (current.value === entry) current.value = null;
	};
}
