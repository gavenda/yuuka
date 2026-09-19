<script setup lang="ts">
import SelectField from '@/components/SelectField.vue';
import { categoryOptions, namedOptions } from '@/lib/selectOptions';
import ConnectedButtonGroup from '@/components/ConnectedButtonGroup.vue';
import FabButton from '@/components/FabButton.vue';
import SettingRow from '@/components/SettingRow.vue';
import ToggleSwitch from '@/components/ToggleSwitch.vue';
import { ApiError } from '@/lib/api';
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
const isValid = computed(() => !roundUpEnabledDraft.value || Boolean(roundUpDestinationDraft.value));

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
	if (saving.value || !isValid.value || !changed.value) return;

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
	<form class="space-y-5" @submit.prevent="save">
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
				<SettingRow title="Destination account" description="Where the rounded-up spare change is deposited." for="round-up-destination">
					<SelectField id="round-up-destination" v-model="roundUpDestinationDraft" :options="destinationChoices" dense />
					<p v-if="roundUpEnabledDraft && !roundUpDestinationDraft" class="mt-2 text-xs text-warning" role="alert">
						Choose a destination account to enable Save the Change.
					</p>
				</SettingRow>

				<SettingRow
					title="Cashflow category"
					description="Optional. Lets you budget the round-ups, the same as a plain transfer."
					for="round-up-category"
				>
					<SelectField id="round-up-category" v-model="roundUpCategoryDraft" :options="categoryChoices" dense />
				</SettingRow>
			</div>
		</section>

		<p class="text-sm text-on-surface-variant">
			Which accounts round up their own purchases is set per account, from that account's edit form under Accounts.
		</p>

		<p v-if="error" class="banner-error" role="alert">
			{{ error }}
		</p>

		<FabButton :label="saving ? 'Saving…' : 'Save'" :icon="SAVE" :disabled="saving || !isValid || !changed" @click="save" />
	</form>
</template>
