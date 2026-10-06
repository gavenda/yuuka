<script setup lang="ts">
import AppIcon from '@/components/AppIcon.vue';
import FieldSupport from '@/components/FieldSupport.vue';
import { ERROR } from '@/lib/icons';
import { supportId } from '@/lib/validation';

/**
 * Material 3's outlined text field (`YuukaTextField`): the label rests in the field until it has focus or a
 * value, then rises into the border. Give it the `error` a form's validation has for it and it turns to the
 * error colour, says what is wrong beneath itself in place of its `hint`, shows the error icon and is
 * announced as invalid. `id` is also the key the form's validation knows the field by.
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
	}>(),
	{ error: null, hint: undefined, placeholder: ' ', type: 'text', multiline: false },
);

const model = defineModel<string>({ required: true });
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
				:class="{ 'pr-12': error }"
				:type="type"
				:placeholder="placeholder"
				:aria-invalid="error ? 'true' : undefined"
				:aria-describedby="error || hint ? supportId(id) : undefined"
				v-bind="$attrs"
			/>
			<label class="label" :for="id">{{ label }}</label>
			<AppIcon v-if="error" :icon="ERROR" class="pointer-events-none absolute top-4 right-3 text-error" />
		</div>
		<FieldSupport :id="id" :error="error" :hint="hint" />
	</div>
</template>
