<script setup lang="ts">
import { ref, watch } from 'vue';
import { t } from '@/i18n';

/**
 * The company's logo as PSE Edge shows it (`StockLogo` in `InvestmentsScreen.kt`), or the ticker's first letters
 * on a tonal circle without one or while it cannot be loaded. The logos are mostly JPEGs on white, so each stands
 * on a white card of its own rather than on the theme's surface, where a dark one would read as a white box.
 */
const props = defineProps<{ symbol: string; name: string; logoUrl: string | null }>();

const failed = ref(false);
watch(
	() => props.logoUrl,
	() => (failed.value = false),
);
</script>

<template>
	<span v-if="logoUrl && !failed" class="inline-grid place-items-center rounded-2xl bg-white px-4 py-3">
		<img :src="logoUrl" :alt="t('investments.logo', { name })" class="max-h-16 max-w-[220px] object-contain" @error="failed = true" />
	</span>
	<span
		v-else
		class="type-title-medium grid size-14 shrink-0 place-items-center rounded-full bg-secondary-container text-on-secondary-container"
		aria-hidden="true"
		>{{ symbol.slice(0, 2) }}</span
	>
</template>
