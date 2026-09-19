<script setup lang="ts">
import ActionIcon from '@/components/ActionIcon.vue';
import { api, ApiError } from '@/lib/api';
import { nextColor, PALETTE } from '@/lib/palette';
import { showSnackbar } from '@/lib/snackbar';
import { useLedgerStore } from '@/stores/ledger';
import type { Tag } from '@/types';
import { ref } from 'vue';

const ledger = useLedgerStore();

const newName = ref('');
const newColor = ref(nextColor(ledger.tags.length));
const editingId = ref<string | null>(null);
const draftName = ref('');
const draftColor = ref('');
const error = ref<string | null>(null);
const busy = ref(false);

/** Every change goes to the API and comes back through the store, so the list is only ever what the API said. */
async function run(action: () => Promise<unknown>): Promise<boolean> {
	error.value = null;
	busy.value = true;

	try {
		await action();
		await ledger.refreshTags();
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

	if (await run(() => api.createTag({ name, color: newColor.value }))) {
		newName.value = '';
		newColor.value = nextColor(ledger.tags.length);
	}
}

function startEdit(tag: Tag): void {
	editingId.value = tag.id;
	draftName.value = tag.name;
	draftColor.value = tag.color;
	error.value = null;
}

async function commitEdit(id: string): Promise<void> {
	const name = draftName.value.trim();
	if (!name) return;

	if (await run(() => api.updateTag(id, { name, color: draftColor.value }))) editingId.value = null;
}

async function remove(tag: Tag): Promise<void> {
	if (!confirm(`Delete “${tag.name}”? It comes off every transaction wearing it; the transactions stay.`)) return;

	if (await run(() => api.deleteTag(tag.id))) showSnackbar('Tag deleted');
}
</script>

<template>
	<div class="space-y-4">
		<form class="flex items-start gap-2 pt-2" @submit.prevent="add">
			<div class="field min-w-0 flex-1">
				<label class="label" for="new-tag">New tag</label>
				<input id="new-tag" v-model="newName" class="input" maxlength="80" placeholder="e.g. Reimbursable" />
			</div>
			<button type="submit" class="btn-primary mt-2 shrink-0" :disabled="busy || !newName.trim()">Add</button>
		</form>

		<!-- The swatches are the validated palette, the same eight categories take. -->
		<div class="flex flex-wrap items-center gap-2" role="group" aria-label="Colour of the new tag">
			<button
				v-for="slot in PALETTE"
				:key="slot.light"
				type="button"
				class="size-6 rounded-full ring-offset-2 transition-transform hover:scale-110"
				:class="newColor === slot.light ? 'ring-2 ring-on-surface' : ''"
				:style="{ backgroundColor: slot.light }"
				:aria-label="slot.name"
				:aria-pressed="newColor === slot.light"
				@click="newColor = slot.light"
			/>
		</div>

		<p v-if="error" class="banner-error" role="alert">
			{{ error }}
		</p>

		<p v-if="!ledger.tags.length" class="py-2 text-center text-sm text-on-surface-variant">
			No tags yet. Add one, then put it on a transaction.
		</p>

		<ul v-else class="divide-y divide-outline-variant">
			<li v-for="tag in ledger.tags" :key="tag.id" class="group py-2">
				<form v-if="editingId === tag.id" class="space-y-2" @submit.prevent="commitEdit(tag.id)">
					<div class="flex gap-2">
						<input v-model="draftName" class="input input-sm" maxlength="80" autofocus @keydown.esc="editingId = null" />
						<button type="submit" class="btn-primary btn-sm" :disabled="busy">Save</button>
						<button type="button" class="btn-text btn-sm" @click="editingId = null">Cancel</button>
					</div>
					<div class="flex flex-wrap items-center gap-2" role="group" :aria-label="`Colour of ${tag.name}`">
						<button
							v-for="slot in PALETTE"
							:key="slot.light"
							type="button"
							class="size-6 rounded-full ring-offset-2 transition-transform hover:scale-110"
							:class="draftColor === slot.light ? 'ring-2 ring-on-surface' : ''"
							:style="{ backgroundColor: slot.light }"
							:aria-label="slot.name"
							:aria-pressed="draftColor === slot.light"
							@click="draftColor = slot.light"
						/>
					</div>
				</form>

				<div v-else class="flex items-center gap-3">
					<span class="size-3 shrink-0 rounded-full" :style="{ backgroundColor: tag.color }" aria-hidden="true" />
					<span class="min-w-0 flex-1 truncate text-sm font-medium text-on-surface">{{ tag.name }}</span>

					<div class="row-actions">
						<ActionIcon icon="edit" :label="`Edit ${tag.name}`" @click="startEdit(tag)" />
						<ActionIcon icon="delete" :label="`Delete ${tag.name}`" danger @click="remove(tag)" />
					</div>
				</div>
			</li>
		</ul>
	</div>
</template>
