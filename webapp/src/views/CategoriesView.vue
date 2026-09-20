<script setup lang="ts">
import SelectField from '@/components/SelectField.vue';
import { namedOptions } from '@/lib/selectOptions';
import ActionIcon from '@/components/ActionIcon.vue';
import EmptyState from '@/components/EmptyState.vue';
import FabButton from '@/components/FabButton.vue';
import ModalDialog from '@/components/ModalDialog.vue';
import { api, ApiError } from '@/lib/api';
import { nextColor, PALETTE } from '@/lib/palette';
import { showSnackbar } from '@/lib/snackbar';
import { useBudgetStore } from '@/stores/budget';
import { useLedgerStore } from '@/stores/ledger';
import type { Category, CategoryKind } from '@/types';
import { computed, onMounted, reactive, ref } from 'vue';

const ledger = useLedgerStore();
const budget = useBudgetStore();

const dialogOpen = ref(false);
const editing = ref<Category | null>(null);
const error = ref<string | null>(null);
const showArchived = ref(false);

const form = reactive({
	name: '',
	kind: 'expense' as CategoryKind,
	color: PALETTE[0].light,
	parentId: '',
});

interface Section {
	key: string;
	title: string;
	description: string;
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

	const familiesFor = (match: (category: Category) => boolean) =>
		visible
			.filter((category) => category.parentId === null && match(category))
			.map((parent) => ({ parent, children: visible.filter((category) => category.parentId === parent.id) }));

	return [
		{
			key: 'expense',
			title: 'Expense',
			description: 'What you spend on.',
			kind: 'expense',
			families: familiesFor((c) => c.kind === 'expense'),
		},
		{
			key: 'income',
			title: 'Income',
			description: 'What you earn.',
			kind: 'income',
			families: familiesFor((c) => c.kind === 'income'),
		},
		{
			key: 'cashflow',
			title: 'Cashflow',
			description: 'For transfers between your own accounts.',
			kind: 'transfer',
			families: familiesFor((c) => c.kind === 'transfer'),
		},
	];
});

/** Parents the new category could be nested under, matching the chosen section. */
const parentOptions = computed(() =>
	ledger.categories.filter((category) => category.parentId === null && !category.archived && category.kind === form.kind),
);

const parentChoices = computed(() => namedOptions(parentOptions.value, { value: '', label: 'Nothing — this is a top-level category' }));
const KIND_CHOICES = [
	{ value: 'expense', label: 'Expense' },
	{ value: 'income', label: 'Income' },
];

const archivedCount = computed(() => ledger.categories.filter((category) => category.archived).length);

/** True once the form's colour has strayed from the validated palette onto a hand-picked hex. */
const isCustomColor = computed(() => !PALETTE.some((slot) => slot.light === form.color));

function pickCustomColor(event: Event): void {
	form.color = (event.target as HTMLInputElement).value;
}

function openCreate(section: Section, parentId = ''): void {
	editing.value = null;
	error.value = null;

	const siblings = ledger.categories.filter((category) => category.kind === section.kind).length;
	Object.assign(form, {
		name: '',
		kind: section.kind,
		color: nextColor(siblings),
		parentId,
	});
	dialogOpen.value = true;
}

function openEdit(category: Category): void {
	editing.value = category;
	error.value = null;
	Object.assign(form, {
		name: category.name,
		kind: category.kind,
		color: category.color,
		parentId: category.parentId ?? '',
	});
	dialogOpen.value = true;
}

async function save(): Promise<void> {
	// Kind is only meaningful when creating a top-level category; a child
	// inherits its parent's, and the API ignores what is sent anyway.
	const payload = editing.value
		? { name: form.name, kind: form.kind, color: form.color }
		: { name: form.name, kind: form.kind, color: form.color, parentId: form.parentId || null };

	try {
		if (editing.value) await api.updateCategory(editing.value.id, payload);
		else await api.createCategory(payload);

		dialogOpen.value = false;
		showSnackbar(editing.value ? 'Changes saved' : 'Category added');
		await Promise.all([ledger.refreshCategories(), budget.refresh()]);
	} catch (caught) {
		error.value = caught instanceof ApiError ? caught.message : 'Could not save the category.';
	}
}

async function toggleArchived(category: Category): Promise<void> {
	await api.updateCategory(category.id, { archived: !category.archived });
	await ledger.refreshCategories();
}

async function remove(category: Category): Promise<void> {
	if (!confirm(`Delete “${category.name}”? Transactions keep their history but become uncategorised.`)) return;

	await api.deleteCategory(category.id);
	showSnackbar('Category deleted');
	await Promise.all([ledger.refreshCategories(), budget.refresh()]);
}

onMounted(() => ledger.load());
</script>

<template>
	<div class="space-y-6">
		<EmptyState
			v-if="!ledger.loading && !ledger.categories.length"
			title="No categories yet"
			description="Categories are how spending gets grouped and budgeted."
		>
			<button type="button" class="btn-primary" @click="openCreate(sections[0])">Add a category</button>
		</EmptyState>

		<section v-for="section in sections" v-else :key="section.key" class="card p-5">
			<header class="mb-3 flex items-start justify-between gap-3">
				<div>
					<h2 class="text-sm font-medium text-on-surface">{{ section.title }}</h2>
					<p class="mt-0.5 text-xs text-on-surface-variant">{{ section.description }}</p>
				</div>
				<button type="button" class="btn-text btn-sm" @click="openCreate(section)">Add</button>
			</header>

			<p v-if="!section.families.length" class="py-4 text-center text-sm text-on-surface-variant">
				No {{ section.title.toLowerCase() }} categories yet.
			</p>

			<ul v-else class="divide-y divide-outline-variant">
				<li v-for="family in section.families" :key="family.parent.id" class="py-1">
					<!-- Parent, then its own children indented beneath it. Nesting stops
					     here: a subcategory cannot have children of its own. -->
					<div class="group flex items-center gap-3 py-1.5">
						<span class="h-3 w-3 shrink-0 rounded-full" :style="{ backgroundColor: family.parent.color }" aria-hidden="true" />

						<span class="min-w-0 flex-1 truncate text-sm font-medium text-on-surface">
							{{ family.parent.name }}
							<span v-if="family.parent.archived" class="ml-1 text-xs font-normal text-outline">Archived</span>
						</span>

						<div class="row-actions">
							<button
								type="button"
								class="btn-text btn-sm"
								:title="`Add a subcategory under ${family.parent.name}`"
								@click="openCreate(section, family.parent.id)"
							>
								+ Sub
							</button>
							<ActionIcon icon="edit" :label="`Edit ${family.parent.name}`" @click="openEdit(family.parent)" />
							<ActionIcon
								:icon="family.parent.archived ? 'restore' : 'archive'"
								:label="`${family.parent.archived ? 'Restore' : 'Archive'} ${family.parent.name}`"
								@click="toggleArchived(family.parent)"
							/>
							<ActionIcon icon="delete" :label="`Delete ${family.parent.name}`" danger @click="remove(family.parent)" />
						</div>
					</div>

					<div v-for="child in family.children" :key="child.id" class="group flex items-center gap-3 py-1.5 pl-6">
						<span class="h-2 w-2 shrink-0 rounded-full opacity-60" :style="{ backgroundColor: child.color }" aria-hidden="true" />

						<span class="min-w-0 flex-1 truncate text-sm text-on-surface">
							{{ child.name }}
							<span v-if="child.archived" class="ml-1 text-xs text-outline">Archived</span>
						</span>

						<div class="row-actions">
							<ActionIcon icon="edit" :label="`Edit ${child.name}`" @click="openEdit(child)" />
							<ActionIcon
								:icon="child.archived ? 'restore' : 'archive'"
								:label="`${child.archived ? 'Restore' : 'Archive'} ${child.name}`"
								@click="toggleArchived(child)"
							/>
							<ActionIcon icon="delete" :label="`Delete ${child.name}`" danger @click="remove(child)" />
						</div>
					</div>
				</li>
			</ul>
		</section>

		<button v-if="archivedCount" type="button" class="btn-text" @click="showArchived = !showArchived">
			{{ showArchived ? 'Hide' : 'Show' }} {{ archivedCount }} archived
		</button>

		<ModalDialog :open="dialogOpen" :title="editing ? 'Edit category' : 'New category'" @close="dialogOpen = false">
			<form class="space-y-4" @submit.prevent="save">
				<div class="field">
					<label class="label" for="category-name">Name</label>
					<input id="category-name" v-model="form.name" class="input" required placeholder="Groceries" />
				</div>

				<div v-if="!editing" class="field">
					<label class="label" for="category-parent">Nest under</label>
					<SelectField id="category-parent" v-model="form.parentId" :options="parentChoices" />
					<p class="mt-1 text-xs text-on-surface-variant">
						A subcategory inherits its parent's kind, and its spending counts towards the parent's budget. Nesting stops at one level.
					</p>
				</div>

				<div v-if="form.kind !== 'transfer' && !form.parentId" class="field">
					<label class="label" for="category-kind">Kind</label>
					<SelectField id="category-kind" v-model="form.kind" :options="KIND_CHOICES" />
				</div>

				<fieldset>
					<legend class="label">Colour</legend>
					<!-- The eight slots are a validated set: picking from them keeps
					     adjacent categories distinguishable, including under CVD. A
					     custom hex opts out of that guarantee, so it stays a deliberate
					     extra step rather than a ninth slot in the same row. -->
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
							required
							pattern="^#[0-9a-fA-F]{6}$"
							maxlength="7"
							placeholder="#64748b"
							aria-label="Custom colour hex value"
						/>
					</div>
				</fieldset>

				<p v-if="error" class="banner-error" role="alert">
					{{ error }}
				</p>

				<div class="flex justify-end gap-2 pt-2">
					<button type="button" class="btn-text" @click="dialogOpen = false">Cancel</button>
					<button type="submit" class="btn-primary">{{ editing ? 'Save changes' : 'Add category' }}</button>
				</div>
			</form>
		</ModalDialog>

		<FabButton label="Add category" @click="openCreate(sections[0])" />
	</div>
</template>
