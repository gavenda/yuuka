import { createAuth0 } from '@auth0/auth0-vue';
import { watch } from 'vue';

const domain = import.meta.env.VITE_AUTH0_DOMAIN;
const clientId = import.meta.env.VITE_AUTH0_CLIENT_ID;
const audience = import.meta.env.VITE_AUTH0_AUDIENCE;

/** True when the app has enough configuration to talk to Auth0 at all. */
export const isConfigured = Boolean(domain && clientId && audience);

/**
 * The Auth0 plugin instance.
 *
 * `createAuth0` returns an object that is both a Vue plugin and the client
 * itself, so the router and the API layer can read the same reactive state that
 * `useAuth0()` hands to components.
 *
 * Auth0 returns to the site root rather than a dedicated callback route. The
 * plugin notices the `code` and `state` on the URL during startup, exchanges
 * them, strips the query and navigates on, so no route has to exist for it.
 *
 * Install it *after* the router: on startup the plugin looks for `$router` and
 * `push`es to the route the user originally asked for. Without the router it
 * would fall back to rewriting history behind Vue Router's back, leaving the
 * two disagreeing about the current page.
 *
 * Tokens are cached in localStorage with refresh-token rotation: browsers now
 * block the third-party cookies that silent iframe renewal depended on, so this
 * is what keeps a session alive across reloads.
 */
export const auth0 = createAuth0({
	domain,
	clientId,
	authorizationParams: { audience, redirect_uri: window.location.origin },
	cacheLocation: 'localstorage',
	useRefreshTokens: true,
});

/**
 * Resolves once the plugin has finished restoring any existing session.
 *
 * Navigation guards must wait for this: until it settles `isAuthenticated` is
 * still false, and a signed-in user reloading a page would be bounced to the
 * sign-in screen.
 */
export function whenAuthReady(): Promise<void> {
	if (!auth0.isLoading.value) return Promise.resolve();

	return new Promise((resolve) => {
		const stop = watch(auth0.isLoading, (loading) => {
			if (loading) return;
			stop();
			resolve();
		});
	});
}
