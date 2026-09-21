import { setTokenProvider, setUnauthorizedHandler } from '@/lib/api';
import { auth0, isConfigured } from '@/lib/auth0';
import { clearCache } from '@/lib/cache';
import { stopPush } from '@/lib/push';
import { shouldReloadForStaleChunk } from '@/lib/staleChunk';
import { registerServiceWorker } from '@/lib/pwa';
import { router } from '@/router';
import { createPinia } from 'pinia';
import { createApp } from 'vue';
import App from './App.vue';
import NotConfiguredView from './views/NotConfiguredView.vue';
import './style.css';

// A route chunk that fails to load after a deploy is a stale tab, not a broken app:
// reload for the new build instead of leaving the click doing nothing.
window.addEventListener('vite:preloadError', (event) => {
	if (!shouldReloadForStaleChunk()) return;
	event.preventDefault();
	window.location.reload();
});

if (!isConfigured) {
	createApp(NotConfiguredView).mount('#app');
} else {
	// The API asks for a token per request, so the SDK can refresh a stale one
	// without anything here having to track expiry.
	setTokenProvider(async () => {
		if (!auth0.isAuthenticated.value) return null;

		try {
			return await auth0.getAccessTokenSilently();
		} catch {
			// The refresh token is gone or was rejected: there is no live session.
			return null;
		}
	});

	// Any 401 means the session the API saw is no longer good. Sign out locally
	// (no round trip to Auth0) rather than pushing to `/login` directly: while
	// `isAuthenticated` is still true the router guard immediately bounces `/login`
	// back to the current route, which retries the same request and 401s again —
	// an infinite redirect loop. Clearing the session here lets App.vue's
	// `watch(isAuthenticated, …)` do the one redirect that actually sticks.
	//
	// The local copy goes with it: it holds the books of a session that is over.
	setUnauthorizedHandler(() => {
		clearCache();
		// The unsent work deliberately stays. A rejected token is the same
		// person, who will sign in again and still wants what they entered
		// saved; the router guard discards the queue if it turns out to be
		// someone else. Only a sign-out the user asked for throws it away.
		void stopPush();
		void auth0.logout({ openUrl: false });
	});

	// Router before Auth0: the plugin uses it to navigate after a login callback.
	createApp(App).use(createPinia()).use(router).use(auth0).mount('#app');
}

registerServiceWorker();
