<script setup lang="ts">
import AlertDialog from '@/components/AlertDialog.vue';

/**
 * A short list to pick from, as Material 3's basic dialog (`SelectionDialog` on Android): the title, the
 * `SelectionItem`s and one text button at the foot. A list to pick one of closes itself on the pick, so its
 * button is Cancel; one that toggles several stays open and passes "Done" as `dismissLabel`, since every
 * press has already been written.
 */
withDefaults(defineProps<{ open: boolean; title: string; dismissLabel?: string }>(), { dismissLabel: 'Cancel' });
const emit = defineEmits<{ close: [] }>();
</script>

<template>
	<AlertDialog :open="open" :title="title" role="dialog" @close="emit('close')">
		<div class="selection-list sm:w-96"><slot /></div>
		<template #actions>
			<button type="button" class="btn-text" @click="emit('close')">{{ dismissLabel }}</button>
		</template>
	</AlertDialog>
</template>
