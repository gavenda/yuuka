<script setup lang="ts">
import ActionIcon from '@/components/ActionIcon.vue';
import AppIcon from '@/components/AppIcon.vue';
import ColourField from '@/components/ColourField.vue';
import ConnectedButtonGroup from '@/components/ConnectedButtonGroup.vue';
import EmptyState from '@/components/EmptyState.vue';
import FabButton from '@/components/FabButton.vue';
import FormDialog from '@/components/FormDialog.vue';
import YuukaSays from '@/components/YuukaSays.vue';
import SwipeReveal from '@/components/SwipeReveal.vue';
import TextField from '@/components/TextField.vue';
import { whenDiscardable } from '@/lib/formPane';
import { api, ApiError } from '@/lib/api';
import { useHarmonised } from '@/lib/harmonise';
import { ADD, EXPAND_MORE, LABEL_FILL } from '@/lib/icons';
import { nextColor, PALETTE } from '@/lib/palette';
import { showSnackbar } from '@/lib/snackbar';
import { colorProblem, nameProblem, useFormValidation } from '@/lib/validation';
import { useBudgetStore } from '@/stores/budget';
import { useLedgerStore } from '@/stores/ledger';
import type { Category, CategoryKind } from '@/types';
import { computed, onMounted, reactive, ref, watch } from 'vue';
import { t } from '@/i18n';

const ledger = useLedgerStore();
const budget = useBudgetStore();
const harmonised = useHarmonised();

const dialogOpen = ref(false);
const editing = ref<Category | null>(null);
const error = ref<string | null>(null);
const showArchived = ref(false);
/** The parents whose subcategories are on show. */
const expanded = ref(new Set<string>());

const form = reactive({
	name: '',
	kind: 'expense' as CategoryKind,
	color: PALETTE[0].light,
	parentId: '',
});
/** The name the form opened with. With the colour it would have been given, it tells an entry from an untouched form. */
const openedName = ref('');

interface Section {
	key: string;
	title: string;
	kind: CategoryKind;
	families: { parent: Category; children: Category[] }[];
}

/**
 * Three sections, each a list of top-level categories with their own children
 * beneath. Cashflow is separated out because those categories belong to
 * transfers rather than to spending.
 */
const sections = computed<Section[]>(() => {
	const visible = ledger.categories.filter((category) => showArchived.value || !category.archived);

	const familiesFor = (kind: CategoryKind) =>
		visible
			.filter((category) => category.parentId === null && category.kind === kind)
			.map((parent) => ({ parent, children: visible.filter((category) => category.parentId === parent.id) }));

	return [
		{ key: 'expense', title: t('common.expense'), kind: 'expense', families: familiesFor('expense') },
		{ key: 'income', title: t('common.income'), kind: 'income', families: familiesFor('income') },
		{ key: 'cashflow', title: t('common.cashflow'), kind: 'transfer', families: familiesFor('transfer') },
	];
});

const KIND_CHOICES: { value: CategoryKind; label: string }[] = [
	{ value: 'expense', label: t('common.expense') },
	{ value: 'income', label: t('common.income') },
	{ value: 'transfer', label: t('common.cashflow') },
];

const archivedCount = computed(() => ledger.categories.filter((category) => category.archived).length);

/** A subcategory takes its parent's kind, so that is the kind its name has to be unique within. */
const effectiveKind = computed(() => (form.parentId ? (ledger.categoriesById.get(form.parentId)?.kind ?? form.kind) : form.kind));
const parentName = computed(() => (form.parentId ? (ledger.categoriesById.get(form.parentId)?.name ?? '') : ''));

const validation = useFormValidation({
	'category-name': () =>
		nameProblem(
			form.name,
			(name) =>
				ledger.categories.some(
					(category) =>
						category.id !== editing.value?.id &&
						category.name === name &&
						category.kind === effectiveKind.value &&
						(category.parentId ?? '') === form.parentId,
				),
			t('categories.taken'),
		),
	'category-color': () => colorProblem(form.color),
});
const { error: fieldError, touch } = validation;

const colourFor = (kind: CategoryKind): string => nextColor(ledger.categories.filter((category) => category.kind === kind).length);

// Changing section moves a new category's colour with it, which is not an entry of the person's own.
const dirty = computed(() => form.name !== openedName.value || form.color !== (editing.value?.color ?? colourFor(form.kind)));

function open(values: typeof form): void {
	error.value = null;
	validation.reset();
	Object.assign(form, values);
	openedName.value = form.name;
	dialogOpen.value = true;
}

function openCreate(kind: CategoryKind = 'expense', parentId = ''): void {
	whenDiscardable(() => {
		editing.value = null;
		open({ name: '', kind, color: colourFor(kind), parentId });
	});
}

function openEdit(category: Category): void {
	whenDiscardable(() => {
		editing.value = category;
		open({ name: category.name, kind: category.kind, color: category.color, parentId: category.parentId ?? '' });
	});
}

// A form standing beside the list closes with the row it was editing, wherever that was deleted.
watch(
	() => ledger.categories,
	(rows) => {
		if (dialogOpen.value && editing.value && !rows.some((row) => row.id === editing.value?.id)) dialogOpen.value = false;
	},
);

/** A new top-level category can still change section; the next free colour follows it there. */
function selectKind(kind: CategoryKind): void {
	form.kind = kind;
	form.color = colourFor(kind);
}

/** A parent with subcategories opens and closes them; any other row is edited by pressing it. */
function press(category: Category, children: number): void {
	if (!children) return openEdit(category);

	// One family is open at a time; opening another closes it.
	expanded.value = expanded.value.has(category.id) ? new Set() : new Set([category.id]);
}

async function save(): Promise<void> {
	error.value = null;
	if (!validation.isValid.value) return;

	// Kind is only meaningful when creating a top-level category; a child
	// inherits its parent's, and the API ignores what is sent anyway.
	const payload = editing.value
		? { name: form.name.trim(), kind: form.kind, color: form.color }
		: { name: form.name.trim(), kind: form.kind, color: form.color, parentId: form.parentId || null };

	try {
		if (editing.value) await api.updateCategory(editing.value.id, payload);
		else await api.createCategory(payload);

		dialogOpen.value = false;
		showSnackbar(editing.value ? t('categories.updated') : t('categories.added'));
		await Promise.all([ledger.refreshCategories(), budget.refresh()]);
	} catch (caught) {
		error.value = caught instanceof ApiError ? caught.message : t('common.couldNotSave');
	}
}

async function toggleArchived(category: Category): Promise<void> {
	await api.updateCategory(category.id, { archived: !category.archived });
	showSnackbar(category.archived ? t('categories.restored') : t('categories.archived'));
	await ledger.refreshCategories();
}

/** Transactions keep their history and become uncategorised, so there is nothing here to ask about first. */
async function remove(category: Category): Promise<void> {
	await api.deleteCategory(category.id);
	showSnackbar(t('categories.deleted'));
	await Promise.all([ledger.refreshCategories(), budget.refresh()]);
}

onMounted(() => ledger.load());
</script>

<template>
	<div class="flex flex-col gap-4 px-4 pt-4 pb-24">
		<EmptyState
			v-if="!ledger.categories.length"
			mood="neutral"
			:title="t('categories.emptyTitle')"
			:description="t('categories.emptyDescription')"
		/>

		<!-- A section is one segmented list under its heading, as the settings groups are drawn. -->
		<section v-for="section in sections" :key="section.key" class="flex flex-col gap-4">
			<h2 class="settings-header">{{ section.title }} ({{ section.families.length }})</h2>

			<div class="group-rows">
				<p v-if="!section.families.length" class="group-row type-body-medium p-5">No {{ section.title.toLowerCase() }} categories yet.</p>

				<template v-for="family in section.families" :key="family.parent.id">
					<!-- A parent's row. The row paints itself, which is what keeps the swipe's actions hidden behind it. -->
					<SwipeReveal>
						<template #actions>
							<ActionIcon icon="edit" :label="t('common.rename', { name: family.parent.name })" @click="openEdit(family.parent)" />
							<ActionIcon
								:icon="family.parent.archived ? 'restore' : 'archive'"
								:label="t(family.parent.archived ? 'common.restore' : 'common.archive', { name: family.parent.name })"
								@click="toggleArchived(family.parent)"
							/>
							<ActionIcon icon="delete" :label="t('common.delete', { name: family.parent.name })" danger @click="remove(family.parent)" />
						</template>

						<!-- The row wears the state layer and the button's ::before is stretched over it, so the whole row answers to the pointer. -->
						<div
							class="group-row state-layer flex items-center pr-4"
							:class="[
								family.children.length ? 'min-h-[72px]' : 'min-h-[60px]',
								expanded.has(family.parent.id) ? 'bg-primary-container text-on-primary-container' : '',
							]"
						>
							<button
								type="button"
								class="focus-ring flex min-w-0 flex-1 cursor-pointer items-center gap-3 self-stretch pl-4 text-left before:absolute before:inset-0"
								:aria-expanded="family.children.length ? expanded.has(family.parent.id) : undefined"
								:aria-current="dialogOpen && editing?.id === family.parent.id ? 'true' : undefined"
								@click="press(family.parent, family.children.length)"
							>
								<span
									class="flex size-6 shrink-0 items-center justify-center rounded-full"
									:class="expanded.has(family.parent.id) ? 'bg-surface-container' : ''"
									aria-hidden="true"
								>
									<span class="flex" :style="{ color: harmonised(family.parent.color) }"><AppIcon :icon="LABEL_FILL" :size="18" /></span>
								</span>
								<span class="min-w-0 flex-1">
									<span class="type-body-large block truncate">{{
										family.parent.archived ? t('common.archivedName', { name: family.parent.name }) : family.parent.name
									}}</span>
									<span
										v-if="family.children.length"
										class="type-body-medium block"
										:class="expanded.has(family.parent.id) ? 'text-on-primary-container' : 'text-on-surface-variant'"
									>
										{{ t('categories.subcategories', family.children.length) }}
									</span>
								</span>
							</button>

							<button
								type="button"
								class="btn-icon text-on-surface-variant"
								:aria-label="t('categories.addSubTo', { name: family.parent.name })"
								:title="t('categories.addSub')"
								@click="openCreate(section.kind, family.parent.id)"
							>
								<AppIcon :icon="ADD" />
							</button>
							<AppIcon
								v-if="family.children.length"
								:icon="EXPAND_MORE"
								class="text-on-surface-variant transition-transform duration-300 ease-emphasized-decelerate"
								:class="expanded.has(family.parent.id) ? 'rotate-180' : ''"
							/>
						</div>
					</SwipeReveal>

					<!-- Its subcategories, in primary container with their parent. Pressing one edits it. -->
					<template v-if="expanded.has(family.parent.id)">
						<SwipeReveal v-for="child in family.children" :key="child.id">
							<template #actions>
								<ActionIcon icon="edit" :label="t('common.rename', { name: child.name })" @click="openEdit(child)" />
								<ActionIcon
									:icon="child.archived ? 'restore' : 'archive'"
									:label="t(child.archived ? 'common.restore' : 'common.archive', { name: child.name })"
									@click="toggleArchived(child)"
								/>
								<ActionIcon icon="delete" :label="t('common.delete', { name: child.name })" danger @click="remove(child)" />
							</template>

							<button
								type="button"
								class="group-row state-layer focus-ring flex min-h-[72px] cursor-pointer items-center gap-3 bg-primary-container pr-4 pl-4 text-on-primary-container"
								:aria-current="dialogOpen && editing?.id === child.id ? 'true' : undefined"
								@click="openEdit(child)"
							>
								<span class="flex size-6 shrink-0 items-center justify-center rounded-full bg-surface-container" aria-hidden="true">
									<span class="flex" :style="{ color: harmonised(child.color) }"><AppIcon :icon="LABEL_FILL" :size="18" /></span>
								</span>
								<span class="type-body-large min-w-0 flex-1 truncate">{{
									child.archived ? t('common.archivedName', { name: child.name }) : child.name
								}}</span>
							</button>
						</SwipeReveal>
					</template>
				</template>
			</div>
		</section>

		<div v-if="archivedCount > 0">
			<button type="button" class="btn-text" @click="showArchived = !showArchived">
				{{ t(showArchived ? 'common.hideArchived' : 'common.showArchived', { count: archivedCount }) }}
			</button>
		</div>

		<FormDialog
			:open="dialogOpen"
			:title="editing ? t('categories.editCategory') : t('categories.newCategory')"
			:save-enabled="validation.isValid.value"
			:dirty="dirty"
			@close="dialogOpen = false"
			@save="save"
		>
			<div class="contents" @input="validation.onInput">
				<!-- Only a new top-level category chooses its section: a subcategory takes its parent's, and an existing one keeps its own. -->
				<ConnectedButtonGroup
					v-if="!editing && !form.parentId"
					:model-value="form.kind"
					:label="t('categories.kindOf')"
					:options="KIND_CHOICES"
					@update:model-value="selectKind"
				/>

				<TextField
					id="category-name"
					v-model="form.name"
					:label="t('common.name')"
					:error="fieldError('category-name')"
					@blur="touch('category-name')"
				/>

				<p v-if="!editing && form.parentId" class="type-body-small text-on-surface">
					{{ t('categories.nestedUnder', { name: parentName }) }}
				</p>

				<ColourField id="category-color" v-model="form.color" :error="fieldError('category-color')" @blur="touch('category-color')" />

				<p v-if="error" class="type-body-small text-error" role="alert">{{ error }}</p>

				<!-- Only a new one: Yuuka at the foot of the form. -->
				<YuukaSays v-if="!editing" mood="neutral" class="pt-2">{{ t('categories.formLine') }}</YuukaSays>
			</div>
		</FormDialog>

		<FabButton :label="t('categories.newCategory')" @click="openCreate()" />
	</div>
</template>
