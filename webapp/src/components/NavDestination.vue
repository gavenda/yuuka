<script setup lang="ts">
import AppIcon from '@/components/AppIcon.vue';
import type { IconPath } from '@/lib/icons';

/** One destination in the phone's bottom bar: an icon in a pill that fills when it is current, with its label beneath. */
defineProps<{
	to: string;
	label: string;
	icon: IconPath;
}>();
</script>

<template>
	<!-- `custom`, so the anchor itself can read `isActive` for its own classes. -->
	<RouterLink v-slot="{ href, navigate, isActive }" :to="to" custom>
		<a
			:href="href"
			class="focus-ring group flex flex-1 flex-col items-center gap-1 rounded-lg text-on-surface-variant"
			:aria-current="isActive ? 'page' : undefined"
			@click="navigate"
		>
			<span
				class="grid h-8 w-16 place-items-center rounded-full transition-colors"
				:class="isActive ? 'bg-secondary-container text-on-secondary-container' : 'group-hover:bg-on-surface/8'"
			>
				<AppIcon :icon="icon" />
			</span>
			<span class="type-label-medium" :class="isActive ? 'text-on-surface' : ''">{{ label }}</span>
		</a>
	</RouterLink>
</template>
