<script setup lang="ts">
import CatPattern from '@/components/CatPattern.vue';
import AppIcon from '@/components/AppIcon.vue';
import { formatMonth } from '@/lib/dates';
import { currentFab, type FabEntry } from '@/lib/fab';
import { ATTACH_MONEY, CALENDAR_MONTH, LOGOUT, MENU, MENU_OPEN, MONEY_OFF } from '@/lib/icons';
import { currentMonthAction } from '@/lib/monthAction';
import { dailyLinks, navGroups, settingsLink } from '@/lib/nav';
import { useAmountVisibility } from '@/lib/privacy';
import { railDocked, railPushesContent, useRail } from '@/lib/rail';
import { Fab, NavigationItem, NavigationRail, NavigationRailMenuButton } from 'tailwind-material3';
import { computed, nextTick, onBeforeUnmount, onMounted, ref, shallowRef, watch } from 'vue';
import { RouterLink, useRoute } from 'vue-router';
import { t } from '@/i18n';

defineProps<{
	/** The app's version, shown small after the title once the rail is open. */
	version: string;
	/** Who is signed in, for the account card that shows once the rail is open. */
	account: { name: string | null; email: string | null; picture: string | null; initial: string };
}>();

const emit = defineEmits<{ 'sign-out': [] }>();

const { expanded, collapse, setExpanded } = useRail();
const route = useRoute();

/** A destination is current on its own screen, and on one opened from it (the account types, from Settings). */
function isCurrent(to: string): boolean {
	return route.path === to || route.meta.parent === to;
}

/**
 * The FAB in the slot is one persistent button, never rebuilt. A page bringing a FAB opens the slot and pops
 * the button in; when the last page's FAB goes it pops out and the slot closes. Between two pages that both
 * have one, the button folds back to its square, takes the new label, and unfolds again, with the slot
 * staying put. A page leaving cannot tell "another FAB is coming" from "none is", so it always starts by
 * folding, and only pops out if no new one turns up in time.
 */
const FOLD_MS = 250;
const FOLD_HOLD_MS = 200;

const shownFab = shallowRef<FabEntry | null>(null);
/** The slot: whether the space for the FAB is open, which is what pushes the destinations down. */
const fabOpen = ref(false);
/** The button itself: popped in, or popped out and waiting. */
const fabShown = ref(false);
/** Folded to a square while it changes label. Only visible when the rail is open, as a slim FAB is a square anyway. */
const fabFolded = ref(false);
const fabExpanded = computed(() => expanded.value && !fabFolded.value);
const slotEl = ref<HTMLElement | null>(null);

let fabTimer: ReturnType<typeof setTimeout> | undefined;
let foldedAt = 0;
/** Tells an opening that has since been overtaken to stand down. */
let fabGeneration = 0;

function foldFab(): void {
	if (fabFolded.value) return;
	fabFolded.value = true;
	foldedAt = performance.now();
}

async function presentFab(fab: FabEntry): Promise<void> {
	clearTimeout(fabTimer);
	const mine = ++fabGeneration;

	if (fabOpen.value) {
		foldFab();

		// The label changes once the fold has taken it out of sight, then the button unfolds.
		const wait = expanded.value ? Math.max(0, FOLD_MS - (performance.now() - foldedAt)) : 0;
		fabTimer = setTimeout(() => {
			shownFab.value = fab;
			fabShown.value = true;
			fabFolded.value = false;
		}, wait);
		return;
	}

	// Build the button in its popped-out state and let the browser settle on it before showing it; a
	// button created already showing would have nothing to animate from.
	fabFolded.value = false;
	shownFab.value = fab;
	await nextTick();
	void slotEl.value?.offsetHeight;
	if (mine !== fabGeneration) return;

	fabOpen.value = true;
	fabShown.value = true;
}

function dismissFab(): void {
	clearTimeout(fabTimer);
	fabGeneration++;
	foldFab();

	fabTimer = setTimeout(() => {
		fabShown.value = false;
		fabTimer = setTimeout(() => (fabOpen.value = false), 150);
	}, FOLD_HOLD_MS);
}

watch(currentFab, (fab) => (fab ? void presentFab(fab) : dismissFab()), { immediate: true });

function pressFab(): void {
	shownFab.value?.run();
	afterChoice();
}

const { hidden: amountsHidden, toggle: toggleAmounts } = useAmountVisibility();

/** Not on a screen that shows no amount, where the hide-amounts switch has nothing to mask. */
const showsAmounts = computed(() => route.meta.amountFree !== true);

/** The month on show, which labels the month button once the rail is open; null on a screen that looks at none. */
const month = computed(() => (currentMonthAction.value ? formatMonth(currentMonthAction.value.month) : null));

/** A floating rail is in the way once it has done its job; a docked one stays put. */
function afterChoice(): void {
	if (!railPushesContent()) collapse();
}

function onKeydown(event: KeyboardEvent): void {
	if (event.key !== 'Escape') return;
	if (expanded.value) collapse();
}

/**
 * The open rail is as wide as what it holds — the longest label, the account card — as Material's wide rail is,
 * between 220 and 360 (tailwind-material3's `NavigationRail` sizes itself). Docked, the page moves aside by as much:
 * the rail's width is read on every frame it changes and handed to the page as `--rail-open`, so the page follows
 * the rail's own spring rather than a curve of its own.
 */
const railEl = ref<HTMLElement | null>(null);
let observer: ResizeObserver | undefined;

function measure(): void {
	const width = railEl.value?.querySelector('nav')?.offsetWidth;
	if (!width) return;
	document.documentElement.style.setProperty('--rail-open', `${width}px`);
}

onMounted(() => {
	const nav = railEl.value?.querySelector('nav');
	measure();
	if (nav && typeof ResizeObserver !== 'undefined') {
		observer = new ResizeObserver(measure);
		observer.observe(nav);
	}
});

onBeforeUnmount(() => {
	clearTimeout(fabTimer);
	observer?.disconnect();
});
</script>

<template>
	<!--
	  tailwind-material3's `NavigationRail`, which morphs between slim (96px) and open by itself: items' pills grow to
	  hug icon and label, labels cross-fade from under the icons to beside them, and icons keep their place. Docked
	  (from 1024px) the open rail pushes the page aside; below that it is modal, floating over the page behind a scrim
	  that closes it.
	-->
	<div
		ref="railEl"
		class="fixed inset-y-0 left-0 z-40 hidden sm:block [&_nav>div:last-child]:min-h-0 [&_nav>div:last-child]:flex-1"
		@keydown="onKeydown"
	>
		<NavigationRail
			id="primary-rail"
			:expanded="expanded"
			:modal="!railDocked"
			:label="t('nav.primary')"
			class="h-full"
			@update:expanded="setExpanded"
		>
			<template #header>
				<!-- The app's mark, in either state, with its name and version beside it once the rail is open. It is
				     a mark rather than a control (no link, and never the menu button, which sits beneath it), so it
				     cannot be mistaken for one. The rail's destinations are the way home. -->
				<div class="relative isolate ms-2 flex items-center pb-3" aria-hidden="true">
					<!-- Only in the open rail: a strip of the print behind the name, from the rail's top edge down to
					     the menu button and thinning out towards the page. -->
					<CatPattern
						class="rail-fade absolute -top-16 -left-7 -z-10 h-[calc(100%+4rem)] w-[220px]"
						print="small"
						fade="trailing"
						:data-shown="expanded"
					/>
					<img src="/yuuka.png" alt="" class="size-10 shrink-0 rounded-full object-cover" />
					<span class="rail-fade flex items-baseline gap-1.5 pr-4 pl-3 whitespace-nowrap text-on-surface" :data-shown="expanded">
						<span class="type-title-large">yuuka</span>
						<span class="type-label-small">v{{ version }}</span>
					</span>
				</div>

				<NavigationRailMenuButton class="text-on-surface!" :expand-label="t('nav.expand')" :collapse-label="t('nav.collapse')">
					<template #menu-icon><AppIcon :icon="MENU" /></template>
					<template #menu-open-icon><AppIcon :icon="MENU_OPEN" /></template>
				</NavigationRailMenuButton>

				<!-- The screen's leading action, under the menu as Material places it. It has a slot of its own: when a
				     page brings a FAB the slot opens and the destinations slide down, and the FAB pops in; when it goes,
				     the reverse. The same button in both rail states: a square while slim, an extended FAB when open. -->
				<div ref="slotEl" class="rail-slot" :data-open="fabOpen" :inert="!fabOpen">
					<div>
						<Fab v-if="shownFab" class="rail-fab" :data-shown="fabShown" :expanded="fabExpanded" :title="shownFab.label" @click="pressFab">
							<template #icon><AppIcon :icon="shownFab.icon" /></template>
							{{ shownFab.label }}
						</Fab>
					</div>
				</div>
			</template>

			<!-- One scrolling column: on a short window the open rail would otherwise run off the bottom. -->
			<div class="-mx-2 min-h-0 flex-1 overflow-x-hidden overflow-y-auto px-2 [scrollbar-width:none]">
				<NavigationItem
					v-for="link in dailyLinks"
					:key="link.to"
					:as="RouterLink"
					:to="link.to"
					:label="link.label"
					:active="isCurrent(link.to)"
					@click="afterChoice"
				>
					<template #icon><AppIcon :icon="link.icon" /></template>
					<template v-if="link.iconSelected" #active-icon><AppIcon :icon="link.iconSelected" /></template>
				</NavigationItem>

				<!-- Only in the open rail: it grows out of nothing rather than appearing, and is inert while shut. -->
				<div class="rail-collapse" :data-open="expanded" :inert="!expanded">
					<div>
						<template v-for="group in navGroups" :key="group.label">
							<h2 class="type-title-small pt-4 pb-2 pl-6 whitespace-nowrap text-on-surface">{{ group.label }}</h2>
							<NavigationItem
								v-for="link in group.links"
								:key="link.to"
								:as="RouterLink"
								:to="link.to"
								:label="link.label"
								:active="isCurrent(link.to)"
								@click="afterChoice"
							>
								<template #icon><AppIcon :icon="link.icon" /></template>
								<template v-if="link.iconSelected" #active-icon><AppIcon :icon="link.iconSelected" /></template>
							</NavigationItem>
						</template>

						<!-- About the app rather than the money, so no heading: a gap sets them apart. -->
						<NavigationItem
							class="mt-4"
							:as="RouterLink"
							:to="settingsLink.to"
							:label="settingsLink.label"
							:active="isCurrent(settingsLink.to)"
							@click="afterChoice"
						>
							<template #icon><AppIcon :icon="settingsLink.icon" /></template>
							<template v-if="settingsLink.iconSelected" #active-icon><AppIcon :icon="settingsLink.iconSelected" /></template>
						</NavigationItem>

						<NavigationItem
							:label="t('common.signOut')"
							@click="
								afterChoice();
								emit('sign-out');
							"
						>
							<template #icon><AppIcon :icon="LOGOUT" /></template>
						</NavigationItem>
					</div>
				</div>
			</div>

			<!-- The foot stays at the bottom of the rail however far the destinations above scroll. The display switch
			     is in reach in both states: a phone keeps it in its top bar, but a wide window has none. -->
			<div class="flex-none pt-4 pb-10">
				<!-- Not on a screen that shows no amount, where there is nothing for it to mask. -->
				<NavigationItem
					v-if="showsAmounts"
					:label="amountsHidden ? t('common.showAmounts') : t('common.hideAmounts')"
					:aria-pressed="amountsHidden"
					:title="amountsHidden ? t('common.showAmounts') : t('common.hideAmounts')"
					@click="toggleAmounts"
				>
					<template #icon><AppIcon :icon="amountsHidden ? MONEY_OFF : ATTACH_MONEY" /></template>
				</NavigationItem>

				<!-- On a screen that is looking at a month, the button that opens its month picker (`MonthAction`),
				     which a phone keeps in the bar after the switch above. -->
				<NavigationItem
					v-if="currentMonthAction && month"
					:label="month"
					aria-haspopup="dialog"
					:aria-label="t('common.months.choose', { month })"
					:title="t('common.months.choose', { month })"
					@click="
						afterChoice();
						currentMonthAction.open();
					"
				>
					<template #icon><AppIcon :icon="CALENDAR_MONTH" /></template>
				</NavigationItem>

				<div class="rail-collapse" :data-open="expanded && !!account.name" :inert="!expanded">
					<div>
						<div
							class="relative isolate mx-4 mt-4 flex w-fit items-center gap-3 overflow-hidden rounded-xl bg-surface-container-highest px-4 py-3 text-on-surface-variant"
						>
							<CatPattern class="absolute inset-0 -z-10 size-full" print="small" fade="none" />
							<img
								v-if="account.picture"
								:src="account.picture"
								alt=""
								class="size-10 shrink-0 rounded-full object-cover"
								referrerpolicy="no-referrer"
							/>
							<span
								v-else
								class="type-title-small grid size-10 shrink-0 place-items-center rounded-full bg-primary-container text-on-primary-container"
								aria-hidden="true"
							>
								{{ account.initial }}
							</span>

							<span class="max-w-[136px] min-w-0">
								<span class="type-title-medium block truncate">{{ account.name }}</span>
								<span v-if="account.email && account.email !== account.name" class="type-body-small block truncate">{{
									account.email
								}}</span>
							</span>
						</div>
					</div>
				</div>
			</div>
		</NavigationRail>
	</div>
</template>
