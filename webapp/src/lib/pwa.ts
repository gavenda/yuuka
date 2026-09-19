import { readonly, ref } from 'vue';
import { registerSW } from 'virtual:pwa-register';

const ready = ref(false);
let apply: ((reload?: boolean) => Promise<void>) | null = null;

/** How often an open app checks for a new build. A browser only does so on navigation and once a day, which an installed app can go weeks without. */
const UPDATE_CHECK_MS = 60 * 60 * 1000;

/**
 * Whether a newer build has been downloaded and is waiting. It is never applied on its own: taking
 * it over reloads the page, and that would throw away a half-filled form. The interface offers it
 * instead, and until it is taken this tab keeps running the build it started with, files and all.
 */
export const updateReady = readonly(ready);

/** Registers the service worker that lets the app open with no network. */
export function registerServiceWorker(): void {
	apply = registerSW({
		onNeedRefresh: () => (ready.value = true),
		onRegisteredSW: (_url, registration) => {
			if (!registration) return;
			setInterval(() => {
				if (navigator.onLine) void registration.update().catch(() => {});
			}, UPDATE_CHECK_MS);
		},
	});
}

/** Switches to the waiting build and reloads onto it. */
export function applyUpdate(): void {
	void apply?.(true);
}
