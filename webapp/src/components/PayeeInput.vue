<script setup lang="ts">
import AppIcon from '@/components/AppIcon.vue';
import FieldSupport from '@/components/FieldSupport.vue';
import { api } from '@/lib/api';
import { readPayees, replacePayees } from '@/lib/cache';
import { ERROR } from '@/lib/icons';
import { isExhausted, PAYEE_LIMIT, rankPayees } from '@/lib/payees';
import { supportId } from '@/lib/validation';
import type { Payee } from '@/types';
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue';

/**
 * The payee field: an outlined text field that offers back what was last filed under a name as it is
 * typed (`PayeeField` on Android), so the rest of the form can fill itself in. `error` is what the form's
 * validation has to say about it.
 */
defineProps<{ label: string; placeholder?: string; error?: string | null }>();
const emit = defineEmits<{ select: [Payee]; blur: [] }>();
const model = defineModel<string>({ required: true });

/**
 * The whole history is fetched once when the form opens and filtered here.
 * A personal ledger has tens or hundreds of payees, not thousands, so matching
 * locally is instant and avoids a request per keystroke. It is kept in the
 * local copy's `payees` store too, which a full sync replaces.
 */
const all = ref<Payee[]>([]);
const open = ref(false);
const active = ref(-1);
const root = ref<HTMLElement | null>(null);

const matches = computed(() => rankPayees(all.value, model.value));

const showList = computed(() => open.value && matches.value.length > 0 && !isExhausted(matches.value, model.value));

async function load(): Promise<void> {
	// What the local copy remembers first, so suggestions work offline, then what the API has now.
	const remembered = await readPayees();
	if (remembered) all.value = remembered;

	try {
		const fresh = (await api.listPayees(undefined, PAYEE_LIMIT)).payees;
		all.value = fresh;
		void replacePayees(fresh);
	} catch {
		// Suggestions are a convenience; typing still works without them.
	}
}

function choose(entry: Payee): void {
	model.value = entry.payee;
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
		// The list goes first; the form behind it only closes on the next Escape.
		event.preventDefault();
		open.value = false;
		active.value = -1;
	}
}

function onClickOutside(event: MouseEvent): void {
	if (root.value && !root.value.contains(event.target as Node)) open.value = false;
}

watch(
	() => model.value,
	() => {
		active.value = -1;
	},
);

/** Typing brings back a list that was put away. */
function onInput(event: Event): void {
	model.value = (event.target as HTMLInputElement).value;
	open.value = true;
}

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
	<div ref="root">
		<div class="field">
			<input
				id="payee"
				:value="model"
				class="input"
				:class="{ 'pr-12': error }"
				autocomplete="off"
				role="combobox"
				aria-autocomplete="list"
				:aria-expanded="showList"
				:aria-invalid="error ? 'true' : undefined"
				:aria-describedby="error ? supportId('payee') : undefined"
				aria-controls="payee-suggestions"
				:placeholder="placeholder ?? ' '"
				@input="onInput"
				@focus="open = true"
				@blur="emit('blur')"
				@keydown="onKeydown"
			/>
			<label class="label" for="payee">{{ label }}</label>
			<AppIcon v-if="error" :icon="ERROR" class="pointer-events-none absolute top-4 right-3 text-error" />

			<ul v-if="showList" id="payee-suggestions" role="listbox" class="menu absolute inset-x-0 z-20 max-h-64 overflow-y-auto">
				<li v-for="(entry, index) in matches" :key="entry.id" role="option" :aria-selected="index === active">
					<!-- mousedown, not click: the input's blur would otherwise close the
					     list before the click landed. -->
					<button
						type="button"
						class="menu-item py-1"
						:class="index === active ? 'bg-on-surface/10' : ''"
						@mousedown.prevent="choose(entry)"
						@mouseenter="active = index"
					>
						<span class="min-w-0 flex-1">
							<span class="block truncate">{{ entry.payee }}</span>
							<span v-if="hint(entry)" class="type-body-small block truncate">{{ hint(entry) }}</span>
						</span>
					</button>
				</li>
			</ul>
		</div>
		<FieldSupport id="payee" :error="error" />
	</div>
</template>
