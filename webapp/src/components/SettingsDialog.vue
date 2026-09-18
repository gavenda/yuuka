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

const tab = ref<'general' | 'save-the-change'>('general');

const draft = ref(ledger.displayCurrency);
const budgetModeDraft = ref(ledger.budgetMode);
const defaultAccountDraft = ref(ledger.defaultAccountId ?? '');
const error = ref<string | null>(null);
const saving = ref(false);

const roundUpEnabledDraft = ref(ledger.roundUpRule?.enabled ?? false);
const roundToDraft = ref<1000 | 10000>(ledger.roundUpRule?.roundTo ?? 1000);
const roundUpDestinationDraft = ref(ledger.roundUpRule?.destinationAccountId ?? '');
const roundUpCategoryDraft = ref(ledger.roundUpRule?.categoryId ?? '');

/** A round-up posts as an ordinary transfer, so it takes the same Cashflow tree a plain transfer does. */
const roundUpCategoryGroups = computed(() => ledger.groupForPicker(ledger.transferCategories));

const normalised = computed(() => draft.value.trim().toUpperCase());
const isValid = computed(() => /^[A-Za-z]{3}$/.test(draft.value.trim()));
const currencyChanged = computed(() => normalised.value !== ledger.displayCurrency);
const budgetModeChanged = computed(() => budgetModeDraft.value !== ledger.budgetMode);
const defaultAccountChanged = computed(() => (defaultAccountDraft.value || null) !== ledger.defaultAccountId);
const generalChanged = computed(() => currencyChanged.value || budgetModeChanged.value || defaultAccountChanged.value);

const roundUpEnabledChanged = computed(() => roundUpEnabledDraft.value !== (ledger.roundUpRule?.enabled ?? false));
const roundToChanged = computed(() => roundToDraft.value !== (ledger.roundUpRule?.roundTo ?? 1000));
const roundUpDestinationChanged = computed(
	() => (roundUpDestinationDraft.value || null) !== (ledger.roundUpRule?.destinationAccountId ?? null),
);
const roundUpCategoryChanged = computed(() => (roundUpCategoryDraft.value || null) !== (ledger.roundUpRule?.categoryId ?? null));
const roundUpChanged = computed(
	() => roundUpEnabledChanged.value || roundToChanged.value || roundUpDestinationChanged.value || roundUpCategoryChanged.value,
);
// A destination is required once the rule is on — nothing sensible to save without one.
const roundUpValid = computed(() => !roundUpEnabledDraft.value || Boolean(roundUpDestinationDraft.value));

const changed = computed(() => generalChanged.value || roundUpChanged.value);

// Reopening should show what is actually saved, not a half-typed attempt.
watch(
	() => props.open,
	(open) => {
		if (!open) return;
		tab.value = 'general';
		draft.value = ledger.displayCurrency;
		budgetModeDraft.value = ledger.budgetMode;
		defaultAccountDraft.value = ledger.defaultAccountId ?? '';
		roundUpEnabledDraft.value = ledger.roundUpRule?.enabled ?? false;
		roundToDraft.value = ledger.roundUpRule?.roundTo ?? 1000;
		roundUpDestinationDraft.value = ledger.roundUpRule?.destinationAccountId ?? '';
		roundUpCategoryDraft.value = ledger.roundUpRule?.categoryId ?? '';
		error.value = null;
	},
);

const preview = computed(() => (isValid.value ? formatMoney(123_456, normalised.value) : '—'));

async function save(): Promise<void> {
	if (!isValid.value || !roundUpValid.value || !changed.value) return;

	saving.value = true;
	error.value = null;

	try {
		await Promise.all([
			generalChanged.value
				? ledger.updateSettings({
						...(currencyChanged.value ? { displayCurrency: normalised.value } : {}),
						...(budgetModeChanged.value ? { budgetMode: budgetModeDraft.value } : {}),
						...(defaultAccountChanged.value ? { defaultAccountId: defaultAccountDraft.value || null } : {}),
					})
				: Promise.resolve(),
			roundUpChanged.value
				? ledger.updateRoundUpRule({
						...(roundUpEnabledChanged.value ? { enabled: roundUpEnabledDraft.value } : {}),
						...(roundToChanged.value ? { roundTo: roundToDraft.value } : {}),
						...(roundUpDestinationChanged.value ? { destinationAccountId: roundUpDestinationDraft.value || null } : {}),
						...(roundUpCategoryChanged.value ? { categoryId: roundUpCategoryDraft.value || null } : {}),
					})
				: Promise.resolve(),
		]);
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
		<div class="flex overflow-hidden rounded-md border border-slate-300 dark:border-slate-700">
			<button
				type="button"
				class="flex-1 px-3 py-1.5 text-sm font-medium"
				:class="tab === 'general' ? 'bg-blue-600 text-white' : 'text-slate-500 dark:text-slate-400'"
				@click="tab = 'general'"
			>
				General
			</button>
			<button
				type="button"
				class="flex-1 px-3 py-1.5 text-sm font-medium"
				:class="tab === 'save-the-change' ? 'bg-blue-600 text-white' : 'text-slate-500 dark:text-slate-400'"
				@click="tab = 'save-the-change'"
			>
				Save the Change
			</button>
		</div>

		<div v-show="tab === 'general'" class="space-y-4">
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

			<div>
				<label class="label" for="default-account">Default account</label>
				<select id="default-account" v-model="defaultAccountDraft" class="input">
					<option value="">First active account</option>
					<option v-for="account in ledger.activeAccounts" :key="account.id" :value="account.id">{{ account.name }}</option>
				</select>
				<p class="mt-1.5 text-xs text-slate-500 dark:text-slate-400">Which account a new transaction opens on.</p>
			</div>
		</div>

		<div v-show="tab === 'save-the-change'" class="space-y-4">
			<label class="flex items-center gap-2 text-sm text-slate-700 dark:text-slate-300">
				<input
					v-model="roundUpEnabledDraft"
					type="checkbox"
					class="size-4 rounded border-slate-300 accent-blue-600 dark:border-slate-700"
				/>
				Round up purchases
			</label>
			<p class="-mt-2 text-xs text-slate-500 dark:text-slate-400">
				Rounds up expenses on accounts you've opted into below, and deposits the spare change into the account you choose here.
			</p>

			<div>
				<label class="label">Round up to the nearest</label>
				<div class="flex overflow-hidden rounded-md border border-slate-300 dark:border-slate-700">
					<button
						type="button"
						class="flex-1 px-3 py-1.5 text-sm font-medium"
						:class="roundToDraft === 1000 ? 'bg-blue-600 text-white' : 'text-slate-500 dark:text-slate-400'"
						@click="roundToDraft = 1000"
					>
						₱10
					</button>
					<button
						type="button"
						class="flex-1 px-3 py-1.5 text-sm font-medium"
						:class="roundToDraft === 10000 ? 'bg-blue-600 text-white' : 'text-slate-500 dark:text-slate-400'"
						@click="roundToDraft = 10000"
					>
						₱100
					</button>
				</div>
			</div>

			<div>
				<label class="label" for="round-up-destination">Destination account</label>
				<select id="round-up-destination" v-model="roundUpDestinationDraft" class="input">
					<option value="" disabled>Choose an account</option>
					<option v-for="account in ledger.activeAccounts" :key="account.id" :value="account.id">{{ account.name }}</option>
				</select>
				<p class="mt-1.5 text-xs text-slate-500 dark:text-slate-400">Where the rounded-up spare change is deposited.</p>
			</div>

			<div>
				<label class="label" for="round-up-category">Cashflow category</label>
				<select id="round-up-category" v-model="roundUpCategoryDraft" class="input">
					<option value="">Uncategorized</option>
					<template v-for="group in roundUpCategoryGroups" :key="group.parent.id">
						<option :value="group.parent.id">{{ group.parent.name }}</option>
						<option v-for="child in group.children" :key="child.id" :value="child.id">&nbsp;&nbsp;&nbsp;{{ child.name }}</option>
					</template>
				</select>
				<p class="mt-1.5 text-xs text-slate-500 dark:text-slate-400">
					Optional. Lets you budget the round-ups, the same as a plain transfer.
				</p>
			</div>

			<p v-if="roundUpEnabledDraft && !roundUpDestinationDraft" class="text-sm text-amber-700 dark:text-amber-400" role="alert">
				Choose a destination account to enable Save the Change.
			</p>

			<p class="text-xs text-slate-500 dark:text-slate-400">
				Which accounts round up their own purchases is set per account, from that account's edit form under Accounts.
			</p>
		</div>

		<p v-if="error" class="rounded-lg bg-rose-50 px-3 py-2 text-sm text-rose-700 dark:bg-rose-500/10 dark:text-rose-400" role="alert">
			{{ error }}
		</p>

		<div class="flex justify-end gap-2 pt-2">
			<button type="button" class="btn-secondary" @click="emit('close')">Cancel</button>
			<button type="submit" class="btn-primary" :disabled="saving || !isValid || !roundUpValid || !changed">
				{{ saving ? 'Saving…' : 'Save' }}
			</button>
		</div>
	</form>
</template>
