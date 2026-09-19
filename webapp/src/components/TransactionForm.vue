<script setup lang="ts">
import PayeeInput from '@/components/PayeeInput.vue';
import { currentTime, today } from '@/lib/dates';
import { ApiError } from '@/lib/api';
import { parseMoney, toDecimalString } from '@/lib/money';
import { useLedgerStore } from '@/stores/ledger';
import type { Payee, Transaction } from '@/types';
import { computed, nextTick, reactive, ref, watch } from 'vue';

type Mode = 'expense' | 'income' | 'transfer';

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
});

const error = ref<string | null>(null);
const submitting = ref(false);

/**
 * Which categories this mode may use. Transfers take the Cashflow tree, which
 * is what lets an investment contribution be budgeted; spending and income take
 * the standard ones. The API enforces the same split.
 */
const categoryGroups = computed(() => {
	if (form.mode === 'transfer') return ledger.groupForPicker(ledger.transferCategories);
	return ledger.groupForPicker(form.mode === 'income' ? ledger.incomeCategories : ledger.expenseCategories);
});

/** Flattened, for checking whether the current selection is still valid. */
const selectable = computed(() => categoryGroups.value.flatMap((group) => [group.parent, ...group.children]));

/** Seeds the form from an existing transaction, or resets it for a new one. */
watch(
	() => props.transaction,
	(transaction) => {
		error.value = null;

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

	const minor = parseMoney(form.amount);
	if (minor === null || minor <= 0) {
		error.value = 'Enter an amount greater than zero.';
		return;
	}

	if (form.mode === 'transfer' && form.accountId === form.toAccountId) {
		error.value = 'Choose two different accounts.';
		return;
	}

	submitting.value = true;

	try {
		// A time is optional; omitting it leaves the date to stand on its own. An
		// automated transaction never has one, and the API refuses it if it does.
		const occurredOn = form.occurredTime && !isAutomated.value ? `${form.occurredOn}T${form.occurredTime}` : form.occurredOn;

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
					}
				: {
						mode: form.mode,
						accountId: form.accountId,
						categoryId: form.categoryId || null,
						amount: form.mode === 'expense' ? -minor : minor,
						occurredOn,
						payee: form.payee,
						notes: form.notes,
					};

		emit('submit', payload);
	} catch (caught) {
		error.value = caught instanceof ApiError ? caught.message : 'Something went wrong.';
	} finally {
		submitting.value = false;
	}
}

defineExpose({
	fail: (message: string) => {
		error.value = message;
	},
});
</script>

<template>
	<form class="space-y-4" @submit.prevent="submit">
		<div v-if="!isEditing" class="grid grid-cols-3 gap-1 rounded-lg bg-slate-100 p-1 dark:bg-slate-800">
			<button
				v-for="option in ['expense', 'income', 'transfer'] as Mode[]"
				:key="option"
				type="button"
				class="rounded-md px-3 py-1.5 text-sm font-medium capitalize transition-colors"
				:class="
					form.mode === option
						? 'bg-white text-slate-900 shadow-sm dark:bg-slate-900 dark:text-white'
						: 'text-slate-600 hover:text-slate-900 dark:text-slate-400 dark:hover:text-white'
				"
				@click="form.mode = option"
			>
				{{ option }}
			</button>
		</div>

		<!-- First field: naming it is what makes the rest fill itself in. -->
		<PayeeInput
			v-model="form.payee"
			:label="form.mode === 'transfer' ? 'Name' : 'Payee'"
			:placeholder="form.mode === 'transfer' ? 'Leave blank to name it From → To' : 'Who was paid'"
			@select="applyPayee"
		/>

		<div>
			<label class="label" for="amount">Amount</label>
			<input id="amount" v-model="form.amount" class="input tabular" inputmode="decimal" placeholder="0.00" required />
		</div>

		<div class="grid gap-4 sm:grid-cols-2">
			<div>
				<label class="label" for="account">{{ form.mode === 'transfer' ? 'From account' : 'Account' }}</label>
				<select id="account" v-model="form.accountId" class="input" required>
					<option value="" disabled>Select an account</option>
					<option v-for="account in ledger.activeAccounts" :key="account.id" :value="account.id">{{ account.name }}</option>
				</select>
			</div>

			<div v-if="form.mode === 'transfer'">
				<label class="label" for="to-account">To account</label>
				<select id="to-account" v-model="form.toAccountId" class="input" required>
					<option value="" disabled>Select an account</option>
					<option v-for="account in ledger.activeAccounts" :key="account.id" :value="account.id">{{ account.name }}</option>
				</select>
			</div>
		</div>

		<div>
			<label class="label" for="category">{{ form.mode === 'transfer' ? 'Cashflow category' : 'Category' }}</label>
			<!-- Parents and their children are both selectable, but only one at a
			     time: a transaction carries a single category, never both. -->
			<select id="category" v-model="form.categoryId" class="input">
				<option value="">Uncategorized</option>
				<template v-for="group in categoryGroups" :key="group.parent.id">
					<option :value="group.parent.id">{{ group.parent.name }}</option>
					<option v-for="child in group.children" :key="child.id" :value="child.id">&nbsp;&nbsp;&nbsp;{{ child.name }}</option>
				</template>
			</select>
			<p v-if="form.mode === 'transfer'" class="mt-1 text-xs text-slate-500 dark:text-slate-400">
				Optional. Categorising a transfer lets you budget it — an investment contribution is a movement, not spending.
			</p>
		</div>

		<div class="grid gap-4 sm:grid-cols-2">
			<div>
				<label class="label" for="date">Date</label>
				<input id="date" v-model="form.occurredOn" type="date" class="input" required />
			</div>

			<div>
				<label class="label" for="time">Time</label>
				<!-- A subscription posts at 00:00 UTC, so there is no time here for anyone to set. -->
				<input id="time" v-model="form.occurredTime" type="time" class="input" :disabled="isAutomated" />
			</div>
		</div>

		<p v-if="isAutomated" class="-mt-2 text-xs text-slate-500 dark:text-slate-400">
			Posted automatically by a subscription at 00:00 UTC, so its time can't be changed. You can still edit or delete it.
		</p>

		<div>
			<label class="label" for="notes">Notes</label>
			<input id="notes" v-model="form.notes" class="input" placeholder="Optional" />
		</div>

		<p v-if="error" class="rounded-lg bg-rose-50 px-3 py-2 text-sm text-rose-700 dark:bg-rose-500/10 dark:text-rose-400" role="alert">
			{{ error }}
		</p>

		<div class="flex justify-end gap-2 pt-2">
			<button v-if="isEditing" type="button" class="btn-danger mr-auto" @click="emit('delete')">Delete</button>
			<button type="button" class="btn-secondary" @click="emit('cancel')">Cancel</button>
			<button type="submit" class="btn-primary" :disabled="submitting">
				{{ isEditing ? 'Save changes' : 'Add transaction' }}
			</button>
		</div>
	</form>
</template>
