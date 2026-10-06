<script setup lang="ts">
import { ref } from 'vue';

/**
 * A field that opens a picker instead of taking typing, for a date or a time of day (`PickerField` on
 * Android). It reads as an outlined field holding the value as the API spells it — `2026-10-06`, `05:06` —
 * and pressing it opens the browser's own date or time picker, which is this platform's.
 *
 * With `disabled` it shows `display` and never opens — for a value someone else decides, such as the
 * time of day of a transaction a subscription posted.
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
	invalid?: boolean;
	describedby?: string;
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
	<div class="field" :data-filled="Boolean(display ?? model)">
		<input
			:id="id"
			class="input cursor-pointer"
			type="text"
			readonly
			:value="display ?? model"
			:placeholder="placeholder ?? ' '"
			:disabled="disabled"
			:aria-invalid="invalid || undefined"
			:aria-describedby="describedby"
			aria-haspopup="dialog"
			@click="pick"
			@keydown.enter.prevent="pick"
			@keydown.space.prevent="pick"
			@blur="emit('blur')"
		/>
		<label class="label" :for="id">{{ label }}</label>

		<!-- The picker itself. It sits under the field, unseen, so the browser anchors its popup there. -->
		<input
			ref="native"
			v-model="model"
			:type="type"
			:min="min"
			tabindex="-1"
			aria-hidden="true"
			class="pointer-events-none absolute bottom-0 left-0 h-px w-full opacity-0"
		/>
	</div>
</template>
