<script setup lang="ts">
import ConnectedButtonGroup from '@/components/ConnectedButtonGroup.vue';
import FabButton from '@/components/FabButton.vue';
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
	<form class="max-w-xl space-y-5" @submit.prevent="save">
		<section class="card space-y-4 p-5">
			<div>
				<label class="flex items-center gap-2 text-sm text-on-surface">
					<input v-model="roundUpEnabledDraft" type="checkbox" class="size-4 rounded border-outline accent-primary" />
					Round up purchases
				</label>
				<p class="mt-1.5 text-xs text-on-surface-variant">
					Rounds up expenses on accounts you've opted into, and deposits the spare change into the account you choose here.
				</p>
			</div>

			<div>
				<label class="label">Round up to the nearest</label>
				<ConnectedButtonGroup
					v-model="roundToDraft"
					label="Round up to the nearest"
					:options="[
						{ value: 1000, label: '₱10' },
						{ value: 10000, label: '₱100' },
					]"
				/>
			</div>

			<div class="field">
				<label class="label" for="round-up-destination">Destination account</label>
				<select id="round-up-destination" v-model="roundUpDestinationDraft" class="input">
					<option value="" disabled>Choose an account</option>
					<option v-for="account in ledger.activeAccounts" :key="account.id" :value="account.id">{{ account.name }}</option>
				</select>
				<p class="mt-1.5 text-xs text-on-surface-variant">Where the rounded-up spare change is deposited.</p>
			</div>

			<div class="field">
				<label class="label" for="round-up-category">Cashflow category</label>
				<select id="round-up-category" v-model="roundUpCategoryDraft" class="input">
					<option value="">Uncategorized</option>
					<template v-for="group in roundUpCategoryGroups" :key="group.parent.id">
						<option :value="group.parent.id">{{ group.parent.name }}</option>
						<option v-for="child in group.children" :key="child.id" :value="child.id">&nbsp;&nbsp;&nbsp;{{ child.name }}</option>
					</template>
				</select>
				<p class="mt-1.5 text-xs text-on-surface-variant">Optional. Lets you budget the round-ups, the same as a plain transfer.</p>
			</div>

			<p v-if="roundUpEnabledDraft && !roundUpDestinationDraft" class="text-sm text-warning" role="alert">
				Choose a destination account to enable Save the Change.
			</p>
		</section>

		<p class="text-xs text-on-surface-variant">
			Which accounts round up their own purchases is set per account, from that account's edit form under Accounts.
		</p>

		<p v-if="error" class="banner-error" role="alert">
			{{ error }}
		</p>

		<FabButton :label="saving ? 'Saving…' : 'Save'" :icon="SAVE" :disabled="saving || !isValid || !changed" @click="save" />
	</form>
</template>
