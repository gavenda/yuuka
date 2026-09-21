/**
 * Being told, rather than asking.
 *
 * A change made on the phone leaves this browser's copy quietly wrong until
 * something makes it ask again. Polling would cost a request a minute to catch
 * a change a day, so the server pushes instead: a data-only FCM message naming
 * which slices of the ledger moved, and this refetches exactly those.
 *
 * Three things it deliberately is not:
 *
 * - **Not a notification.** Nothing is shown. The user did not need telling —
 *   they made the change themselves, on their other device. The message is a
 *   hint to this tab, and `firebase-messaging-sw.js` stays silent for the same
 *   reason.
 * - **Not required.** FCM does not promise delivery, and permission is often
 *   refused. Everything still works without it: the app syncs on open, on
 *   reconnect, and on pull-to-refresh. Nothing here may be built on a message
 *   having arrived.
 * - **Not asked for on arrival.** The browser's permission prompt is expensive
 *   to spend, so it is only requested once the user has actually signed in and
 *   the app has something to keep in sync.
 *
 * Firebase is loaded on demand rather than bundled into the app's first paint:
 * it is a large dependency for a feature that is an optimisation.
 */

import { deviceId } from './device';
import { request } from './http';
import { parseSlices } from './slices';
import { refreshSlices } from './sync';

/** Set from `.env`; a deployment that has not configured FCM simply never registers. */
const CONFIG = {
	apiKey: import.meta.env.VITE_FIREBASE_API_KEY,
	authDomain: import.meta.env.VITE_FIREBASE_AUTH_DOMAIN,
	projectId: import.meta.env.VITE_FIREBASE_PROJECT_ID,
	messagingSenderId: import.meta.env.VITE_FIREBASE_MESSAGING_SENDER_ID,
	appId: import.meta.env.VITE_FIREBASE_APP_ID,
};

const VAPID_KEY = import.meta.env.VITE_FIREBASE_VAPID_KEY;

/** Whether this build has everything it needs to receive pushes. */
export const isPushConfigured = Boolean(CONFIG.apiKey && CONFIG.projectId && CONFIG.messagingSenderId && CONFIG.appId && VAPID_KEY);

let registeredToken: string | null = null;
let starting = false;

/**
 * Registers for pushes and keeps refreshing what they name.
 *
 * Safe to call more than once — a second call while the first is in flight, or
 * after it succeeded, does nothing. Every failure is swallowed: an unsupported
 * browser, a refused permission and an unreachable API all mean the same thing
 * here, which is that this tab finds out about changes the slower way.
 */
export async function startPush(): Promise<void> {
	if (!isPushConfigured || starting || registeredToken) return;
	if (!('serviceWorker' in navigator) || !('Notification' in window)) return;

	starting = true;
	try {
		const { initializeApp } = await import('firebase/app');
		const { getMessaging, getToken, isSupported, onMessage } = await import('firebase/messaging');

		if (!(await isSupported())) return;

		// Asking is the expensive part, so a refusal is taken as final for this
		// page rather than asked again on the next navigation.
		const permission = await Notification.requestPermission();
		if (permission !== 'granted') return;

		// The messaging service worker is separate from the PWA's own. Workbox
		// generates that one and owns its scope; this one exists only to hold a
		// listener while the tab is closed, and giving it a scope of its own is
		// what keeps the two from fighting over registration.
		// The config travels on the URL rather than being repeated inside the
		// worker, which cannot read `import.meta.env`. These are public
		// identifiers, the same ones the page itself ships.
		const workerUrl = `/firebase-messaging-sw.js?${new URLSearchParams(CONFIG as Record<string, string>)}`;
		const serviceWorkerRegistration = await navigator.serviceWorker.register(workerUrl, {
			scope: '/firebase-cloud-messaging-push-scope',
		});

		const messaging = getMessaging(initializeApp(CONFIG));
		const token = await getToken(messaging, { vapidKey: VAPID_KEY, serviceWorkerRegistration });
		if (!token) return;

		await request<{ registered: boolean }>('/devices', {
			method: 'PUT',
			body: JSON.stringify({ token, deviceId: deviceId(), platform: 'web' }),
		});
		registeredToken = token;

		// A message arriving while the tab is open. The one arriving while it is
		// closed is the service worker's, and is deliberately dropped — there is
		// no screen to bring up to date, and the app syncs when it is next opened.
		onMessage(messaging, (payload) => {
			const slices = parseSlices(payload.data?.slices);
			if (slices.length > 0) void refreshSlices(slices);
		});
	} catch (error) {
		console.warn('Push registration skipped', error);
	} finally {
		starting = false;
	}
}

/**
 * Gives up this install's registration.
 *
 * Signing out has to unregister, or the next push would wake a browser whose
 * cache has been cleared and whose session is gone — and, on a shared machine,
 * would be about a ledger the person now sitting there cannot see.
 */
export async function stopPush(): Promise<void> {
	const token = registeredToken;
	registeredToken = null;
	if (!token) return;

	try {
		await request<void>(`/devices/${encodeURIComponent(token)}`, { method: 'DELETE' });
	} catch {
		// The server prunes a token it cannot deliver to, so a failure here costs
		// at most one undeliverable push.
	}
}
