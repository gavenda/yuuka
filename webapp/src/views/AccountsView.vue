<script setup lang="ts">
import SelectField from '@/components/SelectField.vue';
import { namedOptions } from '@/lib/selectOptions';
import AccountWatermark from '@/components/AccountWatermark.vue';
import ActionIcon from '@/components/ActionIcon.vue';
import AccountTypeManager from '@/components/AccountTypeManager.vue';
import EmptyState from '@/components/EmptyState.vue';
import FabButton from '@/components/FabButton.vue';
import ModalDialog from '@/components/ModalDialog.vue';
import ToggleSwitch from '@/components/ToggleSwitch.vue';
import MoneyText from '@/components/MoneyText.vue';
import StatCard from '@/components/StatCard.vue';
import { api, ApiError } from '@/lib/api';
import { parseMoney, toDecimalString } from '@/lib/money';
import { showSnackbar } from '@/lib/snackbar';
import { currencyProblem, logoUrlProblem, nameProblem, supportId, useFormValidation } from '@/lib/validation';
import FieldSupport from '@/components/FieldSupport.vue';
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
	roundUpSource: false,
});

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

const typeChoices = computed(() => namedOptions(ledger.activeAccountTypes, { value: '', label: 'Select a type', disabled: true }));
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
		roundUpSource: false,
	});
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
		roundUpSource: account.roundUpSource,
	});
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
		roundUpSource: form.roundUpSource,
	};

	try {
		if (editing.value) await api.updateAccount(editing.value.id, payload);
		else await api.createAccount(payload);

		dialogOpen.value = false;
		showSnackbar(editing.value ? 'Changes saved' : 'Account added');
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
	await ledger.refreshAccounts();
}

async function remove(account: Account): Promise<void> {
	if (!confirm(`Delete “${account.name}”?`)) return;

	try {
		await api.deleteAccount(account.id);
	} catch (caught) {
		// The API refuses to silently destroy history; ask before forcing it.
		if (caught instanceof ApiError && caught.status === 409) {
			if (!confirm(`${caught.message}\n\nDelete the account and its transactions?`)) return;
			await api.deleteAccount(account.id, true);
		} else {
			throw caught;
		}
	}

	showSnackbar('Account deleted');
	await Promise.all([ledger.refreshAccounts(), budget.refresh()]);
}

onMounted(() => ledger.load());
</script>

<template>
	<div class="space-y-6">
		<header class="flex items-center gap-2">
			<button type="button" class="btn-secondary" @click="typesOpen = true">Manage types</button>
		</header>

		<StatCard label="Net worth" :amount="ledger.netWorth" :currency="ledger.displayCurrency" />

		<EmptyState v-if="!ledger.loading && !visible.length" title="No accounts yet" description="Add the accounts you want to track.">
			<button type="button" class="btn-primary" @click="openCreate">Add an account</button>
		</EmptyState>

		<!-- Grouped by account type, each group carrying its own subtotal. -->
		<div v-else class="space-y-6">
			<section v-for="group in groups" :key="group.id" class="space-y-3">
				<header class="flex items-baseline justify-between gap-3 border-b border-outline-variant pb-2">
					<h2 class="text-sm font-medium text-on-surface">
						{{ group.name }}
						<span class="ml-1 text-xs font-normal text-on-surface-variant">
							{{ group.accounts.length }} {{ group.accounts.length === 1 ? 'account' : 'accounts' }}
						</span>
					</h2>
					<MoneyText :amount="group.total" :currency="group.currency" signed class="text-sm font-medium" />
				</header>

				<ul class="grid gap-4 sm:grid-cols-2">
					<li
						v-for="account in group.accounts"
						:key="account.id"
						class="card group relative flex items-center justify-between gap-3 overflow-hidden p-5"
					>
						<!-- Positioned, so the content paints above the watermark. -->
						<div class="relative min-w-0 flex-1">
							<p class="truncate font-medium text-on-surface">
								{{ account.name }}
								<span v-if="account.archived" class="ml-1 rounded bg-surface-container-high px-1.5 py-0.5 text-xs text-on-surface-variant">
									Archived
								</span>
							</p>
							<!-- The type is the group heading; only the currency is left to say. -->
							<p class="mt-0.5 text-xs text-on-surface-variant">{{ account.currency }}</p>
							<MoneyText :amount="account.balance" :currency="account.currency" signed class="mt-2 block text-lg font-medium" />
						</div>

						<div class="flex flex-col items-end gap-4">
							<AccountWatermark class="flex-0 block" :logo-url="account.logoUrl" :invert-dark="account.logoInvertDark" />
							<div class="flex-1 row-actions">
								<ActionIcon icon="edit" :label="`Edit ${account.name}`" @click="openEdit(account)" />
								<ActionIcon icon="adjust" :label="`Adjust balance for ${account.name}`" @click="openAdjust(account)" />
								<ActionIcon
									:icon="account.archived ? 'restore' : 'archive'"
									:label="`${account.archived ? 'Restore' : 'Archive'} ${account.name}`"
									@click="toggleArchived(account)"
								/>
								<ActionIcon icon="delete" :label="`Delete ${account.name}`" danger @click="remove(account)" />
							</div>
						</div>
					</li>
				</ul>
			</section>
		</div>

		<button v-if="archivedCount" type="button" class="btn-text" @click="showArchived = !showArchived">
			{{ showArchived ? 'Hide' : 'Show' }} {{ archivedCount }} archived
		</button>

		<ModalDialog :open="typesOpen" title="Account types" @close="typesOpen = false">
			<AccountTypeManager @changed="budget.refresh()" />
		</ModalDialog>

		<ModalDialog :open="dialogOpen" :title="editing ? 'Edit account' : 'New account'" @close="dialogOpen = false">
			<form class="space-y-4" novalidate @submit.prevent="save" @input="validation.onInput">
				<div class="field">
					<label class="label" for="account-name">Name</label>
					<input
						id="account-name"
						v-model="form.name"
						class="input"
						required
						placeholder="Everyday checking"
						:aria-invalid="fieldError('account-name') ? true : undefined"
						:aria-describedby="describe('account-name')"
						@blur="touch('account-name')"
					/>
					<FieldSupport id="account-name" :error="fieldError('account-name')" />
				</div>

				<div class="grid gap-4 sm:grid-cols-2">
					<div class="field">
						<label class="label" for="account-type">Type</label>
						<SelectField
							id="account-type"
							v-model="form.typeId"
							:options="typeChoices"
							required
							:invalid="Boolean(fieldError('account-type'))"
							:describedby="describe('account-type')"
							@blur="touch('account-type')"
						/>
						<FieldSupport id="account-type" :error="fieldError('account-type')" />
					</div>

					<div class="field">
						<label class="label" for="account-balance">Starting balance</label>
						<input
							id="account-balance"
							v-model="form.startingBalance"
							class="input tabular"
							inputmode="decimal"
							placeholder="0.00"
							:aria-invalid="fieldError('account-balance') ? true : undefined"
							:aria-describedby="supportId('account-balance')"
							@blur="touch('account-balance')"
						/>
						<FieldSupport
							id="account-balance"
							:error="fieldError('account-balance')"
							hint="The balance before any transaction below was recorded."
						/>
					</div>
				</div>

				<div class="field">
					<label class="label" for="account-currency">Currency</label>
					<input
						id="account-currency"
						v-model="form.currency"
						class="input uppercase"
						maxlength="3"
						required
						:placeholder="ledger.displayCurrency"
						:aria-invalid="fieldError('account-currency') ? true : undefined"
						:aria-describedby="describe('account-currency')"
						@blur="touch('account-currency')"
					/>
					<FieldSupport id="account-currency" :error="fieldError('account-currency')" />
				</div>

				<!-- A switch, not a checkbox: text and explanation on the left, the switch on the right, and the text toggles it. -->
				<div class="flex items-center justify-between gap-4">
					<div class="min-w-0">
						<label for="account-round-up" class="block text-sm text-on-surface">Round up purchases (Save the Change)</label>
						<p class="mt-0.5 text-xs text-on-surface-variant">Every expense on this account rounds up to the nearest ₱10 or ₱100.</p>
					</div>
					<ToggleSwitch id="account-round-up" v-model="form.roundUpSource" label="Round up purchases (Save the Change)" />
				</div>

				<div>
					<div class="flex items-center gap-3">
						<div class="field min-w-0 flex-1">
							<label class="label" for="account-logo">Logo URL (optional)</label>
							<input
								id="account-logo"
								v-model="form.logoUrl"
								class="input"
								type="url"
								inputmode="url"
								placeholder="https://example.com/logo.png"
								:aria-invalid="fieldError('account-logo') ? true : undefined"
								:aria-describedby="describe('account-logo')"
								@blur="touch('account-logo')"
							/>
							<FieldSupport id="account-logo" :error="fieldError('account-logo')" />
						</div>
					</div>
					<div class="mt-3 flex items-center justify-between gap-4">
						<label for="account-logo-invert" class="text-sm text-on-surface">Invert colours in dark mode</label>
						<ToggleSwitch id="account-logo-invert" v-model="form.logoInvertDark" label="Invert colours in dark mode" />
					</div>
				</div>

				<p v-if="error" class="banner-error" role="alert">
					{{ error }}
				</p>

				<div class="flex justify-end gap-2 pt-2">
					<button type="button" class="btn-text" @click="dialogOpen = false">Cancel</button>
					<button type="submit" class="btn-primary" :disabled="!validation.isValid.value">
						{{ editing ? 'Save changes' : 'Add account' }}
					</button>
				</div>
			</form>
		</ModalDialog>

		<ModalDialog :open="adjustDialogOpen" title="Adjust balance" @close="adjustDialogOpen = false">
			<form v-if="adjusting" class="space-y-4" novalidate @submit.prevent="saveAdjustment" @input="adjustValidation.onInput">
				<p class="text-sm text-on-surface-variant">
					{{ adjusting.name }}'s current balance is
					<MoneyText :amount="adjusting.balance" :currency="adjusting.currency" class="font-medium text-on-surface" />. Enter what it should
					be instead — the difference is logged as its own transaction, dated today.
				</p>

				<div class="field">
					<label class="label" for="adjust-balance">New balance</label>
					<input
						id="adjust-balance"
						v-model="adjustForm.balance"
						class="input tabular"
						inputmode="decimal"
						placeholder="0.00"
						:aria-invalid="adjustFieldError('adjust-balance') ? true : undefined"
						:aria-describedby="adjustFieldError('adjust-balance') ? supportId('adjust-balance') : undefined"
						@blur="adjustTouch('adjust-balance')"
					/>
					<FieldSupport id="adjust-balance" :error="adjustFieldError('adjust-balance')" />
				</div>

				<p v-if="adjustDifference !== null && adjustDifference !== 0" class="text-sm text-on-surface-variant">
					Logs
					<MoneyText :amount="adjustDifference" :currency="adjusting.currency" signed class="font-medium" />
					as {{ adjustDifference > 0 ? 'income' : 'an expense' }}.
				</p>

				<div class="field">
					<label class="label" for="adjust-payee">Payee (optional)</label>
					<input
						id="adjust-payee"
						v-model="adjustForm.payee"
						class="input"
						placeholder="Balance adjustment"
						:aria-invalid="adjustFieldError('adjust-payee') ? true : undefined"
						:aria-describedby="adjustFieldError('adjust-payee') ? supportId('adjust-payee') : undefined"
						@blur="adjustTouch('adjust-payee')"
					/>
					<FieldSupport id="adjust-payee" :error="adjustFieldError('adjust-payee')" />
				</div>

				<p v-if="adjustError" class="banner-error" role="alert">
					{{ adjustError }}
				</p>

				<div class="flex justify-end gap-2 pt-2">
					<button type="button" class="btn-text" @click="adjustDialogOpen = false">Cancel</button>
					<button type="submit" class="btn-primary" :disabled="!adjustValidation.isValid.value">Save adjustment</button>
				</div>
			</form>
		</ModalDialog>

		<FabButton label="Add account" @click="openCreate" />
	</div>
</template>
