<script setup lang="ts">
import ConnectedButtonGroup from '@/components/ConnectedButtonGroup.vue';
import FabButton from '@/components/FabButton.vue';
import { ApiError } from '@/lib/api';
import { SAVE } from '@/lib/icons';
import { formatMoney } from '@/lib/money';
import { showSnackbar } from '@/lib/snackbar';
import { useBudgetStore } from '@/stores/budget';
import { useLedgerStore } from '@/stores/ledger';
import { computed, onMounted, ref, watch } from 'vue';

const ledger = useLedgerStore();
const budget = useBudgetStore();

/** Common choices; any 3-letter code is accepted, so the field stays free-text. */
const SUGGESTIONS = ['PHP', 'USD', 'EUR', 'GBP', 'JPY', 'AUD', 'CAD', 'SGD', 'HKD', 'KRW', 'CNY', 'INR'];

const draft = ref(ledger.displayCurrency);
const budgetModeDraft = ref(ledger.budgetMode);
const defaultAccountDraft = ref(ledger.defaultAccountId ?? '');
const error = ref<string | null>(null);
const saving = ref(false);

const normalised = computed(() => draft.value.trim().toUpperCase());
const isValid = computed(() => /^[A-Za-z]{3}$/.test(draft.value.trim()));
const currencyChanged = computed(() => normalised.value !== ledger.displayCurrency);
const budgetModeChanged = computed(() => budgetModeDraft.value !== ledger.budgetMode);
const defaultAccountChanged = computed(() => (defaultAccountDraft.value || null) !== ledger.defaultAccountId);
const changed = computed(() => currencyChanged.value || budgetModeChanged.value || defaultAccountChanged.value);

const preview = computed(() => (isValid.value ? formatMoney(123_456, normalised.value) : '—'));

// The screen shows what is actually saved, so it follows the ledger: on arrival, once it has loaded, and after a save.
watch(
	() => [ledger.displayCurrency, ledger.budgetMode, ledger.defaultAccountId] as const,
	([currency, mode, account]) => {
		draft.value = currency;
		budgetModeDraft.value = mode;
		defaultAccountDraft.value = account ?? '';
	},
);

onMounted(() => ledger.load());

async function save(): Promise<void> {
	if (saving.value || !isValid.value || !changed.value) return;

	saving.value = true;
	error.value = null;

	try {
		await ledger.updateSettings({
			...(currencyChanged.value ? { displayCurrency: normalised.value } : {}),
			...(budgetModeChanged.value ? { budgetMode: budgetModeDraft.value } : {}),
			...(defaultAccountChanged.value ? { defaultAccountId: defaultAccountDraft.value || null } : {}),
		});
		// The summary carries formatted figures nowhere, but refreshing keeps the
		// dashboard consistent with anything a setting touched.
		await budget.refresh();
		showSnackbar('Settings saved');
	} catch (caught) {
		error.value = caught instanceof ApiError ? caught.message : 'Could not save the setting.';
	} finally {
		saving.value = false;
	}
}
</script>

<template>
	<form class="max-w-xl space-y-5" @submit.prevent="save">
		<section class="card space-y-4 p-5">
			<div class="field">
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

				<p class="mt-1.5 text-xs text-on-surface-variant">
					Used for net worth, the monthly summary and budgets. Each account keeps its own currency for what it holds.
				</p>
			</div>

			<div class="rounded-sm bg-surface-container px-3 py-2">
				<p class="text-xs text-on-surface-variant">Preview</p>
				<p class="tabular mt-0.5 text-lg font-medium text-on-surface">{{ preview }}</p>
			</div>

			<p v-if="draft.trim() && !isValid" class="text-sm text-warning" role="alert">Use a 3-letter currency code, such as PHP.</p>
		</section>

		<section class="card space-y-4 p-5">
			<div>
				<label class="label">Budget mode</label>
				<ConnectedButtonGroup
					v-model="budgetModeDraft"
					label="Budget mode"
					:options="[
						{ value: 'fixed', label: 'Fixed' },
						{ value: 'monthly', label: 'Monthly' },
					]"
				/>

				<p class="mt-1.5 text-xs text-on-surface-variant">
					<template v-if="budgetModeDraft === 'fixed'"> A category's planned amount applies to every month, until changed again. </template>
					<template v-else> Each month keeps its own planned amount, set separately. </template>
				</p>
			</div>

			<div class="field">
				<label class="label" for="default-account">Default account</label>
				<select id="default-account" v-model="defaultAccountDraft" class="input">
					<option value="">First active account</option>
					<option v-for="account in ledger.activeAccounts" :key="account.id" :value="account.id">{{ account.name }}</option>
				</select>
				<p class="mt-1.5 text-xs text-on-surface-variant">Which account a new transaction opens on.</p>
			</div>
		</section>

		<p v-if="error" class="banner-error" role="alert">
			{{ error }}
		</p>

		<FabButton :label="saving ? 'Saving…' : 'Save'" :icon="SAVE" :disabled="saving || !isValid || !changed" @click="save" />
	</form>
</template>
