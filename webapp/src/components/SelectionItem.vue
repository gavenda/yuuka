<script setup lang="ts">
import AppIcon from '@/components/AppIcon.vue';
import { CHECK, type IconPath } from '@/lib/icons';

/**
 * One choice in a list to pick from — a sheet of accounts, the currency list (`ExpressiveModalSelectionItem`).
 * Put them in a `.selection-list`: the rows lie a hair apart, round at the list's ends and near-square
 * between, and the chosen one fills with the primary container, rounds off and carries a tick, so the choice
 * reads by shape and mark as well as colour. It leads with an `icon`, or with a short `badge` of text such
 * as a currency's symbol.
 */
defineProps<{ title: string; subtitle?: string | null; selected: boolean; icon?: IconPath; badge?: string }>();
</script>

<template>
	<button type="button" class="selection-item state-layer focus-ring" :aria-pressed="selected">
		<span
			v-if="badge !== undefined"
			class="type-title-medium grid size-10 shrink-0 place-items-center rounded-full"
			:class="selected ? 'bg-on-primary-container text-primary-container' : 'bg-surface-container text-on-surface'"
			aria-hidden="true"
			>{{ badge }}</span
		>
		<AppIcon v-else-if="icon" :icon="icon" />

		<span class="min-w-0 flex-1">
			<span class="type-body-large block truncate">{{ title }}</span>
			<span v-if="subtitle" class="type-body-medium block truncate">{{ subtitle }}</span>
		</span>

		<span v-if="selected" class="grid size-5 shrink-0 place-items-center rounded-full bg-on-primary-container text-primary-container">
			<AppIcon :icon="CHECK" :size="14" />
		</span>
	</button>
</template>
