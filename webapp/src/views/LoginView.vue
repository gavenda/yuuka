<script setup lang="ts">
import { useAuth0 } from '@auth0/auth0-vue';
import CatPattern from '@/components/CatPattern.vue';
import { onBeforeUnmount, onMounted, ref } from 'vue';
import { useRoute } from 'vue-router';

const { loginWithRedirect, error } = useAuth0();
const route = useRoute();
const redirecting = ref(false);
const version = __APP_VERSION__;

async function signIn(): Promise<void> {
	redirecting.value = true;
	const target = typeof route.query.redirect === 'string' ? route.query.redirect : undefined;

	try {
		// `appState` survives the round trip to Auth0; the plugin reads `target`
		// back on return and navigates there instead of the dashboard.
		await loginWithRedirect({ appState: target ? { target } : undefined });
	} catch {
		redirecting.value = false;
	}
}

/*
 * The backdrop, as `halftonePattern` draws it on Android: a halftone rising from the foot of the window to a
 * zigzag edge. A grid of dots that are nothing at the edge and grow with their depth beneath it, until they run
 * together into a flat tint. It is drawn as a mask, so the tint under it stays a colour role and follows the theme.
 */
const STEP = 12;
const PEAK = 0.58;
const VALLEY = 0.9;
/** The depth, as a fraction of the window's height, at which the dots are full size. */
const FADE = 0.3;
/** A full-size dot's radius in grid steps: just past the 0.707 at which neighbours close the gaps between them. */
const FULL = 0.72;

const backdrop = ref<HTMLElement | null>(null);
const mask = ref('');
let observer: ResizeObserver | undefined;

/** How far down the window the halftone's edge is at `x`, both as fractions: a peak near the left, a valley near the right. */
function zigzagEdge(x: number): number {
	const phase = ((((x - 0.15) / 0.6) % 2) + 2) % 2;
	return PEAK + (VALLEY - PEAK) * (1 - Math.abs(phase - 1));
}

function drawHalftone(): void {
	const el = backdrop.value;
	const width = el?.clientWidth ?? 0;
	const height = el?.clientHeight ?? 0;
	if (!width || !height) return;

	const scale = window.devicePixelRatio || 1;
	const canvas = document.createElement('canvas');
	canvas.width = Math.round(width * scale);
	canvas.height = Math.round(height * scale);
	const context = canvas.getContext('2d');
	if (!context) return;
	context.scale(scale, scale);

	const fade = height * FADE;
	const columns = Math.ceil(width / 2 / STEP);
	for (let y = height - STEP / 2; y > height * PEAK; y -= STEP) {
		for (let column = -columns; column < columns; column++) {
			const x = width / 2 + (column + 0.5) * STEP;
			const depth = y - zigzagEdge(x / width) * height;
			const radius = STEP * FULL * Math.min(Math.max(depth / fade, 0), 1);
			if (radius <= 0.25) continue;
			context.beginPath();
			context.arc(x, y, radius, 0, Math.PI * 2);
			context.fill();
		}
	}
	mask.value = `url(${canvas.toDataURL()})`;
}

onMounted(() => {
	drawHalftone();
	if (backdrop.value && typeof ResizeObserver !== 'undefined') {
		observer = new ResizeObserver(drawHalftone);
		observer.observe(backdrop.value);
	}
});

onBeforeUnmount(() => observer?.disconnect());
</script>

<template>
	<!-- The Android sign-in screen: the mark, the name, what it is, and one button, in the middle of the
	     window, capped so a wide one does not stretch the button from edge to edge. -->
	<div class="relative isolate flex min-h-dvh flex-col items-center justify-center px-6 py-12 text-center text-on-surface">
		<div
			ref="backdrop"
			class="halftone pointer-events-none absolute inset-0 -z-10"
			:style="mask ? { maskImage: mask } : { visibility: 'hidden' }"
			aria-hidden="true"
		></div>
		<!-- Translucent, so the print still shows where the halftone is solid. -->
		<CatPattern class="absolute inset-0 -z-10 size-full" />

		<div class="flex w-full max-w-78 flex-col items-center">
			<!-- A mark, as in the rail: the name beneath it is what is read out. -->
			<img src="/yuuka.png" alt="" class="size-28 rounded-full object-cover" />
			<h1 class="type-headline-large pt-6">yuuka</h1>
			<p class="type-body-large pt-1 pb-10 text-on-surface-variant">Personal budgeting and financial tracking.</p>

			<button type="button" class="btn-primary type-title-medium min-h-14 w-full" :disabled="redirecting" @click="signIn">
				{{ redirecting ? 'Redirecting…' : 'Log in' }}
			</button>

			<p v-if="error" class="type-body-medium pt-4 text-error" role="alert">{{ error.message }}</p>
		</div>

		<p class="type-label-small absolute inset-x-0 bottom-4 text-on-surface-variant">v{{ version }}</p>
	</div>
</template>

<style scoped>
/* The tint the halftone is cut from: the Android screen's `primary` at 16% over the background. */
.halftone {
	background-color: color-mix(in srgb, var(--color-primary) 16%, var(--color-background));
	mask-size: 100% 100%;
}
</style>
