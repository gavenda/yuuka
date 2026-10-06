<script setup lang="ts">
import { computed, ref } from 'vue';

/**
 * A hue and saturation wheel over a brightness strip (`ColorWheelPicker`), for a colour that is not one of
 * the palette's. Both are gradients rather than a rasterised image, so they stay smooth at any size: a
 * conic gradient for hue, starting at three o'clock and running clockwise, under a white-to-clear radial
 * one for saturation. Press or drag either; the arrow keys nudge the one that has focus.
 */
const model = defineModel<string>({ required: true });

interface Hsv {
	h: number;
	s: number;
	v: number;
}

function toHsv(hex: string): Hsv {
	const match = /^#([0-9a-fA-F]{6})$/.exec(hex.trim());
	// Not a colour yet (a hex still being typed): the grey the custom swatch starts from.
	const value = parseInt(match ? match[1]! : '64748b', 16);
	const r = ((value >> 16) & 255) / 255;
	const g = ((value >> 8) & 255) / 255;
	const b = (value & 255) / 255;
	const max = Math.max(r, g, b);
	const delta = max - Math.min(r, g, b);

	let h = 0;
	if (delta > 0) {
		if (max === r) h = ((g - b) / delta) % 6;
		else if (max === g) h = (b - r) / delta + 2;
		else h = (r - g) / delta + 4;
		h = (h * 60 + 360) % 360;
	}

	return { h, s: max === 0 ? 0 : delta / max, v: max };
}

function toHex({ h, s, v }: Hsv): string {
	const f = (n: number): string => {
		const k = (n + h / 60) % 6;
		const channel = v - v * s * Math.max(0, Math.min(k, 4 - k, 1));
		return Math.round(channel * 255)
			.toString(16)
			.padStart(2, '0');
	};
	return `#${f(5)}${f(3)}${f(1)}`.toUpperCase();
}

const hsv = computed(() => toHsv(model.value));
const full = computed(() => toHex({ ...hsv.value, v: 1 }));

const wheel = ref<HTMLElement | null>(null);
const strip = ref<HTMLElement | null>(null);

function fromWheel(event: PointerEvent): void {
	const box = wheel.value?.getBoundingClientRect();
	if (!box) return;
	const radius = box.width / 2;
	const dx = event.clientX - box.left - radius;
	const dy = event.clientY - box.top - radius;
	const angle = (Math.atan2(dy, dx) * 180) / Math.PI;
	model.value = toHex({ h: (angle + 360) % 360, s: Math.min(1, Math.hypot(dx, dy) / radius), v: hsv.value.v });
}

function fromStrip(event: PointerEvent): void {
	const box = strip.value?.getBoundingClientRect();
	if (!box) return;
	model.value = toHex({ ...hsv.value, v: Math.min(1, Math.max(0, (event.clientX - box.left) / box.width)) });
}

function drag(event: PointerEvent, update: (event: PointerEvent) => void): void {
	(event.currentTarget as HTMLElement).setPointerCapture(event.pointerId);
	update(event);
}

function nudgeWheel(event: KeyboardEvent): void {
	const { h, s, v } = hsv.value;
	if (event.key === 'ArrowLeft') model.value = toHex({ h: (h + 355) % 360, s, v });
	else if (event.key === 'ArrowRight') model.value = toHex({ h: (h + 5) % 360, s, v });
	else if (event.key === 'ArrowUp') model.value = toHex({ h, s: Math.min(1, s + 0.05), v });
	else if (event.key === 'ArrowDown') model.value = toHex({ h, s: Math.max(0, s - 0.05), v });
	else return;
	event.preventDefault();
}

function nudgeStrip(event: KeyboardEvent): void {
	const step =
		event.key === 'ArrowLeft' || event.key === 'ArrowDown' ? -0.05 : event.key === 'ArrowRight' || event.key === 'ArrowUp' ? 0.05 : 0;
	if (!step) return;
	event.preventDefault();
	model.value = toHex({ ...hsv.value, v: Math.min(1, Math.max(0, hsv.value.v + step)) });
}

const indicator = computed(() => {
	const angle = (hsv.value.h * Math.PI) / 180;
	return {
		left: `${50 + Math.cos(angle) * hsv.value.s * 50}%`,
		top: `${50 + Math.sin(angle) * hsv.value.s * 50}%`,
	};
});
</script>

<template>
	<div class="pt-1">
		<div
			ref="wheel"
			role="slider"
			tabindex="0"
			aria-label="Hue and saturation"
			:aria-valuenow="Math.round(hsv.h)"
			aria-valuemin="0"
			aria-valuemax="360"
			class="focus-ring relative aspect-square w-full cursor-crosshair touch-none rounded-full"
			style="background: radial-gradient(closest-side, #fff, #fff0), conic-gradient(from 90deg, #f00, #ff0, #0f0, #0ff, #00f, #f0f, #f00)"
			@pointerdown="drag($event, fromWheel)"
			@pointermove="$event.buttons && fromWheel($event)"
			@keydown="nudgeWheel"
		>
			<span
				class="pointer-events-none absolute size-5 -translate-1/2 rounded-full border-[3px] border-white outline outline-black/35"
				:style="indicator"
			/>
		</div>

		<div
			ref="strip"
			role="slider"
			tabindex="0"
			aria-label="Brightness"
			:aria-valuenow="Math.round(hsv.v * 100)"
			aria-valuemin="0"
			aria-valuemax="100"
			class="focus-ring relative mt-3 h-7 cursor-pointer touch-none rounded-full"
			:style="{ background: `linear-gradient(to right, #000, ${full})` }"
			@pointerdown="drag($event, fromStrip)"
			@pointermove="$event.buttons && fromStrip($event)"
			@keydown="nudgeStrip"
		>
			<span
				class="pointer-events-none absolute top-1/2 size-6 -translate-y-1/2 rounded-full border-2 border-white"
				:style="{ left: `calc(${hsv.v * 100}% - 12px)`, backgroundColor: model }"
			/>
		</div>
	</div>
</template>
