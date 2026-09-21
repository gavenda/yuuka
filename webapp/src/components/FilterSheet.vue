<script setup lang="ts">
import ModalDialog from '@/components/ModalDialog.vue';

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
	<ModalDialog :open="open" :title="title" @close="emit('close')">
		<div class="flex flex-col gap-3">
			<div v-if="selected.length" class="flex justify-end">
				<button type="button" class="btn-text btn-sm" @click="selected = []">Clear</button>
			</div>

			<p v-if="!options.length" class="text-sm text-on-surface-variant">{{ emptyText }}</p>

			<div v-else class="flex flex-wrap gap-2">
				<button
					v-for="option in options"
					:key="option.id"
					type="button"
					class="chip"
					:aria-pressed="selected.includes(option.id)"
					@click="toggle(option.id)"
				>
					<svg
						v-if="selected.includes(option.id)"
						viewBox="0 0 20 20"
						class="size-4 shrink-0"
						fill="none"
						stroke="currentColor"
						stroke-width="2"
						aria-hidden="true"
					>
						<path d="M4.5 10.5l3.5 3.5 7.5-8" stroke-linecap="round" stroke-linejoin="round" />
					</svg>
					<span class="truncate">{{ option.label }}</span>
				</button>
			</div>
		</div>
	</ModalDialog>
</template>
