<script setup lang="ts">
import ActionIcon from '@/components/ActionIcon.vue';
import EmptyState from '@/components/EmptyState.vue';
import FabButton from '@/components/FabButton.vue';
import ModalDialog from '@/components/ModalDialog.vue';
import { api, ApiError } from '@/lib/api';
import { nextColor, PALETTE } from '@/lib/palette';
import { formatCount } from '@/lib/count';
import { showSnackbar } from '@/lib/snackbar';
import { colorProblem, nameProblem, sameName, supportId, useFormValidation } from '@/lib/validation';
import FieldSupport from '@/components/FieldSupport.vue';
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

const validation = useFormValidation({
	'tag-name': () =>
		nameProblem(
			form.name,
			(name) => ledger.tags.some((tag) => tag.id !== editing.value?.id && sameName(tag.name, name)),
			'A tag with that name already exists.',
		),
	'tag-color': () => colorProblem(form.color),
});
const { error: fieldError, touch } = validation;
const describe = (id: string): string | undefined => (fieldError(id) ? supportId(id) : undefined);

/** A long list is searched rather than scrolled; the box only appears once there is enough to lose something in. */
const SEARCH_FROM = 8;

/** True once the colour has strayed from the validated palette onto a hand-picked hex. */
const isCustomColor = computed(() => !PALETTE.some((slot) => slot.light === form.color));

function pickCustomColor(event: Event): void {
	form.color = (event.target as HTMLInputElement).value;
}

const visible = computed(() => {
	const needle = search.value.trim().toLowerCase();
	return needle ? ledger.tags.filter((tag) => tag.name.toLowerCase().includes(needle)) : ledger.tags;
});

function openCreate(): void {
	editing.value = null;
	error.value = null;
	validation.reset();
	Object.assign(form, { name: '', color: nextColor(ledger.tags.length) });
	dialogOpen.value = true;
}

function openEdit(tag: Tag): void {
	editing.value = tag;
	error.value = null;
	validation.reset();
	Object.assign(form, { name: tag.name, color: tag.color });
	dialogOpen.value = true;
}

async function save(): Promise<void> {
	error.value = null;
	if (!validation.isValid.value) return;

	const name = form.name.trim();
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

// The counts move whenever a transaction is saved, and `load` is done once the ledger has been fetched, so ask again.
onMounted(async () => {
	await ledger.load();
	await ledger.refreshTags().catch(() => undefined);
});

/** A copy saved before counts existed has none, so read a missing one as zero until the API answers. */
const countOf = (tag: Tag) => tag.transactionCount ?? 0;
const countLabel = (tag: Tag) => `${formatCount(countOf(tag))} ${countOf(tag) === 1 ? 'transaction' : 'transactions'}`;
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

			<p v-if="!visible.length" class="py-4 text-center text-sm text-on-surface-variant">No tag matches “{{ search.trim() }}”.</p>

			<!-- One card per tag, its count at the end. Actions come first: they only show on hover, so the count keeps its place. -->
			<ul v-else class="space-y-2">
				<li v-for="tag in visible" :key="tag.id" class="card group flex items-center gap-3 px-4 py-3">
					<span class="size-3 shrink-0 rounded-full" :style="{ backgroundColor: tag.color }" aria-hidden="true" />
					<span class="min-w-0 flex-1 truncate text-sm font-medium text-on-surface">{{ tag.name }}</span>

					<div class="row-actions">
						<ActionIcon icon="edit" :label="`Edit ${tag.name}`" @click="openEdit(tag)" />
						<ActionIcon icon="delete" :label="`Delete ${tag.name}`" danger @click="remove(tag)" />
					</div>

					<span
						class="tabular shrink-0 text-xs text-on-surface-variant"
						:title="`${countOf(tag).toLocaleString()} ${countOf(tag) === 1 ? 'transaction' : 'transactions'}`"
					>
						{{ countLabel(tag) }}
					</span>
				</li>
			</ul>
		</template>

		<ModalDialog :open="dialogOpen" :title="editing ? 'Edit tag' : 'New tag'" @close="dialogOpen = false">
			<form class="space-y-4" novalidate @submit.prevent="save" @input="validation.onInput">
				<div class="field">
					<label class="label" for="tag-name">Name</label>
					<input
						id="tag-name"
						v-model="form.name"
						class="input"
						required
						placeholder="Reimbursable"
						:aria-invalid="fieldError('tag-name') ? true : undefined"
						:aria-describedby="describe('tag-name')"
						@blur="touch('tag-name')"
					/>
					<FieldSupport id="tag-name" :error="fieldError('tag-name')" />
				</div>

				<fieldset>
					<legend class="label">Colour</legend>
					<!-- The eight slots are a validated set, the same a category takes. A custom hex opts out of that guarantee,
					     so it stays a deliberate extra step rather than a ninth slot in the same row. -->
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

						<label
							class="relative grid h-8 w-8 cursor-pointer place-items-center rounded-full text-outline ring-offset-2 transition-transform hover:scale-110"
							:class="
								isCustomColor
									? 'ring-2 ring-on-surface'
									: 'bg-[repeating-conic-gradient(var(--color-outline-variant)_0_25%,transparent_0_50%)] bg-[length:8px_8px] ring-1 ring-outline'
							"
							:style="isCustomColor ? { backgroundColor: form.color } : {}"
							title="Custom colour"
						>
							<span v-if="!isCustomColor" aria-hidden="true">+</span>
							<input
								type="color"
								class="sr-only"
								:value="isCustomColor ? form.color : '#64748b'"
								aria-label="Pick a custom colour"
								@input="pickCustomColor"
							/>
						</label>

						<input
							v-if="isCustomColor"
							v-model="form.color"
							class="input input-sm w-28 font-mono"
							id="tag-color"
							required
							maxlength="7"
							placeholder="#64748b"
							aria-label="Custom colour hex value"
							:aria-invalid="fieldError('tag-color') ? true : undefined"
							:aria-describedby="describe('tag-color')"
							@blur="touch('tag-color')"
						/>
					</div>
					<FieldSupport id="tag-color" :error="fieldError('tag-color')" class="!px-0" />
				</fieldset>

				<p v-if="error" class="banner-error" role="alert">
					{{ error }}
				</p>

				<div class="flex justify-end gap-2 pt-2">
					<button type="button" class="btn-text" @click="dialogOpen = false">Cancel</button>
					<button type="submit" class="btn-primary" :disabled="saving || !validation.isValid.value">
						{{ editing ? 'Save changes' : 'Add tag' }}
					</button>
				</div>
			</form>
		</ModalDialog>

		<FabButton label="Add tag" @click="openCreate" />
	</div>
</template>
