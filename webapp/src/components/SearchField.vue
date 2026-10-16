<script setup lang="ts">
import AppIcon from '@/components/AppIcon.vue';
import { CLOSE, SEARCH } from '@/lib/icons';
import { t } from '@/i18n';

/**
 * A search drawn as Material's search bar (`.search-field`): a pill that filters as it is typed, with a
 * button to empty it once it holds anything. `label` names it for assistive tech; `placeholder` is what an
 * empty one says, cut short with an ellipsis where it does not fit.
 */
defineProps<{ label: string; placeholder: string }>();
const model = defineModel<string>({ required: true });
</script>

<template>
	<label class="search-field">
		<AppIcon :icon="SEARCH" />
		<input v-model="model" type="search" :aria-label="label" :placeholder="placeholder" />
		<!-- Pressing it leaves the focus where it was, so the typing carries on in the emptied field. -->
		<button v-if="model" type="button" class="btn-icon -mr-2" :aria-label="t('common.clear')" @mousedown.prevent @click="model = ''">
			<AppIcon :icon="CLOSE" />
		</button>
	</label>
</template>
