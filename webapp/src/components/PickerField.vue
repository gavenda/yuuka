<script setup lang="ts">
import { DatePicker, TextField, TimePicker } from 'tailwind-material3';
import { computed, ref } from 'vue';
import { t } from '@/i18n';
import { useModalCount } from '@/lib/modal';

/**
 * A field that opens a picker instead of taking typing, for a date or a time of day (`PickerField` on
 * Android). It reads as an outlined field (tailwind-material3's `TextField`, read-only) holding the value as the API spells it — `2026-10-06`, `05:06` —
 * and pressing it opens the library's modal `DatePicker` or `TimePicker`, Material's own pickers as
 * `YuukaDatePickerDialog` and `YuukaTimePickerDialog` are on Android: a change is a draft until OK, and the
 * time is told in twelve hours as it is there.
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

const open = ref(false);
useModalCount(() => open.value);

/** `YYYY-MM-DD` read as a local calendar day; `new Date('2026-10-06')` would be UTC midnight, a day early west of it. */
function toDate(value: string | undefined): Date | null {
	const match = /^(\d{4})-(\d{2})-(\d{2})$/.exec(value ?? '');
	return match ? new Date(Number(match[1]), Number(match[2]) - 1, Number(match[3])) : null;
}

function fromDate(date: Date): string {
	const pad = (n: number) => String(n).padStart(2, '0');
	return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`;
}

const date = computed({
	get: () => toDate(model.value),
	set: (value: Date | null) => {
		if (value) model.value = fromDate(value);
	},
});

const time = computed({
	get: () => model.value || null,
	set: (value: string | null) => {
		if (value) model.value = value;
	},
});

const earliest = computed(() => toDate(props.min));
const labels = computed(() => ({ confirm: t('common.ok'), cancel: t('common.cancel') }));

function pick(): void {
	if (!props.disabled) open.value = true;
}

/** Closing the picker is the field being left: what was picked (or not) is now the form's to judge. */
function closed(): void {
	emit('blur');
}
</script>

<template>
	<div>
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
			:aria-expanded="open"
			@click="pick"
			@keydown.enter.prevent="pick"
			@keydown.space.prevent="pick"
			@blur="!open && emit('blur')"
		>
			<template v-if="error || hint" #supporting-text>
				<span v-if="error" role="alert">{{ error }}</span>
				<template v-else>{{ hint }}</template>
			</template>
		</TextField>

		<DatePicker v-if="type === 'date'" v-model="date" v-model:open="open" :min="earliest" :labels="labels" @closed="closed" />
		<TimePicker v-else v-model="time" v-model:open="open" :hour12="true" :labels="labels" @closed="closed" />
	</div>
</template>
