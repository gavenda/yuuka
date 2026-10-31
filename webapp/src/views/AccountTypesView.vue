<script setup lang="ts">
import ActionIcon from '@/components/ActionIcon.vue';
import AppIcon from '@/components/AppIcon.vue';
import AlertDialog from '@/components/AlertDialog.vue';
import FabButton from '@/components/FabButton.vue';
import SwipeReveal from '@/components/SwipeReveal.vue';
import TextField from '@/components/TextField.vue';
import { api, ApiError } from '@/lib/api';
import { DRAG_INDICATOR } from '@/lib/icons';
import { showSnackbar } from '@/lib/snackbar';
import { showSaved } from '@/lib/sync';
import { nameProblem, useFormValidation } from '@/lib/validation';
import { useLedgerStore } from '@/stores/ledger';
import type { AccountType } from '@/types';
import { computed, onBeforeUnmount, onMounted, ref } from 'vue';
import { t } from '@/i18n';

/**
 * The account types, on a screen of their own reached from Settings (`AccountTypesScreen`): a list like any
 * other in the app. Pressing a type renames it, swiping it shows archive and delete, and the screen's
 * leading action asks for a new one by name in a basic dialog — the same one a rename opens. An active type is
 * moved by the handle at its end — dragged, or with the arrow keys — and the order is saved the moment it is
 * dropped; archived types are not ordered, and stay after the active ones.
 */
const ledger = useLedgerStore();

/** Whether the name dialog is up, and the type it renames — none while it is adding one. */
const nameOpen = ref(false);
const renaming = ref<AccountType | null>(null);
const name = ref('');
/** A failure that belongs to no one field — the change itself went wrong — said where it was asked for. */
const nameError = ref<string | null>(null);
const listError = ref<string | null>(null);
const busy = ref(false);
const showArchived = ref(false);

/** Types are unique by name, so a name is checked against the others (a rename may keep its own). */
const validation = useFormValidation({
	'account-type-name': () =>
		nameProblem(
			name.value,
			(candidate) => ledger.accountTypes.some((type) => type.id !== renaming.value?.id && type.name === candidate),
			t('accounts.types.taken'),
		),
});

/** An order dropped but not yet back from the local copy, held on screen so the rows do not jump back meanwhile. */
const pending = ref<string[] | null>(null);
const active = computed(() => {
	const types = ledger.accountTypes.filter((type) => !type.archived);
	const order = pending.value;
	if (!order) return types;
	return [...types].sort((a, b) => order.indexOf(a.id) - order.indexOf(b.id));
});
const archivedTypes = computed(() => ledger.accountTypes.filter((type) => type.archived));
const visible = computed(() => [...active.value, ...(showArchived.value ? archivedTypes.value : [])]);
const archivedCount = computed(() => archivedTypes.value.length);

/** The rows' gap: `.group-rows`' 2px, so a row's pitch is its height and this. */
const ROW_GAP = 2;

/**
 * A drag in progress: the row being moved, where it started, how far the pointer has gone, the distance from
 * one row to the next, and the place it would land in if dropped now. The list keeps its order while it runs —
 * the dragged row follows the pointer and the rows it passes slide aside — and is reordered on the drop.
 */
const drag = ref<{ id: string; pointerId: number; from: number; startY: number; dy: number; pitch: number; to: number } | null>(null);

function shiftOf(index: number): number {
	const current = drag.value;
	if (!current) return 0;
	if (index === current.from) return current.dy;
	if (current.from < index && index <= current.to) return -current.pitch;
	if (current.to <= index && index < current.from) return current.pitch;
	return 0;
}

/** Writes the active types in [ids]' order, unless it is the order they are already in. */
async function saveOrder(ids: string[]): Promise<void> {
	if (ids.join() === active.value.map((type) => type.id).join()) return;
	listError.value = null;
	pending.value = ids;
	try {
		await api.reorderAccountTypes(ids);
		await showSaved();
	} catch (caught) {
		listError.value = caught instanceof ApiError ? caught.message : t('common.errorGeneric');
	} finally {
		pending.value = null;
	}
}

function moved(from: number, to: number): string[] {
	const ids = active.value.map((type) => type.id);
	ids.splice(to, 0, ...ids.splice(from, 1));
	return ids;
}

function onHandleDown(event: PointerEvent, index: number): void {
	if (event.button !== 0 || drag.value) return;
	const row = (event.currentTarget as HTMLElement).closest('li');
	const pitch = (row?.offsetHeight ?? 56) + ROW_GAP;
	drag.value = { id: active.value[index].id, pointerId: event.pointerId, from: index, startY: event.clientY, dy: 0, pitch, to: index };
	window.addEventListener('pointermove', onHandleMove);
	window.addEventListener('pointerup', onHandleUp);
	window.addEventListener('pointercancel', onHandleUp);
}

function onHandleMove(event: PointerEvent): void {
	const current = drag.value;
	if (!current || event.pointerId !== current.pointerId) return;
	// Kept within the active rows, as the list's first and last row are as far as it can go.
	const dy = Math.min(
		(active.value.length - 1 - current.from) * current.pitch,
		Math.max(-current.from * current.pitch, event.clientY - current.startY),
	);
	drag.value = { ...current, dy, to: current.from + Math.round(dy / current.pitch) };
}

function stopDragging(): void {
	window.removeEventListener('pointermove', onHandleMove);
	window.removeEventListener('pointerup', onHandleUp);
	window.removeEventListener('pointercancel', onHandleUp);
}

function onHandleUp(event: PointerEvent): void {
	const current = drag.value;
	if (!current || event.pointerId !== current.pointerId) return;
	stopDragging();
	const ids = moved(current.from, current.to);
	drag.value = null;
	void saveOrder(ids);
}

/** The keyboard's way to move a type, one place at a time. */
function onHandleKey(event: KeyboardEvent, index: number): void {
	const to = event.key === 'ArrowUp' ? index - 1 : event.key === 'ArrowDown' ? index + 1 : null;
	if (to === null || to < 0 || to >= active.value.length) return;
	event.preventDefault();
	void saveOrder(moved(index, to));
}

onBeforeUnmount(stopDragging);

const countLabel = (type: AccountType) =>
	type.accountCount ? t('accounts.types.accountCount', type.accountCount) : t('accounts.types.unused');

/** Every mutation shows the local copy again, since accounts display the type name. */
async function run(action: () => Promise<unknown>, done: string, failed: typeof nameError): Promise<boolean> {
	nameError.value = null;
	listError.value = null;
	busy.value = true;

	try {
		await action();
		showSnackbar(done);
		await showSaved();
		return true;
	} catch (caught) {
		failed.value = caught instanceof ApiError ? caught.message : t('common.errorGeneric');
		return false;
	} finally {
		busy.value = false;
	}
}

function openName(type: AccountType | null): void {
	renaming.value = type;
	name.value = type?.name ?? '';
	nameError.value = null;
	validation.reset();
	nameOpen.value = true;
}

async function saveName(): Promise<void> {
	if (busy.value || !validation.isValid.value) return;
	const trimmed = name.value.trim();
	const type = renaming.value;

	// Put new types after the existing ones rather than at the top.
	const sortOrder = ledger.accountTypes.reduce((highest, existing) => Math.max(highest, existing.sortOrder), -1) + 1;
	const saved = type
		? await run(() => api.updateAccountType(type.id, { name: trimmed }), t('accounts.types.renamed'), nameError)
		: await run(() => api.createAccountType({ name: trimmed, sortOrder }), t('accounts.types.added'), nameError);
	if (saved) nameOpen.value = false;
}

const toggleArchived = (type: AccountType) =>
	run(
		() => api.updateAccountType(type.id, { archived: !type.archived }),
		type.archived ? t('accounts.types.restored') : t('accounts.types.archived'),
		listError,
	);

const remove = (type: AccountType) => run(() => api.deleteAccountType(type.id), t('accounts.types.deleted'), listError);

onMounted(() => ledger.load());
</script>

<template>
	<div class="flex flex-col gap-4 px-4 pt-4 pb-24">
		<!-- One row per type, its accounts counted at the end, drawn as one connected block. -->
		<ul v-if="visible.length" class="group-rows">
			<!-- The dragged row follows the pointer above the rest, and the rows it passes slide aside. -->
			<li
				v-for="(type, index) in visible"
				:key="type.id"
				class="relative"
				:class="[
					drag?.id === type.id ? 'z-10 shadow-elevation-2' : '',
					drag && drag.id !== type.id ? 'transition-transform duration-200 ease-standard' : '',
				]"
				:style="drag ? { transform: `translateY(${shiftOf(index)}px)` } : undefined"
			>
				<SwipeReveal>
					<template #actions>
						<ActionIcon
							:icon="type.archived ? 'restore' : 'archive'"
							:label="t(type.archived ? 'common.restore' : 'common.archive', { name: type.name })"
							:disabled="busy"
							@click="toggleArchived(type)"
						/>
						<!-- A type still in use cannot be deleted: the API reports how many accounts hold it. -->
						<ActionIcon
							icon="delete"
							:label="type.accountCount ? t('accounts.types.inUse', { name: type.name }) : t('common.delete', { name: type.name })"
							danger
							:disabled="busy || type.accountCount > 0"
							@click="remove(type)"
						/>
					</template>

					<!-- The row wears the state layer and the rename button's ::before is stretched over it, so the whole row
					     answers to the pointer; the handle at its end stands above that, and is the only place a drag starts. -->
					<div class="group-row state-layer flex min-h-14 items-center pr-2" :class="type.archived ? 'pr-4' : ''">
						<button
							type="button"
							class="focus-ring flex min-w-0 flex-1 cursor-pointer items-center gap-3 self-stretch pl-4 text-left before:absolute before:inset-0"
							:aria-label="t('common.rename', { name: type.name })"
							@click="openName(type)"
						>
							<span class="type-body-large min-w-0 flex-1 truncate">{{
								type.archived ? t('common.archivedName', { name: type.name }) : type.name
							}}</span>
							<span class="type-body-small shrink-0 text-on-surface-variant">{{ countLabel(type) }}</span>
						</button>
						<button
							v-if="!type.archived"
							type="button"
							class="btn-icon relative ml-1 size-12 cursor-grab touch-none text-on-surface-variant"
							:class="drag?.id === type.id ? 'cursor-grabbing' : ''"
							:aria-label="t('accounts.types.reorder', { name: type.name })"
							:title="t('accounts.types.reorder', { name: type.name })"
							@pointerdown.stop="onHandleDown($event, index)"
							@keydown="onHandleKey($event, index)"
						>
							<AppIcon :icon="DRAG_INDICATOR" />
						</button>
					</div>
				</SwipeReveal>
			</li>
		</ul>

		<p v-if="listError" class="type-body-small text-error" role="alert">{{ listError }}</p>

		<div v-if="archivedCount > 0">
			<button type="button" class="btn-text" @click="showArchived = !showArchived">
				{{ t(showArchived ? 'common.hideArchived' : 'common.showArchived', { count: archivedCount }) }}
			</button>
		</div>

		<!-- A type is only its name, so adding one and renaming one are the same single field in a basic dialog. -->
		<AlertDialog
			:open="nameOpen"
			:title="renaming ? t('accounts.types.renameTitle') : t('accounts.types.newTitle')"
			role="dialog"
			@close="busy || (nameOpen = false)"
		>
			<form id="account-type-form" class="flex flex-col gap-3 sm:w-80" novalidate @submit.prevent="saveName" @input="validation.onInput">
				<TextField
					id="account-type-name"
					v-model="name"
					:label="t('common.name')"
					autofocus
					:disabled="busy"
					:error="validation.error('account-type-name')"
					@blur="validation.touch('account-type-name')"
				/>
				<p v-if="nameError" class="type-body-small text-error" role="alert">{{ nameError }}</p>
			</form>

			<template #actions>
				<button type="button" class="btn-text" :disabled="busy" @click="nameOpen = false">{{ t('common.cancel') }}</button>
				<button type="submit" form="account-type-form" class="btn-primary" :disabled="busy || !validation.isValid.value">
					{{ renaming ? t('common.save') : t('common.add') }}
				</button>
			</template>
		</AlertDialog>

		<FabButton :label="t('accounts.types.newType')" @click="openName(null)" />
	</div>
</template>
