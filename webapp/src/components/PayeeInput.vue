<script setup lang="ts">
import { api } from '@/lib/api';
import { isExhausted, rankPayees } from '@/lib/payees';
import type { Payee } from '@/types';
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue';

const props = defineProps<{ modelValue: string; label: string; placeholder?: string }>();
const emit = defineEmits<{ 'update:modelValue': [string]; select: [Payee] }>();

/**
 * The whole history is fetched once when the form opens and filtered here.
 * A personal ledger has tens or hundreds of payees, not thousands, so matching
 * locally is instant and avoids a request per keystroke.
 */
const all = ref<Payee[]>([]);
const open = ref(false);
const active = ref(-1);
const root = ref<HTMLElement | null>(null);

const matches = computed(() => rankPayees(all.value, props.modelValue));

const showList = computed(() => open.value && matches.value.length > 0 && !isExhausted(matches.value, props.modelValue));

async function load(): Promise<void> {
	try {
		all.value = (await api.listPayees(undefined, 50)).payees;
	} catch {
		// Suggestions are a convenience; typing still works without them.
		all.value = [];
	}
}

function choose(entry: Payee): void {
	emit('update:modelValue', entry.payee);
	emit('select', entry);
	open.value = false;
	active.value = -1;
}

function onKeydown(event: KeyboardEvent): void {
	if (!showList.value) {
		if (event.key === 'ArrowDown') open.value = true;
		return;
	}

	if (event.key === 'ArrowDown') {
		event.preventDefault();
		active.value = (active.value + 1) % matches.value.length;
	} else if (event.key === 'ArrowUp') {
		event.preventDefault();
		active.value = active.value <= 0 ? matches.value.length - 1 : active.value - 1;
	} else if (event.key === 'Enter' && active.value >= 0) {
		// Only swallow Enter when a suggestion is highlighted, so the key still
		// submits the form the rest of the time.
		event.preventDefault();
		choose(matches.value[active.value]);
	} else if (event.key === 'Escape') {
		open.value = false;
		active.value = -1;
	}
}

function onClickOutside(event: MouseEvent): void {
	if (root.value && !root.value.contains(event.target as Node)) open.value = false;
}

watch(
	() => props.modelValue,
	() => {
		active.value = -1;
	},
);

onMounted(() => {
	void load();
	document.addEventListener('click', onClickOutside);
});

onBeforeUnmount(() => document.removeEventListener('click', onClickOutside));

/** A one-line reminder of what this payee was last filed under. */
function hint(entry: Payee): string {
	const parts = [
		entry.kind === 'transfer' ? [entry.accountName, entry.toAccountName].filter(Boolean).join(' → ') : entry.accountName,
		entry.categoryName,
	].filter(Boolean);

	return parts.join(' · ');
}
</script>

<template>
	<div ref="root" class="relative">
		<label class="label" for="payee">{{ label }}</label>

		<input
			id="payee"
			:value="modelValue"
			class="input"
			autocomplete="off"
			role="combobox"
			aria-autocomplete="list"
			:aria-expanded="showList"
			aria-controls="payee-suggestions"
			:placeholder="placeholder"
			@input="emit('update:modelValue', ($event.target as HTMLInputElement).value)"
			@focus="open = true"
			@keydown="onKeydown"
		/>

		<ul
			v-if="showList"
			id="payee-suggestions"
			role="listbox"
			class="absolute z-20 mt-1 max-h-64 w-full overflow-y-auto rounded-lg border border-slate-200 bg-white py-1 shadow-lg dark:border-slate-700 dark:bg-slate-900"
		>
			<li v-for="(entry, index) in matches" :key="entry.id" role="option" :aria-selected="index === active">
				<!-- mousedown, not click: the input's blur would otherwise close the
				     list before the click landed. -->
				<button
					type="button"
					class="flex w-full items-center gap-2 px-3 py-2 text-left"
					:class="index === active ? 'bg-slate-100 dark:bg-slate-800' : 'hover:bg-slate-50 dark:hover:bg-slate-800/60'"
					@mousedown.prevent="choose(entry)"
					@mouseenter="active = index"
				>
					<span
						v-if="entry.categoryColor"
						class="h-2.5 w-2.5 shrink-0 rounded-full"
						:style="{ backgroundColor: entry.categoryColor }"
						aria-hidden="true"
					/>

					<span class="min-w-0 flex-1">
						<span class="block truncate text-sm font-medium text-slate-900 dark:text-slate-100">{{ entry.payee }}</span>
						<span v-if="hint(entry)" class="block truncate text-xs text-slate-500 dark:text-slate-400">{{ hint(entry) }}</span>
					</span>
				</button>
			</li>
		</ul>
	</div>
</template>
