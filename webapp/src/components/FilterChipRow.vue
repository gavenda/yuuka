<script setup lang="ts">
import { Button, Chip } from 'tailwind-material3';
import { t } from '@/i18n';

/**
 * A multi-select filter as one row of chips that slides down under the filter bar and scrolls sideways, the
 * way the Android app does it (`FilterChipRow`): every option is a chip to switch on or off, and a change
 * applies at once, so the list below updates as chips are picked. Clear stays at the end of the row rather
 * than in it, so it appearing never moves a chip from under a pointer. The row is inert while shut.
 */
defineProps<{
	open: boolean;
	/** Names the group for a screen reader: "Filter by tag". */
	label: string;
	options: { id: string; label: string }[];
	/** What to say when there is nothing to choose from. */
	emptyText: string;
}>();

const selected = defineModel<string[]>({ required: true });

function toggle(id: string): void {
	selected.value = selected.value.includes(id) ? selected.value.filter((chosen) => chosen !== id) : [...selected.value, id];
}
</script>

<template>
	<div class="slide-down" :data-open="open" :inert="!open">
		<div>
			<div class="flex min-h-12 items-center pt-2" role="group" :aria-label="label">
				<p v-if="!options.length" class="type-body-medium px-2">{{ emptyText }}</p>

				<template v-else>
					<!-- Edge to edge, as the row is on Android; the vertical padding is room for a chip's focus ring. -->
					<div
						class="-ml-4 flex min-w-0 flex-1 gap-2 overflow-x-auto py-1.5 pl-4 [scrollbar-width:none]"
						:class="selected.length ? 'pr-2' : '-mr-4 pr-4'"
					>
						<!-- tailwind-material3's filter chip, at the app's 8dp corners: it fills and carries a check once it is on. -->
						<Chip
							v-for="option in options"
							:key="option.id"
							type="filter"
							class="rounded-sm!"
							:selected="selected.includes(option.id)"
							@update:selected="toggle(option.id)"
						>
							{{ option.label }}
						</Chip>
					</div>

					<Button variant="text" v-if="selected.length" class="shrink-0" @click="selected = []">{{ t('common.clear') }}</Button>
				</template>
			</div>
		</div>
	</div>
</template>
