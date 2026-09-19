import { auth0, whenAuthReady } from '@/lib/auth0';
import { adoptCacheFor } from '@/lib/cache';
import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router';

const routes: RouteRecordRaw[] = [
	{ path: '/login', name: 'login', component: () => import('@/views/LoginView.vue'), meta: { public: true, title: 'Sign in' } },
	{ path: '/', name: 'dashboard', component: () => import('@/views/DashboardView.vue'), meta: { title: 'Dashboard' } },
	{ path: '/transactions', name: 'transactions', component: () => import('@/views/TransactionsView.vue'), meta: { title: 'Transactions' } },
	{ path: '/budget', name: 'budget', component: () => import('@/views/BudgetView.vue'), meta: { title: 'Budget' } },
	{ path: '/accounts', name: 'accounts', component: () => import('@/views/AccountsView.vue'), meta: { title: 'Accounts' } },
	{ path: '/categories', name: 'categories', component: () => import('@/views/CategoriesView.vue'), meta: { title: 'Categories' } },
	{
		path: '/subscriptions',
		name: 'subscriptions',
		component: () => import('@/views/SubscriptionsView.vue'),
		meta: { title: 'Subscriptions' },
	},
	{ path: '/settings', name: 'settings', component: () => import('@/views/SettingsView.vue'), meta: { title: 'Settings' } },
	{
		path: '/save-the-change',
		name: 'save-the-change',
		component: () => import('@/views/SaveTheChangeView.vue'),
		meta: { title: 'Save the Change' },
	},
	{ path: '/:pathMatch(.*)*', redirect: '/' },
];

export const router = createRouter({
	history: createWebHistory(),
	routes,
	scrollBehavior: () => ({ top: 0 }),
});

router.beforeEach(async (to) => {
	// Wait for the SDK to finish restoring a session — and, when Auth0 has just
	// redirected back to the root with a `code`, to finish exchanging it. Acting
	// before that settles would bounce an arriving or reloading user to sign-in.
	await whenAuthReady();

	// The local copy belongs to one person. Settle whose it is before any view reads it.
	if (auth0.isAuthenticated.value) adoptCacheFor(auth0.user.value?.sub);

	if (!to.meta.public && !auth0.isAuthenticated.value) {
		return { name: 'login', query: to.fullPath === '/' ? {} : { redirect: to.fullPath } };
	}

	if (to.name === 'login' && auth0.isAuthenticated.value) {
		return { name: 'dashboard' };
	}
});

router.afterEach((to) => {
	document.title = to.meta.title ? `${to.meta.title} · yuuka` : 'yuuka';
});
