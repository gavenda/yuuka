import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ref } from 'vue';

/**
 * The guard is the only thing standing between an unauthenticated visitor and
 * every page of someone's finances, so it is exercised directly against a fake
 * Auth0 state rather than trusted by inspection.
 */
const isAuthenticated = ref(false);
const isLoading = ref(false);
const user = ref<{ sub: string } | undefined>(undefined);
const adoptCacheFor = vi.fn();

vi.mock('@/lib/cache', () => ({ adoptCacheFor }));

vi.mock('@/lib/auth0', () => ({
	auth0: {
		get isAuthenticated() {
			return isAuthenticated;
		},
		get isLoading() {
			return isLoading;
		},
		get user() {
			return user;
		},
	},
	whenAuthReady: async () => {
		// Mirrors the real helper: settle only once the SDK has stopped loading.
		while (isLoading.value) await new Promise((resolve) => setTimeout(resolve, 1));
	},
	isConfigured: true,
}));

const { router } = await import('./index');

beforeEach(async () => {
	isAuthenticated.value = false;
	isLoading.value = false;
	user.value = undefined;
	adoptCacheFor.mockClear();
	await router.replace('/login');
	await router.isReady();
});

describe('signed out', () => {
	it('leaves the local copy of the ledger alone', async () => {
		await router.push('/budget');
		expect(adoptCacheFor).not.toHaveBeenCalled();
	});

	it('sends every protected route to the sign-in screen', async () => {
		for (const path of ['/', '/transactions', '/budget', '/accounts', '/categories', '/subscriptions']) {
			await router.push(path);
			expect(router.currentRoute.value.name, path).toBe('login');
		}
	});

	it('remembers where the visitor was headed', async () => {
		await router.push('/budget');
		expect(router.currentRoute.value.query.redirect).toBe('/budget');
	});

	it('does not add a redirect for the dashboard, which is the default anyway', async () => {
		await router.push('/');
		expect(router.currentRoute.value.query.redirect).toBeUndefined();
	});

	it('sends unknown paths to the sign-in screen rather than 404ing into the app', async () => {
		await router.push('/nope/deep');
		expect(router.currentRoute.value.name).toBe('login');
	});
});

describe('signed in', () => {
	beforeEach(() => {
		isAuthenticated.value = true;
		user.value = { sub: 'auth0|a' };
	});

	it('ties the local copy of the ledger to the person who is signed in', async () => {
		await router.push('/budget');
		expect(adoptCacheFor).toHaveBeenCalledWith('auth0|a');
	});

	it('allows the protected routes', async () => {
		for (const [path, name] of [
			['/', 'dashboard'],
			['/transactions', 'transactions'],
			['/budget', 'budget'],
			['/accounts', 'accounts'],
			['/categories', 'categories'],
		]) {
			await router.push(path);
			expect(router.currentRoute.value.name, path).toBe(name);
		}
	});

	it('bounces the sign-in screen to the dashboard', async () => {
		// Navigate away first: pushing the route we are already on is a duplicate
		// navigation, which Vue Router drops without consulting the guard.
		await router.push('/budget');
		await router.push('/login');
		expect(router.currentRoute.value.name).toBe('dashboard');
	});
});

describe('while the session is still being restored', () => {
	it('waits rather than bouncing a signed-in user to the sign-in screen', async () => {
		// This is the reload case: the SDK has not yet reported the existing
		// session, so a guard that read `isAuthenticated` immediately would be wrong.
		isLoading.value = true;
		isAuthenticated.value = false;

		const navigation = router.push('/budget');

		setTimeout(() => {
			isAuthenticated.value = true;
			isLoading.value = false;
		}, 10);

		await navigation;
		expect(router.currentRoute.value.name).toBe('budget');
	});

	it('lets a user arriving back from Auth0 land on the root', async () => {
		// Auth0 redirects to the site root rather than a callback route, so the
		// first navigation happens while the SDK is still exchanging the code.
		// Deciding before that settles would send an arriving user to sign-in.
		isLoading.value = true;
		isAuthenticated.value = false;

		const navigation = router.push('/');

		setTimeout(() => {
			isAuthenticated.value = true;
			isLoading.value = false;
		}, 10);

		await navigation;
		expect(router.currentRoute.value.name).toBe('dashboard');
	});

	it('still refuses when the restore finishes with no session', async () => {
		isLoading.value = true;
		isAuthenticated.value = false;

		const navigation = router.push('/accounts');
		setTimeout(() => {
			isLoading.value = false;
		}, 10);

		await navigation;
		expect(router.currentRoute.value.name).toBe('login');
	});
});
