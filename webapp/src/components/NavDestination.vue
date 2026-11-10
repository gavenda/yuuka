<script setup lang="ts">
import AppIcon from '@/components/AppIcon.vue';
import type { IconPath } from '@/lib/icons';
import { NavigationItem } from 'tailwind-material3';
import { computed } from 'vue';
import { RouterLink, useLink } from 'vue-router';

/** One destination in the phone's bottom bar (tailwind-material3's `NavigationItem` in its `NavigationBar`): an icon in a pill that fills when it is current, with its label beneath. */
const props = defineProps<{
	to: string;
	label: string;
	icon: IconPath;
	/** The filled glyph worn while it is current. */
	iconSelected?: IconPath;
}>();

const link = useLink({ to: computed(() => props.to) });
</script>

<template>
	<NavigationItem :as="RouterLink" :to="to" :label="label" :active="link.isActive.value">
		<template #icon><AppIcon :icon="icon" /></template>
		<template v-if="iconSelected" #active-icon><AppIcon :icon="iconSelected" /></template>
	</NavigationItem>
</template>
