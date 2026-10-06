<script setup lang="ts">
import { supportId } from '@/lib/validation';

/**
 * The line beneath a field. It carries the field's error when it has one and its hint otherwise —
 * Material 3 keeps both in the same slot, so an error replaces the hint rather than stacking with it.
 * Point the control's `aria-describedby` at `supportId(id)` so a screen reader reads it with the field,
 * and set `aria-invalid` from the same error.
 */
defineProps<{ id: string; error?: string | null; hint?: string }>();
</script>

<template>
	<!-- `role="alert"` on the error only: a hint is background, an error is news and is read out as it appears. -->
	<p v-if="error" :id="supportId(id)" class="field-support field-support-error" role="alert">{{ error }}</p>
	<p v-else-if="hint" :id="supportId(id)" class="field-support">{{ hint }}</p>
</template>
