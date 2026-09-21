<script setup lang="ts">
import ActionIcon from '@/components/ActionIcon.vue';
import FieldSupport from '@/components/FieldSupport.vue';
import { api, ApiError } from '@/lib/api';
import { nameProblem, supportId, useFormValidation } from '@/lib/validation';
import { useLedgerStore } from '@/stores/ledger';
import type { AccountType } from '@/types';
import { computed, ref } from 'vue';

const ledger = useLedgerStore();
const emit = defineEmits<{ changed: [] }>();

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
async function run(action: () => Promise<unknown>): Promise<boolean> {
	error.value = null;
	busy.value = true;

	try {
		await action();
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
	if (await run(() => api.createAccountType({ name, sortOrder }))) {
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
	if (await run(() => api.updateAccountType(id, { name }))) editingId.value = null;
}

const toggleArchived = (type: AccountType) => run(() => api.updateAccountType(type.id, { archived: !type.archived }));

async function remove(type: AccountType): Promise<void> {
	if (!confirm(`Delete “${type.name}”?`)) return;
	await run(() => api.deleteAccountType(type.id));
}
</script>

<template>
	<div class="space-y-4">
		<p class="text-sm text-on-surface-variant">These are your own labels. Rename one and every account using it follows.</p>

		<form class="flex items-start gap-2 pt-2" novalidate @submit.prevent="add" @input="addValidation.onInput">
			<div class="field min-w-0 flex-1">
				<label class="label" for="new-account-type">New type</label>
				<input
					id="new-account-type"
					v-model="newName"
					class="input"
					placeholder="e.g. Crypto Wallet"
					:aria-invalid="addValidation.error('new-account-type') ? true : undefined"
					:aria-describedby="addValidation.error('new-account-type') ? supportId('new-account-type') : undefined"
					@blur="addValidation.touch('new-account-type')"
				/>
				<FieldSupport id="new-account-type" :error="addValidation.error('new-account-type')" />
			</div>
			<button type="submit" class="btn-primary mt-2 shrink-0" :disabled="busy || !addValidation.isValid.value">Add</button>
		</form>

		<p v-if="error" class="banner-error" role="alert">
			{{ error }}
		</p>

		<ul class="divide-y divide-outline-variant">
			<li v-for="type in visible" :key="type.id" class="group flex items-center gap-2 py-2.5">
				<form
					v-if="editingId === type.id"
					class="min-w-0 flex-1"
					novalidate
					@submit.prevent="commitRename(type.id)"
					@input="renameValidation.onInput"
				>
					<div class="flex gap-2">
						<input
							id="rename-account-type"
							v-model="draftName"
							class="input input-sm"
							aria-label="Type name"
							autofocus
							:aria-invalid="renameValidation.error('rename-account-type') ? true : undefined"
							:aria-describedby="renameValidation.error('rename-account-type') ? supportId('rename-account-type') : undefined"
							@blur="renameValidation.touch('rename-account-type')"
							@keydown.esc="editingId = null"
						/>
						<button type="submit" class="btn-primary btn-sm" :disabled="busy || !renameValidation.isValid.value">Save</button>
						<button type="button" class="btn-text btn-sm" @click="editingId = null">Cancel</button>
					</div>
					<FieldSupport id="rename-account-type" :error="renameValidation.error('rename-account-type')" class="!px-3" />
				</form>

				<template v-else>
					<span class="min-w-0 flex-1 truncate text-sm font-medium text-on-surface">
						{{ type.name }}
						<span v-if="type.archived" class="ml-1 text-xs font-normal text-outline">Archived</span>
					</span>

					<span class="shrink-0 text-xs text-on-surface-variant">
						{{ type.accountCount }} {{ type.accountCount === 1 ? 'account' : 'accounts' }}
					</span>

					<div class="row-actions row-actions-visible">
						<ActionIcon icon="edit" :label="`Rename ${type.name}`" @click="startRename(type)" />
						<ActionIcon
							:icon="type.archived ? 'restore' : 'archive'"
							:label="`${type.archived ? 'Restore' : 'Archive'} ${type.name}`"
							@click="toggleArchived(type)"
						/>
						<!-- A type in use cannot be deleted; archiving is the way to retire
						     it. The button stays in place rather than disappearing: dropping
						     it would change the row's width and knock the account counts out
						     of line, and a disabled control says why it cannot be used. -->
						<ActionIcon
							icon="delete"
							:disabled="type.accountCount > 0"
							:label="
								type.accountCount > 0
									? `${type.name} is used by ${type.accountCount} account${type.accountCount === 1 ? '' : 's'} — archive it instead`
									: `Delete ${type.name}`
							"
							danger
							@click="remove(type)"
						/>
					</div>
				</template>
			</li>
		</ul>

		<button v-if="archivedCount" type="button" class="btn-text" @click="showArchived = !showArchived">
			{{ showArchived ? 'Hide' : 'Show' }} {{ archivedCount }} archived
		</button>
	</div>
</template>
