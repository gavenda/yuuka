<script setup lang="ts">
import CatPattern from '@/components/CatPattern.vue';
import AppIcon from '@/components/AppIcon.vue';
import RailItem from '@/components/RailItem.vue';
import { currentFab, type FabAction, type FabEntry } from '@/lib/fab';
import { ATTACH_MONEY, EVENT_REPEAT, LOGOUT, MENU, MENU_OPEN, MONEY_OFF, SAVINGS, SETTINGS, type IconPath } from '@/lib/icons';
import { useAmountVisibility } from '@/lib/privacy';
import { railPushesContent, useRail } from '@/lib/rail';
import { computed, nextTick, onBeforeUnmount, onMounted, ref, shallowRef, watch } from 'vue';
import { useRoute } from 'vue-router';
import { t } from '@/i18n';

export interface RailLink {
	to: string;
	label: string;
	icon: IconPath;
}

const props = defineProps<{
	links: RailLink[];
	/** The destinations that are not daily ones, first in the open rail's "More" group, ahead of the ones set up once. */
	moreLinks: RailLink[];
	/** The app's version, shown small after the title once the rail is open. */
	version: string;
	/** Who is signed in, for the account card that shows once the rail is open. */
	account: { name: string | null; email: string | null; picture: string | null; initial: string };
}>();

const emit = defineEmits<{ 'sign-out': [] }>();

const { expanded, toggle, collapse } = useRail();
const route = useRoute();

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
const fabEl = ref<HTMLElement | null>(null);

/**
 * A FAB with `actions` lists them in a menu instead of running. The rail clips what it holds, and a slim
 * one is narrower than the menu, so the menu is drawn over the page from where the button is.
 */
const menuAt = ref<{ top: number; left: number } | null>(null);

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
	menuAt.value = null;

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
	menuAt.value = null;
	foldFab();

	fabTimer = setTimeout(() => {
		fabShown.value = false;
		fabTimer = setTimeout(() => (fabOpen.value = false), 150);
	}, FOLD_HOLD_MS);
}

watch(currentFab, (fab) => (fab ? void presentFab(fab) : dismissFab()), { immediate: true });

function pressFab(): void {
	const fab = shownFab.value;
	if (!fab || fab.disabled) return;

	if (!fab.actions.length) {
		fab.run();
		afterChoice();
		return;
	}

	const box = fabEl.value?.getBoundingClientRect();
	menuAt.value = menuAt.value || !box ? null : { top: box.bottom, left: box.left };
}

function chooseAction(action: FabAction): void {
	menuAt.value = null;
	action.run();
	afterChoice();
}

const { hidden: amountsHidden, toggle: toggleAmounts } = useAmountVisibility();

/** Not on a screen that shows no amount, where the hide-amounts switch has nothing to mask. */
const showsAmounts = computed(() => route.meta.amountFree !== true);

/**
 * Set up once and then left alone, so they live behind the menu rather than in the row of daily
 * destinations, and follow the less-visited screens passed as `moreLinks`. Save the Change sits with the
 * screens, as in the Android drawer, and Settings is its own screen too.
 */
const ownMoreLinks = computed<RailLink[]>(() => [
	{ to: '/subscriptions', label: t('nav.subscriptions'), icon: EVENT_REPEAT },
	{ to: '/save-the-change', label: t('nav.saveTheChange'), icon: SAVINGS },
	{ to: '/settings', label: t('nav.settings'), icon: SETTINGS },
]);
const allMoreLinks = computed(() => [...props.moreLinks, ...ownMoreLinks.value]);

/** A floating rail is in the way once it has done its job; a docked one stays put. */
function afterChoice(): void {
	if (!railPushesContent()) collapse();
}

function onKeydown(event: KeyboardEvent): void {
	if (event.key !== 'Escape') return;
	if (menuAt.value) menuAt.value = null;
	else if (expanded.value) collapse();
}

/**
 * The open rail is as wide as what it holds — the longest label, the account card — as Material's wide rail
 * is: its content plus a 20px margin, between 220 and 360. The content is always laid out at its open size
 * (the slim rail only clips it), so its width can be read at any time, and the page is told through
 * `--rail-open` how far to move aside.
 */
const innerEl = ref<HTMLElement | null>(null);
let observer: ResizeObserver | undefined;

function measure(): void {
	const width = innerEl.value?.offsetWidth;
	if (!width) return;
	document.documentElement.style.setProperty('--rail-open', `${Math.min(360, Math.max(220, width + 20))}px`);
}

onMounted(() => {
	measure();
	if (innerEl.value && typeof ResizeObserver !== 'undefined') {
		observer = new ResizeObserver(measure);
		observer.observe(innerEl.value);
	}
});

onBeforeUnmount(() => {
	clearTimeout(fabTimer);
	observer?.disconnect();
});
</script>

<template>
	<!-- Below the docked width the open rail floats over the page, so the page behind it is dimmed
	     and a tap on it closes the rail. -->
	<Transition
		enter-active-class="transition-opacity duration-300 ease-standard-decelerate"
		enter-from-class="opacity-0"
		leave-active-class="transition-opacity duration-200 ease-standard-accelerate"
		leave-to-class="opacity-0"
	>
		<div v-if="expanded" class="fixed inset-0 z-30 hidden bg-scrim/32 sm:block lg:hidden" aria-hidden="true" @click="collapse" />
	</Transition>

	<!--
	  One structure for both states. Opening the rail changes its width and flips `expanded`; every
	  element below moves to its open geometry by CSS transition (see `.rail-item`, `.rail-fab` and
	  `.rail-collapse` in style.css), so the slim and the open rail morph into each other rather than swap.
	  Icons sit 36px from the left edge in both, which is what lets them stay put while everything else moves.
	-->
	<nav
		id="primary-rail"
		:aria-label="t('nav.primary')"
		class="rail fixed inset-y-0 left-0 z-40 hidden overflow-hidden bg-surface sm:block"
		:class="{ 'max-lg:bg-surface-container max-lg:shadow-elevation-2': expanded }"
		:data-expanded="expanded"
		@keydown="onKeydown"
	>
		<div ref="innerEl" class="rail-inner pt-11">
			<!-- The app's mark, in either state, with its name and version beside it once the rail is open. It is
			     a mark rather than a control (no link, and never the menu button, which sits beneath it), so it
			     cannot be mistaken for one. The rail's Dashboard destination is the way home. -->
			<div class="relative isolate flex items-center pb-3 pl-7" aria-hidden="true">
				<!-- Only in the open rail: a strip of the print behind the name, from the rail's top edge down to
				     the menu button and thinning out towards the page. -->
				<CatPattern
					class="rail-fade absolute -top-16 left-0 -z-10 h-[calc(100%+4rem)] w-[220px]"
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

			<button
				type="button"
				class="btn-icon m-1 ml-7 text-on-surface"
				:aria-label="expanded ? t('nav.collapse') : t('nav.expand')"
				:aria-expanded="expanded"
				aria-controls="primary-rail"
				@click="toggle"
			>
				<!-- Both glyphs are always there, stacked; the one that matches the state shows. Open, it is
				     Material's "menu open", since pressing it then closes the rail. -->
				<AppIcon :icon="MENU" class="rail-fade col-start-1 row-start-1" :data-shown="!expanded" />
				<AppIcon :icon="MENU_OPEN" class="rail-fade col-start-1 row-start-1" :data-shown="expanded" />
			</button>

			<!-- The screen's leading action, under the menu as Material places it. It has a slot of its own:
			     when a page brings a FAB the slot opens and the destinations slide down, and the FAB pops in;
			     when it goes, the reverse. The same button in both rail states: a 56px square that widens into
			     an extended FAB and reveals its label. -->
			<div ref="slotEl" class="rail-slot" :data-open="fabOpen" :inert="!fabOpen">
				<div>
					<div v-if="shownFab" class="rail-fab-box">
						<span class="rail-fab-ghost" aria-hidden="true">{{ shownFab.label }}</span>
						<button
							ref="fabEl"
							type="button"
							class="fab rail-fab"
							:class="shownFab.disabled ? '' : 'shadow-elevation-3'"
							:data-expanded="fabExpanded"
							:data-shown="fabShown"
							:disabled="shownFab.disabled"
							:aria-label="shownFab.label"
							:aria-haspopup="shownFab.actions.length ? 'menu' : undefined"
							:aria-expanded="shownFab.actions.length ? !!menuAt : undefined"
							:title="shownFab.label"
							@click="pressFab"
						>
							<AppIcon :icon="shownFab.icon" />
							<span class="rail-fade whitespace-nowrap" :data-shown="fabExpanded">{{ shownFab.label }}</span>
						</button>
					</div>
				</div>
			</div>

			<!-- Material's wide rail keeps this gap between its header and its items. -->
			<div class="mt-10 flex min-h-0 flex-1 flex-col">
				<!-- One scrolling column: on a short window the open rail would otherwise run off the bottom. -->
				<div class="min-h-0 flex-1 overflow-x-hidden overflow-y-auto [scrollbar-width:none]">
					<RailItem
						v-for="link in links"
						:key="link.to"
						:to="link.to"
						:label="link.label"
						:icon="link.icon"
						:expanded="expanded"
						@click="afterChoice"
					/>

					<!-- Only in the open rail: it grows out of nothing rather than appearing, and is inert while shut. -->
					<div class="rail-collapse" :data-open="expanded" :inert="!expanded">
						<div>
							<h2 class="type-title-small pt-4 pb-2 pl-8 whitespace-nowrap text-on-surface">{{ t('nav.more') }}</h2>

							<RailItem
								v-for="link in allMoreLinks"
								:key="link.to"
								:to="link.to"
								:label="link.label"
								:icon="link.icon"
								expanded
								@click="afterChoice"
							/>

							<RailItem
								:label="t('common.signOut')"
								:icon="LOGOUT"
								expanded
								@click="
									afterChoice();
									emit('sign-out');
								"
							/>
						</div>
					</div>
				</div>

				<!-- The foot stays at the bottom of the rail however far the destinations above scroll. The display
				     switch is in reach in both states: a phone keeps it in its top bar, but a wide window has none. -->
				<div class="flex-none py-4">
					<!-- Not on a screen that shows no amount, where there is nothing for it to mask. -->
					<RailItem
						v-if="showsAmounts"
						variant="action"
						:label="amountsHidden ? t('common.showAmounts') : t('common.hideAmounts')"
						:icon="amountsHidden ? MONEY_OFF : ATTACH_MONEY"
						:expanded="expanded"
						:aria-label="amountsHidden ? t('common.showAmounts') : t('common.hideAmounts')"
						:aria-pressed="amountsHidden"
						:title="amountsHidden ? t('common.showAmounts') : t('common.hideAmounts')"
						@click="toggleAmounts"
					/>

					<div class="rail-collapse" :data-open="expanded && !!account.name" :inert="!expanded">
						<div>
							<div
								class="relative isolate mx-6 my-4 flex w-fit items-center gap-3 overflow-hidden rounded-xl bg-surface-container-highest px-4 py-3 text-on-surface-variant"
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

				<div class="h-6 flex-none" />
			</div>
		</div>
	</nav>

	<!-- The FAB's menu, when its screen's leading action is really a few. -->
	<Teleport to="body">
		<template v-if="menuAt && shownFab">
			<div class="fixed inset-0 z-50" aria-hidden="true" @click="menuAt = null" />
			<div role="menu" class="menu fixed z-50 min-w-28" :style="{ top: `${menuAt.top}px`, left: `${menuAt.left}px` }" @keydown="onKeydown">
				<button
					v-for="action in shownFab.actions"
					:key="action.label"
					type="button"
					role="menuitem"
					class="menu-item pr-4"
					@click="chooseAction(action)"
				>
					<AppIcon :icon="action.icon" />
					{{ action.label }}
				</button>
			</div>
		</template>
	</Teleport>
</template>
