<script setup lang="ts">
import { Chip } from 'tailwind-material3';
import FormDialog from '@/components/FormDialog.vue';
import YuukaSays from '@/components/YuukaSays.vue';
import PickerField from '@/components/PickerField.vue';
import SelectField from '@/components/SelectField.vue';
import TextField from '@/components/TextField.vue';
import { categoryOptions, namedOptions } from '@/lib/selectOptions';
import PayeeInput from '@/components/PayeeInput.vue';
import ConnectedButtonGroup from '@/components/ConnectedButtonGroup.vue';
import { currentTime, formatLongDate, today } from '@/lib/dates';
import FieldSupport from '@/components/FieldSupport.vue';
import { convertAtRate, currencySymbol, formatRate, parseMoney, parseRate, rateBetween, toDecimalString } from '@/lib/money';
import { readTransferFee } from '@/lib/cache';
import { useHarmonised } from '@/lib/harmonise';
import { supportId, useFormValidation } from '@/lib/validation';
import { api, ApiError } from '@/lib/api';
import { newId } from '@/lib/ids';
import { displayMoney } from '@/lib/privacy';
import { showSnackbar } from '@/lib/snackbar';
import { showSaved } from '@/lib/sync';
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
const props = defineProps<{ open: boolean; transaction?: Transaction | null; transferInflow?: Transaction | null }>();
const emit = defineEmits<{ close: [] }>();

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
	/** Between two currencies: what one of the destination's cost in the source's, as Wise quotes it, and what arrived. */
	rate: '',
	toAmount: '',
	/** What the transfer cost, an expense on the source account; blank is none. */
	fee: '',
	feeCategoryId: '',
});

/**
 * Whether the rate is the person's own — typed, or what an edited transfer already moved at — rather than the one
 * looked up. A rate of their own is never overwritten.
 */
const rateOwn = ref(false);
/** What is said under the rate: that it is being looked up, which day's it is, or that there is none. */
const rateNote = ref<string | null>(null);
/** The expense the transfer being edited already posted for its fee, so an edit changes that one. */
const feeId = ref<string | null>(null);
/**
 * Whether the fee of the transfer being edited has been read. Until it has, "no fee" cannot be told from "not read
 * yet", so the form does not save — as on Android.
 */
const feeLoaded = ref(true);

/** A failure that belongs to no one field — the save itself went wrong. Each field's own problem is drawn beside it. */
const error = ref<string | null>(null);

/** The most tags one transaction wears; the API refuses more. */
const MAX_TAGS = 10;

const fromCurrency = computed(() => ledger.accountsById.get(form.accountId)?.currency.toUpperCase() ?? null);
const toCurrency = computed(() => ledger.accountsById.get(form.toAccountId)?.currency.toUpperCase() ?? null);

/** The two currencies a rate is between, `PHP>USD`; empty while either account is not chosen. */
const currencyPair = computed(() => (fromCurrency.value && toCurrency.value ? `${fromCurrency.value}>${toCurrency.value}` : ''));
/** The pair the rate on show is for, so that changing either account's currency asks for a new one. */
const ratePair = ref('');

/** A transfer between accounts of two currencies, which asks for a rate and what arrived. */
const crossCurrency = computed(
	() => form.mode === 'transfer' && fromCurrency.value !== null && toCurrency.value !== null && fromCurrency.value !== toCurrency.value,
);

const validation = useFormValidation({
	payee: () => (form.payee.trim().length > 120 ? t('transactions.form.payeeTooLong', { max: 120 }) : null),
	amount: () => {
		// Between two currencies the amount is worked out, so a blank one is Received's or Rate's to report.
		if (!form.amount.trim()) return crossCurrency.value ? null : t('transactions.form.amountRequired');
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
	rate: () => {
		if (!crossCurrency.value) return null;
		if (!form.rate.trim()) return t('transactions.form.rateRequired');
		return parseRate(form.rate) === null ? t('transactions.form.rateNumber') : null;
	},
	received: () => {
		if (!crossCurrency.value) return null;
		if (!form.toAmount.trim()) return t('transactions.form.receivedRequired');
		const minor = parseMoney(form.toAmount);
		if (minor === null) return t('transactions.form.amountNumber');
		return minor > 0 ? null : t('transactions.form.amountPositive');
	},
	fee: () => {
		if (form.mode !== 'transfer' || !form.fee.trim()) return null;
		const minor = parseMoney(form.fee);
		if (minor === null) return t('transactions.form.feeNumber');
		return minor > 0 ? null : t('transactions.form.feePositive');
	},
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
			rate: '',
			toAmount: '',
			fee: '',
			feeCategoryId: '',
		});
		rateOwn.value = false;
		rateNote.value = null;
		feeId.value = null;
		feeLoaded.value = true;
	} else {
		const isTransfer = Boolean(transaction.transferId);
		// A time of day is an optional `THH:MM` suffix; a bare date has none. An automated row never has one.
		const hasTime = transaction.occurredOn.length > 10 && !transaction.automated;

		const inflow = isTransfer ? (props.transferInflow ?? null) : null;
		const rate = inflow ? rateBetween(Math.abs(transaction.amount), inflow.amount) : null;

		Object.assign(form, {
			mode: isTransfer ? 'transfer' : transaction.amount >= 0 ? 'income' : 'expense',
			accountId: transaction.accountId,
			toAccountId: inflow?.accountId ?? '',
			categoryId: transaction.categoryId ?? '',
			amount: toDecimalString(Math.abs(transaction.amount)),
			occurredOn: transaction.occurredOn.slice(0, 10),
			occurredTime: hasTime ? transaction.occurredOn.slice(11, 16) : '',
			payee: transaction.payee,
			notes: transaction.notes,
			tagIds: transaction.tags.map((tag) => tag.id),
			// What the transfer moved at is the person's, so it is not looked up again over it.
			rate: rate === null ? '' : formatRate(rate),
			toAmount: inflow ? toDecimalString(inflow.amount) : '',
			fee: '',
			feeCategoryId: '',
		});
		rateOwn.value = rate !== null;
		rateNote.value = null;
		feeId.value = null;
		feeLoaded.value = !transaction.transferId;

		// The fee is its own row, read from the copy. Once it is in, it is what the form opened with: only its two
		// keys of the snapshot move, so whatever was typed meanwhile still counts as a change, and a fee already
		// typed is not overwritten.
		if (transaction.transferId) {
			const transferId = transaction.transferId;
			void readTransferFee(transferId).then((fee) => {
				if (props.transaction?.transferId !== transferId) return;
				feeLoaded.value = true;
				if (!fee) return;
				feeId.value = fee.id;
				const stored = { fee: toDecimalString(Math.abs(fee.amount)), feeCategoryId: fee.categoryId ?? '' };
				opened.value = JSON.stringify({ ...JSON.parse(opened.value), ...stored });
				if (!form.fee) Object.assign(form, stored);
			});
		}
	}

	ratePair.value = currencyPair.value;
	opened.value = JSON.stringify(form);
	void lookUpRate();
}

watch(
	() => props.open,
	(open) => {
		if (open) seed();
	},
	{ immediate: true },
);

// Beside a supporting pane the list stays usable, so another row can be opened while the form is: it starts
// over on that one.
watch([() => props.transaction, () => props.transferInflow], () => {
	if (props.open) seed();
});

// Switching mode invalidates a category belonging to another set.
watch(
	() => form.mode,
	() => {
		if (!selectable.value.some((category) => category.id === form.categoryId)) form.categoryId = '';
	},
);

/**
 * What left, as what arrived comes to at the rate — the figure Wise calls "we converted". It is never typed between
 * two currencies, so while either is not a number yet it is blank rather than a figure that no longer holds.
 */
function deriveSent(): void {
	const received = parseMoney(form.toAmount);
	const rate = parseRate(form.rate);
	form.amount = received !== null && received > 0 && rate !== null ? toDecimalString(convertAtRate(received, rate)) : '';
}

/** Typing a rate makes it the person's own, and what left follows it. */
function onRateTyped(): void {
	rateOwn.value = true;
	rateNote.value = null;
	deriveSent();
}

/** Bumped for every lookup, so an answer that is no longer for the accounts and day on show is dropped. */
let lookup = 0;

/**
 * Fills in the rate for the two currencies on the transfer's day, unless the person has given their own. Online
 * only: unreachable, or a pair nobody quotes, leaves the field for them to type.
 */
async function lookUpRate(): Promise<void> {
	const asked = ++lookup;
	if (!props.open || !crossCurrency.value || rateOwn.value) return;

	const from = fromCurrency.value!;
	const to = toCurrency.value!;
	rateNote.value = t('transactions.form.rateLoading');
	try {
		// Quoted as one of the destination's in the source's, so it is asked for that way round.
		const answer = await api.exchangeRate(to, from, form.occurredOn || today());
		if (asked !== lookup || rateOwn.value) return;
		form.rate = formatRate(answer.rate);
		rateNote.value = t('transactions.form.rateFound', { date: formatLongDate(answer.date) });
		deriveSent();
	} catch {
		if (asked === lookup && !rateOwn.value) rateNote.value = t('transactions.form.rateUnavailable');
	}
}

watch([currencyPair, () => form.occurredOn], () => {
	if (!crossCurrency.value) {
		rateNote.value = null;
		return;
	}
	// A rate — the person's own or one looked up — was for the two currencies it was given for. Another pair
	// clears it and what arrived, so neither can be saved for the wrong currencies while a new one is asked for.
	if (currencyPair.value !== ratePair.value) {
		ratePair.value = currencyPair.value;
		rateOwn.value = false;
		form.rate = '';
		form.toAmount = '';
		form.amount = '';
	}
	void lookUpRate();
});

/** The fee is an expense, so it takes the standard expense categories. */
const feeCategoryChoices = computed(() =>
	categoryOptions(ledger.groupForPicker(ledger.expenseCategories), { value: '', label: t('common.uncategorized') }),
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
	// A fee left blank is none: on an edit that takes off the one it had, and on a new transfer it is left out.
	const feeMinor = form.fee.trim() ? (parseMoney(form.fee) as number) : null;
	const fee =
		feeMinor !== null
			? { ...(feeId.value ? { id: feeId.value } : {}), amount: feeMinor, categoryId: form.feeCategoryId || null }
			: feeId.value
				? null
				: undefined;

	const body =
		form.mode === 'transfer'
			? {
					fromAccountId: form.accountId,
					toAccountId: form.toAccountId,
					amount: minor,
					// Each leg is in its own account's currency; between two of one currency what arrived is what left.
					toAmount: crossCurrency.value ? (parseMoney(form.toAmount) as number) : minor,
					...(fee === undefined ? {} : { fee }),
					occurredOn,
					notes: form.notes,
					categoryId: form.categoryId || null,
					payee: form.payee,
					tagIds,
				}
			: {
					accountId: form.accountId,
					categoryId: form.categoryId || null,
					amount: form.mode === 'expense' ? -minor : minor,
					occurredOn,
					payee: form.payee,
					notes: form.notes,
					tagIds,
				};

	const editing = props.transaction ?? null;
	try {
		let roundUp: Transaction | null = null;

		if (form.mode === 'transfer' && editing?.transferId) {
			// The inflow leg is the one this form was opened beside; without it a new one would be drawn.
			await api.updateTransfer(editing.transferId, body, { fromId: editing.id, toId: props.transferInflow?.id ?? newId('transaction') });
		} else if (form.mode === 'transfer') {
			await api.createTransfer(body);
		} else if (editing) {
			await api.updateTransaction(editing.id, body);
		} else {
			roundUp = (await api.createTransaction(body)).roundUp ?? null;
		}

		emit('close');
		showSnackbar(
			editing
				? t('transactions.changesSaved')
				: form.mode === 'transfer'
					? t('transactions.transferAdded')
					: t('transactions.transactionAdded'),
		);
		if (roundUp) announceRoundUp(roundUp);
		await showSaved();
	} catch (caught) {
		error.value = caught instanceof ApiError ? caught.message : t('common.couldNotSave');
	}
}

/** Says "₱X saved to <account>" after a purchase triggers a Save the Change round-up. */
function announceRoundUp(roundUp: Transaction): void {
	const destinationCurrency = ledger.accountsById.get(roundUp.accountId)?.currency ?? ledger.displayCurrency;
	showSnackbar(
		t('transactions.roundUpSaved', {
			amount: displayMoney(roundUp.amount, destinationCurrency),
			account: roundUp.accountName ?? t('transactions.roundUpFallback'),
		}),
	);
}

/** The amount, and a fee, lead with the currency of the account they leave; what arrived with the destination's. */
const amountPrefix = computed(() => (fromCurrency.value ? currencySymbol(fromCurrency.value) : undefined));
const receivedPrefix = computed(() => (toCurrency.value ? currencySymbol(toCurrency.value) : undefined));
</script>

<template>
	<FormDialog
		:open="open"
		:title="isEditing ? t('transactions.editTransaction') : t('transactions.newTransaction')"
		:save-enabled="feeLoaded && validation.isValid.value"
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

			<!-- Between two currencies what left is worked out from Received and Rate below, so it is read, not typed.
			     Keyed on that: the library decides whether a field has a supporting row as it mounts, so a hint that
			     arrives later would not be drawn. -->
			<TextField
				id="amount"
				:key="crossCurrency ? 'converted' : 'typed'"
				v-model="form.amount"
				:label="t('common.amount')"
				placeholder="0.00"
				inputmode="decimal"
				:readonly="crossCurrency"
				:prefix="amountPrefix"
				:hint="crossCurrency ? t('transactions.form.amountConverted') : undefined"
				:error="fieldError('amount')"
				@blur="touch('amount')"
			/>

			<div>
				<SelectField
					id="account"
					v-model="form.accountId"
					:label="form.mode === 'transfer' ? t('transactions.form.fromAccount') : t('common.account')"
					:options="accountChoices"
					:error="fieldError('account')"
					@blur="touch('account')"
				/>
			</div>

			<div v-if="form.mode === 'transfer'">
				<SelectField
					id="to-account"
					v-model="form.toAccountId"
					:label="t('transactions.form.toAccount')"
					:options="accountChoices"
					:error="fieldError('to-account')"
					@blur="touch('to-account')"
				/>
			</div>

			<!-- Between two currencies, what arrived and the rate are typed, as Wise lists them, and the amount above
			     follows. -->
			<div v-if="crossCurrency" class="flex gap-3">
				<TextField
					id="rate"
					v-model="form.rate"
					class="min-w-0 flex-1"
					:label="t('transactions.form.rate')"
					inputmode="decimal"
					:hint="rateNote ?? t('transactions.form.rateHint', { from: toCurrency!, to: fromCurrency! })"
					:error="fieldError('rate')"
					@update:model-value="onRateTyped"
					@blur="touch('rate')"
				/>
				<TextField
					id="received"
					v-model="form.toAmount"
					class="min-w-0 flex-1"
					:label="t('transactions.form.received')"
					placeholder="0.00"
					inputmode="decimal"
					:prefix="receivedPrefix"
					:error="fieldError('received')"
					@update:model-value="deriveSent"
					@blur="touch('received')"
				/>
			</div>

			<!-- A fee is spending, not a movement: it posts its own expense on the account the money left. -->
			<template v-if="form.mode === 'transfer'">
				<TextField
					id="fee"
					v-model="form.fee"
					:label="t('transactions.form.fee')"
					:placeholder="t('common.optional')"
					inputmode="decimal"
					:prefix="amountPrefix"
					:hint="t('transactions.form.feeHint')"
					:error="fieldError('fee')"
					@blur="touch('fee')"
				/>
				<SelectField
					v-if="form.fee.trim()"
					id="fee-category"
					v-model="form.feeCategoryId"
					:label="t('transactions.form.feeCategory')"
					:options="feeCategoryChoices"
				/>
			</template>

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
						:error="fieldError('date')"
						@blur="touch('date')"
					/>
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
					<Chip
						v-for="tag in ledger.tags"
						:key="tag.id"
						type="filter"
						class="max-w-full rounded-sm!"
						:selected="form.tagIds.includes(tag.id)"
						@update:selected="toggleTag(tag.id)"
					>
						<template #icon>
							<span class="grid place-items-center" aria-hidden="true">
								<span class="size-2! rounded-full" :style="{ backgroundColor: harmonised(tag.color) }" />
							</span>
						</template>
						<span class="truncate">{{ tag.name }}</span>
					</Chip>
				</div>
				<FieldSupport id="tags" :error="fieldError('tags')" class="!px-0" />
			</fieldset>

			<!-- Only for a failure that is no one field's — the save itself. What is wrong with a field is said beneath the field. -->
			<p v-if="error" class="type-body-small text-error" role="alert">{{ error }}</p>

			<!-- Only a new transaction: Yuuka at the foot of the form, ready to count it. -->
			<YuukaSays v-if="!isEditing" mood="attentive" class="pt-2">{{ t('transactions.form.newLine') }}</YuukaSays>
		</div>
	</FormDialog>
</template>
