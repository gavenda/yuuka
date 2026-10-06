<script setup lang="ts">
import AccountLogo from '@/components/AccountLogo.vue';
import AccountTypeManager from '@/components/AccountTypeManager.vue';
import ActionIcon from '@/components/ActionIcon.vue';
import AlertDialog from '@/components/AlertDialog.vue';
import BottomSheet from '@/components/BottomSheet.vue';
import EmptyState from '@/components/EmptyState.vue';
import FabButton from '@/components/FabButton.vue';
import FieldSupport from '@/components/FieldSupport.vue';
import FormDialog from '@/components/FormDialog.vue';
import MoneyText from '@/components/MoneyText.vue';
import SelectField from '@/components/SelectField.vue';
import SwipeReveal from '@/components/SwipeReveal.vue';
import TextField from '@/components/TextField.vue';
import ToggleSwitch from '@/components/ToggleSwitch.vue';
import { api, ApiError } from '@/lib/api';
import { CONTRACT_EDIT, LIBRARY_ADD } from '@/lib/icons';
import { currencyName, parseMoney, toDecimalString } from '@/lib/money';
import { displayMoney } from '@/lib/privacy';
import { namedOptions } from '@/lib/selectOptions';
import { showSnackbar } from '@/lib/snackbar';
import { currencyProblem, logoUrlProblem, nameProblem, supportId, useFormValidation } from '@/lib/validation';
import { useBudgetStore } from '@/stores/budget';
import { useLedgerStore } from '@/stores/ledger';
import type { Account } from '@/types';
import { computed, onMounted, reactive, ref } from 'vue';

const ledger = useLedgerStore();
const budget = useBudgetStore();

const dialogOpen = ref(false);
const editing = ref<Account | null>(null);
const error = ref<string | null>(null);
const showArchived = ref(false);

const typesOpen = ref(false);
const form = reactive({
	name: '',
	typeId: '',
	currency: ledger.displayCurrency,
	startingBalance: '0.00',
	logoUrl: '',
	logoInvertDark: false,
});
/** What the form opened with, so that closing it can tell an entry from an untouched form. */
const opened = ref('');
const dirty = computed(() => JSON.stringify(form) !== opened.value);

// Whether an account's purchases round up is not here: it is chosen on the Save the Change screen, beside the rule it feeds.
const validation = useFormValidation({
	'account-name': () => nameProblem(form.name),
	'account-type': () => (ledger.accountTypes.some((type) => type.id === form.typeId) ? null : 'Choose a type.'),
	'account-balance': () => (parseMoney(form.startingBalance) === null ? 'Enter a number, such as 1250.00.' : null),
	'account-currency': () => currencyProblem(form.currency),
	'account-logo': () => logoUrlProblem(form.logoUrl),
});
const { error: fieldError, touch } = validation;
const describe = (id: string): string | undefined => (fieldError(id) ? supportId(id) : undefined);

const adjustDialogOpen = ref(false);
const adjusting = ref<Account | null>(null);
const adjustError = ref<string | null>(null);
const adjustForm = reactive({
	balance: '0.00',
	payee: '',
});

const adjustValidation = useFormValidation({
	'adjust-balance': () => {
		const target = parseMoney(adjustForm.balance);
		if (target === null) return 'Enter a number, such as 1250.00.';
		return adjusting.value && target === adjusting.value.balance ? 'That is already the current balance.' : null;
	},
	'adjust-payee': () => (adjustForm.payee.trim().length > 120 ? 'Use 120 characters or fewer.' : null),
});
const { error: adjustFieldError, touch: adjustTouch } = adjustValidation;

/** Today, in the account's own local timezone rather than UTC — matches how a transaction date picker behaves elsewhere. */
function today(): string {
	const now = new Date();
	return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}-${String(now.getDate()).padStart(2, '0')}`;
}

const adjustDifference = computed(() => {
	if (!adjusting.value) return null;
	const target = parseMoney(adjustForm.balance);
	if (target === null) return null;
	return target - adjusting.value.balance;
});

const typeChoices = computed(() => namedOptions(ledger.activeAccountTypes));
const visible = computed(() => ledger.accounts.filter((account) => showArchived.value || !account.archived));
const archivedCount = computed(() => ledger.accounts.filter((account) => account.archived).length);

interface AccountGroup {
	id: string;
	name: string;
	accounts: Account[];
	total: number;
	currency: string;
}

function toGroup(id: string, name: string, accounts: Account[]): AccountGroup {
	// A subtotal only names a currency when the group holds just the one; mixed
	// groups fall back to the display currency, as the net worth figure does.
	const currencies = new Set(accounts.map((account) => account.currency));

	return {
		id,
		name,
		accounts,
		total: accounts.reduce((sum, account) => sum + account.balance, 0),
		currency: currencies.size === 1 ? [...currencies][0]! : ledger.displayCurrency,
	};
}

/** Accounts listed under their type, in the order the types are sorted in. */
const groups = computed<AccountGroup[]>(() => {
	const byType = new Map<string, Account[]>();

	for (const account of visible.value) {
		const bucket = byType.get(account.typeId);
		if (bucket) bucket.push(account);
		else byType.set(account.typeId, [account]);
	}

	const groups: AccountGroup[] = [];

	for (const type of [...ledger.accountTypes].sort((a, b) => a.sortOrder - b.sortOrder)) {
		const accounts = byType.get(type.id);
		if (!accounts) continue;

		byType.delete(type.id);
		groups.push(toGroup(type.id, type.name, accounts));
	}

	// An account whose type is no longer listed still needs a home, last.
	for (const [typeId, accounts] of byType) {
		groups.push(toGroup(typeId || 'untyped', accounts[0]!.typeName ?? 'Uncategorized', accounts));
	}

	return groups;
});

function openCreate(): void {
	editing.value = null;
	error.value = null;
	validation.reset();
	Object.assign(form, {
		name: '',
		typeId: ledger.activeAccountTypes[0]?.id ?? '',
		currency: ledger.accounts[0]?.currency ?? ledger.displayCurrency,
		startingBalance: '0.00',
		logoUrl: '',
		logoInvertDark: false,
	});
	opened.value = JSON.stringify(form);
	dialogOpen.value = true;
}

function openEdit(account: Account): void {
	editing.value = account;
	error.value = null;
	validation.reset();
	Object.assign(form, {
		name: account.name,
		typeId: account.typeId,
		currency: account.currency,
		startingBalance: toDecimalString(account.startingBalance),
		logoUrl: account.logoUrl ?? '',
		logoInvertDark: account.logoInvertDark,
	});
	opened.value = JSON.stringify(form);
	dialogOpen.value = true;
}

async function save(): Promise<void> {
	error.value = null;
	if (!validation.isValid.value) return;
	const startingBalance = parseMoney(form.startingBalance) as number;

	const payload = {
		name: form.name.trim(),
		typeId: form.typeId,
		currency: form.currency.trim().toUpperCase(),
		startingBalance,
		logoUrl: form.logoUrl.trim(),
		logoInvertDark: form.logoInvertDark,
	};

	try {
		if (editing.value) await api.updateAccount(editing.value.id, payload);
		else await api.createAccount(payload);

		dialogOpen.value = false;
		showSnackbar(editing.value ? 'Account updated' : 'Account added');
		await Promise.all([ledger.refreshAccounts(), budget.refresh()]);
	} catch (caught) {
		error.value = caught instanceof ApiError ? caught.message : 'Could not save the account.';
	}
}

function openAdjust(account: Account): void {
	adjusting.value = account;
	adjustError.value = null;
	adjustValidation.reset();
	Object.assign(adjustForm, { balance: toDecimalString(account.balance), payee: '' });
	adjustDialogOpen.value = true;
}

async function saveAdjustment(): Promise<void> {
	const account = adjusting.value;
	if (!account) return;

	adjustError.value = null;
	if (!adjustValidation.isValid.value) return;
	const balance = parseMoney(adjustForm.balance) as number;

	try {
		await api.adjustAccount(account.id, { balance, occurredOn: today(), payee: adjustForm.payee.trim() || undefined });
		adjustDialogOpen.value = false;
		showSnackbar('Balance adjusted');
		await Promise.all([ledger.refreshAccounts(), budget.refresh()]);
	} catch (caught) {
		adjustError.value = caught instanceof ApiError ? caught.message : 'Could not adjust the balance.';
	}
}

async function toggleArchived(account: Account): Promise<void> {
	await api.updateAccount(account.id, { archived: !account.archived });
	showSnackbar(account.archived ? 'Account restored' : 'Account archived');
	await ledger.refreshAccounts();
}

/** The account a delete has been asked for, and what the API said the first time it was tried. */
const pendingDelete = ref<Account | null>(null);
const deleteWarning = ref<string | null>(null);

function closeDelete(): void {
	pendingDelete.value = null;
	deleteWarning.value = null;
}

async function remove(): Promise<void> {
	const account = pendingDelete.value;
	if (!account) return;

	try {
		// Having been told the account has history, the second press means it.
		await api.deleteAccount(account.id, deleteWarning.value !== null);
	} catch (caught) {
		// The API refuses to silently destroy history; say so and ask again before forcing it.
		if (caught instanceof ApiError && caught.status === 409) deleteWarning.value = caught.message;
		else showSnackbar(caught instanceof ApiError ? caught.message : 'Could not delete the account.');
		return;
	}

	closeDelete();
	showSnackbar('Account deleted');
	await Promise.all([ledger.refreshAccounts(), budget.refresh()]);
}

/** The FAB's two actions: the rail lists them in a menu from its button. */
const fabActions = [
	{ label: 'New account', icon: LIBRARY_ADD, run: openCreate },
	{ label: 'Edit account types', icon: CONTRACT_EDIT, run: () => (typesOpen.value = true) },
];

onMounted(() => ledger.load());
</script>

<template>
	<div class="px-4 pt-4 pb-24">
		<EmptyState v-if="!groups.length" title="No accounts yet" description="Add the accounts you want to track." />

		<!-- Accounts under their type. The rows of a type are a hair apart, the way a settings group is drawn;
		     the space between one type and the next comes from the heading's own padding. -->
		<section v-for="group in groups" :key="group.id">
			<div class="group-header">
				<h2>{{ group.name }} ({{ group.accounts.length }})</h2>
				<MoneyText :amount="group.total" :currency="group.currency" tone="signed-alert" />
			</div>

			<ul class="group-rows">
				<li v-for="account in group.accounts" :key="account.id">
					<SwipeReveal>
						<template #actions>
							<ActionIcon icon="adjust" :label="`Adjust balance for ${account.name}`" @click="openAdjust(account)" />
							<ActionIcon
								:icon="account.archived ? 'restore' : 'archive'"
								:label="`${account.archived ? 'Restore' : 'Archive'} ${account.name}`"
								@click="toggleArchived(account)"
							/>
							<ActionIcon icon="delete" :label="`Delete ${account.name}`" danger @click="pendingDelete = account" />
						</template>

						<!-- The whole row opens the edit form. Spans, not blocks, because it is a button. -->
						<button type="button" class="group-row state-layer focus-ring cursor-pointer" @click="openEdit(account)">
							<span class="flex items-center gap-4 px-4 py-3">
								<span class="flex min-w-0 flex-1 flex-col gap-1">
									<span class="type-title-medium truncate">{{ account.archived ? `${account.name} (Archived)` : account.name }}</span>
									<span class="type-body-small">{{ currencyName(account.currency) }}</span>
									<MoneyText :amount="account.balance" :currency="account.currency" tone="signed-alert" class="type-title-medium" />
								</span>
								<AccountLogo :name="account.name" :logo-url="account.logoUrl" :invert-dark="account.logoInvertDark" :size="28" />
							</span>
						</button>
					</SwipeReveal>
				</li>
			</ul>
		</section>

		<div v-if="archivedCount > 0" class="pt-1">
			<button type="button" class="btn-text" @click="showArchived = !showArchived">
				{{ showArchived ? 'Hide' : 'Show' }} {{ archivedCount }} archived
			</button>
		</div>

		<AccountTypeManager :open="typesOpen" @changed="budget.refresh()" @close="typesOpen = false" />

		<FormDialog
			:open="dialogOpen"
			:title="editing ? 'Edit account' : 'New account'"
			:save-enabled="validation.isValid.value"
			:dirty="dirty"
			@close="dialogOpen = false"
			@save="save"
		>
			<div class="contents" @input="validation.onInput">
				<TextField id="account-name" v-model="form.name" label="Name" :error="fieldError('account-name')" @blur="touch('account-name')" />

				<div>
					<SelectField
						id="account-type"
						v-model="form.typeId"
						label="Type"
						:options="typeChoices"
						:invalid="Boolean(fieldError('account-type'))"
						:describedby="describe('account-type')"
						@blur="touch('account-type')"
					/>
					<FieldSupport id="account-type" :error="fieldError('account-type')" />
				</div>

				<TextField
					id="account-balance"
					v-model="form.startingBalance"
					label="Starting balance"
					placeholder="0.00"
					inputmode="decimal"
					:error="fieldError('account-balance')"
					@blur="touch('account-balance')"
				/>

				<TextField
					id="account-currency"
					v-model="form.currency"
					label="Currency"
					class="uppercase"
					maxlength="3"
					:error="fieldError('account-currency')"
					@blur="touch('account-currency')"
				/>

				<TextField
					id="account-logo"
					v-model="form.logoUrl"
					label="Logo URL (optional)"
					type="url"
					:error="fieldError('account-logo')"
					@blur="touch('account-logo')"
				/>

				<!-- Always shown, not only once a logo is entered; it has no effect until the account has one. -->
				<div class="type-body-large flex items-center text-on-surface">
					<span class="flex-1">Invert colours in dark mode</span>
					<ToggleSwitch v-model="form.logoInvertDark" label="Invert colours in dark mode" />
				</div>

				<p v-if="error" class="type-body-small text-error" role="alert">{{ error }}</p>
			</div>
		</FormDialog>

		<BottomSheet :open="adjustDialogOpen" label="Adjust balance" @close="adjustDialogOpen = false">
			<form v-if="adjusting" class="flex flex-col gap-3 p-5" novalidate @submit.prevent="saveAdjustment" @input="adjustValidation.onInput">
				<h2 class="type-title-medium">Adjust balance</h2>
				<p class="type-body-medium">
					{{ adjusting.name }}'s current balance is {{ displayMoney(adjusting.balance, adjusting.currency) }}. Enter what it should be
					instead — the difference is logged as its own transaction, dated today.
				</p>

				<TextField
					id="adjust-balance"
					v-model="adjustForm.balance"
					label="New balance"
					placeholder="0.00"
					inputmode="decimal"
					:error="adjustFieldError('adjust-balance')"
					@blur="adjustTouch('adjust-balance')"
				/>

				<p v-if="adjustDifference" class="type-body-small">
					Logs {{ displayMoney(adjustDifference, adjusting.currency) }} as {{ adjustDifference > 0 ? 'income' : 'an expense' }}.
				</p>

				<TextField
					id="adjust-payee"
					v-model="adjustForm.payee"
					label="Payee (optional)"
					:error="adjustFieldError('adjust-payee')"
					@blur="adjustTouch('adjust-payee')"
				/>

				<p v-if="adjustError" class="type-body-small text-error" role="alert">{{ adjustError }}</p>

				<div class="flex gap-2">
					<button type="button" class="btn-text flex-1" @click="adjustDialogOpen = false">Cancel</button>
					<button type="submit" class="btn-primary flex-1" :disabled="!adjustValidation.isValid.value">Save adjustment</button>
				</div>
			</form>
		</BottomSheet>

		<AlertDialog :open="pendingDelete !== null" :title="`Delete &quot;${pendingDelete?.name}&quot;?`" @close="closeDelete">
			<p v-if="deleteWarning" class="whitespace-pre-line">{{ deleteWarning }}{{ '\n\n' }}Delete the account and its transactions?</p>
			<template #actions>
				<button type="button" class="btn-text" @click="closeDelete">Cancel</button>
				<button type="button" class="btn-text" @click="remove">{{ deleteWarning ? 'Delete anyway' : 'Delete' }}</button>
			</template>
		</AlertDialog>

		<FabButton label="Account actions" :actions="fabActions" />
	</div>
</template>
