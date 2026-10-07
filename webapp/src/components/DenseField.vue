<script setup lang="ts">
import AppIcon from '@/components/AppIcon.vue';
import FieldSupport from '@/components/FieldSupport.vue';
import { CANCEL, ERROR } from '@/lib/icons';
import { supportId } from '@/lib/validation';
import { t } from '@/i18n';

/**
 * A single-line outlined field with cut-down vertical padding and no label of its own, for a row beside a
 * button (`DenseOutlinedTextField`): the standard field's 56dp always dwarfs a 40dp button next to it. It
 * can lead with a `prefix`, such as a currency symbol, and with `clearable` shows a clear button whenever
 * there is text to clear. Its type size follows the text around it.
 */
defineOptions({ inheritAttrs: false });

defineProps<{
	id: string;
	/** Names the field for assistive tech, since nothing visible does. */
	label: string;
	placeholder?: string;
	prefix?: string;
	error?: string | null;
	clearable?: boolean;
	disabled?: boolean;
}>();

const model = defineModel<string>({ required: true });
</script>

<template>
	<div class="min-w-0">
		<div class="input-box" :aria-invalid="error ? 'true' : undefined" :data-disabled="disabled || undefined">
			<span v-if="prefix" class="shrink-0 text-on-surface-variant">{{ prefix }}</span>
			<input
				:id="id"
				v-model="model"
				class="min-w-0 flex-1 bg-transparent placeholder:text-on-surface-variant focus:outline-none"
				:placeholder="placeholder"
				:aria-label="label"
				:disabled="disabled"
				:aria-invalid="error ? 'true' : undefined"
				:aria-describedby="error ? supportId(id) : undefined"
				v-bind="$attrs"
			/>
			<AppIcon v-if="error" :icon="ERROR" class="text-error" />
			<button
				v-else-if="clearable && model"
				type="button"
				class="btn-icon -mr-2 text-on-surface-variant"
				:aria-label="t('common.clear')"
				:disabled="disabled"
				@click="model = ''"
			>
				<AppIcon :icon="CANCEL" />
			</button>
		</div>
		<FieldSupport :id="id" :error="error" />
	</div>
</template>
