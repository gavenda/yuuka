<script setup lang="ts">
import NavDestination from '@/components/NavDestination.vue';
import NavRail from '@/components/NavRail.vue';
import SnackbarHost from '@/components/SnackbarHost.vue';
import { clearCache } from '@/lib/cache';
import {
	ACCOUNT_BALANCE_WALLET,
	ATTACH_MONEY,
	CATEGORY,
	DARK_MODE,
	DASHBOARD,
	LIGHT_MODE,
	MONEY_OFF,
	PIE_CHART,
	RECEIPT,
	SELL,
} from '@/lib/icons';
import { isOnline } from '@/lib/online';
import { useRail } from '@/lib/rail';
import { useAmountVisibility } from '@/lib/privacy';
import { applyUpdate, updateReady } from '@/lib/pwa';
import { clearSnackbars, showSnackbar } from '@/lib/snackbar';
import { fullSync } from '@/lib/sync';
import { useTheme } from '@/lib/theme';
import { useAuth0 } from '@auth0/auth0-vue';
import { useBudgetStore } from '@/stores/budget';
import { useLedgerStore } from '@/stores/ledger';
import { useSubscriptionStore } from '@/stores/subscriptions';
import { useTransactionStore } from '@/stores/transactions';
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';

const { isAuthenticated, isLoading, user, error, logout } = useAuth0();
const ledger = useLedgerStore();
const budget = useBudgetStore();
const transactions = useTransactionStore();
const subscriptions = useSubscriptionStore();
const router = useRouter();
const route = useRoute();
const { theme, toggle } = useTheme();
const { hidden: amountsHidden, toggle: toggleAmounts } = useAmountVisibility();
const userMenuOpen = ref(false);
const userMenuRoot = ref<HTMLElement | null>(null);

/** Every destination, as the rail lists them. A phone's bar has room for fewer; `phoneMenu: true` sends one to the avatar menu instead. */
const links = [
	{ to: '/', label: 'Dashboard', icon: DASHBOARD },
	{ to: '/transactions', label: 'Transactions', icon: RECEIPT },
	{ to: '/budget', label: 'Budget', icon: PIE_CHART, phoneMenu: true },
	{ to: '/accounts', label: 'Accounts', icon: ACCOUNT_BALANCE_WALLET },
	{ to: '/categories', label: 'Categories', icon: CATEGORY, phoneMenu: true },
	{ to: '/tags', label: 'Tags', icon: SELL, phoneMenu: true },
];

const phoneBarLinks = links.filter((link) => !link.phoneMenu);
const phoneMenuLinks = links.filter((link) => link.phoneMenu);

const showShell = computed(() => isAuthenticated.value);

/** The page makes room for the rail: a slim one always, an open one only where it fits beside the page. */
const { expanded: railExpanded } = useRail();
const contentInset = computed(() => {
	if (!showShell.value || isLoading.value) return '';
	return railExpanded.value ? 'sm:pl-20 lg:pl-72' : 'sm:pl-20';
});

/** The top app bar names the screen; the views underneath don't repeat it. */
const pageTitle = computed(() => (route.meta.title as string | undefined) ?? 'yuuka');

/** The phone's bar sits on the page's surface until content scrolls beneath it, then lifts a tonal step. */
const scrolled = ref(false);
function onScroll(): void {
	scrolled.value = window.scrollY > 0;
}
const appVersion = __APP_VERSION__;

/** Auth0 fills whichever of these the connection provides. */
const displayName = computed(() => user.value?.name ?? user.value?.nickname ?? user.value?.email ?? null);
const avatarInitial = computed(() => displayName.value?.trim().charAt(0).toUpperCase() || '?');

async function signOut(): Promise<void> {
	userMenuOpen.value = false;

	// Clear cached data first: logging out navigates away to Auth0, and the next
	// sign-in should not briefly show the previous session's books. That includes
	// the copy kept in the browser, which would otherwise outlast the session.
	clearSnackbars();
	ledger.reset();
	budget.reset();
	transactions.reset();
	subscriptions.reset();
	clearCache();

	// Ends the Auth0 session too, not just the local one.
	await logout({ logoutParams: { returnTo: window.location.origin } });
}

function onClickOutsideUserMenu(event: MouseEvent): void {
	if (userMenuRoot.value && !userMenuRoot.value.contains(event.target as Node)) userMenuOpen.value = false;
}

function onKeydownUserMenu(event: KeyboardEvent): void {
	if (event.key === 'Escape') userMenuOpen.value = false;
}

// A session that expires mid-visit should land on the sign-in screen, not on a
// wall of failed requests.
watch(isAuthenticated, (authenticated) => {
	if (!authenticated && router.currentRoute.value.meta.public !== true) {
		router.push({ name: 'login' });
	}
});

// Coming back online is the cue to catch up on whatever was showing a saved copy.
function onBackOnline(): void {
	if (isAuthenticated.value) void fullSync();
}

// A new build waits until it is taken, so it is offered as a message that stays until
// the person acts on it or waves it away.
watch(
	updateReady,
	(ready) => {
		if (ready) showSnackbar('A new version is ready.', { action: { label: 'Reload', run: applyUpdate }, duration: null });
	},
	{ immediate: true },
);

onMounted(() => {
	document.addEventListener('click', onClickOutsideUserMenu);
	window.addEventListener('online', onBackOnline);
	window.addEventListener('scroll', onScroll, { passive: true });
});
onBeforeUnmount(() => {
	document.removeEventListener('click', onClickOutsideUserMenu);
	window.removeEventListener('online', onBackOnline);
	window.removeEventListener('scroll', onScroll);
});
</script>

<template>
	<div class="min-h-dvh transition-[padding] duration-300 ease-emphasized-decelerate" :class="contentInset">
		<!-- With room for it, navigation moves to a rail down the side, and the bottom bar below
		     takes over on a phone. The rail opens into a drawer that also holds what is not a daily
		     destination: subscriptions, settings and signing out. -->
		<NavRail
			v-if="showShell && !isLoading"
			:links="links"
			:version="appVersion"
			:account="{ name: displayName, email: user?.email ?? null, picture: user?.picture ?? null, initial: avatarInitial }"
			@sign-out="signOut"
		/>

		<!-- Phones only. With a rail the current destination is already marked, so a bar naming the
		     screen would just repeat it; the heading below keeps the page titled for assistive tech. -->
		<h1 v-if="showShell && !isLoading" class="sr-only max-sm:hidden">{{ pageTitle }}</h1>

		<header
			v-if="showShell && !isLoading"
			class="sticky top-0 z-30 transition-colors sm:hidden"
			:class="scrolled ? 'bg-surface-container' : 'bg-surface'"
		>
			<div class="mx-auto flex h-16 max-w-6xl items-center gap-2 px-4">
				<RouterLink to="/" class="focus-ring shrink-0 rounded-full" aria-label="Dashboard">
					<img src="/yuuka.png" alt="" class="size-8 rounded-full object-cover" />
				</RouterLink>

				<h1 class="min-w-0 flex-1 truncate text-xl text-on-surface">{{ pageTitle }}</h1>

				<div class="flex shrink-0 items-center">
					<button
						type="button"
						class="btn-icon"
						:aria-label="amountsHidden ? 'Show amounts' : 'Hide amounts'"
						:aria-pressed="amountsHidden"
						:title="amountsHidden ? 'Show amounts' : 'Hide amounts'"
						@click="toggleAmounts"
					>
						<svg viewBox="0 0 24 24" class="h-6 w-6" fill="currentColor" aria-hidden="true">
							<path :d="amountsHidden ? MONEY_OFF : ATTACH_MONEY" />
						</svg>
					</button>

					<button type="button" class="btn-icon" :aria-label="`Switch to ${theme === 'dark' ? 'light' : 'dark'} mode`" @click="toggle">
						<svg viewBox="0 0 24 24" class="h-6 w-6" fill="currentColor" aria-hidden="true">
							<path :d="theme === 'dark' ? LIGHT_MODE : DARK_MODE" />
						</svg>
					</button>

					<div ref="userMenuRoot" class="relative ml-1" @keydown="onKeydownUserMenu">
						<button
							type="button"
							class="btn-icon"
							aria-haspopup="menu"
							:aria-expanded="userMenuOpen"
							aria-label="Account menu"
							@click="userMenuOpen = !userMenuOpen"
						>
							<img v-if="user?.picture" :src="user.picture" alt="" class="size-8 rounded-full" referrerpolicy="no-referrer" />
							<span
								v-else
								class="grid size-8 place-items-center rounded-full bg-primary-container text-sm font-medium text-on-primary-container"
								aria-hidden="true"
							>
								{{ avatarInitial }}
							</span>
						</button>

						<div v-if="userMenuOpen" role="menu" class="menu absolute right-0 z-40 mt-2 w-60">
							<div v-if="displayName" class="truncate border-b border-outline-variant px-3 pb-3">
								<span class="block truncate text-sm font-medium text-on-surface">{{ displayName }}</span>
								<span v-if="user?.email && user.email !== displayName" class="block truncate text-xs text-on-surface-variant">{{
									user.email
								}}</span>
							</div>

							<!-- Reached from here rather than the tab bar: it is set up once and
							     then left alone, unlike the pages that are visited every day. -->
							<!-- What the phone's bar has no room for. -->
							<RouterLink
								v-for="(link, index) in phoneMenuLinks"
								:key="link.to"
								:to="link.to"
								role="menuitem"
								class="menu-item"
								:class="index === 0 ? 'mt-2' : ''"
								@click="userMenuOpen = false"
							>
								{{ link.label }}
							</RouterLink>

							<RouterLink to="/subscriptions" role="menuitem" class="menu-item" @click="userMenuOpen = false"> Subscriptions </RouterLink>

							<RouterLink to="/save-the-change" role="menuitem" class="menu-item" @click="userMenuOpen = false">
								Save the Change
							</RouterLink>

							<RouterLink to="/settings" role="menuitem" class="menu-item" @click="userMenuOpen = false"> Settings </RouterLink>

							<button type="button" role="menuitem" class="menu-item" @click="signOut">Sign out</button>
						</div>
					</div>
				</div>
			</div>
		</header>

		<!-- Everything on screen is the copy this browser last saved; the API is what changes are
		     made against, so say so rather than letting a failed save be the first hint. -->
		<p v-if="showShell && !isLoading && !isOnline" role="status" class="bg-warning/10 px-4 py-2 text-center text-xs text-warning">
			You're offline. Showing what was last saved; changes need a connection.
		</p>

		<!-- On a cold load the SDK is still restoring the session, or exchanging
		     the code Auth0 just redirected back with; the router is waiting on it,
		     so say something rather than showing a blank page. -->
		<div v-if="isLoading" class="flex min-h-dvh items-center justify-center px-4">
			<p class="text-sm text-on-surface-variant">Loading…</p>
		</div>

		<div v-else-if="error && !isAuthenticated" class="flex min-h-dvh items-center justify-center px-4">
			<div class="card max-w-sm p-6 text-center">
				<p class="text-sm text-error" role="alert">{{ error.message }}</p>
				<RouterLink to="/login" class="btn-secondary mt-4">Back to sign in</RouterLink>
			</div>
		</div>

		<main v-else :class="showShell ? 'mx-auto max-w-6xl px-4 pt-2 pb-28 sm:pt-6 sm:pb-10' : ''">
			<RouterView v-slot="{ Component }">
				<Transition name="fade-through" mode="out-in">
					<component :is="Component" />
				</Transition>
			</RouterView>
		</main>

		<!-- Bottom bar keeps the primary navigation in thumb reach on a phone. -->
		<nav
			v-if="showShell && !isLoading"
			aria-label="Primary"
			class="fixed inset-x-0 bottom-0 z-30 flex bg-surface-container pt-3 pb-[max(1rem,env(safe-area-inset-bottom))] sm:hidden"
		>
			<NavDestination v-for="link in phoneBarLinks" :key="link.to" :to="link.to" :label="link.label" :icon="link.icon" />
		</nav>

		<SnackbarHost />
	</div>
</template>
