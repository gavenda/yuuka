import { ref } from 'vue';

/** Below the rail's breakpoint (`sm`, 40rem / 640px): a phone, with a bottom bar and full-screen forms. */
const QUERY = '(max-width: 39.99rem)';

const query = typeof window !== 'undefined' ? window.matchMedia?.(QUERY) : undefined;

/** Whether the window is a phone's width, kept up to date as it is resized. */
export const compactWindow = ref(query?.matches ?? false);

query?.addEventListener?.('change', (event) => {
	compactWindow.value = event.matches;
});
