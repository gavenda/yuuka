<script setup lang="ts">
import { TextField } from 'tailwind-material3';
import { ref } from 'vue';

/**
 * A field that opens a picker instead of taking typing, for a date or a time of day (`PickerField` on
 * Android). It reads as an outlined field (tailwind-material3's `TextField`, read-only) holding the value as the API spells it — `2026-10-06`, `05:06` —
 * and pressing it opens the browser's own date or time picker, which is this platform's.
 *
 * With `disabled` it shows `display` and never opens — for a value someone else decides, such as the
 * time of day of a transaction a subscription posted. `id` is the key the form's validation knows the field by; its
 * `error` or `hint` is said beneath it, as a `TextField`'s is.
 */
const props = defineProps<{
	id: string;
	label: string;
	type: 'date' | 'time';
	/** Earliest date that may be picked, as `YYYY-MM-DD`. */
	min?: string;
	disabled?: boolean;
	/** Shown in place of the value, for a field that is not anyone's to set. */
	display?: string;
	/** Shown while the field is empty and has the focus. */
	placeholder?: string;
	error?: string | null;
	hint?: string;
}>();

const model = defineModel<string>({ required: true });
const emit = defineEmits<{ blur: [] }>();

const native = ref<HTMLInputElement | null>(null);

function pick(): void {
	if (props.disabled) return;
	const input = native.value;
	if (!input) return;

	try {
		input.showPicker();
	} catch {
		// A browser without `showPicker` still has the native control underneath to focus and type into.
		input.focus();
	}
}
</script>

<template>
	<div class="relative">
		<TextField
			variant="outlined"
			class="w-full [&_input]:cursor-pointer [&_input]:caret-transparent"
			readonly
			:model-value="display ?? model"
			:label="label"
			:placeholder="placeholder"
			:disabled="disabled"
			:error="Boolean(error)"
			:data-field="id"
			aria-haspopup="dialog"
			@click="pick"
			@keydown.enter.prevent="pick"
			@keydown.space.prevent="pick"
			@blur="emit('blur')"
		>
			<template v-if="error || hint" #supporting-text>
				<span v-if="error" role="alert">{{ error }}</span>
				<template v-else>{{ hint }}</template>
			</template>
		</TextField>

		<!-- The picker itself. It sits under the field, unseen, so the browser anchors its popup there. -->
		<input
			ref="native"
			v-model="model"
			:type="type"
			:min="min"
			tabindex="-1"
			aria-hidden="true"
			class="pointer-events-none absolute top-13 left-0 h-px w-full opacity-0"
		/>
	</div>
</template>
