import { ref, watchEffect } from 'vue';

const STORAGE_KEY = 'yuuka.theme';
type Theme = 'light' | 'dark';

function preferred(): Theme {
	try {
		const stored = localStorage.getItem(STORAGE_KEY);
		if (stored === 'light' || stored === 'dark') return stored;
	} catch {
		// Fall through to the OS preference.
	}
	return window.matchMedia?.('(prefers-color-scheme: dark)').matches ? 'dark' : 'light';
}

const theme = ref<Theme>(preferred());

watchEffect(() => {
	document.documentElement.classList.toggle('dark', theme.value === 'dark');
	try {
		localStorage.setItem(STORAGE_KEY, theme.value);
	} catch {
		// A remembered theme is a convenience, not a requirement.
	}
});

export function useTheme() {
	return {
		theme,
		toggle: () => {
			theme.value = theme.value === 'dark' ? 'light' : 'dark';
		},
	};
}
