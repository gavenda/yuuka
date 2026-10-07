<script setup lang="ts">
import AlertDialog from '@/components/AlertDialog.vue';
import CatMark from '@/components/CatMark.vue';
import CatPattern from '@/components/CatPattern.vue';
import NavDestination from '@/components/NavDestination.vue';
import NavRail from '@/components/NavRail.vue';
import SnackbarHost from '@/components/SnackbarHost.vue';
import { CAT } from '@/lib/cat';
import { clearCache } from '@/lib/cache';
import AppIcon from '@/components/AppIcon.vue';
import { ACCOUNT_BALANCE_WALLET, ARROW_BACK, ATTACH_MONEY, CATEGORY, DASHBOARD, MONEY_OFF, PIE_CHART, RECEIPT, SELL } from '@/lib/icons';
import { isOnline } from '@/lib/online';
import { useRail } from '@/lib/rail';
import { useAmountVisibility } from '@/lib/privacy';
import { applyUpdate, updateReady } from '@/lib/pwa';
import { clearSnackbars, showSnackbar } from '@/lib/snackbar';
import { fullSync, installSliceRefresher } from '@/lib/sync';
import { discardQueue, flush, hasUnsentChanges, isSyncing, startQueue, unsentChanges } from '@/lib/queue';
import { startPush, stopPush } from '@/lib/push';
import { setLedgerView } from '@/lib/provisional';
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
const { hidden: amountsHidden, toggle: toggleAmounts } = useAmountVisibility();
const userMenuOpen = ref(false);
const userMenuRoot = ref<HTMLElement | null>(null);

/**
 * Every destination. The daily ones sit in the rail and the phone's bar; `more: true` sends one to the rail's "More"
 * group and, on a phone, to the avatar menu instead.
 */
const links = [
	{ to: '/', label: 'Dashboard', icon: DASHBOARD },
	{ to: '/transactions', label: 'Transactions', icon: RECEIPT },
	{ to: '/budget', label: 'Budget', icon: PIE_CHART, more: true },
	{ to: '/accounts', label: 'Accounts', icon: ACCOUNT_BALANCE_WALLET },
	{ to: '/categories', label: 'Categories', icon: CATEGORY, more: true },
	{ to: '/tags', label: 'Tags', icon: SELL, more: true },
];

const dailyLinks = links.filter((link) => !link.more);
const moreLinks = links.filter((link) => link.more);

const showShell = computed(() => isAuthenticated.value);

/** The page makes room for the rail (`.shell` in style.css): a slim one always, an open one only where it fits beside the page. */
const { expanded: railExpanded } = useRail();
const shellShown = computed(() => showShell.value && !isLoading.value);

/** The top app bar names the screen; the views underneath don't repeat it. */
const pageTitle = computed(() => (route.meta.title as string | undefined) ?? 'yuuka');
const parentPath = computed(() => route.meta.parent as string | undefined);

/** The phone's bar sits on the page's background until content scrolls beneath it, then lifts a tonal step. */
const scrolled = ref(false);
function onScroll(): void {
	scrolled.value = window.scrollY > 0;
}
const appVersion = __APP_VERSION__;

/** Auth0 fills whichever of these the connection provides. */
const displayName = computed(() => user.value?.name ?? user.value?.nickname ?? user.value?.email ?? null);
const avatarInitial = computed(() => displayName.value?.trim().charAt(0).toUpperCase() || '?');

/** Signing out discards the queue, so it asks first rather than taking unsent work with it on a slip. */
const confirmingSignOut = ref(false);
function askSignOut(): void {
	userMenuOpen.value = false;
	confirmingSignOut.value = true;
}

async function signOut(): Promise<void> {
	confirmingSignOut.value = false;

	// Clear cached data first: logging out navigates away to Auth0, and the next
	// sign-in should not briefly show the previous session's books. That includes
	// the copy kept in the browser, which would otherwise outlast the session.
	clearSnackbars();
	ledger.reset();
	budget.reset();
	transactions.reset();
	subscriptions.reset();
	clearCache();
	// Unsent changes go with the session that made them, and this install stops
	// being worth waking.
	await discardQueue();
	await stopPush();

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
// The queue listens for the same event on its own; this is the read half.
function onBackOnline(): void {
	if (isAuthenticated.value) void fullSync();
}

/**
 * What the offline queue needs to know to build a provisional row — an
 * account's name, a category's colour, the balance a transaction moves. It is
 * handed over rather than imported, because everything that holds it imports
 * the API, which is what asks.
 */
setLedgerView(() => ({
	accounts: ledger.accounts,
	accountTypes: ledger.accountTypes,
	categories: ledger.categories,
	tags: ledger.tags,
	roundUpRule: ledger.roundUpRule,
	settings: ledger.settings,
	displayCurrency: ledger.displayCurrency,
	transaction: (id: string) => transactions.transactions.find((row: { id: string }) => row.id === id) ?? null,
	subscription: (id: string) => subscriptions.subscriptions.find((row: { id: string }) => row.id === id) ?? null,
}));

installSliceRefresher();

// Draining the queue and registering for pushes both need a session, so they
// wait for one rather than starting with the page.
watch(
	isAuthenticated,
	(authenticated) => {
		if (!authenticated) return;
		startQueue();
		void startPush();
	},
	{ immediate: true },
);

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
	<div class="min-h-dvh" :class="{ shell: shellShown }" :data-rail-open="railExpanded">
		<!-- With room for it, navigation moves to a rail down the side, and the bottom bar below
		     takes over on a phone. The rail opens into a drawer that also holds what is not a daily
		     destination: budget, categories, tags, subscriptions, settings and signing out. -->
		<NavRail
			v-if="shellShown"
			:links="dailyLinks"
			:more-links="moreLinks"
			:version="appVersion"
			:account="{ name: displayName, email: user?.email ?? null, picture: user?.picture ?? null, initial: avatarInitial }"
			@sign-out="askSignOut"
		/>

		<!-- Phones only. With a rail the current destination is already marked, so a bar naming the
		     screen would just repeat it; the heading below keeps the page titled for assistive tech. -->
		<h1 v-if="shellShown" class="sr-only max-sm:hidden">{{ pageTitle }}</h1>

		<header
			v-if="shellShown"
			class="sticky top-0 z-30 transition-colors sm:hidden"
			:class="scrolled ? 'bg-surface-container' : 'bg-background'"
		>
			<div class="flex h-16 items-center gap-2 px-4">
				<!-- A screen opened from another leads back to it, where the others lead home. -->
				<RouterLink v-if="parentPath" :to="parentPath" class="btn-icon -ml-2 text-on-surface" aria-label="Back">
					<AppIcon :icon="ARROW_BACK" />
				</RouterLink>
				<RouterLink v-else to="/" class="focus-ring shrink-0 rounded-full" aria-label="Dashboard">
					<img src="/yuuka.png" alt="" class="size-8 rounded-full object-cover" />
				</RouterLink>

				<h1 class="type-title-large min-w-0 flex-1 truncate text-on-surface">{{ pageTitle }}</h1>

				<div class="flex shrink-0 items-center">
					<!-- Not on a screen that shows no amount, where there is nothing for it to mask. -->
					<button
						v-if="route.meta.amountFree !== true"
						type="button"
						class="btn-icon"
						:aria-label="amountsHidden ? 'Show amounts' : 'Hide amounts'"
						:aria-pressed="amountsHidden"
						:title="amountsHidden ? 'Show amounts' : 'Hide amounts'"
						@click="toggleAmounts"
					>
						<AppIcon :icon="amountsHidden ? MONEY_OFF : ATTACH_MONEY" />
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
								class="type-title-small grid size-8 place-items-center rounded-full bg-primary-container text-on-primary-container"
								aria-hidden="true"
							>
								{{ avatarInitial }}
							</span>
						</button>

						<div v-if="userMenuOpen" role="menu" class="menu absolute right-0 z-40 mt-2 w-60">
							<div v-if="displayName" class="truncate border-b border-outline-variant px-3 pb-3">
								<span class="type-title-small block truncate text-on-surface">{{ displayName }}</span>
								<span v-if="user?.email && user.email !== displayName" class="type-body-small block truncate text-on-surface-variant">{{
									user.email
								}}</span>
							</div>

							<!-- What the phone's bar has no room for: the less-visited screens, then what is set up
							     once and then left alone. -->
							<RouterLink
								v-for="(link, index) in moreLinks"
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

							<button type="button" role="menuitem" class="menu-item" @click="askSignOut">Sign out</button>
						</div>
					</div>
				</div>
			</div>
		</header>

		<!-- On a cold load the SDK is still restoring the session, or exchanging
		     the code Auth0 just redirected back with; the router is waiting on it,
		     so say something rather than showing a blank page. -->
		<div v-if="isLoading" class="relative isolate flex min-h-dvh items-center justify-center px-4">
			<!-- The sign-in screen's print, so the wait before it and after it is the same place. -->
			<CatPattern class="absolute inset-0 -z-10 size-full" />
			<p class="type-body-medium text-on-surface-variant">Loading…</p>
		</div>

		<div v-else-if="error && !isAuthenticated" class="flex min-h-dvh items-center justify-center px-4">
			<div class="card flex max-w-sm flex-col items-center p-6 text-center">
				<CatMark class="mb-4" />
				<p class="type-body-medium text-error" role="alert">{{ error.message }}</p>
				<RouterLink to="/login" class="btn-secondary mt-4">Back to sign in</RouterLink>
			</div>
		</div>

		<main v-else :class="showShell ? 'max-sm:pb-20' : ''">
			<!-- What stays put while the page scrolls beneath it, as a screen's top bar does on Android: a
			     screen teleports its own bar here (`TopBar`), and what has not reached the server yet is said
			     beneath it, whichever screen is showing. -->
			<div v-if="showShell" class="sticky top-16 z-20 bg-background sm:top-0">
				<div id="top-bar" />

				<!-- Changes are saved here first and sent after, so being offline is no longer a reason a
				     save can fail — but it is still worth saying that what is on screen has not reached the
				     server yet, and how much of it. The count is the queue's, not a guess. Worth noticing,
				     but neither a failure nor an action: tertiary, not error or primary. -->
				<p
					v-if="!isOnline || hasUnsentChanges"
					role="status"
					class="type-label-large bg-tertiary-container px-4 py-2 text-center text-on-tertiary-container"
				>
					<template v-if="hasUnsentChanges">
						<template v-if="isSyncing">Syncing {{ unsentChanges }} {{ unsentChanges === 1 ? 'change' : 'changes' }}…</template>
						<template v-else>
							{{ unsentChanges }} {{ unsentChanges === 1 ? 'change' : 'changes' }} saved on this device, waiting to sync
							<button v-if="isOnline" type="button" class="ml-1 underline underline-offset-2" @click="flush()">Try now</button>
						</template>
					</template>
					<template v-else>You're offline. Showing what was last saved; changes are kept here until there is a connection.</template>
				</p>
			</div>

			<RouterView v-slot="{ Component }">
				<Transition name="fade-through" mode="out-in">
					<component :is="Component" />
				</Transition>
			</RouterView>
		</main>

		<!-- Bottom bar keeps the primary navigation in thumb reach on a phone. -->
		<nav
			v-if="shellShown"
			aria-label="Primary"
			class="fixed inset-x-0 bottom-0 z-30 flex bg-surface-container pt-3 pb-[max(1rem,env(safe-area-inset-bottom))] sm:hidden"
		>
			<NavDestination v-for="link in dailyLinks" :key="link.to" :to="link.to" :label="link.label" :icon="link.icon" />
		</nav>

		<!-- Only a sign-out that loses work is a destructive one, so only that takes the error colour. -->
		<AlertDialog :open="confirmingSignOut" title="Sign out?" :icon="CAT" :danger="hasUnsentChanges" @close="confirmingSignOut = false">
			<template v-if="hasUnsentChanges">
				{{ unsentChanges }} {{ unsentChanges === 1 ? 'change' : 'changes' }} saved on this device
				{{ unsentChanges === 1 ? 'has' : 'have' }} not been synced yet. Signing out now will lose {{ unsentChanges === 1 ? 'it' : 'them' }}.
			</template>
			<template v-else>You will need to sign in again to see your ledger on this device.</template>
			<template #actions>
				<button type="button" class="btn-text" @click="confirmingSignOut = false">No, stay signed in</button>
				<button type="button" class="btn-text" :class="{ 'text-error': hasUnsentChanges }" @click="signOut">Yes, sign out</button>
			</template>
		</AlertDialog>

		<SnackbarHost />
	</div>
</template>
