import { onBeforeUnmount, onMounted, readonly, ref, type Ref } from 'vue';

/**
 * One animation-frame clock shared by everything that draws its own motion, so a page of budget rings
 * costs a single `requestAnimationFrame` loop rather than one each. It runs only while something on
 * screen is using it, and the browser already holds frames back for a hidden tab.
 */
const now = ref(0);
let users = 0;
let handle = 0;

function tick(time: number): void {
	now.value = time;
	handle = requestAnimationFrame(tick);
}

/** Whether the person has asked for less motion; then the clock never starts and what reads it stands still. */
export function reducedMotion(): boolean {
	return typeof matchMedia === 'function' && matchMedia('(prefers-reduced-motion: reduce)').matches;
}

/** The time of the current frame in milliseconds, for as long as the calling component is mounted. */
export function useFrameClock(): Readonly<Ref<number>> {
	let running = false;

	onMounted(() => {
		if (reducedMotion() || typeof requestAnimationFrame !== 'function') return;
		running = true;
		if (users++ === 0) handle = requestAnimationFrame(tick);
	});

	onBeforeUnmount(() => {
		if (!running) return;
		running = false;
		if (--users === 0) cancelAnimationFrame(handle);
	});

	return readonly(now);
}
