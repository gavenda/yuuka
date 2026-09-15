import { setTokenProvider, setUnauthorizedHandler } from '@/lib/api';
import { auth0, isConfigured } from '@/lib/auth0';
import { router } from '@/router';
import { createPinia } from 'pinia';
import { createApp } from 'vue';
import App from './App.vue';
import NotConfiguredView from './views/NotConfiguredView.vue';
import './style.css';

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

	// Any 401 means the token the API saw is no longer good; get a fresh one.
	setUnauthorizedHandler(() => {
		void router.push({ name: 'login', query: { redirect: router.currentRoute.value.fullPath } });
	});

	// Router before Auth0: the plugin uses it to navigate after a login callback.
	createApp(App).use(createPinia()).use(router).use(auth0).mount('#app');
}
