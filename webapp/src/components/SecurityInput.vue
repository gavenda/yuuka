<script setup lang="ts">
import AppIcon from '@/components/AppIcon.vue';
import FieldSupport from '@/components/FieldSupport.vue';
import { ERROR } from '@/lib/icons';
import { supportId } from '@/lib/validation';
import type { Security } from '@/types';
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue';

/**
 * The stock a trade is of, typed rather than scrolled to: the PSE lists hundreds. What is typed is matched
 * against symbols first, then names, and the list offers the closest few (`SecurityField` on Android).
 */
const props = defineProps<{ label: string; securities: Security[]; selected: Security | null; error?: string | null }>();
const emit = defineEmits<{ select: [Security]; blur: [] }>();

const labelOf = (security: Security) => `${security.symbol} · ${security.name}`;
const query = ref(props.selected ? labelOf(props.selected) : '');
const open = ref(false);
const active = ref(-1);
const root = ref<HTMLElement | null>(null);

watch(
	() => props.selected,
	(security) => {
		if (security) query.value = labelOf(security);
	},
);

const matches = computed(() => {
	const needle = query.value.trim().toUpperCase();
	if (!needle || (props.selected && query.value === labelOf(props.selected))) return [];
	const bySymbol = props.securities.filter((security) => security.symbol.startsWith(needle));
	const byName = props.securities.filter((security) => security.name.toUpperCase().includes(needle));
	return [...new Set([...bySymbol, ...byName])].slice(0, 8);
});
const showList = computed(() => open.value && matches.value.length > 0);

function choose(security: Security): void {
	query.value = labelOf(security);
	emit('select', security);
	open.value = false;
	active.value = -1;
}

function onKeydown(event: KeyboardEvent): void {
	if (!showList.value) return;
	if (event.key === 'ArrowDown') {
		event.preventDefault();
		active.value = (active.value + 1) % matches.value.length;
	} else if (event.key === 'ArrowUp') {
		event.preventDefault();
		active.value = active.value <= 0 ? matches.value.length - 1 : active.value - 1;
	} else if (event.key === 'Enter' && active.value >= 0) {
		event.preventDefault();
		choose(matches.value[active.value]!);
	} else if (event.key === 'Escape') {
		event.preventDefault();
		open.value = false;
	}
}

function onClickOutside(event: MouseEvent): void {
	if (root.value && !root.value.contains(event.target as Node)) open.value = false;
}

onMounted(() => document.addEventListener('click', onClickOutside));
onBeforeUnmount(() => document.removeEventListener('click', onClickOutside));
</script>

<template>
	<div ref="root">
		<div class="field">
			<input
				id="trade-stock"
				v-model="query"
				class="input uppercase"
				:class="{ 'pr-12': error }"
				autocomplete="off"
				role="combobox"
				aria-autocomplete="list"
				:aria-expanded="showList"
				:aria-invalid="error ? 'true' : undefined"
				:aria-describedby="error ? supportId('trade-stock') : undefined"
				aria-controls="stock-suggestions"
				placeholder=" "
				@input="
					open = true;
					active = -1;
				"
				@focus="open = true"
				@blur="emit('blur')"
				@keydown="onKeydown"
			/>
			<label class="label" for="trade-stock">{{ label }}</label>
			<AppIcon v-if="error" :icon="ERROR" class="pointer-events-none absolute top-4 right-3 text-error" />

			<ul v-if="showList" id="stock-suggestions" role="listbox" class="menu absolute inset-x-0 z-20 max-h-64 overflow-y-auto">
				<li v-for="(security, index) in matches" :key="security.symbol" role="option" :aria-selected="index === active">
					<button
						type="button"
						class="menu-item py-1"
						:class="index === active ? 'bg-on-surface/10' : ''"
						@mousedown.prevent="choose(security)"
						@mouseenter="active = index"
					>
						<span class="block truncate">{{ labelOf(security) }}</span>
					</button>
				</li>
			</ul>
		</div>
		<FieldSupport id="trade-stock" :error="error" />
	</div>
</template>
