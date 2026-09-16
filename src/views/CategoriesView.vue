<script setup lang="ts">
import ActionIcon from '@/components/ActionIcon.vue';
import EmptyState from '@/components/EmptyState.vue';
import ModalDialog from '@/components/ModalDialog.vue';
import { api, ApiError } from '@/lib/api';
import { nextColor, PALETTE } from '@/lib/palette';
import { useBudgetStore } from '@/stores/budget';
import { useLedgerStore } from '@/stores/ledger';
import type { Category, CategoryKind, CategoryScope } from '@/types';
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
	appliesTo: 'standard' as CategoryScope,
	color: PALETTE[0].light,
	parentId: '',
});

interface Section {
	key: string;
	title: string;
	description: string;
	kind: CategoryKind;
	appliesTo: CategoryScope;
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
			appliesTo: 'standard',
			families: familiesFor((c) => c.appliesTo === 'standard' && c.kind === 'expense'),
		},
		{
			key: 'income',
			title: 'Income',
			description: 'What you earn.',
			kind: 'income',
			appliesTo: 'standard',
			families: familiesFor((c) => c.appliesTo === 'standard' && c.kind === 'income'),
		},
		{
			key: 'cashflow',
			title: 'Cashflow',
			description: 'For transfers between your own accounts — investments, savings, debt repayment.',
			kind: 'expense',
			appliesTo: 'transfer',
			families: familiesFor((c) => c.appliesTo === 'transfer'),
		},
	];
});

/** Parents the new category could be nested under, matching the chosen section. */
const parentOptions = computed(() =>
	ledger.categories.filter(
		(category) => category.parentId === null && !category.archived && category.appliesTo === form.appliesTo && category.kind === form.kind,
	),
);

const archivedCount = computed(() => ledger.categories.filter((category) => category.archived).length);

/** True once the form's colour has strayed from the validated palette onto a hand-picked hex. */
const isCustomColor = computed(() => !PALETTE.some((slot) => slot.light === form.color));

function pickCustomColor(event: Event): void {
	form.color = (event.target as HTMLInputElement).value;
}

function openCreate(section: Section, parentId = ''): void {
	editing.value = null;
	error.value = null;

	const siblings = ledger.categories.filter(
		(category) => category.appliesTo === section.appliesTo && category.kind === section.kind,
	).length;
	Object.assign(form, {
		name: '',
		kind: section.kind,
		appliesTo: section.appliesTo,
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
		appliesTo: category.appliesTo,
		color: category.color,
		parentId: category.parentId ?? '',
	});
	dialogOpen.value = true;
}

async function save(): Promise<void> {
	// Kind and scope are only sent when creating a top-level category; a child
	// inherits both from its parent, and the API ignores what is sent anyway.
	const payload = editing.value
		? { name: form.name, kind: form.kind, color: form.color }
		: { name: form.name, kind: form.kind, appliesTo: form.appliesTo, color: form.color, parentId: form.parentId || null };

	try {
		if (editing.value) await api.updateCategory(editing.value.id, payload);
		else await api.createCategory(payload);

		dialogOpen.value = false;
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
	await Promise.all([ledger.refreshCategories(), budget.refresh()]);
}

onMounted(() => ledger.load());
</script>

<template>
	<div class="space-y-6">
		<header class="flex flex-wrap items-center justify-between gap-3">
			<h1 class="text-xl font-semibold tracking-tight text-slate-900 dark:text-white">Categories</h1>
			<button type="button" class="btn-primary" @click="openCreate(sections[0])">Add category</button>
		</header>

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
					<h2 class="text-sm font-semibold text-slate-900 dark:text-white">{{ section.title }}</h2>
					<p class="mt-0.5 text-xs text-slate-500 dark:text-slate-400">{{ section.description }}</p>
				</div>
				<button type="button" class="btn-ghost shrink-0 px-2 py-1 text-xs" @click="openCreate(section)">Add</button>
			</header>

			<p v-if="!section.families.length" class="py-4 text-center text-sm text-slate-500 dark:text-slate-400">
				No {{ section.title.toLowerCase() }} categories yet.
			</p>

			<ul v-else class="divide-y divide-slate-100 dark:divide-slate-800/60">
				<li v-for="family in section.families" :key="family.parent.id" class="py-1">
					<!-- Parent, then its own children indented beneath it. Nesting stops
					     here: a subcategory cannot have children of its own. -->
					<div class="group flex items-center gap-3 py-1.5">
						<span class="h-3 w-3 shrink-0 rounded-full" :style="{ backgroundColor: family.parent.color }" aria-hidden="true" />

						<span class="min-w-0 flex-1 truncate text-sm font-medium text-slate-900 dark:text-slate-100">
							{{ family.parent.name }}
							<span v-if="family.parent.archived" class="ml-1 text-xs font-normal text-slate-400 dark:text-slate-500">Archived</span>
						</span>

						<div class="row-actions">
							<button
								type="button"
								class="btn-ghost px-2 py-1 text-xs"
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

						<span class="min-w-0 flex-1 truncate text-sm text-slate-700 dark:text-slate-300">
							{{ child.name }}
							<span v-if="child.archived" class="ml-1 text-xs text-slate-400 dark:text-slate-500">Archived</span>
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

		<button v-if="archivedCount" type="button" class="btn-ghost text-sm" @click="showArchived = !showArchived">
			{{ showArchived ? 'Hide' : 'Show' }} {{ archivedCount }} archived
		</button>

		<ModalDialog :open="dialogOpen" :title="editing ? 'Edit category' : 'New category'" @close="dialogOpen = false">
			<form class="space-y-4" @submit.prevent="save">
				<div>
					<label class="label" for="category-name">Name</label>
					<input id="category-name" v-model="form.name" class="input" required placeholder="Groceries" />
				</div>

				<div v-if="!editing">
					<label class="label" for="category-parent">Nest under</label>
					<select id="category-parent" v-model="form.parentId" class="input">
						<option value="">Nothing — this is a top-level category</option>
						<option v-for="parent in parentOptions" :key="parent.id" :value="parent.id">{{ parent.name }}</option>
					</select>
					<p class="mt-1 text-xs text-slate-500 dark:text-slate-400">
						A subcategory inherits its parent's kind, and its spending counts towards the parent's budget. Nesting stops at one level.
					</p>
				</div>

				<div v-if="form.appliesTo === 'standard' && !form.parentId">
					<label class="label" for="category-kind">Kind</label>
					<select id="category-kind" v-model="form.kind" class="input">
						<option value="expense">Expense</option>
						<option value="income">Income</option>
					</select>
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
							class="h-8 w-8 rounded-full ring-offset-2 transition-transform hover:scale-110 dark:ring-offset-slate-900"
							:class="form.color === slot.light ? 'ring-2 ring-slate-900 dark:ring-white' : ''"
							:style="{ backgroundColor: slot.light }"
							:aria-label="slot.name"
							:aria-pressed="form.color === slot.light"
							@click="form.color = slot.light"
						/>

						<label
							class="relative grid h-8 w-8 cursor-pointer place-items-center rounded-full text-slate-400 ring-offset-2 transition-transform hover:scale-110 dark:text-slate-500 dark:ring-offset-slate-900"
							:class="
								isCustomColor
									? 'ring-2 ring-slate-900 dark:ring-white'
									: 'bg-[repeating-conic-gradient(#cbd5e1_0_25%,transparent_0_50%)] bg-[length:8px_8px] ring-1 ring-slate-300 dark:bg-[repeating-conic-gradient(#475569_0_25%,transparent_0_50%)] dark:ring-slate-600'
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
							class="input w-28 font-mono text-xs"
							required
							pattern="^#[0-9a-fA-F]{6}$"
							maxlength="7"
							placeholder="#64748b"
							aria-label="Custom colour hex value"
						/>
					</div>
				</fieldset>

				<p v-if="error" class="rounded-lg bg-rose-50 px-3 py-2 text-sm text-rose-700 dark:bg-rose-500/10 dark:text-rose-400" role="alert">
					{{ error }}
				</p>

				<div class="flex justify-end gap-2 pt-2">
					<button type="button" class="btn-secondary" @click="dialogOpen = false">Cancel</button>
					<button type="submit" class="btn-primary">{{ editing ? 'Save changes' : 'Add category' }}</button>
				</div>
			</form>
		</ModalDialog>
	</div>
</template>
