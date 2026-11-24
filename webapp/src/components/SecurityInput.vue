<script setup lang="ts">
import AppIcon from '@/components/AppIcon.vue';
import { ERROR } from '@/lib/icons';
import { TextField } from 'tailwind-material3';
import { tickerOf } from '@/lib/investments';
import type { EtfMatch, Security } from '@/types';
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue';
import { t } from '@/i18n';

/**
 * The stock, fund or ETF a trade is of, typed rather than scrolled to: the PSE lists hundreds, and the trust banks
 * hundreds more UITFs. What is typed is matched against symbols first, then names (and a fund's trust entity),
 * and the list offers the closest few (`SecurityField` on Android). A fund reads as its name and who runs it,
 * since its symbol means nothing to anyone. ETFs are not all kept — there are thousands — so with `search` the
 * ones Yahoo Finance finds for what is typed follow, and picking one asks `resolve` to add it first.
 */
const props = defineProps<{
	label: string;
	securities: Security[];
	selected: Security | null;
	error?: string | null;
	search?: (query: string) => Promise<EtfMatch[]>;
	resolve?: (match: EtfMatch) => Promise<Security>;
}>();
const emit = defineEmits<{ select: [Security]; blur: [] }>();

const labelOf = (security: Security) => {
	if (security.kind === 'fund') return [security.name, security.provider].filter(Boolean).join(' · ');
	return `${tickerOf(security.symbol)} · ${security.name}`;
};
const remoteLabel = (match: EtfMatch) => `${tickerOf(match.symbol)} · ${match.name} · ${t(`investments.exchange${match.exchange}`)}`;

/** What Yahoo found for the query, less what is kept already; and whether picking one is under way or failed. */
const found = ref<EtfMatch[]>([]);
const resolving = ref(false);
const lookupError = ref<string | null>(null);
let searchTimer: ReturnType<typeof setTimeout> | undefined;
let searchRevision = 0;
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
	// An ETF is typed by its ticker (`VOO`), not its symbol (`ETF-VOO`).
	const bySymbol = props.securities.filter(
		(security) => security.symbol.startsWith(needle) || tickerOf(security.symbol).startsWith(needle),
	);
	const byName = props.securities.filter(
		(security) => security.name.toUpperCase().includes(needle) || Boolean(security.provider?.toUpperCase().includes(needle)),
	);
	return [...new Set([...bySymbol, ...byName])].slice(0, 8);
});

type Option = { key: string; label: string; security?: Security; match?: EtfMatch };
const options = computed<Option[]>(() => {
	const kept = new Set(props.securities.map((security) => security.symbol));
	return [
		...matches.value.map((security) => ({ key: security.symbol, label: labelOf(security), security })),
		...found.value.filter((match) => !kept.has(match.symbol)).map((match) => ({ key: match.symbol, label: remoteLabel(match), match })),
	];
});
const showList = computed(() => open.value && options.value.length > 0);

// Yahoo is asked a moment after typing stops, and only for something that looks like a ticker or a name.
watch(query, (typed) => {
	clearTimeout(searchTimer);
	const asked = ++searchRevision;
	const needle = typed.trim();
	// Nothing to ask while an ETF just picked is being looked up: what the field shows then is its label, not a query.
	if (
		!props.search ||
		resolving.value ||
		needle.length < 2 ||
		needle.length > 60 ||
		(props.selected && typed === labelOf(props.selected))
	) {
		found.value = [];
		return;
	}
	searchTimer = setTimeout(async () => {
		try {
			const results = await props.search!(needle);
			if (asked === searchRevision) found.value = results;
		} catch {
			// Offline, or Yahoo unwell: what is kept is still offered.
			if (asked === searchRevision) found.value = [];
		}
	}, 300);
});

async function choose(option: Option): Promise<void> {
	open.value = false;
	active.value = -1;
	lookupError.value = null;
	if (option.security) {
		query.value = labelOf(option.security);
		emit('select', option.security);
		return;
	}
	if (!option.match || !props.resolve) return;
	resolving.value = true;
	query.value = remoteLabel(option.match);
	try {
		const security = await props.resolve(option.match);
		query.value = labelOf(security);
		emit('select', security);
	} catch {
		lookupError.value = t('investments.etfLookupFailed');
	} finally {
		resolving.value = false;
	}
}

const shownError = computed(() => lookupError.value ?? props.error ?? null);

function onKeydown(event: KeyboardEvent): void {
	if (!showList.value) return;
	if (event.key === 'ArrowDown') {
		event.preventDefault();
		active.value = (active.value + 1) % options.value.length;
	} else if (event.key === 'ArrowUp') {
		event.preventDefault();
		active.value = active.value <= 0 ? options.value.length - 1 : active.value - 1;
	} else if (event.key === 'Enter' && active.value >= 0) {
		event.preventDefault();
		void choose(options.value[active.value]!);
	} else if (event.key === 'Escape') {
		event.preventDefault();
		open.value = false;
	}
}

function onClickOutside(event: MouseEvent): void {
	if (root.value && !root.value.contains(event.target as Node)) open.value = false;
}

onMounted(() => document.addEventListener('click', onClickOutside));
onBeforeUnmount(() => {
	document.removeEventListener('click', onClickOutside);
	clearTimeout(searchTimer);
});
</script>

<template>
	<!-- tailwind-material3's outlined `TextField`; `data-field` is the key the form's validation knows it by. -->
	<div ref="root" class="relative">
		<TextField
			v-model="query"
			variant="outlined"
			class="w-full"
			data-field="trade-stock"
			:label="label"
			:error="Boolean(shownError)"
			autocomplete="off"
			role="combobox"
			aria-autocomplete="list"
			:aria-expanded="showList"
			aria-controls="stock-suggestions"
			@input="
				open = true;
				active = -1;
			"
			@focus="open = true"
			@blur="emit('blur')"
			@keydown="onKeydown"
		>
			<template v-if="shownError" #trailing-icon><AppIcon :icon="ERROR" /></template>
			<template v-if="shownError" #supporting-text
				><span role="alert">{{ shownError }}</span></template
			>
			<template v-else-if="resolving" #supporting-text>{{ t('investments.etfLookingUp') }}</template>
		</TextField>

		<ul v-if="showList" id="stock-suggestions" role="listbox" class="menu absolute inset-x-0 top-15 z-20 max-h-64 overflow-y-auto">
			<li v-for="(option, index) in options" :key="option.key" role="option" :aria-selected="index === active">
				<button
					type="button"
					class="menu-item"
					:data-active="index === active"
					@mousedown.prevent="choose(option)"
					@mouseenter="active = index"
				>
					<span class="block truncate">{{ option.label }}</span>
				</button>
			</li>
		</ul>
	</div>
</template>
