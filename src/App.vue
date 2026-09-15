<script setup lang="ts">
import ModalDialog from '@/components/ModalDialog.vue';
import SettingsDialog from '@/components/SettingsDialog.vue';
import { useAmountVisibility } from '@/lib/privacy';
import { useTheme } from '@/lib/theme';
import { useAuth0 } from '@auth0/auth0-vue';
import { useBudgetStore } from '@/stores/budget';
import { useLedgerStore } from '@/stores/ledger';
import { useTransactionStore } from '@/stores/transactions';
import { computed, ref, watch } from 'vue';
import { useRouter } from 'vue-router';

const { isAuthenticated, isLoading, user, error, logout } = useAuth0();
const ledger = useLedgerStore();
const budget = useBudgetStore();
const transactions = useTransactionStore();
const router = useRouter();
const { theme, toggle } = useTheme();
const { hidden: amountsHidden, toggle: toggleAmounts } = useAmountVisibility();
const settingsOpen = ref(false);

const links = [
	{ to: '/', label: 'Dashboard', icon: 'M3 10l7-7 7 7v8a1 1 0 01-1 1h-4v-5H8v5H4a1 1 0 01-1-1v-8z' },
	{ to: '/transactions', label: 'Transactions', icon: 'M4 6h12M4 10h12M4 14h8' },
	{ to: '/budget', label: 'Budget', icon: 'M3 16V8m5 8V4m5 12v-6m5 6V7' },
	{ to: '/accounts', label: 'Accounts', icon: 'M2 6a2 2 0 012-2h12a2 2 0 012 2v8a2 2 0 01-2 2H4a2 2 0 01-2-2V6zm0 3h16' },
	{ to: '/categories', label: 'Categories', icon: 'M4 4h5v5H4V4zm7 0h5v5h-5V4zM4 11h5v5H4v-5zm7 0h5v5h-5v-5z' },
];

const showShell = computed(() => isAuthenticated.value);

/** Auth0 fills whichever of these the connection provides. */
const displayName = computed(() => user.value?.name ?? user.value?.nickname ?? user.value?.email ?? null);

async function signOut(): Promise<void> {
	// Clear cached data first: logging out navigates away to Auth0, and the next
	// sign-in should not briefly show the previous session's books.
	ledger.reset();
	budget.reset();
	transactions.reset();

	// Ends the Auth0 session too, not just the local one.
	await logout({ logoutParams: { returnTo: window.location.origin } });
}

// A session that expires mid-visit should land on the sign-in screen, not on a
// wall of failed requests.
watch(isAuthenticated, (authenticated) => {
	if (!authenticated && router.currentRoute.value.meta.public !== true) {
		router.push({ name: 'login' });
	}
});
</script>

<template>
	<div class="min-h-dvh">
		<header
			v-if="showShell && !isLoading"
			class="sticky top-0 z-30 border-b border-slate-200 bg-white/90 backdrop-blur dark:border-slate-800 dark:bg-slate-950/90"
		>
			<div class="mx-auto flex max-w-6xl items-center gap-4 px-4 py-3">
				<RouterLink to="/" class="flex items-center gap-2 font-semibold tracking-tight text-slate-900 dark:text-white">
					<span class="grid h-7 w-7 place-items-center rounded-lg bg-emerald-600 text-sm text-white">¥</span>
					yuuka
				</RouterLink>

				<nav class="ml-4 hidden gap-1 sm:flex">
					<RouterLink
						v-for="link in links"
						:key="link.to"
						:to="link.to"
						class="rounded-lg px-3 py-1.5 text-sm font-medium text-slate-600 transition-colors hover:bg-slate-100 hover:text-slate-900 dark:text-slate-400 dark:hover:bg-slate-800 dark:hover:text-white"
						active-class="!bg-emerald-50 !text-emerald-700 dark:!bg-emerald-500/10 dark:!text-emerald-400"
					>
						{{ link.label }}
					</RouterLink>
				</nav>

				<div class="ml-auto flex items-center gap-1">
					<!-- Whose books these are: worth stating plainly once the app holds
					     more than one person's data. -->
					<span v-if="displayName" class="mr-1 hidden items-center gap-2 sm:flex">
						<img v-if="user?.picture" :src="user.picture" alt="" class="h-6 w-6 rounded-full ring-1 ring-slate-200 dark:ring-slate-700" />
						<span class="max-w-[10rem] truncate text-sm text-slate-600 dark:text-slate-400">{{ displayName }}</span>
					</span>

					<button
						type="button"
						class="btn-ghost px-2 py-1.5"
						:aria-label="amountsHidden ? 'Show amounts' : 'Hide amounts'"
						:aria-pressed="amountsHidden"
						:title="amountsHidden ? 'Show amounts' : 'Hide amounts'"
						@click="toggleAmounts"
					>
						<svg
							v-if="amountsHidden"
							viewBox="0 0 20 20"
							class="h-4.5 w-4.5"
							fill="none"
							stroke="currentColor"
							stroke-width="1.5"
							aria-hidden="true"
						>
							<path
								d="M7.5 4.4A7.4 7.4 0 0 1 10 4c4 0 7 4 7 6a8.5 8.5 0 0 1-1.7 2.6M5.2 6.3C3.8 7.5 3 9.2 3 10c0 2 3 6 7 6a7.6 7.6 0 0 0 3-.6"
								stroke-linecap="round"
							/>
							<path d="M3 3l14 14" stroke-linecap="round" />
						</svg>

						<svg v-else viewBox="0 0 20 20" class="h-4.5 w-4.5" fill="none" stroke="currentColor" stroke-width="1.5" aria-hidden="true">
							<path d="M10 4c4 0 7 4 7 6s-3 6-7 6-7-4-7-6 3-6 7-6z" />
							<circle cx="10" cy="10" r="2.25" />
						</svg>
					</button>

					<button type="button" class="btn-ghost px-2 py-1.5" aria-label="Settings" @click="settingsOpen = true">
						<svg viewBox="0 0 24 24" class="h-4.5 w-4.5" fill="none" stroke="currentColor" stroke-width="1.8" aria-hidden="true">
							<path
								stroke-linecap="round"
								stroke-linejoin="round"
								d="M9.594 3.94c.09-.542.56-.94 1.11-.94h2.593c.55 0 1.02.398 1.11.94l.213 1.281c.063.374.313.686.645.87.074.04.147.083.22.127.324.196.72.257 1.075.124l1.217-.456a1.125 1.125 0 0 1 1.37.49l1.296 2.247a1.125 1.125 0 0 1-.26 1.431l-1.003.827c-.293.24-.438.613-.431.992a6.759 6.759 0 0 1 0 .255c-.007.378.138.75.43.99l1.005.828c.424.35.534.954.26 1.43l-1.298 2.247a1.125 1.125 0 0 1-1.369.491l-1.217-.456c-.355-.133-.75-.072-1.076.124a6.57 6.57 0 0 1-.22.128c-.331.183-.581.495-.644.869l-.213 1.28c-.09.543-.56.941-1.11.941h-2.594c-.55 0-1.02-.398-1.11-.94l-.213-1.281c-.062-.374-.312-.686-.644-.87a6.52 6.52 0 0 1-.22-.127c-.325-.196-.72-.257-1.076-.124l-1.217.456a1.125 1.125 0 0 1-1.369-.49l-1.297-2.247a1.125 1.125 0 0 1 .26-1.431l1.004-.827c.292-.24.437-.613.43-.992a6.932 6.932 0 0 1 0-.255c.007-.378-.138-.75-.43-.99l-1.004-.828a1.125 1.125 0 0 1-.26-1.43l1.297-2.247a1.125 1.125 0 0 1 1.37-.491l1.216.456c.356.133.751.072 1.076-.124.072-.044.146-.087.22-.128.332-.183.582-.495.644-.869l.214-1.281Z"
							/>
							<path stroke-linecap="round" stroke-linejoin="round" d="M15 12a3 3 0 1 1-6 0 3 3 0 0 1 6 0Z" />
						</svg>
					</button>

					<button
						type="button"
						class="btn-ghost px-2 py-1.5"
						:aria-label="`Switch to ${theme === 'dark' ? 'light' : 'dark'} mode`"
						@click="toggle"
					>
						<svg v-if="theme === 'dark'" viewBox="0 0 20 20" class="h-4.5 w-4.5" fill="currentColor" aria-hidden="true">
							<path
								d="M10 3V1m0 18v-2m7-7h2M1 10h2m12.07-5.07l1.42-1.42M3.51 16.49l1.42-1.42m0-10.14L3.51 3.51m12.98 12.98l-1.42-1.42M10 6a4 4 0 100 8 4 4 0 000-8z"
								stroke="currentColor"
								stroke-width="1.5"
								fill="none"
								stroke-linecap="round"
							/>
						</svg>
						<svg v-else viewBox="0 0 20 20" class="h-4.5 w-4.5" fill="currentColor" aria-hidden="true">
							<path d="M17 11.5A7.5 7.5 0 018.5 3a7.5 7.5 0 108.5 8.5z" />
						</svg>
					</button>

					<button type="button" class="btn-ghost px-2 py-1.5 text-sm" @click="signOut">Sign out</button>
				</div>
			</div>
		</header>

		<!-- On a cold load the SDK is still restoring the session, or exchanging
		     the code Auth0 just redirected back with; the router is waiting on it,
		     so say something rather than showing a blank page. -->
		<div v-if="isLoading" class="flex min-h-dvh items-center justify-center px-4">
			<p class="text-sm text-slate-500 dark:text-slate-400">Loading…</p>
		</div>

		<div v-else-if="error && !isAuthenticated" class="flex min-h-dvh items-center justify-center px-4">
			<div class="card max-w-sm p-6 text-center">
				<p class="text-sm text-rose-700 dark:text-rose-400" role="alert">{{ error.message }}</p>
				<RouterLink to="/login" class="btn-secondary mt-4">Back to sign in</RouterLink>
			</div>
		</div>

		<main v-else :class="showShell ? 'mx-auto max-w-6xl px-4 pt-6 pb-24 sm:pb-10' : ''">
			<RouterView />
		</main>

		<ModalDialog :open="settingsOpen" title="Settings" @close="settingsOpen = false">
			<SettingsDialog :open="settingsOpen" @close="settingsOpen = false" />
		</ModalDialog>

		<!-- Bottom tabs keep the primary navigation in thumb reach on a phone. -->
		<nav
			v-if="showShell && !isLoading"
			class="fixed inset-x-0 bottom-0 z-30 border-t border-slate-200 bg-white/95 backdrop-blur sm:hidden dark:border-slate-800 dark:bg-slate-950/95"
		>
			<div class="flex">
				<RouterLink
					v-for="link in links"
					:key="link.to"
					:to="link.to"
					class="flex flex-1 flex-col items-center gap-1 py-2 text-[10px] font-medium text-slate-500 dark:text-slate-400"
					active-class="!text-emerald-600 dark:!text-emerald-400"
				>
					<svg viewBox="0 0 20 20" class="h-5 w-5" fill="none" stroke="currentColor" stroke-width="1.5" aria-hidden="true">
						<path :d="link.icon" stroke-linecap="round" stroke-linejoin="round" />
					</svg>
					{{ link.label }}
				</RouterLink>
			</div>
		</nav>
	</div>
</template>
