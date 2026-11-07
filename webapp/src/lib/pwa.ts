import { computed, readonly, ref } from 'vue';
import type { Router } from 'vue-router';
import { registerSW } from 'virtual:pwa-register';
import { paneOpen, pendingSwitch } from '@/lib/formPane';
import { modalOpen } from '@/lib/modal';

const ready = ref(false);
let apply: ((reload?: boolean) => Promise<void>) | null = null;
let applying = false;

/** How often an open app checks for a new build. A browser only does so on navigation and once a day, which an installed app can go weeks without. */
const UPDATE_CHECK_MS = 60 * 60 * 1000;

/**
 * Whether a newer build has been downloaded and is waiting. Until it is taken this tab keeps running the build
 * it started with, files and all.
 */
export const updateReady = readonly(ready);

/**
 * Whether reloading now would throw nothing away. Queued changes are in IndexedDB and survive a reload; what
 * would not is a form or dialog on screen, so nothing may be open — not even one waiting on "discard changes?".
 */
const safeToReload = computed(() => !modalOpen.value && !paneOpen.value && pendingSwitch.value === null);

/** Registers the service worker that lets the app open with no network. */
export function registerServiceWorker(): void {
	apply = registerSW({
		onNeedRefresh: () => {
			ready.value = true;
			if (document.visibilityState === 'hidden') applyWhenSafe();
		},
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
	if (applying) return;
	applying = true;
	void apply?.(true);
}

function applyWhenSafe(): void {
	if (ready.value && safeToReload.value) applyUpdate();
}

/**
 * Takes a waiting build without being asked, at a moment the person will not notice: just after they move to
 * another screen, which is already a fresh view, or while the tab is out of sight. Never with a form or dialog
 * open. The "new version" snackbar stays as the way to take it sooner.
 */
export function installAutoUpdate(router: Router): void {
	router.afterEach((to, from, failure) => {
		// The first navigation is the page loading, not the person moving; a filter only changes the query.
		if (!failure && from.matched.length > 0 && to.path !== from.path) applyWhenSafe();
	});
	document.addEventListener('visibilitychange', () => {
		if (document.visibilityState === 'hidden') applyWhenSafe();
	});
}
