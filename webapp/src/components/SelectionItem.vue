<script setup lang="ts">
import AppIcon from '@/components/AppIcon.vue';
import { CHECK, type IconPath } from '@/lib/icons';
import { ListItem } from 'tailwind-material3';

/**
 * One choice in a list to pick from — a dialog of accounts, the currency list (`ExpressiveModalSelectionItem`).
 * It is tailwind-material3's `ListItem` as a button, so it brings the library's ripple, state layer and focus
 * ring. Put them in the library's `List` with `selection-list`: the rows lie a hair apart, round at the list's
 * ends and near-square between, and the chosen one fills with the primary container, rounds off and carries a
 * tick, so the choice reads by shape and mark as well as colour. It leads with an `icon`, or with a short `badge`
 * of text such as a currency's symbol.
 */
defineProps<{ title: string; subtitle?: string | null; selected: boolean; icon?: IconPath; badge?: string }>();
</script>

<template>
	<ListItem as="button" :supporting-text="subtitle ?? undefined" :aria-pressed="selected">
		<template v-if="badge !== undefined || icon" #leading>
			<span
				v-if="badge !== undefined"
				class="type-title-medium grid size-10 place-items-center rounded-full"
				:class="selected ? 'bg-on-primary-container text-primary-container' : 'bg-surface-container text-on-surface'"
				aria-hidden="true"
				>{{ badge }}</span
			>
			<AppIcon v-else-if="icon" :icon="icon" />
		</template>

		<span class="block truncate">{{ title }}</span>

		<template v-if="selected" #trailing>
			<span class="grid size-5 place-items-center rounded-full bg-on-primary-container text-primary-container">
				<AppIcon :icon="CHECK" :size="14" />
			</span>
		</template>
	</ListItem>
</template>
