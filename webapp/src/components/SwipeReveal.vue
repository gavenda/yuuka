<script setup lang="ts">
import { onBeforeUnmount, ref } from 'vue';

/**
 * A list row whose trailing actions stay hidden until the row is dragged left, as `SwipeToRevealActions`
 * does on Android — by a finger, or by a mouse held down on it. Dragging it back, or pressing the row
 * while it is open, closes it again.
 *
 * The actions are in the tab order after the row itself and the row opens when one of them takes focus,
 * so a keyboard reaches them without a drag.
 */
const offset = ref(0);
const open = ref(false);
const dragging = ref(false);
const actionsEl = ref<HTMLElement | null>(null);

/** How far a press may wander and still be a press rather than the start of a drag. */
const SLOP = 8;

let startX = 0;
let startY = 0;
let startOffset = 0;
let pointerId: number | null = null;
let decided: 'drag' | 'scroll' | null = null;

const width = (): number => actionsEl.value?.offsetWidth ?? 0;

function settle(toOpen: boolean): void {
	open.value = toOpen;
	offset.value = toOpen ? -width() : 0;
}

function onPointerDown(event: PointerEvent): void {
	if (event.button !== 0) return;
	pointerId = event.pointerId;
	startX = event.clientX;
	startY = event.clientY;
	startOffset = offset.value;
	decided = null;
	window.addEventListener('pointermove', onPointerMove);
	window.addEventListener('pointerup', onPointerUp);
	window.addEventListener('pointercancel', onPointerUp);
}

function onPointerMove(event: PointerEvent): void {
	if (event.pointerId !== pointerId) return;
	const dx = event.clientX - startX;
	const dy = event.clientY - startY;

	if (decided === null) {
		if (Math.abs(dx) < SLOP && Math.abs(dy) < SLOP) return;
		// A mostly vertical move is the page scrolling, and stays that for the rest of the gesture.
		decided = Math.abs(dx) > Math.abs(dy) ? 'drag' : 'scroll';
		dragging.value = decided === 'drag';
	}
	if (decided !== 'drag') return;

	offset.value = Math.min(0, Math.max(-width(), startOffset + dx));
}

function onPointerUp(event: PointerEvent): void {
	if (event.pointerId !== pointerId) return;
	stopListening();
	if (decided !== 'drag') return;

	settle(offset.value < -width() / 2);
	// The click that ends a drag belongs to the drag, not to the row under it.
	window.addEventListener('click', swallowClick, { capture: true, once: true });
	setTimeout(() => window.removeEventListener('click', swallowClick, { capture: true }), 0);
	dragging.value = false;
}

function swallowClick(event: MouseEvent): void {
	event.stopPropagation();
	event.preventDefault();
}

function stopListening(): void {
	pointerId = null;
	window.removeEventListener('pointermove', onPointerMove);
	window.removeEventListener('pointerup', onPointerUp);
	window.removeEventListener('pointercancel', onPointerUp);
}

onBeforeUnmount(stopListening);

defineExpose({ close: () => settle(false) });
</script>

<template>
	<div class="relative">
		<!-- The row lies over its actions, and comes first so that Tab reaches the row and then what is behind it. -->
		<div
			class="relative z-[1] touch-pan-y select-none"
			:class="dragging ? '' : 'transition-transform duration-200 ease-standard'"
			:style="{ transform: `translateX(${offset}px)` }"
			@pointerdown="onPointerDown"
		>
			<slot />
			<!-- While it is open the row is a way to close it, not something to press. -->
			<div v-if="open" class="absolute inset-0 cursor-pointer" @click="settle(false)" />
		</div>

		<div
			ref="actionsEl"
			class="absolute inset-y-0 right-0 flex items-center"
			@focusin="settle(true)"
			@focusout="!actionsEl?.contains($event.relatedTarget as Node | null) && settle(false)"
		>
			<slot name="actions" :close="() => settle(false)" />
		</div>
	</div>
</template>
