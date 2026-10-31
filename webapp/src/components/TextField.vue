<script setup lang="ts">
import AppIcon from '@/components/AppIcon.vue';
import FieldSupport from '@/components/FieldSupport.vue';
import { t } from '@/i18n';
import { CANCEL, ERROR } from '@/lib/icons';
import { supportId } from '@/lib/validation';
import { nextTick, ref, watch } from 'vue';

/**
 * Material 3's outlined text field (`YuukaTextField`): the label rests in the field until it has focus or a
 * value, then rises into the border. Give it the `error` a form's validation has for it and it turns to the
 * error colour, says what is wrong beneath itself in place of its `hint`, shows the error icon and is
 * announced as invalid. `id` is also the key the form's validation knows the field by. A `prefix`, such as
 * a currency symbol, leads the value once the field has focus or a value, and `clearable` shows a clear button
 * whenever there is text to clear.
 */
defineOptions({ inheritAttrs: false });

const props = withDefaults(
	defineProps<{
		id: string;
		label: string;
		error?: string | null;
		hint?: string;
		/** Shown once the label has risen, while the field is still empty. */
		placeholder?: string;
		type?: string;
		multiline?: boolean;
		prefix?: string;
		clearable?: boolean;
		disabled?: boolean;
	}>(),
	{ error: null, hint: undefined, placeholder: ' ', type: 'text', multiline: false, prefix: undefined, clearable: false, disabled: false },
);

const model = defineModel<string>({ required: true });

/** The input starts after the prefix, so it is measured rather than guessed from its length. */
const prefixEl = ref<HTMLElement | null>(null);
const prefixWidth = ref(0);

watch(
	() => props.prefix,
	async () => {
		await nextTick();
		prefixWidth.value = prefixEl.value?.offsetWidth ?? 0;
	},
	{ immediate: true },
);
</script>

<template>
	<div>
		<div class="field">
			<textarea
				v-if="multiline"
				:id="id"
				v-model="model"
				class="input resize-none pt-4"
				rows="1"
				:placeholder="placeholder"
				:aria-invalid="error ? 'true' : undefined"
				:aria-describedby="error || hint ? supportId(id) : undefined"
				v-bind="$attrs"
			/>
			<input
				v-else
				:id="id"
				v-model="model"
				class="input"
				:class="{ 'pr-12': error || (clearable && model) }"
				:style="prefix ? { paddingLeft: `calc(1rem + ${prefixWidth}px + 0.25rem)` } : undefined"
				:type="type"
				:placeholder="placeholder"
				:disabled="disabled"
				:aria-invalid="error ? 'true' : undefined"
				:aria-describedby="error || hint ? supportId(id) : undefined"
				v-bind="$attrs"
			/>
			<span v-if="prefix && !multiline" ref="prefixEl" class="field-prefix" aria-hidden="true">{{ prefix }}</span>
			<label class="label" :for="id">{{ label }}</label>
			<AppIcon v-if="error" :icon="ERROR" class="pointer-events-none absolute top-4 right-3 text-error" />
			<button
				v-else-if="clearable && model && !multiline"
				type="button"
				class="btn-icon absolute top-2 right-1 text-on-surface-variant"
				:aria-label="t('common.clear')"
				:disabled="disabled"
				@click="model = ''"
			>
				<AppIcon :icon="CANCEL" />
			</button>
		</div>
		<FieldSupport :id="id" :error="error" :hint="hint" />
	</div>
</template>
