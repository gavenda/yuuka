<script setup lang="ts">
import { ApiError } from '@/lib/api';
import { formatMoney } from '@/lib/money';
import { useBudgetStore } from '@/stores/budget';
import { useLedgerStore } from '@/stores/ledger';
import { computed, ref, watch } from 'vue';

const props = defineProps<{ open: boolean }>();
const emit = defineEmits<{ close: [] }>();

const ledger = useLedgerStore();
const budget = useBudgetStore();

/** Common choices; any 3-letter code is accepted, so the field stays free-text. */
const SUGGESTIONS = ['PHP', 'USD', 'EUR', 'GBP', 'JPY', 'AUD', 'CAD', 'SGD', 'HKD', 'KRW', 'CNY', 'INR'];

const draft = ref(ledger.displayCurrency);
const budgetModeDraft = ref(ledger.budgetMode);
const error = ref<string | null>(null);
const saving = ref(false);

const normalised = computed(() => draft.value.trim().toUpperCase());
const isValid = computed(() => /^[A-Za-z]{3}$/.test(draft.value.trim()));
const currencyChanged = computed(() => normalised.value !== ledger.displayCurrency);
const budgetModeChanged = computed(() => budgetModeDraft.value !== ledger.budgetMode);
const changed = computed(() => currencyChanged.value || budgetModeChanged.value);

// Reopening should show what is actually saved, not a half-typed attempt.
watch(
	() => props.open,
	(open) => {
		if (!open) return;
		draft.value = ledger.displayCurrency;
		budgetModeDraft.value = ledger.budgetMode;
		error.value = null;
	},
);

const preview = computed(() => (isValid.value ? formatMoney(123_456, normalised.value) : '—'));

async function save(): Promise<void> {
	if (!isValid.value || !changed.value) return;

	saving.value = true;
	error.value = null;

	try {
		await ledger.updateSettings({
			...(currencyChanged.value ? { displayCurrency: normalised.value } : {}),
			...(budgetModeChanged.value ? { budgetMode: budgetModeDraft.value } : {}),
		});
		// The summary carries formatted figures nowhere, but refreshing keeps the
		// dashboard consistent with anything either setting touched.
		await budget.refresh();
		emit('close');
	} catch (caught) {
		error.value = caught instanceof ApiError ? caught.message : 'Could not save the setting.';
	} finally {
		saving.value = false;
	}
}
</script>

<template>
	<form class="space-y-4" @submit.prevent="save">
		<div>
			<label class="label" for="display-currency">Display currency</label>
			<input
				id="display-currency"
				v-model="draft"
				class="input uppercase"
				maxlength="3"
				list="currency-suggestions"
				autocomplete="off"
				required
			/>
			<datalist id="currency-suggestions">
				<option v-for="code in SUGGESTIONS" :key="code" :value="code" />
			</datalist>

			<p class="mt-1.5 text-xs text-slate-500 dark:text-slate-400">
				Used for net worth, the monthly summary and budgets. Each account keeps its own currency for what it holds.
			</p>
		</div>

		<div class="rounded-lg bg-slate-50 px-3 py-2 dark:bg-slate-950/40">
			<p class="text-xs text-slate-500 dark:text-slate-400">Preview</p>
			<p class="tabular mt-0.5 text-lg font-semibold text-slate-900 dark:text-white">{{ preview }}</p>
		</div>

		<p v-if="draft.trim() && !isValid" class="text-sm text-amber-700 dark:text-amber-400" role="alert">
			Use a 3-letter currency code, such as PHP.
		</p>

		<div>
			<label class="label">Budget mode</label>
			<div class="flex overflow-hidden rounded-md border border-slate-300 dark:border-slate-700">
				<button
					type="button"
					class="flex-1 px-3 py-1.5 text-sm font-medium"
					:class="budgetModeDraft === 'fixed' ? 'bg-blue-600 text-white' : 'text-slate-500 dark:text-slate-400'"
					@click="budgetModeDraft = 'fixed'"
				>
					Fixed
				</button>
				<button
					type="button"
					class="flex-1 px-3 py-1.5 text-sm font-medium"
					:class="budgetModeDraft === 'monthly' ? 'bg-blue-600 text-white' : 'text-slate-500 dark:text-slate-400'"
					@click="budgetModeDraft = 'monthly'"
				>
					Monthly
				</button>
			</div>

			<p class="mt-1.5 text-xs text-slate-500 dark:text-slate-400">
				<template v-if="budgetModeDraft === 'fixed'"> A category's planned amount applies to every month, until changed again. </template>
				<template v-else> Each month keeps its own planned amount, set separately. </template>
			</p>
		</div>

		<p v-if="error" class="rounded-lg bg-rose-50 px-3 py-2 text-sm text-rose-700 dark:bg-rose-500/10 dark:text-rose-400" role="alert">
			{{ error }}
		</p>

		<div class="flex justify-end gap-2 pt-2">
			<button type="button" class="btn-secondary" @click="emit('close')">Cancel</button>
			<button type="submit" class="btn-primary" :disabled="saving || !isValid || !changed">
				{{ saving ? 'Saving…' : 'Save' }}
			</button>
		</div>
	</form>
</template>
