<script setup lang="ts">
import PreferenceSelect from '@/components/PreferenceSelect.vue';
import { categoryOptions, namedOptions } from '@/lib/selectOptions';
import ConnectedButtonGroup from '@/components/ConnectedButtonGroup.vue';
import FabButton from '@/components/FabButton.vue';
import SettingRow from '@/components/SettingRow.vue';
import ToggleSwitch from '@/components/ToggleSwitch.vue';
import FieldSupport from '@/components/FieldSupport.vue';
import { ApiError } from '@/lib/api';
import { supportId, useFormValidation } from '@/lib/validation';
import { SAVE } from '@/lib/icons';
import { showSnackbar } from '@/lib/snackbar';
import { useBudgetStore } from '@/stores/budget';
import { useLedgerStore } from '@/stores/ledger';
import { computed, onMounted, ref, watch } from 'vue';

const ledger = useLedgerStore();
const budget = useBudgetStore();

const roundUpEnabledDraft = ref(ledger.roundUpRule?.enabled ?? false);
const roundToDraft = ref<1000 | 10000>(ledger.roundUpRule?.roundTo ?? 1000);
const roundUpDestinationDraft = ref(ledger.roundUpRule?.destinationAccountId ?? '');
const roundUpCategoryDraft = ref(ledger.roundUpRule?.categoryId ?? '');
/** A failure that belongs to no one field — the save itself went wrong. */
const error = ref<string | null>(null);
const saving = ref(false);

/** A round-up posts as an ordinary transfer, so it takes the same Cashflow tree a plain transfer does. */
const roundUpCategoryGroups = computed(() => ledger.groupForPicker(ledger.transferCategories));
const destinationChoices = computed(() => namedOptions(ledger.activeAccounts, { value: '', label: 'Choose an account', disabled: true }));
const categoryChoices = computed(() => categoryOptions(roundUpCategoryGroups.value, { value: '', label: 'Uncategorized' }));

const enabledChanged = computed(() => roundUpEnabledDraft.value !== (ledger.roundUpRule?.enabled ?? false));
const roundToChanged = computed(() => roundToDraft.value !== (ledger.roundUpRule?.roundTo ?? 1000));
const destinationChanged = computed(() => (roundUpDestinationDraft.value || null) !== (ledger.roundUpRule?.destinationAccountId ?? null));
const categoryChanged = computed(() => (roundUpCategoryDraft.value || null) !== (ledger.roundUpRule?.categoryId ?? null));
const changed = computed(() => enabledChanged.value || roundToChanged.value || destinationChanged.value || categoryChanged.value);
// A destination is required once the rule is on — nothing sensible to save without one.
const validation = useFormValidation({
	'round-up-destination': () =>
		!roundUpEnabledDraft.value || ledger.activeAccounts.some((account) => account.id === roundUpDestinationDraft.value)
			? null
			: 'Choose a destination account to enable Save the Change.',
});
const { error: fieldError, touch } = validation;
// Turning the rule on without a destination is the mistake, so it is said the moment the switch is thrown.
watch(roundUpEnabledDraft, (enabled) => enabled && touch('round-up-destination'));

// The screen shows what is actually saved, so it follows the rule: on arrival, once it has loaded, and after a save.
watch(
	() => ledger.roundUpRule,
	(rule) => {
		roundUpEnabledDraft.value = rule?.enabled ?? false;
		roundToDraft.value = rule?.roundTo ?? 1000;
		roundUpDestinationDraft.value = rule?.destinationAccountId ?? '';
		roundUpCategoryDraft.value = rule?.categoryId ?? '';
	},
	{ deep: true },
);

onMounted(() => ledger.load());

async function save(): Promise<void> {
	if (saving.value || !changed.value) return;
	if (!validation.isValid.value) return;

	saving.value = true;
	error.value = null;

	try {
		await ledger.updateRoundUpRule({
			...(enabledChanged.value ? { enabled: roundUpEnabledDraft.value } : {}),
			...(roundToChanged.value ? { roundTo: roundToDraft.value } : {}),
			...(destinationChanged.value ? { destinationAccountId: roundUpDestinationDraft.value || null } : {}),
			...(categoryChanged.value ? { categoryId: roundUpCategoryDraft.value || null } : {}),
		});
		await budget.refresh();
		showSnackbar('Save the Change settings saved');
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
			<h2 class="type-title-small px-5 pt-4 text-primary">Round-ups</h2>
			<div class="divide-y divide-outline-variant px-5">
				<SettingRow
					title="Round up purchases"
					description="Rounds up expenses on the accounts you've opted in, and deposits the spare change into the account you choose below."
					inline
				>
					<ToggleSwitch v-model="roundUpEnabledDraft" label="Round up purchases" />
				</SettingRow>

				<SettingRow title="Round up to the nearest" description="The spare change is the gap to the next multiple of this.">
					<ConnectedButtonGroup
						v-model="roundToDraft"
						label="Round up to the nearest"
						:options="[
							{ value: 1000, label: '₱10' },
							{ value: 10000, label: '₱100' },
						]"
					/>
				</SettingRow>
			</div>
		</section>

		<section class="card">
			<h2 class="type-title-small px-5 pt-4 text-primary">Where it goes</h2>
			<div class="divide-y divide-outline-variant px-5">
				<div class="py-1">
					<PreferenceSelect
						id="round-up-destination"
						v-model="roundUpDestinationDraft"
						label="Destination account"
						:options="destinationChoices"
						:invalid="Boolean(fieldError('round-up-destination'))"
						:describedby="fieldError('round-up-destination') ? supportId('round-up-destination') : undefined"
						@blur="touch('round-up-destination')"
					/>
					<FieldSupport id="round-up-destination" :error="fieldError('round-up-destination')" class="!px-0" />
				</div>

				<div class="py-1">
					<PreferenceSelect
						id="round-up-category"
						v-model="roundUpCategoryDraft"
						label="Cashflow category"
						:options="categoryChoices"
					/>
				</div>
			</div>
		</section>

		<p class="text-sm text-on-surface-variant">
			Which accounts round up their own purchases is set per account, from that account's edit form under Accounts.
		</p>

		<p v-if="error" class="banner-error" role="alert">
			{{ error }}
		</p>

		<FabButton
			:label="saving ? 'Saving…' : 'Save'"
			:icon="SAVE"
			:disabled="saving || !changed || !validation.isValid.value"
			@click="save"
		/>
	</form>
</template>
