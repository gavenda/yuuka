import { computed, onBeforeUnmount, onMounted, ref, type Ref } from 'vue';
import { reducedMotion } from './frameClock';

/** How many screens down the page has to be before scrolling back up offers the way to the top. */
const SCREENS = 2;

/** How near the top it goes off again: lower than where it comes on, so it cannot flicker at the edge. */
const OFF_SCREENS = 1;

/**
 * Whether the screen's button should take the page back to the top, as `BackToTopState` has it on Android: it
 * comes on while the page is scrolled up from more than two screens down, and goes off on scrolling down or
 * within a screen of the top. Once pressed it holds until the page has got there, since the trip itself would
 * otherwise turn it off and on again on the way; scrolling down meanwhile lets go. The window is what
 * scrolls, so it is the one watched. Call it from a component's setup: it stops watching when the component
 * goes.
 */
export function useBackToTop(): { active: Readonly<Ref<boolean>>; scroll: () => void } {
	const shown = ref(false);
	const returning = ref(false);
	let last = 0;

	function onScroll(): void {
		const y = window.scrollY;
		const screen = window.innerHeight;
		if (returning.value) {
			// Arrived, or taken over by a scroll the other way.
			if (y <= 0 || y > last) returning.value = false;
			if (!returning.value) shown.value = false;
		} else if (y > last || y <= screen * OFF_SCREENS) {
			shown.value = false;
		} else if (y < last && y > screen * SCREENS) {
			shown.value = true;
		}
		last = y;
	}

	function scroll(): void {
		if (window.scrollY <= 0) return;
		returning.value = true;
		window.scrollTo({ top: 0, behavior: reducedMotion() ? 'auto' : 'smooth' });
	}

	onMounted(() => {
		last = window.scrollY;
		window.addEventListener('scroll', onScroll, { passive: true });
	});
	onBeforeUnmount(() => window.removeEventListener('scroll', onScroll));

	return { active: computed(() => shown.value || returning.value), scroll };
}
