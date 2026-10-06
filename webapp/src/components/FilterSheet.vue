<script setup lang="ts">
import AppIcon from '@/components/AppIcon.vue';
import BottomSheet from '@/components/BottomSheet.vue';
import { CHECK } from '@/lib/icons';

/**
 * A multi-select filter as a sheet of chips, the way the Android app does it: every option is a chip to switch
 * on or off, and a change applies at once, so the list behind updates as chips are picked.
 */
defineProps<{
	open: boolean;
	title: string;
	options: { id: string; label: string }[];
	/** What to say when there is nothing to choose from. */
	emptyText: string;
}>();

const selected = defineModel<string[]>({ required: true });
const emit = defineEmits<{ close: [] }>();

function toggle(id: string): void {
	selected.value = selected.value.includes(id) ? selected.value.filter((chosen) => chosen !== id) : [...selected.value, id];
}
</script>

<template>
	<BottomSheet :open="open" :label="title" @close="emit('close')">
		<div class="flex flex-col gap-3 px-4 pb-6">
			<div class="flex min-h-10 items-center justify-between">
				<h2 class="type-title-medium">{{ title }}</h2>
				<button v-if="selected.length" type="button" class="btn-text" @click="selected = []">Clear</button>
			</div>

			<p v-if="!options.length" class="type-body-medium">{{ emptyText }}</p>

			<div v-else class="flex flex-wrap gap-2">
				<button
					v-for="option in options"
					:key="option.id"
					type="button"
					class="chip"
					:aria-pressed="selected.includes(option.id)"
					@click="toggle(option.id)"
				>
					<AppIcon v-if="selected.includes(option.id)" :icon="CHECK" :size="18" />
					<span class="truncate">{{ option.label }}</span>
				</button>
			</div>
		</div>
	</BottomSheet>
</template>
