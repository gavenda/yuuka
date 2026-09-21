<script setup lang="ts">
import type { SelectOption } from '@/lib/selectOptions';
import { computed, nextTick, onBeforeUnmount, ref, watch } from 'vue';

/**
 * A choice set the way Android's settings set one, for a settings screen (a form keeps the `SelectField` combobox): a preference row that reads as the label with the current
 * choice beneath it, and a dialog of radio buttons that opens when it is tapped. Choosing closes the dialog
 * at once; Cancel, the scrim and Escape leave the value as it was. It stands where a native `<select>` did,
 * and the label lives here rather than beside it — the row is the label.
 *
 * The radios are native inputs in a `radiogroup`, so a screen reader reads them as such. The arrow keys move
 * between them without choosing (a native radio would choose, and so close, on the first press), and Space
 * or Enter chooses the one in focus. The dialog is teleported out and sits above any dialog the field is in,
 * and Escape is caught before it can reach that one.
 */
const props = defineProps<{
	id: string;
	/** What is being chosen: the row's title and the dialog's. */
	label: string;
	modelValue: string;
	options: SelectOption[];
	required?: boolean;
	disabled?: boolean;
	/** The choice is not acceptable: the row's title takes the error colour. What is wrong is said beneath it, in a `FieldSupport`. */
	invalid?: boolean;
	/** The id of that supporting text. */
	describedby?: string;
}>();

const emit = defineEmits<{ 'update:modelValue': [value: string]; blur: [] }>();

const open = ref(false);
const trigger = ref<HTMLButtonElement | null>(null);
const group = ref<HTMLElement | null>(null);

const titleId = computed(() => `${props.id}-title`);

const selected = computed(() => props.options.find((option) => option.value === props.modelValue));
/** A disabled option chosen by default is the placeholder, so it reads as one. */
const isPlaceholder = computed(() => !selected.value || selected.value.disabled === true);

/** A placeholder ("Select an account") is what the row says while nothing is chosen, not something to choose. */
const choices = computed(() => props.options.filter((option) => !option.disabled));

function radios(): HTMLInputElement[] {
	return [...(group.value?.querySelectorAll<HTMLInputElement>('input[type="radio"]:not(:disabled)') ?? [])];
}

function show(): void {
	if (props.disabled || open.value) return;

	open.value = true;
	// Focus lands on the current choice, or the first, so the keyboard starts where the value is.
	void nextTick(() => (radios().find((radio) => radio.checked) ?? radios()[0])?.focus());
}

function close(): void {
	if (!open.value) return;

	open.value = false;
	trigger.value?.focus();
	// The row has been visited: what it holds can now be judged.
	emit('blur');
}

function choose(option: SelectOption): void {
	emit('update:modelValue', option.value);
	close();
}

function onGroupKeydown(event: KeyboardEvent): void {
	const all = radios();
	const at = all.indexOf(document.activeElement as HTMLInputElement);

	switch (event.key) {
		case 'ArrowDown':
		case 'ArrowRight':
			event.preventDefault();
			all[Math.min(all.length - 1, at + 1)]?.focus();
			break;
		case 'ArrowUp':
		case 'ArrowLeft':
			event.preventDefault();
			all[Math.max(0, at - 1)]?.focus();
			break;
		case 'Home':
			event.preventDefault();
			all[0]?.focus();
			break;
		case 'End':
			event.preventDefault();
			all[all.length - 1]?.focus();
			break;
		case 'Enter': {
			event.preventDefault();
			const index = all.indexOf(document.activeElement as HTMLInputElement);
			if (index >= 0) choose(choices.value[index]);
			break;
		}
	}
}

// Caught on the way down, so a dialog around the field, listening on the document, does not close with this one.
function onEscape(event: KeyboardEvent): void {
	if (event.key !== 'Escape') return;

	event.preventDefault();
	event.stopPropagation();
	close();
}

watch(open, (isOpen) => {
	if (isOpen) document.addEventListener('keydown', onEscape, true);
	else document.removeEventListener('keydown', onEscape, true);
});

onBeforeUnmount(() => document.removeEventListener('keydown', onEscape, true));
</script>

<template>
	<div class="relative">
		<button
			:id="id"
			ref="trigger"
			type="button"
			aria-haspopup="dialog"
			:aria-expanded="open"
			:aria-required="required"
			:aria-invalid="invalid || undefined"
			:aria-describedby="describedby"
			:disabled="disabled"
			class="state-layer focus-ring -mx-2 block w-[calc(100%+1rem)] cursor-pointer rounded-md px-2 py-3 text-left disabled:cursor-not-allowed"
			@click="show"
		>
			<span class="block text-base" :class="[invalid ? 'text-error' : 'text-on-surface', disabled ? 'opacity-38' : '']">{{ label }}</span>
			<span
				class="block truncate text-sm"
				:class="[isPlaceholder ? 'text-on-surface-variant/70' : 'text-on-surface-variant', disabled ? 'opacity-38' : '']"
			>
				{{ selected?.label?.trim() || ' ' }}
			</span>
		</button>

		<!-- A button cannot be `required`, so this stands in for it: it is what the form's own validation checks and points at. -->
		<input
			v-if="required"
			:value="modelValue"
			tabindex="-1"
			required
			aria-hidden="true"
			class="pointer-events-none absolute bottom-0 left-1/2 h-px w-px opacity-0"
			@focus="trigger?.focus()"
		/>

		<Teleport to="body">
			<Transition
				enter-active-class="transition duration-200 ease-standard-decelerate"
				enter-from-class="opacity-0"
				leave-active-class="transition duration-150 ease-standard-accelerate"
				leave-to-class="opacity-0"
			>
				<div v-if="open" class="fixed inset-0 z-[60] flex items-center justify-center bg-scrim/32 p-4" @click.self="close">
					<div
						role="dialog"
						aria-modal="true"
						:aria-labelledby="titleId"
						class="dialog-enter flex max-h-[85vh] w-full max-w-sm flex-col rounded-xl bg-surface-container-high shadow-elevation-3"
					>
						<h2 :id="titleId" class="px-6 pt-6 pb-4 text-2xl text-on-surface">{{ label }}</h2>

						<div
							ref="group"
							role="radiogroup"
							:aria-labelledby="titleId"
							class="min-h-0 overflow-y-auto overscroll-contain"
							@keydown="onGroupKeydown"
						>
							<label
								v-for="option in choices"
								:key="option.value"
								class="state-layer type-label-large flex min-h-12 cursor-pointer items-center gap-4 py-3 pr-6 text-on-surface"
								:class="option.indent ? 'pl-12' : 'pl-6'"
							>
								<input
									type="radio"
									:name="id"
									:value="option.value"
									:checked="option.value === modelValue"
									class="size-5 shrink-0 cursor-pointer accent-primary"
									@click="choose(option)"
								/>
								<span class="min-w-0 flex-1">{{ option.label.trim() }}</span>
							</label>
						</div>

						<div class="flex justify-end px-4 py-3">
							<button type="button" class="btn btn-text" @click="close">Cancel</button>
						</div>
					</div>
				</div>
			</Transition>
		</Teleport>
	</div>
</template>
