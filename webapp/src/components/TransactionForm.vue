<script setup lang="ts">
import SelectField from '@/components/SelectField.vue';
import { categoryOptions, namedOptions } from '@/lib/selectOptions';
import PayeeInput from '@/components/PayeeInput.vue';
import ConnectedButtonGroup from '@/components/ConnectedButtonGroup.vue';
import { currentTime, today } from '@/lib/dates';
import FieldSupport from '@/components/FieldSupport.vue';
import { parseMoney, toDecimalString } from '@/lib/money';
import { supportId, useFormValidation } from '@/lib/validation';
import { useLedgerStore } from '@/stores/ledger';
import type { Payee, Transaction } from '@/types';
import { computed, nextTick, reactive, ref, watch } from 'vue';

type Mode = 'expense' | 'income' | 'transfer';

const MODES: { value: Mode; label: string }[] = [
	{ value: 'expense', label: 'Expense' },
	{ value: 'income', label: 'Income' },
	{ value: 'transfer', label: 'Transfer' },
];

const props = defineProps<{ transaction?: Transaction | null; transferToAccountId?: string | null }>();
const emit = defineEmits<{ submit: [Record<string, unknown> & { mode: Mode }]; cancel: []; delete: [] }>();

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
	payee: () => (form.payee.trim().length > 120 ? 'Use 120 characters or fewer.' : null),
	amount: () => {
		if (!form.amount.trim()) return 'Enter an amount.';
		const minor = parseMoney(form.amount);
		if (minor === null) return 'Enter an amount as a number, such as 45.99.';
		return minor > 0 ? null : 'Enter an amount greater than zero.';
	},
	account: () => (ledger.activeAccounts.some((account) => account.id === form.accountId) ? null : 'Choose an account.'),
	'to-account': () => {
		if (form.mode !== 'transfer') return null;
		if (!ledger.activeAccounts.some((account) => account.id === form.toAccountId)) return 'Choose the account it goes to.';
		return form.toAccountId === form.accountId ? 'Choose two different accounts.' : null;
	},
	date: () => (form.occurredOn ? null : 'Enter a date.'),
	notes: () => (form.notes.trim().length > 500 ? 'Use 500 characters or fewer.' : null),
	tags: () => (form.tagIds.length > MAX_TAGS ? `A transaction can wear at most ${MAX_TAGS} tags.` : null),
});
const { error: fieldError, touch } = validation;
const describe = (id: string, hint = false): string | undefined => (fieldError(id) || hint ? supportId(id) : undefined);

/**
 * Which categories this mode may use. Transfers take the Cashflow tree, which
 * is what lets an investment contribution be budgeted; spending and income take
 * the standard ones. The API enforces the same split.
 */
const categoryGroups = computed(() => {
	if (form.mode === 'transfer') return ledger.groupForPicker(ledger.transferCategories);
	return ledger.groupForPicker(form.mode === 'income' ? ledger.incomeCategories : ledger.expenseCategories);
});

const accountChoices = computed(() => namedOptions(ledger.activeAccounts, { value: '', label: 'Select an account', disabled: true }));
const categoryChoices = computed(() => categoryOptions(categoryGroups.value, { value: '', label: 'Uncategorized' }));

/** Flattened, for checking whether the current selection is still valid. */
const selectable = computed(() => categoryGroups.value.flatMap((group) => [group.parent, ...group.children]));

/** Seeds the form from an existing transaction, or resets it for a new one. */
watch(
	() => props.transaction,
	(transaction) => {
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
			return;
		}

		const isTransfer = Boolean(transaction.transferId);
		// A time of day is an optional `THH:MM` suffix; a bare date has none.
		const hasTime = transaction.occurredOn.length > 10;

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
	<form class="space-y-4" novalidate @submit.prevent="submit" @input="validation.onInput">
		<ConnectedButtonGroup v-if="!isEditing" v-model="form.mode" label="Kind of transaction" :options="MODES" />

		<!-- First field: naming it is what makes the rest fill itself in. -->
		<div>
			<PayeeInput
				v-model="form.payee"
				:label="form.mode === 'transfer' ? 'Name' : 'Payee'"
				:placeholder="form.mode === 'transfer' ? 'Leave blank to name it From → To' : 'Who was paid'"
				:invalid="Boolean(fieldError('payee'))"
				:describedby="describe('payee')"
				@select="applyPayee"
				@blur="touch('payee')"
			/>
			<FieldSupport id="payee" :error="fieldError('payee')" />
		</div>

		<div class="field">
			<label class="label" for="amount">Amount</label>
			<input
				id="amount"
				v-model="form.amount"
				class="input tabular"
				inputmode="decimal"
				placeholder="0.00"
				required
				:aria-invalid="fieldError('amount') ? true : undefined"
				:aria-describedby="describe('amount')"
				@blur="touch('amount')"
			/>
			<FieldSupport id="amount" :error="fieldError('amount')" />
		</div>

		<!-- Two columns only for a transfer, which has a second account; otherwise the account has the row to itself. -->
		<div class="grid gap-4" :class="form.mode === 'transfer' ? 'sm:grid-cols-2' : ''">
			<div class="field">
				<label class="label" for="account">{{ form.mode === 'transfer' ? 'From account' : 'Account' }}</label>
				<SelectField
					id="account"
					v-model="form.accountId"
					:options="accountChoices"
					required
					:invalid="Boolean(fieldError('account'))"
					:describedby="describe('account')"
					@blur="touch('account')"
				/>
				<FieldSupport id="account" :error="fieldError('account')" />
			</div>

			<div v-if="form.mode === 'transfer'" class="field">
				<label class="label" for="to-account">To account</label>
				<SelectField
					id="to-account"
					v-model="form.toAccountId"
					:options="accountChoices"
					required
					:invalid="Boolean(fieldError('to-account'))"
					:describedby="describe('to-account')"
					@blur="touch('to-account')"
				/>
				<FieldSupport id="to-account" :error="fieldError('to-account')" />
			</div>
		</div>

		<div class="field">
			<label class="label" for="category">{{ form.mode === 'transfer' ? 'Cashflow category' : 'Category' }}</label>
			<!-- Parents and their children are both selectable, but only one at a
			     time: a transaction carries a single category, never both. -->
			<SelectField
				id="category"
				v-model="form.categoryId"
				:options="categoryChoices"
				:describedby="describe('category', form.mode === 'transfer')"
			/>
			<FieldSupport
				id="category"
				:hint="
					form.mode === 'transfer'
						? 'Optional. Categorising a transfer lets you budget it — an investment contribution is a movement, not spending.'
						: undefined
				"
			/>
		</div>

		<div class="grid gap-4 sm:grid-cols-2">
			<div class="field">
				<label class="label" for="date">Date</label>
				<input
					id="date"
					v-model="form.occurredOn"
					type="date"
					class="input"
					required
					:aria-invalid="fieldError('date') ? true : undefined"
					:aria-describedby="describe('date')"
					@blur="touch('date')"
				/>
				<FieldSupport id="date" :error="fieldError('date')" />
			</div>

			<div class="field">
				<label class="label" for="time">Time</label>
				<!-- A subscription posts at 00:00 UTC, so there is no time here for anyone to set. -->
				<input id="time" v-model="form.occurredTime" type="time" class="input" :disabled="isAutomated" />
			</div>
		</div>

		<p v-if="isAutomated" class="-mt-2 text-xs text-on-surface-variant">
			Posted automatically by a subscription at 00:00 UTC, so its time can't be changed. You can still edit or delete it.
		</p>

		<div class="field">
			<label class="label" for="notes">Notes</label>
			<input
				id="notes"
				v-model="form.notes"
				class="input"
				placeholder="Optional"
				:aria-invalid="fieldError('notes') ? true : undefined"
				:aria-describedby="describe('notes')"
				@blur="touch('notes')"
			/>
			<FieldSupport id="notes" :error="fieldError('notes')" />
		</div>

		<!-- Tags, unlike the category, are any number of labels. They change no figure. -->
		<fieldset v-if="ledger.tags.length" id="tags" tabindex="-1" class="min-w-0" :aria-describedby="describe('tags')">
			<legend class="label">Tags</legend>
			<div class="flex flex-wrap gap-2">
				<button
					v-for="tag in ledger.tags"
					:key="tag.id"
					type="button"
					class="chip"
					:aria-pressed="form.tagIds.includes(tag.id)"
					@click="toggleTag(tag.id)"
				>
					<span class="size-2 shrink-0 rounded-full" :style="{ backgroundColor: tag.color }" aria-hidden="true" />
					<span class="truncate">{{ tag.name }}</span>
				</button>
			</div>
			<FieldSupport id="tags" :error="fieldError('tags')" class="!px-0" />
		</fieldset>

		<!-- Only for a failure that is no one field's — the save itself. What is wrong with a field is said beneath the field. -->
		<p v-if="error" class="banner-error" role="alert">
			{{ error }}
		</p>

		<div class="flex justify-end gap-2 pt-2">
			<button v-if="isEditing" type="button" class="btn-danger mr-auto" @click="emit('delete')">Delete</button>
			<button type="button" class="btn-text" @click="emit('cancel')">Cancel</button>
			<button type="submit" class="btn-primary" :disabled="!validation.isValid.value">
				{{ isEditing ? 'Save changes' : 'Add transaction' }}
			</button>
		</div>
	</form>
</template>
