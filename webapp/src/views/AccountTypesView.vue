<script setup lang="ts">
import ActionIcon from '@/components/ActionIcon.vue';
import AlertDialog from '@/components/AlertDialog.vue';
import FabButton from '@/components/FabButton.vue';
import SwipeReveal from '@/components/SwipeReveal.vue';
import TextField from '@/components/TextField.vue';
import { api, ApiError } from '@/lib/api';
import { showSnackbar } from '@/lib/snackbar';
import { nameProblem, useFormValidation } from '@/lib/validation';
import { useBudgetStore } from '@/stores/budget';
import { useLedgerStore } from '@/stores/ledger';
import type { AccountType } from '@/types';
import { computed, onMounted, ref } from 'vue';
import { t } from '@/i18n';

/**
 * The account types, on a screen of their own reached from Settings (`AccountTypesScreen`): a list like any
 * other in the app. Pressing a type renames it, swiping it shows archive and delete, and the screen's
 * leading action asks for a new one by name in a basic dialog — the same one a rename opens.
 */
const ledger = useLedgerStore();
const budget = useBudgetStore();

/** Whether the name dialog is up, and the type it renames — none while it is adding one. */
const nameOpen = ref(false);
const renaming = ref<AccountType | null>(null);
const name = ref('');
/** A failure that belongs to no one field — the change itself went wrong — said where it was asked for. */
const nameError = ref<string | null>(null);
const listError = ref<string | null>(null);
const busy = ref(false);
const showArchived = ref(false);

/** Types are unique by name, so a name is checked against the others (a rename may keep its own). */
const validation = useFormValidation({
	'account-type-name': () =>
		nameProblem(
			name.value,
			(candidate) => ledger.accountTypes.some((type) => type.id !== renaming.value?.id && type.name === candidate),
			t('accounts.types.taken'),
		),
});

const visible = computed(() => ledger.accountTypes.filter((type) => showArchived.value || !type.archived));
const archivedCount = computed(() => ledger.accountTypes.filter((type) => type.archived).length);

const countLabel = (type: AccountType) =>
	type.accountCount ? t('accounts.types.accountCount', type.accountCount) : t('accounts.types.unused');

/** Every mutation refreshes the store, since accounts display the type name. */
async function run(action: () => Promise<unknown>, done: string, failed: typeof nameError): Promise<boolean> {
	nameError.value = null;
	listError.value = null;
	busy.value = true;

	try {
		await action();
		showSnackbar(done);
		await Promise.all([ledger.refreshAccounts(), budget.refresh()]);
		return true;
	} catch (caught) {
		failed.value = caught instanceof ApiError ? caught.message : t('common.errorGeneric');
		return false;
	} finally {
		busy.value = false;
	}
}

function openName(type: AccountType | null): void {
	renaming.value = type;
	name.value = type?.name ?? '';
	nameError.value = null;
	validation.reset();
	nameOpen.value = true;
}

async function saveName(): Promise<void> {
	if (busy.value || !validation.isValid.value) return;
	const trimmed = name.value.trim();
	const type = renaming.value;

	// Put new types after the existing ones rather than at the top.
	const sortOrder = ledger.accountTypes.reduce((highest, existing) => Math.max(highest, existing.sortOrder), -1) + 1;
	const saved = type
		? await run(() => api.updateAccountType(type.id, { name: trimmed }), t('accounts.types.renamed'), nameError)
		: await run(() => api.createAccountType({ name: trimmed, sortOrder }), t('accounts.types.added'), nameError);
	if (saved) nameOpen.value = false;
}

const toggleArchived = (type: AccountType) =>
	run(
		() => api.updateAccountType(type.id, { archived: !type.archived }),
		type.archived ? t('accounts.types.restored') : t('accounts.types.archived'),
		listError,
	);

const remove = (type: AccountType) => run(() => api.deleteAccountType(type.id), t('accounts.types.deleted'), listError);

onMounted(() => ledger.load());
</script>

<template>
	<div class="flex flex-col gap-4 px-4 pt-4 pb-24">
		<p class="type-body-medium px-2 text-on-surface-variant">{{ t('accounts.types.description') }}</p>

		<!-- One row per type, its accounts counted at the end, drawn as one connected block. -->
		<ul v-if="visible.length" class="group-rows">
			<li v-for="type in visible" :key="type.id">
				<SwipeReveal>
					<template #actions>
						<ActionIcon
							:icon="type.archived ? 'restore' : 'archive'"
							:label="t(type.archived ? 'common.restore' : 'common.archive', { name: type.name })"
							:disabled="busy"
							@click="toggleArchived(type)"
						/>
						<!-- A type still in use cannot be deleted: the API reports how many accounts hold it. -->
						<ActionIcon
							icon="delete"
							:label="type.accountCount ? t('accounts.types.inUse', { name: type.name }) : t('common.delete', { name: type.name })"
							danger
							:disabled="busy || type.accountCount > 0"
							@click="remove(type)"
						/>
					</template>

					<button
						type="button"
						class="group-row state-layer focus-ring cursor-pointer"
						:aria-label="t('common.rename', { name: type.name })"
						@click="openName(type)"
					>
						<span class="flex min-h-14 items-center gap-3 px-4">
							<span class="type-body-large min-w-0 flex-1 truncate">{{
								type.archived ? t('common.archivedName', { name: type.name }) : type.name
							}}</span>
							<span class="type-body-small shrink-0 text-on-surface-variant">{{ countLabel(type) }}</span>
						</span>
					</button>
				</SwipeReveal>
			</li>
		</ul>

		<p v-if="listError" class="type-body-small text-error" role="alert">{{ listError }}</p>

		<div v-if="archivedCount > 0">
			<button type="button" class="btn-text" @click="showArchived = !showArchived">
				{{ t(showArchived ? 'common.hideArchived' : 'common.showArchived', { count: archivedCount }) }}
			</button>
		</div>

		<!-- A type is only its name, so adding one and renaming one are the same single field in a basic dialog. -->
		<AlertDialog
			:open="nameOpen"
			:title="renaming ? t('accounts.types.renameTitle') : t('accounts.types.newTitle')"
			role="dialog"
			@close="busy || (nameOpen = false)"
		>
			<form id="account-type-form" class="flex flex-col gap-3 sm:w-80" novalidate @submit.prevent="saveName" @input="validation.onInput">
				<TextField
					id="account-type-name"
					v-model="name"
					:label="t('common.name')"
					autofocus
					:disabled="busy"
					:error="validation.error('account-type-name')"
					@blur="validation.touch('account-type-name')"
				/>
				<p v-if="nameError" class="type-body-small text-error" role="alert">{{ nameError }}</p>
			</form>

			<template #actions>
				<button type="button" class="btn-text" :disabled="busy" @click="nameOpen = false">{{ t('common.cancel') }}</button>
				<button type="submit" form="account-type-form" class="btn-text" :disabled="busy || !validation.isValid.value">
					{{ renaming ? t('common.save') : t('common.add') }}
				</button>
			</template>
		</AlertDialog>

		<FabButton :label="t('accounts.types.newType')" @click="openName(null)" />
	</div>
</template>
