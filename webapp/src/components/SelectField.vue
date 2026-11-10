<script setup lang="ts">
import AppIcon from '@/components/AppIcon.vue';
import { ARROW_DROP_DOWN } from '@/lib/icons';
import type { SelectOption } from '@/lib/selectOptions';
import { TextField } from 'tailwind-material3';
import { computed, nextTick, onBeforeUnmount, ref, watch } from 'vue';

/**
 * A Material 3 exposed dropdown menu, in place of a native `<select>`: an outlined field (tailwind-material3's
 * `TextField`, read-only) that shows the chosen option, and a menu of options that opens beneath it (or above, when there is no room) at the
 * field's width (`DropdownField` on Android). Give it its `label`, which rests in the field while nothing is
 * chosen and rises into the border once something is.
 *
 * It follows the select-only combobox pattern: focus stays on the field while the menu is open and the
 * highlighted option is announced through `aria-activedescendant`, so arrow keys, Home/End, Enter/Space,
 * Escape and typing to jump all work as they do on the native control. The menu is a popover, positioned against
 * the viewport, so it stands in the top layer above a modal dialog (tailwind-material3's are native `<dialog>`s)
 * and a dialog's scrolling never clips it; it stays inside the field in the page, so a dialog around it does not
 * make it inert. `id` is the key the form's validation
 * knows the field by; its `error` or `hint` is said beneath it, as a `TextField`'s is.
 */
const props = defineProps<{
	id: string;
	options: SelectOption[];
	/** The field's label, drawn in the field itself. */
	label?: string;
	/** For a field with no visible label. */
	ariaLabel?: string;
	required?: boolean;
	disabled?: boolean;
	/** What is wrong with the choice, said beneath the field in the error colour. */
	error?: string | null;
	hint?: string;
}>();

const emit = defineEmits<{ blur: [] }>();
const model = defineModel<string>({ required: true });

const open = ref(false);
const active = ref(-1);
const root = ref<HTMLElement | null>(null);
const field = ref<InstanceType<typeof TextField> | null>(null);
/** The field's input, which is the combobox. */
const trigger = computed(() => (field.value?.input as unknown as HTMLInputElement | null | undefined) ?? null);
const menu = ref<HTMLElement | null>(null);
const position = ref<{ left: number; width: number; top?: number; bottom?: number; maxHeight: number; above: boolean }>({
	left: 0,
	width: 0,
	maxHeight: 320,
	above: false,
});

const listboxId = computed(() => `${props.id}-listbox`);
const optionId = (index: number) => `${props.id}-option-${index}`;

const selected = computed(() => props.options.find((option) => option.value === model.value));
/** A disabled option chosen by default is the placeholder, so it reads as one. */
const isPlaceholder = computed(() => !selected.value || selected.value.disabled === true);

const enabledIndexes = computed(() => props.options.flatMap((option, index) => (option.disabled ? [] : [index])));

/** The most the menu grows before it scrolls, and the gap it keeps from the edge of the screen. */
const MENU_MAX_HEIGHT = 320;
const EDGE_GAP = 8;

function place(): void {
	// The outlined box the input sits in, not the input itself (inset by the field's padding) nor the line beneath.
	const box = (trigger.value?.closest('.group') ?? trigger.value)?.getBoundingClientRect();
	if (!box) return;

	const wanted = Math.min(MENU_MAX_HEIGHT, props.options.length * 48 + 16);
	const below = window.innerHeight - box.bottom - EDGE_GAP;
	const above = box.top - EDGE_GAP;
	// Below by default, as the field's own dropdown arrow promises; above only when it would not fit and there is more room there.
	const flip = below < wanted && above > below;

	position.value = flip
		? {
				left: box.left,
				width: box.width,
				bottom: window.innerHeight - box.top + 2,
				maxHeight: Math.min(MENU_MAX_HEIGHT, above),
				above: true,
			}
		: { left: box.left, width: box.width, top: box.bottom + 2, maxHeight: Math.min(MENU_MAX_HEIGHT, below), above: false };
}

function onOutsidePointer(event: PointerEvent): void {
	const target = event.target as Node;
	if (root.value?.contains(target) || menu.value?.contains(target)) return;
	close();
}

function scrollActiveIntoView(): void {
	void nextTick(() => document.getElementById(optionId(active.value))?.scrollIntoView({ block: 'nearest' }));
}

function show(start?: 'first' | 'last'): void {
	if (props.disabled || open.value) return;

	const chosen = props.options.findIndex((option) => option.value === model.value && !option.disabled);
	const indexes = enabledIndexes.value;
	active.value =
		start === 'first'
			? (indexes[0] ?? -1)
			: start === 'last'
				? (indexes[indexes.length - 1] ?? -1)
				: chosen >= 0
					? chosen
					: (indexes[0] ?? -1);

	place();
	open.value = true;
	void nextTick(() => {
		try {
			menu.value?.showPopover?.();
		} catch {
			// Already showing, or a browser without popovers: the menu is still fixed in place over the page.
		}
	});
	scrollActiveIntoView();
}

function close(): void {
	open.value = false;
}

/** A press on the field toggles the menu; one on an option inside it is the option's to answer. */
function onFieldClick(event: MouseEvent): void {
	if (props.disabled || menu.value?.contains(event.target as Node)) return;
	if (open.value) close();
	else show();
}

function choose(index: number): void {
	const option = props.options[index];
	if (!option || option.disabled) return;

	model.value = option.value;
	close();
	trigger.value?.focus();
	// A choice counts as visiting the field; focus never left it, so there is no blur to say so.
	emit('blur');
}

/** Moves the highlight to the next choosable option in a direction, without wrapping, as a native list does not. */
function move(step: 1 | -1): void {
	const indexes = enabledIndexes.value;
	const at = indexes.indexOf(active.value);
	const next = indexes[at === -1 ? (step === 1 ? 0 : indexes.length - 1) : Math.min(indexes.length - 1, Math.max(0, at + step))];
	if (next === undefined) return;

	active.value = next;
	scrollActiveIntoView();
}

// Typing jumps to the next option that starts with what has been typed, for as long as the keystrokes keep coming.
let typed = '';
let typedTimer: ReturnType<typeof setTimeout> | undefined;

function typeahead(character: string): void {
	typed += character.toLowerCase();
	clearTimeout(typedTimer);
	typedTimer = setTimeout(() => (typed = ''), 600);

	const indexes = enabledIndexes.value;
	const from = Math.max(0, indexes.indexOf(open.value ? active.value : props.options.findIndex((option) => option.value === model.value)));
	// Pressing the same letter again cycles through the options that share it, so the search starts after the current one.
	const ordered =
		typed.length === 1 ? [...indexes.slice(from + 1), ...indexes.slice(0, from + 1)] : [...indexes.slice(from), ...indexes.slice(0, from)];
	const hit = ordered.find((index) => props.options[index].label.trim().toLowerCase().startsWith(typed));
	if (hit === undefined) return;

	if (open.value) {
		active.value = hit;
		scrollActiveIntoView();
	} else {
		model.value = props.options[hit].value;
	}
}

function onKeydown(event: KeyboardEvent): void {
	if (props.disabled || event.altKey || event.ctrlKey || event.metaKey) return;

	if (!open.value) {
		if (['ArrowDown', 'ArrowUp', 'Enter', ' '].includes(event.key)) {
			event.preventDefault();
			show();
		} else if (event.key === 'Home' || event.key === 'End') {
			event.preventDefault();
			show(event.key === 'Home' ? 'first' : 'last');
		} else if (event.key.length === 1) {
			typeahead(event.key);
		}
		return;
	}

	switch (event.key) {
		case 'ArrowDown':
			event.preventDefault();
			move(1);
			break;
		case 'ArrowUp':
			event.preventDefault();
			move(-1);
			break;
		case 'Home':
		case 'End': {
			event.preventDefault();
			const indexes = enabledIndexes.value;
			active.value = (event.key === 'Home' ? indexes[0] : indexes[indexes.length - 1]) ?? active.value;
			scrollActiveIntoView();
			break;
		}
		case 'Enter':
		case ' ':
			event.preventDefault();
			choose(active.value);
			break;
		case 'Escape':
			// Closes the menu only: a dialog around the field listens for Escape on the document and would close with it.
			event.preventDefault();
			event.stopPropagation();
			close();
			break;
		case 'Tab':
			close();
			break;
		default:
			if (event.key.length === 1) typeahead(event.key);
	}
}

// While the menu is open it is kept against the field through scrolling and resizing, and a press elsewhere dismisses it.
function listen(): void {
	document.addEventListener('pointerdown', onOutsidePointer, true);
	window.addEventListener('resize', place);
	window.addEventListener('scroll', place, true);
}

function unlisten(): void {
	document.removeEventListener('pointerdown', onOutsidePointer, true);
	window.removeEventListener('resize', place);
	window.removeEventListener('scroll', place, true);
}

watch(open, (isOpen) => (isOpen ? listen() : unlisten()));

onBeforeUnmount(() => {
	unlisten();
	clearTimeout(typedTimer);
});
</script>

<template>
	<!-- A press anywhere on the field opens it, not only on its text. -->
	<div ref="root" class="relative" @click="onFieldClick">
		<TextField
			ref="field"
			variant="outlined"
			class="w-full [&_input]:cursor-pointer [&_input]:caret-transparent"
			readonly
			:model-value="isPlaceholder ? '' : (selected?.label?.trim() ?? '')"
			:label="label"
			:disabled="disabled"
			:error="Boolean(error)"
			:data-field="id"
			role="combobox"
			:aria-label="label ? undefined : ariaLabel"
			aria-haspopup="listbox"
			:aria-expanded="open"
			:aria-controls="listboxId"
			:aria-activedescendant="open && active >= 0 ? optionId(active) : undefined"
			:aria-required="required"
			@keydown="onKeydown"
			@blur="
				close();
				emit('blur');
			"
		>
			<template #trailing-icon>
				<!-- The dropdown arrow turns over while the menu is open. -->
				<AppIcon :icon="ARROW_DROP_DOWN" class="transition-transform duration-200 ease-standard" :class="open ? 'rotate-180' : ''" />
			</template>
			<template v-if="error || hint" #supporting-text>
				<span v-if="error" role="alert">{{ error }}</span>
				<template v-else>{{ hint }}</template>
			</template>
		</TextField>

		<!-- The field cannot be `required` while it is read-only, so this stands in for it: it is what the form's own
		     validation checks and points at. -->
		<input
			v-if="required"
			:value="model"
			tabindex="-1"
			required
			aria-hidden="true"
			class="pointer-events-none absolute top-12 left-1/2 h-px w-px opacity-0"
			@focus="trigger?.focus()"
		/>

		<Transition
			enter-active-class="transition duration-150 ease-standard-decelerate"
			enter-from-class="scale-y-90 opacity-0"
			leave-active-class="transition duration-100 ease-standard-accelerate"
			leave-to-class="opacity-0"
		>
			<!-- Options are not focusable: focus stays on the field, and pressing one must not take it. -->
			<div
				v-if="open"
				:id="listboxId"
				ref="menu"
				popover="manual"
				role="listbox"
				:aria-label="ariaLabel ?? label"
				class="menu fixed inset-auto z-[60] m-0 overflow-y-auto overscroll-contain border-0"
				:class="position.above ? 'origin-bottom' : 'origin-top'"
				:style="{
					left: `${position.left}px`,
					width: `${position.width}px`,
					top: position.top === undefined ? undefined : `${position.top}px`,
					bottom: position.bottom === undefined ? undefined : `${position.bottom}px`,
					maxHeight: `${position.maxHeight}px`,
				}"
				@mousedown.prevent
			>
				<div
					v-for="(option, index) in options"
					:id="optionId(index)"
					:key="option.value"
					role="option"
					class="menu-option"
					:class="option.indent ? 'pl-7' : ''"
					:aria-selected="option.value === model && !option.disabled"
					:aria-disabled="option.disabled || undefined"
					:data-active="index === active"
					@mousemove="!option.disabled && (active = index)"
					@click="choose(index)"
				>
					<span class="min-w-0 flex-1 truncate">{{ option.label.trim() }}</span>
				</div>
			</div>
		</Transition>
	</div>
</template>
