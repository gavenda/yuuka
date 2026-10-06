import { computed, ref, watchEffect } from 'vue';

const STORAGE_KEY = 'yuuka.theme';

/** How the app chooses between its light and dark scheme: follow the system, or one of the two whatever it says. */
export type ThemeMode = 'system' | 'light' | 'dark';
type Theme = 'light' | 'dark';

function stored(): ThemeMode {
	try {
		const value = localStorage.getItem(STORAGE_KEY);
		if (value === 'light' || value === 'dark') return value;
	} catch {
		// Fall through to following the system.
	}
	return 'system';
}

const systemQuery = window.matchMedia?.('(prefers-color-scheme: dark)');

const mode = ref<ThemeMode>(stored());
const systemDark = ref(systemQuery?.matches ?? false);

// The system can change its mind while the app is open (a scheduled dark mode, say).
systemQuery?.addEventListener?.('change', (event) => {
	systemDark.value = event.matches;
});

/** The scheme on screen: the chosen one, or the system's while following it. */
const theme = computed<Theme>(() => (mode.value === 'system' ? (systemDark.value ? 'dark' : 'light') : mode.value));

watchEffect(() => {
	document.documentElement.classList.toggle('dark', theme.value === 'dark');
});

function setMode(next: ThemeMode): void {
	mode.value = next;
	try {
		// Following the system is the absence of a choice, which is also what `index.html` reads before first paint.
		if (next === 'system') localStorage.removeItem(STORAGE_KEY);
		else localStorage.setItem(STORAGE_KEY, next);
	} catch {
		// A remembered theme is a convenience, not a requirement.
	}
}

/** The app theme, a local preference set from Settings as on Android (`ThemePreference`). */
export function useTheme() {
	return { mode, theme, setMode };
}
