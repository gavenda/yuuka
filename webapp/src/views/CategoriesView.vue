<script setup lang="ts">
import ActionIcon from '@/components/ActionIcon.vue';
import AppIcon from '@/components/AppIcon.vue';
import ColourField from '@/components/ColourField.vue';
import ConnectedButtonGroup from '@/components/ConnectedButtonGroup.vue';
import EmptyState from '@/components/EmptyState.vue';
import FabButton from '@/components/FabButton.vue';
import FormDialog from '@/components/FormDialog.vue';
import SwipeReveal from '@/components/SwipeReveal.vue';
import TextField from '@/components/TextField.vue';
import { api, ApiError } from '@/lib/api';
import { useHarmonised } from '@/lib/harmonise';
import { ADD, EXPAND_MORE } from '@/lib/icons';
import { nextColor, PALETTE } from '@/lib/palette';
import { showSnackbar } from '@/lib/snackbar';
import { colorProblem, nameProblem, useFormValidation } from '@/lib/validation';
import { useBudgetStore } from '@/stores/budget';
import { useLedgerStore } from '@/stores/ledger';
import type { Category, CategoryKind } from '@/types';
import { computed, onMounted, reactive, ref } from 'vue';

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
		{ key: 'expense', title: 'Expense', kind: 'expense', families: familiesFor('expense') },
		{ key: 'income', title: 'Income', kind: 'income', families: familiesFor('income') },
		{ key: 'cashflow', title: 'Cashflow', kind: 'transfer', families: familiesFor('transfer') },
	];
});

const KIND_CHOICES: { value: CategoryKind; label: string }[] = [
	{ value: 'expense', label: 'Expense' },
	{ value: 'income', label: 'Income' },
	{ value: 'transfer', label: 'Cashflow' },
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
			'A category with that name already exists here.',
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
	editing.value = null;
	open({ name: '', kind, color: colourFor(kind), parentId });
}

function openEdit(category: Category): void {
	editing.value = category;
	open({ name: category.name, kind: category.kind, color: category.color, parentId: category.parentId ?? '' });
}

/** A new top-level category can still change section; the next free colour follows it there. */
function selectKind(kind: CategoryKind): void {
	form.kind = kind;
	form.color = colourFor(kind);
}

/** A parent with subcategories opens and closes them; any other row is edited by pressing it. */
function press(category: Category, children: number): void {
	if (!children) return openEdit(category);

	const next = new Set(expanded.value);
	if (!next.delete(category.id)) next.add(category.id);
	expanded.value = next;
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
		showSnackbar(editing.value ? 'Category updated' : 'Category added');
		await Promise.all([ledger.refreshCategories(), budget.refresh()]);
	} catch (caught) {
		error.value = caught instanceof ApiError ? caught.message : 'Could not save the category.';
	}
}

async function toggleArchived(category: Category): Promise<void> {
	await api.updateCategory(category.id, { archived: !category.archived });
	showSnackbar(category.archived ? 'Category restored' : 'Category archived');
	await ledger.refreshCategories();
}

/** Transactions keep their history and become uncategorised, so there is nothing here to ask about first. */
async function remove(category: Category): Promise<void> {
	await api.deleteCategory(category.id);
	showSnackbar('Category deleted');
	await Promise.all([ledger.refreshCategories(), budget.refresh()]);
}

onMounted(() => ledger.load());
</script>

<template>
	<div class="flex flex-col gap-4 px-4 pt-4 pb-24">
		<EmptyState
			v-if="!ledger.categories.length"
			title="No categories yet"
			description="Categories are how spending gets grouped and budgeted."
		/>

		<!-- A section is one block of rows under its heading, as the settings groups are drawn. -->
		<section v-for="section in sections" :key="section.key" class="flex flex-col gap-4">
			<h2 class="settings-header">{{ section.title }} ({{ section.families.length }})</h2>

			<div class="settings-group overflow-hidden">
				<p v-if="!section.families.length" class="type-body-medium p-5">No {{ section.title.toLowerCase() }} categories yet.</p>

				<template v-for="family in section.families" :key="family.parent.id">
					<!-- A parent's row. The row paints the group's own colour, which is what keeps the swipe's actions hidden behind it. -->
					<SwipeReveal>
						<template #actions>
							<ActionIcon icon="edit" :label="`Rename ${family.parent.name}`" @click="openEdit(family.parent)" />
							<ActionIcon
								:icon="family.parent.archived ? 'restore' : 'archive'"
								:label="`${family.parent.archived ? 'Restore' : 'Archive'} ${family.parent.name}`"
								@click="toggleArchived(family.parent)"
							/>
							<ActionIcon icon="delete" :label="`Delete ${family.parent.name}`" danger @click="remove(family.parent)" />
						</template>

						<div class="flex items-center bg-surface-container pr-4" :class="family.children.length ? 'min-h-[72px]' : 'min-h-[60px]'">
							<button
								type="button"
								class="state-layer focus-ring flex min-w-0 flex-1 cursor-pointer items-center gap-3 self-stretch pl-4 text-left"
								:aria-expanded="family.children.length ? expanded.has(family.parent.id) : undefined"
								@click="press(family.parent, family.children.length)"
							>
								<span
									class="size-3 shrink-0 rounded-full"
									:style="{ backgroundColor: harmonised(family.parent.color) }"
									aria-hidden="true"
								/>
								<span class="min-w-0 flex-1">
									<span class="type-body-large block truncate">{{
										family.parent.archived ? `${family.parent.name} (Archived)` : family.parent.name
									}}</span>
									<span v-if="family.children.length" class="type-body-medium block text-on-surface-variant">
										{{ family.children.length }} {{ family.children.length === 1 ? 'subcategory' : 'subcategories' }}
									</span>
								</span>
							</button>

							<button
								type="button"
								class="btn-icon text-on-surface-variant"
								:aria-label="`Add a subcategory to ${family.parent.name}`"
								title="Add a subcategory"
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

					<!-- Its subcategories, set in a step. Pressing one edits it. -->
					<template v-if="expanded.has(family.parent.id)">
						<SwipeReveal v-for="child in family.children" :key="child.id">
							<template #actions>
								<ActionIcon icon="edit" :label="`Rename ${child.name}`" @click="openEdit(child)" />
								<ActionIcon
									:icon="child.archived ? 'restore' : 'archive'"
									:label="`${child.archived ? 'Restore' : 'Archive'} ${child.name}`"
									@click="toggleArchived(child)"
								/>
								<ActionIcon icon="delete" :label="`Delete ${child.name}`" danger @click="remove(child)" />
							</template>

							<button
								type="button"
								class="state-layer focus-ring flex min-h-14 w-full cursor-pointer items-center gap-3 bg-surface-container pr-4 pl-8 text-left"
								@click="openEdit(child)"
							>
								<span class="size-2.5 shrink-0 rounded-full" :style="{ backgroundColor: harmonised(child.color) }" aria-hidden="true" />
								<span class="type-body-medium min-w-0 flex-1 truncate">{{ child.archived ? `${child.name} (Archived)` : child.name }}</span>
							</button>
						</SwipeReveal>
					</template>
				</template>
			</div>
		</section>

		<div v-if="archivedCount > 0">
			<button type="button" class="btn-text" @click="showArchived = !showArchived">
				{{ showArchived ? 'Hide' : 'Show' }} {{ archivedCount }} archived
			</button>
		</div>

		<FormDialog
			:open="dialogOpen"
			:title="editing ? 'Edit category' : 'New category'"
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
					label="Kind of category"
					:options="KIND_CHOICES"
					@update:model-value="selectKind"
				/>

				<TextField
					id="category-name"
					v-model="form.name"
					label="Name"
					:error="fieldError('category-name')"
					@blur="touch('category-name')"
				/>

				<p v-if="!editing && form.parentId" class="type-body-small text-on-surface">Nested under: {{ parentName }}</p>

				<ColourField id="category-color" v-model="form.color" :error="fieldError('category-color')" @touch="touch('category-color')" />

				<p v-if="error" class="type-body-small text-error" role="alert">{{ error }}</p>
			</div>
		</FormDialog>

		<FabButton label="New category" @click="openCreate()" />
	</div>
</template>
