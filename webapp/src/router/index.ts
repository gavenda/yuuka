import { auth0, whenAuthReady } from '@/lib/auth0';
import { adoptCacheFor } from '@/lib/cache';
import { t } from '@/i18n';
import { discardQueue } from '@/lib/queue';
import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router';

/**
 * `meta.titleKey` is the message that names the screen. `meta.amountFree` marks one that shows no amount, where the hide-amounts
 * switch has nothing to mask and is left out (Android's `AMOUNT_FREE_ROUTES`). `meta.parent` is the screen a
 * screen is opened from: the rail keeps marking that one, and a phone's bar leads back to it.
 */
const routes: RouteRecordRaw[] = [
	{ path: '/login', name: 'login', component: () => import('@/views/LoginView.vue'), meta: { public: true, titleKey: 'nav.signIn' } },
	{ path: '/', name: 'dashboard', component: () => import('@/views/DashboardView.vue'), meta: { titleKey: 'nav.dashboard' } },
	{
		path: '/transactions',
		name: 'transactions',
		component: () => import('@/views/TransactionsView.vue'),
		meta: { titleKey: 'nav.transactions' },
	},
	{ path: '/budget', name: 'budget', component: () => import('@/views/BudgetView.vue'), meta: { titleKey: 'nav.budget' } },
	{ path: '/accounts', name: 'accounts', component: () => import('@/views/AccountsView.vue'), meta: { titleKey: 'nav.accounts' } },
	{
		path: '/categories',
		name: 'categories',
		component: () => import('@/views/CategoriesView.vue'),
		meta: { titleKey: 'nav.categories', amountFree: true },
	},
	{ path: '/tags', name: 'tags', component: () => import('@/views/TagsView.vue'), meta: { titleKey: 'nav.tags', amountFree: true } },
	{
		path: '/subscriptions',
		name: 'subscriptions',
		component: () => import('@/views/SubscriptionsView.vue'),
		meta: { titleKey: 'nav.subscriptions' },
	},
	{
		path: '/settings',
		name: 'settings',
		component: () => import('@/views/SettingsView.vue'),
		meta: { titleKey: 'nav.settings', amountFree: true },
	},
	{
		path: '/settings/account-types',
		name: 'account-types',
		component: () => import('@/views/AccountTypesView.vue'),
		meta: { titleKey: 'nav.accountTypes', amountFree: true, parent: '/settings' },
	},
	{
		path: '/save-the-change',
		name: 'save-the-change',
		component: () => import('@/views/SaveTheChangeView.vue'),
		meta: { titleKey: 'nav.saveTheChange', amountFree: true },
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
	// The unsent queue belongs to the same person, so a change of owner takes it
	// too: replaying one person's spending into another's books would be worse
	// than losing it.
	if (auth0.isAuthenticated.value && adoptCacheFor(auth0.user.value?.sub)) await discardQueue();

	if (!to.meta.public && !auth0.isAuthenticated.value) {
		return { name: 'login', query: to.fullPath === '/' ? {} : { redirect: to.fullPath } };
	}

	if (to.name === 'login' && auth0.isAuthenticated.value) {
		return { name: 'dashboard' };
	}
});

router.afterEach((to) => {
	document.title = to.meta.titleKey ? `${t(to.meta.titleKey as string)} · ${t('common.appName')}` : t('common.appName');
});
