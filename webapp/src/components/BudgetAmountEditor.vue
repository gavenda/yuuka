<script setup lang="ts">
import { parseMoney, parsePercent, toDecimalString } from '@/lib/money';
import { displayMoney } from '@/lib/privacy';
import { useBudgetStore } from '@/stores/budget';
import type { CategoryBreakdown } from '@/types';
import ConnectedButtonGroup from '@/components/ConnectedButtonGroup.vue';
import { ref } from 'vue';

const props = defineProps<{ entry: CategoryBreakdown; currency: string }>();

const budget = useBudgetStore();

const editing = ref(false);
const mode = ref<'amount' | 'percent'>('amount');
const draft = ref('');
const saving = ref(false);

function start(): void {
	if (props.entry.plannedPercent !== null) {
		mode.value = 'percent';
		draft.value = String(props.entry.plannedPercent);
	} else {
		mode.value = 'amount';
		draft.value = props.entry.planned > 0 ? toDecimalString(props.entry.planned) : '';
	}
	editing.value = true;
}

async function commit(): Promise<void> {
	if (draft.value.trim() === '') {
		saving.value = true;
		try {
			await budget.setBudget(props.entry.categoryId, { amount: 0 });
			editing.value = false;
		} finally {
			saving.value = false;
		}
		return;
	}

	if (mode.value === 'percent') {
		const percent = parsePercent(draft.value);
		if (percent === null) return;

		saving.value = true;
		try {
			await budget.setBudget(props.entry.categoryId, { percent });
			editing.value = false;
		} finally {
			saving.value = false;
		}
		return;
	}

	const amount = parseMoney(draft.value);
	if (amount === null || amount < 0) return;

	saving.value = true;
	try {
		await budget.setBudget(props.entry.categoryId, { amount });
		editing.value = false;
	} finally {
		saving.value = false;
	}
}
</script>

<template>
	<form v-if="editing" class="flex items-center gap-1" @submit.prevent="commit">
		<!-- Switching tabs only changes how the same draft is interpreted on save, so it carries over rather than resetting. -->
		<ConnectedButtonGroup
			v-model="mode"
			class="shrink-0"
			label="Enter as"
			dense
			:options="[
				{ value: 'amount', label: currency },
				{ value: 'percent', label: '%' },
			]"
		/>
		<input
			v-model="draft"
			class="input input-sm tabular w-24 text-right"
			inputmode="decimal"
			:placeholder="mode === 'percent' ? '0' : '0.00'"
			autofocus
			@keydown.esc="editing = false"
		/>
		<button type="submit" class="btn-primary btn-sm" :disabled="saving">Save</button>
	</form>

	<button v-else type="button" class="btn-secondary btn-sm tabular" @click="start">
		<template v-if="entry.plannedPercent !== null"
			>{{ entry.plannedPercent }}% &middot; {{ displayMoney(entry.planned, currency) }}</template
		>
		<template v-else-if="entry.planned > 0">{{ displayMoney(entry.planned, currency) }}</template>
		<template v-else>Set budget</template>
	</button>
</template>
