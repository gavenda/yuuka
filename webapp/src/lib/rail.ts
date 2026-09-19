import { ref } from 'vue';

const STORAGE_KEY = 'yuuka.rail';

/** Wide enough to leave room for the page beside an expanded rail; below it the rail floats over the page instead. */
const PUSH_QUERY = '(min-width: 1024px)';

/** Whether the viewport has room for the expanded rail to sit beside the page rather than over it. */
export function railPushesContent(): boolean {
	return window.matchMedia?.(PUSH_QUERY).matches ?? false;
}

function initial(): boolean {
	// Only a wide screen brings the rail back open: a floating one on load would greet a phone-sized window with a scrim.
	if (!railPushesContent()) return false;
	try {
		return localStorage.getItem(STORAGE_KEY) === '1';
	} catch {
		return false;
	}
}

/** Whether the navigation rail is open into its expanded form. A remembered choice, not part of the ledger. */
const expanded = ref(initial());

function set(value: boolean): void {
	expanded.value = value;
	try {
		localStorage.setItem(STORAGE_KEY, value ? '1' : '0');
	} catch {
		// A remembered layout is a convenience, not a requirement.
	}
}

export function useRail() {
	return {
		expanded,
		toggle: () => set(!expanded.value),
		collapse: () => set(false),
	};
}
