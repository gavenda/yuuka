<script setup lang="ts">
import ActionIcon from '@/components/ActionIcon.vue';
import FieldSupport from '@/components/FieldSupport.vue';
import FormDialog from '@/components/FormDialog.vue';
import SwipeReveal from '@/components/SwipeReveal.vue';
import { api, ApiError } from '@/lib/api';
import { showSnackbar } from '@/lib/snackbar';
import { nameProblem, supportId, useFormValidation } from '@/lib/validation';
import { useLedgerStore } from '@/stores/ledger';
import type { AccountType } from '@/types';
import { computed, ref } from 'vue';

/**
 * The account types, as a dialog of their own (`AccountTypeManagerContent`). Nothing here waits for a Save:
 * a type is added, renamed, archived or deleted as it is asked for, so the dialog has only its Close.
 */
defineProps<{ open: boolean }>();
const ledger = useLedgerStore();
const emit = defineEmits<{ changed: []; close: [] }>();

const newName = ref('');
const editingId = ref<string | null>(null);
const draftName = ref('');
/** A failure that belongs to no one field — the change itself went wrong. */
const error = ref<string | null>(null);
const busy = ref(false);
const showArchived = ref(false);

/** Types are unique by name, so a name is checked against the others (a rename may keep its own). */
const isTaken = (name: string, exceptId?: string): boolean =>
	ledger.accountTypes.some((type) => type.id !== exceptId && type.name === name);
const TAKEN = 'You already have an account type with that name.';

const addValidation = useFormValidation({ 'new-account-type': () => nameProblem(newName.value, (name) => isTaken(name), TAKEN) });
const renameValidation = useFormValidation({
	'rename-account-type': () => nameProblem(draftName.value, (name) => isTaken(name, editingId.value ?? undefined), TAKEN),
});

const visible = computed(() => ledger.accountTypes.filter((type) => showArchived.value || !type.archived));
const archivedCount = computed(() => ledger.accountTypes.filter((type) => type.archived).length);

/** Every mutation refreshes the store, since accounts display the type name. */
async function run(action: () => Promise<unknown>, done: string): Promise<boolean> {
	error.value = null;
	busy.value = true;

	try {
		await action();
		showSnackbar(done);
		await ledger.refreshAccounts();
		emit('changed');
		return true;
	} catch (caught) {
		error.value = caught instanceof ApiError ? caught.message : 'Something went wrong.';
		return false;
	} finally {
		busy.value = false;
	}
}

async function add(): Promise<void> {
	if (!addValidation.isValid.value) return;
	const name = newName.value.trim();

	// Put new types after the existing ones rather than at the top.
	const sortOrder = ledger.accountTypes.reduce((highest, type) => Math.max(highest, type.sortOrder), -1) + 1;
	if (await run(() => api.createAccountType({ name, sortOrder }), 'Type added')) {
		newName.value = '';
		addValidation.reset();
	}
}

function startRename(type: AccountType): void {
	editingId.value = type.id;
	draftName.value = type.name;
	error.value = null;
	renameValidation.reset();
}

async function commitRename(id: string): Promise<void> {
	if (!renameValidation.isValid.value) return;
	const name = draftName.value.trim();
	if (await run(() => api.updateAccountType(id, { name }), 'Type renamed')) editingId.value = null;
}

const toggleArchived = (type: AccountType) =>
	run(() => api.updateAccountType(type.id, { archived: !type.archived }), type.archived ? 'Type restored' : 'Type archived');

const remove = (type: AccountType) => run(() => api.deleteAccountType(type.id), 'Type deleted');
</script>

<template>
	<FormDialog :open="open" title="Account types" :save="false" @close="emit('close')">
		<p class="type-body-small text-on-surface">These are your own labels. Rename one and every account using it follows.</p>

		<!-- Top-aligned: a field in error grows a line beneath itself, and the button stays beside the field, not the line. -->
		<div class="flex items-start gap-2" @input="addValidation.onInput" @keydown.enter.prevent="add">
			<div class="min-w-0 flex-1">
				<input
					id="new-account-type"
					v-model="newName"
					class="input input-sm"
					placeholder="Add a type"
					aria-label="New account type"
					:aria-invalid="addValidation.error('new-account-type') ? true : undefined"
					:aria-describedby="addValidation.error('new-account-type') ? supportId('new-account-type') : undefined"
					@blur="newName && addValidation.touch('new-account-type')"
				/>
				<FieldSupport id="new-account-type" :error="addValidation.error('new-account-type')" />
			</div>
			<button type="button" class="btn-primary" :disabled="busy || !addValidation.isValid.value" @click="add">Add</button>
		</div>

		<template v-for="type in visible" :key="type.id">
			<div
				v-if="editingId === type.id"
				class="flex items-start"
				@input="renameValidation.onInput"
				@keydown.enter.prevent="commitRename(type.id)"
			>
				<div class="min-w-0 flex-1">
					<input
						id="rename-account-type"
						v-model="draftName"
						class="input input-sm"
						aria-label="Type name"
						:disabled="busy"
						:aria-invalid="renameValidation.error('rename-account-type') ? true : undefined"
						:aria-describedby="renameValidation.error('rename-account-type') ? supportId('rename-account-type') : undefined"
						@keydown.esc.stop="editingId = null"
						@blur="renameValidation.touch('rename-account-type')"
					/>
					<FieldSupport id="rename-account-type" :error="renameValidation.error('rename-account-type')" />
				</div>
				<button type="button" class="btn-text" :disabled="busy || !renameValidation.isValid.value" @click="commitRename(type.id)">
					Save
				</button>
				<button type="button" class="btn-text" :disabled="busy" @click="editingId = null">Cancel</button>
			</div>

			<SwipeReveal v-else>
				<template #actions>
					<ActionIcon icon="edit" :label="`Rename ${type.name}`" :disabled="busy" @click="startRename(type)" />
					<ActionIcon
						:icon="type.archived ? 'restore' : 'archive'"
						:label="`${type.archived ? 'Restore' : 'Archive'} ${type.name}`"
						:disabled="busy"
						@click="toggleArchived(type)"
					/>
					<!-- A type still in use cannot be deleted: the API reports how many accounts hold it. -->
					<ActionIcon icon="delete" :label="`Delete ${type.name}`" danger :disabled="busy || type.accountCount > 0" @click="remove(type)" />
				</template>

				<div class="type-body-large flex items-center bg-surface-container-low py-2 text-on-surface">
					<span class="min-w-0 flex-1 truncate">{{ type.archived ? `${type.name} (Archived)` : type.name }}</span>
					<span class="type-body-small">{{ type.accountCount }}</span>
				</div>
			</SwipeReveal>
		</template>

		<p v-if="error" class="type-body-small text-error" role="alert">{{ error }}</p>

		<div v-if="archivedCount > 0">
			<button type="button" class="btn-text" @click="showArchived = !showArchived">
				{{ showArchived ? 'Hide' : 'Show' }} {{ archivedCount }} archived
			</button>
		</div>
	</FormDialog>
</template>
