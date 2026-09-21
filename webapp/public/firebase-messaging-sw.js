/*
 * The service worker Firebase Cloud Messaging needs, and nothing more.
 *
 * It is separate from the app's own service worker on purpose: `vite-plugin-pwa`
 * generates that one with Workbox and owns its scope, so this one is registered
 * under a scope of its own (`/firebase-cloud-messaging-push-scope`) and the two
 * never fight over a registration.
 *
 * It is served as-is from `public/`, so it cannot read the app's imports or its
 * environment variables. Rather than repeat the `VITE_FIREBASE_*` values here,
 * where they would drift, the registration in `src/lib/push.ts` passes them on
 * the URL and this reads them back off `self.location`. A Firebase web config
 * is a set of public identifiers — the same ones the page itself ships — not a
 * secret, so a query string is the right place for them.
 *
 * It is deliberately silent. The messages the server sends are data-only hints
 * that a slice of the ledger moved, not news: the user made the change
 * themselves, on their other device, and a notification saying so would be
 * noise. `onBackgroundMessage` is registered anyway, because without it the SDK
 * shows a generic "background message" notification of its own.
 *
 * A message arriving while no tab is open is dropped, which is the right
 * answer — there is no screen to bring up to date, and the app syncs when it is
 * next opened. One arriving while a tab is open goes to `onMessage` in
 * `src/lib/push.ts`, which is where the refresh happens.
 */

importScripts('https://www.gstatic.com/firebasejs/10.14.1/firebase-app-compat.js');
importScripts('https://www.gstatic.com/firebasejs/10.14.1/firebase-messaging-compat.js');

const config = Object.fromEntries(new URL(self.location.href).searchParams);

if (config.apiKey && config.projectId && config.messagingSenderId && config.appId) {
	firebase.initializeApp({
		apiKey: config.apiKey,
		authDomain: config.authDomain,
		projectId: config.projectId,
		messagingSenderId: config.messagingSenderId,
		appId: config.appId,
	});

	firebase.messaging().onBackgroundMessage(() => {
		// Intentionally nothing. See the note at the top.
	});
}
