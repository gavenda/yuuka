<script setup lang="ts">
import ActionIcon from '@/components/ActionIcon.vue';
import ColourField from '@/components/ColourField.vue';
import EmptyState from '@/components/EmptyState.vue';
import FabButton from '@/components/FabButton.vue';
import FormDialog from '@/components/FormDialog.vue';
import SwipeReveal from '@/components/SwipeReveal.vue';
import TextField from '@/components/TextField.vue';
import { api, ApiError } from '@/lib/api';
import { formatCount } from '@/lib/count';
import { useHarmonised } from '@/lib/harmonise';
import { nextColor, PALETTE } from '@/lib/palette';
import { showSnackbar } from '@/lib/snackbar';
import { colorProblem, nameProblem, sameName, useFormValidation } from '@/lib/validation';
import { useLedgerStore } from '@/stores/ledger';
import type { Tag } from '@/types';
import { computed, onMounted, reactive, ref } from 'vue';
import { t } from '@/i18n';

const ledger = useLedgerStore();
const harmonised = useHarmonised();

const dialogOpen = ref(false);
const editing = ref<Tag | null>(null);
const error = ref<string | null>(null);
const saving = ref(false);
const search = ref('');

const form = reactive({ name: '', color: PALETTE[0].light });

const validation = useFormValidation({
	'tag-name': () =>
		nameProblem(form.name, (name) => ledger.tags.some((tag) => tag.id !== editing.value?.id && sameName(tag.name, name)), t('tags.taken')),
	'tag-color': () => colorProblem(form.color),
});
const { error: fieldError, touch } = validation;

/** What the form opened with, so that closing it can tell an entry from an untouched form. */
const opened = ref('');
const dirty = computed(() => JSON.stringify(form) !== opened.value);

/** A long list is searched rather than scrolled; the box only appears once there is enough to lose something in. */
const SEARCH_FROM = 8;

const visible = computed(() => {
	const needle = search.value.trim().toLowerCase();
	return needle ? ledger.tags.filter((tag) => tag.name.toLowerCase().includes(needle)) : ledger.tags;
});

function openCreate(): void {
	editing.value = null;
	error.value = null;
	validation.reset();
	Object.assign(form, { name: '', color: nextColor(ledger.tags.length) });
	opened.value = JSON.stringify(form);
	dialogOpen.value = true;
}

function openEdit(tag: Tag): void {
	editing.value = tag;
	error.value = null;
	validation.reset();
	Object.assign(form, { name: tag.name, color: tag.color });
	opened.value = JSON.stringify(form);
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
		showSnackbar(wasEditing ? t('tags.updated') : t('tags.added'));
		await ledger.refreshTags();
	} catch (caught) {
		error.value = caught instanceof ApiError ? caught.message : t('common.couldNotSave');
	} finally {
		saving.value = false;
	}
}

/** Deleting a tag only takes the label off — the transactions stay — so there is nothing here to ask about first. */
async function remove(tag: Tag): Promise<void> {
	try {
		await api.deleteTag(tag.id);
		showSnackbar(t('tags.deleted'));
		await ledger.refreshTags();
	} catch (caught) {
		showSnackbar(caught instanceof ApiError ? caught.message : t('tags.couldNotDelete'));
	}
}

// The counts move whenever a transaction is saved, and `load` is done once the ledger has been fetched, so ask again.
onMounted(async () => {
	await ledger.load();
	await ledger.refreshTags().catch(() => undefined);
});

/** A copy saved before counts existed has none, so read a missing one as zero until the API answers. */
const countOf = (tag: Tag) => tag.transactionCount ?? 0;
const countLabel = (tag: Tag) => t('tags.transactions', { amount: formatCount(countOf(tag)) }, countOf(tag));
</script>

<template>
	<div class="px-4 pt-4 pb-24">
		<EmptyState v-if="!ledger.tags.length" fill :title="t('tags.emptyTitle')" :description="t('tags.emptyDescription')" />

		<template v-else>
			<!-- A long list is searched rather than scrolled; the field only appears once there is enough to lose something in. -->
			<TextField
				v-if="ledger.tags.length >= SEARCH_FROM"
				id="tag-search"
				v-model="search"
				class="mb-1"
				:label="t('tags.search')"
				type="search"
			/>

			<p v-if="!visible.length" class="type-body-small">{{ t('tags.noMatch', { search: search.trim() }) }}</p>

			<!-- One row per tag — its colour leading, its count at the end — drawn as one connected block. -->
			<ul v-else class="group-rows">
				<li v-for="tag in visible" :key="tag.id">
					<SwipeReveal>
						<template #actions>
							<ActionIcon icon="delete" :label="t('common.delete', { name: tag.name })" danger @click="remove(tag)" />
						</template>

						<button type="button" class="group-row state-layer focus-ring cursor-pointer" @click="openEdit(tag)">
							<span class="flex min-h-14 items-center gap-3 px-4">
								<span class="size-3 shrink-0 rounded-full" :style="{ backgroundColor: harmonised(tag.color) }" aria-hidden="true" />
								<span class="type-body-medium min-w-0 flex-1 truncate">{{ tag.name }}</span>
								<span class="type-body-small shrink-0 text-on-surface-variant">{{ countLabel(tag) }}</span>
							</span>
						</button>
					</SwipeReveal>
				</li>
			</ul>
		</template>

		<FormDialog
			:open="dialogOpen"
			:title="editing ? t('tags.editTag') : t('tags.newTag')"
			:save-enabled="validation.isValid.value"
			:submitting="saving"
			:dirty="dirty"
			@close="dialogOpen = false"
			@save="save"
		>
			<div class="contents" @input="validation.onInput">
				<TextField id="tag-name" v-model="form.name" :label="t('common.name')" :error="fieldError('tag-name')" @blur="touch('tag-name')" />

				<ColourField id="tag-color" v-model="form.color" :error="fieldError('tag-color')" @touch="touch('tag-color')" />

				<p v-if="error" class="type-body-small text-error" role="alert">{{ error }}</p>
			</div>
		</FormDialog>

		<FabButton :label="t('tags.newTag')" @click="openCreate" />
	</div>
</template>
