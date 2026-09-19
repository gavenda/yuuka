<script setup lang="ts">
import ActionIcon from '@/components/ActionIcon.vue';
import EmptyState from '@/components/EmptyState.vue';
import FabButton from '@/components/FabButton.vue';
import ModalDialog from '@/components/ModalDialog.vue';
import { api, ApiError } from '@/lib/api';
import { nextColor, PALETTE } from '@/lib/palette';
import { showSnackbar } from '@/lib/snackbar';
import { useLedgerStore } from '@/stores/ledger';
import type { Tag } from '@/types';
import { computed, onMounted, reactive, ref } from 'vue';

const ledger = useLedgerStore();

const dialogOpen = ref(false);
const editing = ref<Tag | null>(null);
const error = ref<string | null>(null);
const saving = ref(false);
const search = ref('');

const form = reactive({ name: '', color: PALETTE[0].light });

/** A long list is searched rather than scrolled; the box only appears once there is enough to lose something in. */
const SEARCH_FROM = 8;

const visible = computed(() => {
	const needle = search.value.trim().toLowerCase();
	return needle ? ledger.tags.filter((tag) => tag.name.toLowerCase().includes(needle)) : ledger.tags;
});

function openCreate(): void {
	editing.value = null;
	error.value = null;
	Object.assign(form, { name: '', color: nextColor(ledger.tags.length) });
	dialogOpen.value = true;
}

function openEdit(tag: Tag): void {
	editing.value = tag;
	error.value = null;
	Object.assign(form, { name: tag.name, color: tag.color });
	dialogOpen.value = true;
}

async function save(): Promise<void> {
	const name = form.name.trim();
	if (!name) return;

	error.value = null;
	saving.value = true;

	try {
		const wasEditing = editing.value !== null;
		if (editing.value) await api.updateTag(editing.value.id, { name, color: form.color });
		else await api.createTag({ name, color: form.color });

		dialogOpen.value = false;
		showSnackbar(wasEditing ? 'Changes saved' : 'Tag added');
		await ledger.refreshTags();
	} catch (caught) {
		error.value = caught instanceof ApiError ? caught.message : 'Could not save the tag.';
	} finally {
		saving.value = false;
	}
}

async function remove(tag: Tag): Promise<void> {
	if (!confirm(`Delete “${tag.name}”? It comes off every transaction wearing it; the transactions stay.`)) return;

	try {
		await api.deleteTag(tag.id);
		showSnackbar('Tag deleted');
		await ledger.refreshTags();
	} catch (caught) {
		showSnackbar(caught instanceof ApiError ? caught.message : 'Could not delete the tag.');
	}
}

onMounted(() => ledger.load());
</script>

<template>
	<div class="space-y-5">
		<p class="text-sm text-on-surface-variant">
			Labels to put on a transaction, shown beside its notes. They sit alongside its category and change no total.
		</p>

		<EmptyState
			v-if="!ledger.loading && !ledger.tags.length"
			title="No tags yet"
			description="Add one, then put it on a transaction to find it by that label later."
		>
			<button type="button" class="btn-primary" @click="openCreate">Add a tag</button>
		</EmptyState>

		<template v-else>
			<div v-if="ledger.tags.length >= SEARCH_FROM" class="field">
				<label class="label" for="tag-search">Search</label>
				<input id="tag-search" v-model="search" class="input" type="search" placeholder="Tag name" />
			</div>

			<section class="card p-5">
				<p v-if="!visible.length" class="py-4 text-center text-sm text-on-surface-variant">No tag matches “{{ search.trim() }}”.</p>

				<ul v-else class="divide-y divide-outline-variant">
					<li v-for="tag in visible" :key="tag.id" class="group flex items-center gap-3 py-2">
						<span class="size-3 shrink-0 rounded-full" :style="{ backgroundColor: tag.color }" aria-hidden="true" />
						<span class="min-w-0 flex-1 truncate text-sm font-medium text-on-surface">{{ tag.name }}</span>

						<div class="row-actions">
							<ActionIcon icon="edit" :label="`Edit ${tag.name}`" @click="openEdit(tag)" />
							<ActionIcon icon="delete" :label="`Delete ${tag.name}`" danger @click="remove(tag)" />
						</div>
					</li>
				</ul>
			</section>
		</template>

		<ModalDialog :open="dialogOpen" :title="editing ? 'Edit tag' : 'New tag'" @close="dialogOpen = false">
			<form class="space-y-4" @submit.prevent="save">
				<div class="field">
					<label class="label" for="tag-name">Name</label>
					<input id="tag-name" v-model="form.name" class="input" required maxlength="80" placeholder="Reimbursable" />
				</div>

				<fieldset>
					<legend class="label">Colour</legend>
					<!-- The validated palette, the same eight slots a category takes. -->
					<div class="flex flex-wrap items-center gap-2">
						<button
							v-for="slot in PALETTE"
							:key="slot.light"
							type="button"
							class="h-8 w-8 rounded-full ring-offset-2 transition-transform hover:scale-110"
							:class="form.color === slot.light ? 'ring-2 ring-on-surface' : ''"
							:style="{ backgroundColor: slot.light }"
							:aria-label="slot.name"
							:aria-pressed="form.color === slot.light"
							@click="form.color = slot.light"
						/>
					</div>
				</fieldset>

				<p v-if="error" class="banner-error" role="alert">
					{{ error }}
				</p>

				<div class="flex justify-end gap-2 pt-2">
					<button type="button" class="btn-text" @click="dialogOpen = false">Cancel</button>
					<button type="submit" class="btn-primary" :disabled="saving || !form.name.trim()">
						{{ editing ? 'Save changes' : 'Add tag' }}
					</button>
				</div>
			</form>
		</ModalDialog>

		<FabButton label="Add tag" @click="openCreate" />
	</div>
</template>
