<script setup lang="ts">
import FormDialog from '@/components/FormDialog.vue';
import PickerField from '@/components/PickerField.vue';
import SelectField from '@/components/SelectField.vue';
import TextField from '@/components/TextField.vue';
import { categoryOptions, namedOptions } from '@/lib/selectOptions';
import PayeeInput from '@/components/PayeeInput.vue';
import ConnectedButtonGroup from '@/components/ConnectedButtonGroup.vue';
import { currentTime, today } from '@/lib/dates';
import FieldSupport from '@/components/FieldSupport.vue';
import { parseMoney, toDecimalString } from '@/lib/money';
import { useHarmonised } from '@/lib/harmonise';
import { supportId, useFormValidation } from '@/lib/validation';
import { useLedgerStore } from '@/stores/ledger';
import type { Payee, Transaction } from '@/types';
import { computed, nextTick, reactive, ref, watch } from 'vue';
import { t } from '@/i18n';

type Mode = 'expense' | 'income' | 'transfer';

const MODES: { value: Mode; label: string }[] = [
	{ value: 'expense', label: t('common.expense') },
	{ value: 'income', label: t('common.income') },
	{ value: 'transfer', label: t('common.transfer') },
];

/**
 * The transaction form, as a dialog of its own (`TransactionForm.kt`): one form in three shapes — an expense,
 * income, or a transfer between two of the user's accounts — opened empty for a new entry or seeded from the
 * row being edited. It takes the screen over on a phone and is an ordinary dialog beside a rail.
 */
const props = defineProps<{ open: boolean; transaction?: Transaction | null; transferToAccountId?: string | null }>();
const emit = defineEmits<{ submit: [Record<string, unknown> & { mode: Mode }]; close: [] }>();

const ledger = useLedgerStore();
const isEditing = computed(() => Boolean(props.transaction));
/** Posted by a subscription's cron at 00:00 UTC — its time of day is not the user's to set. */
const isAutomated = computed(() => props.transaction?.automated === true);

const form = reactive({
	mode: 'expense' as Mode,
	accountId: '',
	toAccountId: '',
	categoryId: '',
	amount: '',
	occurredOn: today(),
	occurredTime: currentTime(),
	payee: '',
	notes: '',
	tagIds: [] as string[],
});

/** A failure that belongs to no one field — the save itself went wrong. Each field's own problem is drawn beside it. */
const error = ref<string | null>(null);

/** The most tags one transaction wears; the API refuses more. */
const MAX_TAGS = 10;

const validation = useFormValidation({
	payee: () => (form.payee.trim().length > 120 ? t('transactions.form.payeeTooLong', { max: 120 }) : null),
	amount: () => {
		if (!form.amount.trim()) return t('transactions.form.amountRequired');
		const minor = parseMoney(form.amount);
		if (minor === null) return t('transactions.form.amountNumber');
		return minor > 0 ? null : t('transactions.form.amountPositive');
	},
	account: () => (ledger.activeAccounts.some((account) => account.id === form.accountId) ? null : t('transactions.form.chooseAccount')),
	'to-account': () => {
		if (form.mode !== 'transfer') return null;
		if (!ledger.activeAccounts.some((account) => account.id === form.toAccountId)) return t('transactions.form.chooseToAccount');
		return form.toAccountId === form.accountId ? t('transactions.form.differentAccounts') : null;
	},
	date: () => (form.occurredOn ? null : t('transactions.form.dateRequired')),
	notes: () => (form.notes.trim().length > 500 ? t('transactions.form.notesTooLong', { max: 500 }) : null),
	tags: () => (form.tagIds.length > MAX_TAGS ? t('transactions.form.tooManyTags', { max: MAX_TAGS }) : null),
});
const { error: fieldError, touch } = validation;
const describe = (id: string): string | undefined => (fieldError(id) ? supportId(id) : undefined);
const harmonised = useHarmonised();

/** What the form opened with, so that closing it can tell an entry from an untouched form. */
const opened = ref('');
const dirty = computed(() => JSON.stringify(form) !== opened.value);

/**
 * Which categories this mode may use. Transfers take the Cashflow tree, which
 * is what lets an investment contribution be budgeted; spending and income take
 * the standard ones. The API enforces the same split.
 */
const categoryGroups = computed(() => {
	if (form.mode === 'transfer') return ledger.groupForPicker(ledger.transferCategories);
	return ledger.groupForPicker(form.mode === 'income' ? ledger.incomeCategories : ledger.expenseCategories);
});

const accountChoices = computed(() => namedOptions(ledger.activeAccounts));
const categoryChoices = computed(() => categoryOptions(categoryGroups.value, { value: '', label: t('common.uncategorized') }));

/** Flattened, for checking whether the current selection is still valid. */
const selectable = computed(() => categoryGroups.value.flatMap((group) => [group.parent, ...group.children]));

/** Seeds the form from the transaction being edited, or resets it for a new one, each time it opens. */
function seed(): void {
	const transaction = props.transaction;
	error.value = null;
	validation.reset();

	if (!transaction) {
		// The user's chosen default, when it is still an active account;
		// otherwise whichever active account happens to sort first.
		const preferred = ledger.activeAccounts.find((account) => account.id === ledger.defaultAccountId);

		Object.assign(form, {
			mode: 'expense',
			accountId: preferred?.id ?? ledger.activeAccounts[0]?.id ?? '',
			toAccountId: '',
			categoryId: '',
			amount: '',
			occurredOn: today(),
			occurredTime: currentTime(),
			payee: '',
			notes: '',
			tagIds: [],
		});
	} else {
		const isTransfer = Boolean(transaction.transferId);
		// A time of day is an optional `THH:MM` suffix; a bare date has none. An automated row never has one.
		const hasTime = transaction.occurredOn.length > 10 && !transaction.automated;

		Object.assign(form, {
			mode: isTransfer ? 'transfer' : transaction.amount >= 0 ? 'income' : 'expense',
			accountId: transaction.accountId,
			toAccountId: isTransfer ? (props.transferToAccountId ?? '') : '',
			categoryId: transaction.categoryId ?? '',
			amount: toDecimalString(Math.abs(transaction.amount)),
			occurredOn: transaction.occurredOn.slice(0, 10),
			occurredTime: hasTime ? transaction.occurredOn.slice(11, 16) : '',
			payee: transaction.payee,
			notes: transaction.notes,
			tagIds: transaction.tags.map((tag) => tag.id),
		});
	}

	opened.value = JSON.stringify(form);
}

watch(
	() => props.open,
	(open) => {
		if (open) seed();
	},
	{ immediate: true },
);

// Switching mode invalidates a category belonging to another set.
watch(
	() => form.mode,
	() => {
		if (!selectable.value.some((category) => category.id === form.categoryId)) form.categoryId = '';
	},
);

/**
 * Applies a remembered payee: the mode it was last used in, the accounts, the
 * category and the notes. Switching mode first matters — the category picker
 * depends on it, and a category from another set would be dropped.
 */
function toggleTag(id: string): void {
	validation.touch('tags');
	form.tagIds = form.tagIds.includes(id) ? form.tagIds.filter((tagId) => tagId !== id) : [...form.tagIds, id];
}

async function applyPayee(entry: Payee): Promise<void> {
	// Switch mode first and let its watcher settle. That watcher clears a
	// category belonging to another set, and it runs asynchronously — setting
	// the category before it fires would see it wiped again straight after.
	if (!isEditing.value && form.mode !== entry.kind) {
		form.mode = entry.kind;
		await nextTick();
	}

	if (entry.accountId) form.accountId = entry.accountId;
	if (entry.kind === 'transfer' && entry.toAccountId) form.toAccountId = entry.toAccountId;
	if (entry.categoryId) form.categoryId = entry.categoryId;
	// Only fill notes that are still empty, so a suggestion never overwrites
	// something already typed.
	if (entry.notes && !form.notes) form.notes = entry.notes;
}

async function submit(): Promise<void> {
	error.value = null;

	// Every field's problem is shown at once and focus lands on the first, so nothing is left to be refused later.
	if (!validation.isValid.value) return;
	const minor = parseMoney(form.amount) as number;

	// A time is optional; omitting it leaves the date to stand on its own. An
	// automated transaction never has one, and the API refuses it if it does.
	const occurredOn = form.occurredTime && !isAutomated.value ? `${form.occurredOn}T${form.occurredTime}` : form.occurredOn;

	// A tag deleted since the form opened would be refused by the API; drop it here instead.
	const tagIds = form.tagIds.filter((id) => ledger.tags.some((tag) => tag.id === id));

	// Direction lives in the sign, so the form's mode is what decides it.
	const payload =
		form.mode === 'transfer'
			? {
					mode: form.mode,
					fromAccountId: form.accountId,
					toAccountId: form.toAccountId,
					amount: minor,
					occurredOn,
					notes: form.notes,
					categoryId: form.categoryId || null,
					payee: form.payee,
					tagIds,
				}
			: {
					mode: form.mode,
					accountId: form.accountId,
					categoryId: form.categoryId || null,
					amount: form.mode === 'expense' ? -minor : minor,
					occurredOn,
					payee: form.payee,
					notes: form.notes,
					tagIds,
				};

	emit('submit', payload);
}

defineExpose({
	fail: (message: string) => {
		error.value = message;
	},
});
</script>

<template>
	<FormDialog
		:open="open"
		:title="isEditing ? t('transactions.editTransaction') : t('transactions.newTransaction')"
		:save-enabled="validation.isValid.value"
		:dirty="dirty"
		@close="emit('close')"
		@save="submit"
	>
		<div class="contents" @input="validation.onInput">
			<ConnectedButtonGroup v-if="!isEditing" v-model="form.mode" :label="t('transactions.form.kindOf')" :options="MODES" />

			<!-- First field: naming it is what makes the rest fill itself in. -->
			<PayeeInput
				v-model="form.payee"
				:label="form.mode === 'transfer' ? t('transactions.form.name') : t('transactions.form.payee')"
				:placeholder="form.mode === 'transfer' ? t('transactions.form.transferPlaceholder') : t('transactions.form.payeePlaceholder')"
				:error="fieldError('payee')"
				@select="applyPayee"
				@blur="touch('payee')"
			/>

			<TextField
				id="amount"
				v-model="form.amount"
				:label="t('common.amount')"
				placeholder="0.00"
				inputmode="decimal"
				:error="fieldError('amount')"
				@blur="touch('amount')"
			/>

			<div>
				<SelectField
					id="account"
					v-model="form.accountId"
					:label="form.mode === 'transfer' ? t('transactions.form.fromAccount') : t('common.account')"
					:options="accountChoices"
					:invalid="Boolean(fieldError('account'))"
					:describedby="describe('account')"
					@blur="touch('account')"
				/>
				<FieldSupport id="account" :error="fieldError('account')" />
			</div>

			<div v-if="form.mode === 'transfer'">
				<SelectField
					id="to-account"
					v-model="form.toAccountId"
					:label="t('transactions.form.toAccount')"
					:options="accountChoices"
					:invalid="Boolean(fieldError('to-account'))"
					:describedby="describe('to-account')"
					@blur="touch('to-account')"
				/>
				<FieldSupport id="to-account" :error="fieldError('to-account')" />
			</div>

			<!-- Parents and their children are both selectable, but only one at a
			     time: a transaction carries a single category, never both. -->
			<SelectField
				id="category"
				v-model="form.categoryId"
				:label="form.mode === 'transfer' ? t('transactions.form.cashflowCategory') : t('common.category')"
				:options="categoryChoices"
			/>

			<div class="flex gap-3">
				<div class="min-w-0 flex-1">
					<PickerField
						id="date"
						v-model="form.occurredOn"
						:label="t('common.date')"
						type="date"
						:invalid="Boolean(fieldError('date'))"
						:describedby="describe('date')"
						@blur="touch('date')"
					/>
					<FieldSupport id="date" :error="fieldError('date')" />
				</div>

				<!-- A subscription posts at 00:00 UTC, so there is no time here for anyone to set. -->
				<PickerField
					id="time"
					v-model="form.occurredTime"
					class="min-w-0 flex-1"
					:label="t('common.time')"
					type="time"
					:placeholder="t('common.optional')"
					:disabled="isAutomated"
					:display="isAutomated ? '00:00 UTC' : undefined"
				/>
			</div>

			<p v-if="isAutomated" class="type-body-small text-on-surface">
				{{ t('transactions.form.automated') }}
			</p>

			<TextField
				id="notes"
				v-model="form.notes"
				:label="t('common.notes')"
				:placeholder="t('common.optional')"
				:error="fieldError('notes')"
				@blur="touch('notes')"
			/>

			<!-- Tags, unlike the category, are any number of labels. They change no figure. -->
			<fieldset v-if="ledger.tags.length" id="tags" tabindex="-1" class="min-w-0" :aria-describedby="describe('tags')">
				<legend class="type-label-medium pb-3.5 text-on-surface">{{ t('common.tags') }}</legend>
				<div class="flex flex-wrap gap-x-2 gap-y-1">
					<button
						v-for="tag in ledger.tags"
						:key="tag.id"
						type="button"
						class="chip pl-2"
						:aria-pressed="form.tagIds.includes(tag.id)"
						@click="toggleTag(tag.id)"
					>
						<span class="size-2 shrink-0 rounded-full" :style="{ backgroundColor: harmonised(tag.color) }" aria-hidden="true" />
						<span class="truncate">{{ tag.name }}</span>
					</button>
				</div>
				<FieldSupport id="tags" :error="fieldError('tags')" class="!px-0" />
			</fieldset>

			<!-- Only for a failure that is no one field's — the save itself. What is wrong with a field is said beneath the field. -->
			<p v-if="error" class="type-body-small text-error" role="alert">{{ error }}</p>
		</div>
	</FormDialog>
</template>
