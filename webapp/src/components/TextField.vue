<script setup lang="ts">
import AppIcon from '@/components/AppIcon.vue';
import { t } from '@/i18n';
import { CANCEL, ERROR } from '@/lib/icons';
import { IconButton, TextField } from 'tailwind-material3';

/**
 * Material 3's outlined text field (`YuukaTextField`), tailwind-material3's outlined `TextField`: the label rests in
 * the field until it has focus or a value, then rises into the border. Give it the `error` a form's validation has
 * for it and it turns to the error colour, says what is wrong beneath itself in place of its `hint`, shows the error
 * icon and is announced as invalid. `id` is the key the form's validation knows the field by (the input's
 * `data-field`; the library names the input itself, so its label stays tied to it). A `prefix`, such as a currency
 * symbol, leads the value once the field has focus or a value, and `clearable` shows a clear button whenever there
 * is text to clear.
 */
defineOptions({ inheritAttrs: false });

withDefaults(
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
	{
		error: null,
		hint: undefined,
		placeholder: undefined,
		type: 'text',
		multiline: false,
		prefix: undefined,
		clearable: false,
		disabled: false,
	},
);

const model = defineModel<string>({ required: true });
</script>

<template>
	<TextField
		v-model="model"
		variant="outlined"
		class="w-full"
		:data-field="id"
		:label="label"
		:type="type"
		:placeholder="placeholder"
		:multiline="multiline"
		:rows="1"
		:prefix="prefix"
		:disabled="disabled"
		:error="Boolean(error)"
		v-bind="$attrs"
	>
		<template v-if="error || (clearable && model && !multiline)" #trailing-icon>
			<AppIcon v-if="error" :icon="ERROR" />
			<IconButton v-else variant="standard" :label="t('common.clear')" :disabled="disabled" @click="model = ''">
				<AppIcon :icon="CANCEL" />
			</IconButton>
		</template>
		<!-- `role="alert"` on the error only: a hint is background, an error is news and is read out as it appears. -->
		<template v-if="error || hint" #supporting-text>
			<span v-if="error" role="alert">{{ error }}</span>
			<template v-else>{{ hint }}</template>
		</template>
	</TextField>
</template>
