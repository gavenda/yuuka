<script setup lang="ts">
import RailItem from '@/components/RailItem.vue';
import { currentFab, type FabEntry } from '@/lib/fab';
import { ATTACH_MONEY, DARK_MODE, EVENT_REPEAT, LIGHT_MODE, LOGOUT, MENU, MONEY_OFF, SAVINGS, SETTINGS } from '@/lib/icons';
import { useAmountVisibility } from '@/lib/privacy';
import { railPushesContent, useRail } from '@/lib/rail';
import { useTheme } from '@/lib/theme';
import { computed, nextTick, onBeforeUnmount, ref, shallowRef, watch } from 'vue';

export interface RailLink {
	to: string;
	label: string;
	icon: string;
}

const props = defineProps<{
	links: RailLink[];
	/** The destinations that are not daily ones, first in the open rail's "More" group, ahead of the ones set up once. */
	moreLinks: RailLink[];
	/** The app's version, shown small after the title once the rail is open. */
	version: string;
	/** Who is signed in, for the account block that shows once the rail is open. */
	account: { name: string | null; email: string | null; picture: string | null; initial: string };
}>();

const emit = defineEmits<{ 'sign-out': [] }>();

const { expanded, toggle, collapse } = useRail();

/**
 * The FAB in the slot is one persistent button, never rebuilt. A page bringing a FAB opens the slot and pops
 * the button in; when the last page's FAB goes it pops out and the slot closes. Between two pages that both
 * have one, the button folds back to its circle, takes the new label, and unfolds again, with the slot
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
/** Folded to a circle while it changes label. Only visible when the rail is open, as a slim FAB is a circle anyway. */
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

onBeforeUnmount(() => clearTimeout(fabTimer));
const { theme, toggle: toggleTheme } = useTheme();
const { hidden: amountsHidden, toggle: toggleAmounts } = useAmountVisibility();

/**
 * Set up once and then left alone, so they live behind the menu rather than in the row of daily
 * destinations, and follow the less-visited screens passed as `moreLinks`. Save the Change sits with the
 * screens, as in the Android drawer, and Settings is its own screen too.
 */
const MORE_LINKS: RailLink[] = [
	{ to: '/subscriptions', label: 'Subscriptions', icon: EVENT_REPEAT },
	{ to: '/save-the-change', label: 'Save the Change', icon: SAVINGS },
	{ to: '/settings', label: 'Settings', icon: SETTINGS },
];
const allMoreLinks = computed(() => [...props.moreLinks, ...MORE_LINKS]);

/** A floating rail is in the way once it has done its job; a docked one stays put. */
function afterChoice(): void {
	if (!railPushesContent()) collapse();
}

function onKeydown(event: KeyboardEvent): void {
	if (event.key === 'Escape' && expanded.value) collapse();
}
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
	  Icons sit 28px from the left edge in both, which is what lets them stay put while everything else moves.
	-->
	<nav
		id="primary-rail"
		aria-label="Primary"
		class="fixed inset-y-0 left-0 z-40 hidden flex-col overflow-x-hidden overflow-y-auto bg-surface py-4 transition-[width,box-shadow] duration-300 ease-emphasized-decelerate sm:flex"
		:class="expanded ? 'w-72 shadow-elevation-2 lg:shadow-none' : 'w-20'"
		@keydown="onKeydown"
	>
		<div class="mb-2 flex flex-col items-start gap-3 pl-5">
			<!-- The logo, in either state. It is a mark rather than a control (no link, and never the menu
			     button, which sits beneath it), so it cannot be mistaken for one. The rail's
			     Dashboard destination is the way home. -->
			<div class="flex items-center gap-3" aria-hidden="true">
				<img src="/yuuka.png" alt="" class="size-10 shrink-0 rounded-full object-cover" />
				<span class="rail-fade flex items-baseline gap-1.5 whitespace-nowrap" :data-shown="expanded">
					<span class="text-xl text-on-surface">yuuka</span>
					<span class="type-label-small text-on-surface-variant">v{{ version }}</span>
				</span>
			</div>

			<button
				type="button"
				class="btn-icon"
				:aria-label="expanded ? 'Collapse navigation' : 'Expand navigation'"
				:aria-expanded="expanded"
				aria-controls="primary-rail"
				@click="toggle"
			>
				<!-- Both glyphs are always there, stacked; the one that matches the state shows. -->
				<svg
					viewBox="0 0 24 24"
					class="rail-fade col-start-1 row-start-1 h-6 w-6"
					:data-shown="!expanded"
					fill="currentColor"
					aria-hidden="true"
				>
					<path :d="MENU" />
				</svg>
				<!-- Material's "menu open", since pressing it while open closes the rail. -->
				<svg
					viewBox="0 -960 960 960"
					class="rail-fade col-start-1 row-start-1 h-6 w-6"
					:data-shown="expanded"
					fill="currentColor"
					aria-hidden="true"
				>
					<path
						d="M120-240v-80h520v80H120Zm664-40L584-480l200-200 56 56-144 144 144 144-56 56ZM120-440v-80h400v80H120Zm0-200v-80h520v80H120Z"
					/>
				</svg>
			</button>
		</div>

		<!-- The screen's leading action, under the menu as Material places it. It has a slot of its own:
		     when a page brings a FAB the slot opens and the destinations slide down, and the FAB pops in;
		     when it goes, the reverse. The same button in both rail states: a 56px circle that widens into
		     an extended FAB and reveals its label. -->
		<div ref="slotEl" class="rail-slot" :data-open="fabOpen" :inert="!fabOpen">
			<div>
				<button
					v-if="shownFab"
					type="button"
					class="fab rail-fab shadow-elevation-1"
					:data-expanded="fabExpanded"
					:data-rail-open="expanded"
					:data-shown="fabShown"
					:disabled="shownFab.disabled"
					:aria-label="shownFab.label"
					:title="shownFab.label"
					@click="
						shownFab.run();
						afterChoice();
					"
				>
					<svg viewBox="0 0 24 24" class="h-6 w-6 shrink-0" fill="currentColor" aria-hidden="true">
						<path :d="shownFab.icon" />
					</svg>
					<span class="rail-fade whitespace-nowrap" :data-shown="fabExpanded">{{ shownFab.label }}</span>
				</button>
			</div>
		</div>

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
				<h2 class="type-title-small pt-4 pb-2 pl-7 whitespace-nowrap text-on-surface-variant">More</h2>

				<RailItem
					v-for="link in allMoreLinks"
					:key="link.to"
					:to="link.to"
					:label="link.label"
					:icon="link.icon"
					variant="action"
					expanded
					@click="afterChoice"
				/>

				<RailItem label="Sign out" :icon="LOGOUT" variant="action" expanded @click="emit('sign-out')" />
			</div>
		</div>

		<div class="min-h-2 flex-1" />

		<!-- The display toggles sit at the foot of the rail from sm up; a phone has no rail, so its top bar keeps them. -->
		<RailItem
			variant="action"
			:label="amountsHidden ? 'Show amounts' : 'Hide amounts'"
			:icon="amountsHidden ? MONEY_OFF : ATTACH_MONEY"
			:expanded="expanded"
			:aria-label="amountsHidden ? 'Show amounts' : 'Hide amounts'"
			:aria-pressed="amountsHidden"
			:title="amountsHidden ? 'Show amounts' : 'Hide amounts'"
			@click="toggleAmounts"
		/>

		<RailItem
			variant="action"
			:label="theme === 'dark' ? 'Light mode' : 'Dark mode'"
			:icon="theme === 'dark' ? LIGHT_MODE : DARK_MODE"
			:expanded="expanded"
			:aria-label="`Switch to ${theme === 'dark' ? 'light' : 'dark'} mode`"
			:title="`Switch to ${theme === 'dark' ? 'light' : 'dark'} mode`"
			@click="toggleTheme"
		/>

		<div class="rail-collapse" :data-open="expanded && !!account.name" :inert="!expanded">
			<div>
				<div class="flex items-center gap-3 py-2 pl-7">
					<img v-if="account.picture" :src="account.picture" alt="" class="size-8 shrink-0 rounded-full" referrerpolicy="no-referrer" />
					<span
						v-else
						class="grid size-8 shrink-0 place-items-center rounded-full bg-primary-container text-sm font-medium text-on-primary-container"
						aria-hidden="true"
					>
						{{ account.initial }}
					</span>

					<span class="min-w-0 pr-4">
						<span class="block truncate text-sm text-on-surface">{{ account.name }}</span>
						<span v-if="account.email && account.email !== account.name" class="block truncate text-xs text-on-surface-variant">{{
							account.email
						}}</span>
					</span>
				</div>
			</div>
		</div>
	</nav>
</template>
