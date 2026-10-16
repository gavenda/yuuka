<script setup lang="ts">
import { computed } from 'vue';

/**
 * One figure against its plan (`PlanMeter` in `DashboardCards.kt`): the label and the share, Material's linear
 * indicator filled as far as the share (full past 100), an optional `marker` standing across it at a fraction
 * of the way, and the `lines` under it.
 */
const props = withDefaults(defineProps<{ label: string; percent: number; color: string; lines: string[]; marker?: number | null }>(), {
	marker: null,
});

const fraction = computed(() => Math.min(1, Math.max(0, props.percent / 100)));
</script>

<template>
	<div>
		<div class="type-body-medium flex justify-between gap-3">
			<span class="min-w-0 truncate">{{ label }}</span>
			<span class="tabular shrink-0">{{ percent }}%</span>
		</div>

		<div class="relative mt-1.5 flex h-4 items-center" aria-hidden="true">
			<div class="flex h-2 w-full gap-1">
				<div v-if="fraction > 0" class="h-full min-w-2 rounded-full" :style="{ width: `${fraction * 100}%`, backgroundColor: color }" />
				<div v-if="fraction < 1" class="h-full min-w-0 flex-1 rounded-full bg-secondary-container" />
			</div>
			<div
				v-if="marker !== null"
				class="absolute inset-y-0 w-0.5 rounded-full bg-on-surface"
				:style="{ left: `calc((100% - 2px) * ${Math.min(1, Math.max(0, marker))})` }"
			/>
		</div>

		<p v-for="line in lines" :key="line" class="type-body-small tabular mt-1 text-on-surface-variant">{{ line }}</p>
	</div>
</template>
