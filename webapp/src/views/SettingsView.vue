<script setup lang="ts">
import PreferenceSelect from '@/components/PreferenceSelect.vue';
import { namedOptions } from '@/lib/selectOptions';
import ConnectedButtonGroup from '@/components/ConnectedButtonGroup.vue';
import FabButton from '@/components/FabButton.vue';
import SettingRow from '@/components/SettingRow.vue';
import FieldSupport from '@/components/FieldSupport.vue';
import { ApiError } from '@/lib/api';
import { currencyProblem, supportId, useFormValidation } from '@/lib/validation';
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
const defaultAccountChoices = computed(() => namedOptions(ledger.activeAccounts, { value: '', label: 'First active account' }));
/** A failure that belongs to no one field — the save itself went wrong. */
const error = ref<string | null>(null);
const saving = ref(false);

const validation = useFormValidation({ 'display-currency': () => currencyProblem(draft.value) });
const { error: fieldError, touch } = validation;

const normalised = computed(() => draft.value.trim().toUpperCase());
const isValid = computed(() => validation.isValid.value);
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
	if (saving.value || !changed.value) return;
	if (!validation.isValid.value) return;

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
	<form class="space-y-5" novalidate @submit.prevent="save" @input="validation.onInput">
		<section class="card">
			<h2 class="type-title-small px-5 pt-4 text-primary">Currency</h2>
			<div class="divide-y divide-outline-variant px-5">
				<SettingRow
					title="Display currency"
					description="Used for net worth, the monthly summary and budgets. Each account keeps its own currency for what it holds."
					for="display-currency"
				>
					<input
						id="display-currency"
						v-model="draft"
						class="input input-sm uppercase"
						maxlength="3"
						list="currency-suggestions"
						autocomplete="off"
						required
						:aria-invalid="fieldError('display-currency') ? true : undefined"
						:aria-describedby="fieldError('display-currency') ? supportId('display-currency') : undefined"
						@blur="touch('display-currency')"
					/>
					<datalist id="currency-suggestions">
						<option v-for="code in SUGGESTIONS" :key="code" :value="code" />
					</datalist>
					<FieldSupport id="display-currency" :error="fieldError('display-currency')" class="!px-3" />
				</SettingRow>

				<SettingRow title="Preview" description="How an amount will read in this currency.">
					<p class="tabular text-lg text-on-surface sm:text-right">{{ preview }}</p>
				</SettingRow>
			</div>
		</section>

		<section class="card">
			<h2 class="type-title-small px-5 pt-4 text-primary">Budgets</h2>
			<div class="px-5">
				<SettingRow
					title="Budget mode"
					:description="
						budgetModeDraft === 'fixed'
							? 'A category\'s planned amount applies to every month, until changed again.'
							: 'Each month keeps its own planned amount, set separately.'
					"
				>
					<ConnectedButtonGroup
						v-model="budgetModeDraft"
						label="Budget mode"
						:options="[
							{ value: 'fixed', label: 'Fixed' },
							{ value: 'monthly', label: 'Monthly' },
						]"
					/>
				</SettingRow>
			</div>
		</section>

		<section class="card">
			<h2 class="type-title-small px-5 pt-4 text-primary">Transactions</h2>
			<div class="px-5">
				<div class="py-1">
					<PreferenceSelect
						id="default-account"
						v-model="defaultAccountDraft"
						label="Default account"
						:options="defaultAccountChoices"
					/>
				</div>
			</div>
		</section>

		<p v-if="error" class="banner-error" role="alert">
			{{ error }}
		</p>

		<FabButton :label="saving ? 'Saving…' : 'Save'" :icon="SAVE" :disabled="saving || !changed || !isValid" @click="save" />
	</form>
</template>
