<script setup lang="ts">
import ActionIcon from '@/components/ActionIcon.vue';
import { api, ApiError } from '@/lib/api';
import { useLedgerStore } from '@/stores/ledger';
import type { AccountType } from '@/types';
import { computed, ref } from 'vue';

const ledger = useLedgerStore();
const emit = defineEmits<{ changed: [] }>();

const newName = ref('');
const editingId = ref<string | null>(null);
const draftName = ref('');
const error = ref<string | null>(null);
const busy = ref(false);
const showArchived = ref(false);

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
	const name = newName.value.trim();
	if (!name) return;

	// Put new types after the existing ones rather than at the top.
	const sortOrder = ledger.accountTypes.reduce((highest, type) => Math.max(highest, type.sortOrder), -1) + 1;
	if (await run(() => api.createAccountType({ name, sortOrder }))) newName.value = '';
}

function startRename(type: AccountType): void {
	editingId.value = type.id;
	draftName.value = type.name;
	error.value = null;
}

async function commitRename(id: string): Promise<void> {
	const name = draftName.value.trim();
	if (!name) return;
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
		<p class="text-sm text-slate-600 dark:text-slate-400">These are your own labels. Rename one and every account using it follows.</p>

		<form class="flex gap-2" @submit.prevent="add">
			<input v-model="newName" class="input" placeholder="Add a type, e.g. Crypto Wallet" aria-label="New account type name" />
			<button type="submit" class="btn-primary shrink-0" :disabled="busy || !newName.trim()">Add</button>
		</form>

		<p v-if="error" class="rounded-lg bg-rose-50 px-3 py-2 text-sm text-rose-700 dark:bg-rose-500/10 dark:text-rose-400" role="alert">
			{{ error }}
		</p>

		<ul class="divide-y divide-slate-100 dark:divide-slate-800/60">
			<li v-for="type in visible" :key="type.id" class="group flex items-center gap-2 py-2.5">
				<form v-if="editingId === type.id" class="flex flex-1 gap-2" @submit.prevent="commitRename(type.id)">
					<input v-model="draftName" class="input py-1" autofocus @keydown.esc="editingId = null" />
					<button type="submit" class="btn-primary px-2 py-1 text-xs" :disabled="busy">Save</button>
					<button type="button" class="btn-ghost px-2 py-1 text-xs" @click="editingId = null">Cancel</button>
				</form>

				<template v-else>
					<span class="min-w-0 flex-1 truncate text-sm font-medium text-slate-900 dark:text-slate-100">
						{{ type.name }}
						<span v-if="type.archived" class="ml-1 text-xs font-normal text-slate-400 dark:text-slate-500">Archived</span>
					</span>

					<span class="shrink-0 text-xs text-slate-500 dark:text-slate-400">
						{{ type.accountCount }} {{ type.accountCount === 1 ? 'account' : 'accounts' }}
					</span>

					<div class="row-actions">
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

		<button v-if="archivedCount" type="button" class="btn-ghost text-sm" @click="showArchived = !showArchived">
			{{ showArchived ? 'Hide' : 'Show' }} {{ archivedCount }} archived
		</button>
	</div>
</template>
