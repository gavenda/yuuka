<script setup lang="ts">
import AccountLogo from '@/components/AccountLogo.vue';
import AccountWatermark from '@/components/AccountWatermark.vue';
import ActionIcon from '@/components/ActionIcon.vue';
import AccountTypeManager from '@/components/AccountTypeManager.vue';
import EmptyState from '@/components/EmptyState.vue';
import ModalDialog from '@/components/ModalDialog.vue';
import MoneyText from '@/components/MoneyText.vue';
import StatCard from '@/components/StatCard.vue';
import { api, ApiError } from '@/lib/api';
import { parseMoney, toDecimalString } from '@/lib/money';
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

const adjustDialogOpen = ref(false);
const adjusting = ref<Account | null>(null);
const adjustError = ref<string | null>(null);
const adjustForm = reactive({
	balance: '0.00',
	payee: '',
});

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
	const startingBalance = parseMoney(form.startingBalance);
	if (startingBalance === null) {
		error.value = 'Starting balance must be a number.';
		return;
	}

	const payload = {
		name: form.name,
		typeId: form.typeId,
		currency: form.currency,
		startingBalance,
		logoUrl: form.logoUrl.trim(),
		logoInvertDark: form.logoInvertDark,
		roundUpSource: form.roundUpSource,
	};

	try {
		if (editing.value) await api.updateAccount(editing.value.id, payload);
		else await api.createAccount(payload);

		dialogOpen.value = false;
		await Promise.all([ledger.refreshAccounts(), budget.refresh()]);
	} catch (caught) {
		error.value = caught instanceof ApiError ? caught.message : 'Could not save the account.';
	}
}

function openAdjust(account: Account): void {
	adjusting.value = account;
	adjustError.value = null;
	Object.assign(adjustForm, { balance: toDecimalString(account.balance), payee: '' });
	adjustDialogOpen.value = true;
}

async function saveAdjustment(): Promise<void> {
	const account = adjusting.value;
	if (!account) return;

	const balance = parseMoney(adjustForm.balance);
	if (balance === null) {
		adjustError.value = 'Balance must be a number.';
		return;
	}
	if (balance === account.balance) {
		adjustError.value = 'That is already the current balance.';
		return;
	}

	try {
		await api.adjustAccount(account.id, { balance, occurredOn: today(), payee: adjustForm.payee.trim() || undefined });
		adjustDialogOpen.value = false;
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

	await Promise.all([ledger.refreshAccounts(), budget.refresh()]);
}

onMounted(() => ledger.load());
</script>

<template>
	<div class="space-y-6">
		<header class="flex flex-wrap items-center justify-between gap-3">
			<h1 class="text-xl font-semibold tracking-tight text-slate-900 dark:text-white">Accounts</h1>
			<div class="flex items-center gap-2">
				<button type="button" class="btn-secondary" @click="typesOpen = true">Manage types</button>
				<button type="button" class="btn-primary" @click="openCreate">Add account</button>
			</div>
		</header>

		<StatCard label="Net worth" :amount="ledger.netWorth" :currency="ledger.displayCurrency" hero />

		<EmptyState v-if="!ledger.loading && !visible.length" title="No accounts yet" description="Add the accounts you want to track.">
			<button type="button" class="btn-primary" @click="openCreate">Add an account</button>
		</EmptyState>

		<!-- Grouped by account type, each group carrying its own subtotal. -->
		<div v-else class="space-y-6">
			<section v-for="group in groups" :key="group.id" class="space-y-3">
				<header class="flex items-baseline justify-between gap-3 border-b border-slate-200 pb-2 dark:border-slate-800">
					<h2 class="text-sm font-semibold text-slate-900 dark:text-white">
						{{ group.name }}
						<span class="ml-1 text-xs font-normal text-slate-500 dark:text-slate-400">
							{{ group.accounts.length }} {{ group.accounts.length === 1 ? 'account' : 'accounts' }}
						</span>
					</h2>
					<MoneyText :amount="group.total" :currency="group.currency" signed class="text-sm font-semibold" />
				</header>

				<ul class="grid gap-4 sm:grid-cols-2">
					<li
						v-for="account in group.accounts"
						:key="account.id"
						class="card group relative flex items-center justify-between gap-3 overflow-hidden p-5"
					>
						<!-- Positioned, so the content paints above the watermark. -->
						<div class="relative min-w-0 flex-1">
							<p class="truncate font-medium text-slate-900 dark:text-white">
								{{ account.name }}
								<span
									v-if="account.archived"
									class="ml-1 rounded bg-slate-100 px-1.5 py-0.5 text-xs text-slate-500 dark:bg-slate-800 dark:text-slate-400"
								>
									Archived
								</span>
							</p>
							<!-- The type is the group heading; only the currency is left to say. -->
							<p class="mt-0.5 text-xs text-slate-500 dark:text-slate-400">{{ account.currency }}</p>
							<MoneyText :amount="account.balance" :currency="account.currency" signed class="mt-2 block text-lg font-semibold" />
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

		<button v-if="archivedCount" type="button" class="btn-ghost text-sm" @click="showArchived = !showArchived">
			{{ showArchived ? 'Hide' : 'Show' }} {{ archivedCount }} archived
		</button>

		<ModalDialog :open="typesOpen" title="Account types" @close="typesOpen = false">
			<AccountTypeManager @changed="budget.refresh()" />
		</ModalDialog>

		<ModalDialog :open="dialogOpen" :title="editing ? 'Edit account' : 'New account'" @close="dialogOpen = false">
			<form class="space-y-4" @submit.prevent="save">
				<div>
					<label class="label" for="account-name">Name</label>
					<input id="account-name" v-model="form.name" class="input" required placeholder="Everyday checking" />
				</div>

				<div class="grid gap-4 sm:grid-cols-2">
					<div>
						<label class="label" for="account-type">Type</label>
						<select id="account-type" v-model="form.typeId" class="input" required>
							<option value="" disabled>Select a type</option>
							<option v-for="type in ledger.activeAccountTypes" :key="type.id" :value="type.id">{{ type.name }}</option>
						</select>
					</div>

					<div>
						<label class="label" for="account-balance">Starting balance</label>
						<input id="account-balance" v-model="form.startingBalance" class="input tabular" inputmode="decimal" placeholder="0.00" />
					</div>
				</div>
				<p class="-mt-2 text-xs text-slate-500 dark:text-slate-400">The balance before any transaction below was recorded.</p>

				<div>
					<label class="label" for="account-currency">Currency</label>
					<input
						id="account-currency"
						v-model="form.currency"
						class="input uppercase"
						maxlength="3"
						required
						:placeholder="ledger.displayCurrency"
					/>
				</div>

				<div>
					<label class="flex items-center gap-2 text-sm text-slate-700 dark:text-slate-300">
						<input
							v-model="form.roundUpSource"
							type="checkbox"
							class="size-4 rounded border-slate-300 accent-blue-600 dark:border-slate-700"
						/>
						Round up purchases (Save the Change)
					</label>
					<p class="mt-1 text-xs text-slate-500 dark:text-slate-400">
						Every expense on this account rounds up to the nearest ₱10 or ₱100 — set the exact amount and destination in Settings.
					</p>
				</div>

				<div>
					<label class="label" for="account-logo">Logo URL</label>
					<div class="flex items-center gap-3">
						<AccountLogo :name="form.name || '?'" :logo-url="form.logoUrl.trim() || null" :invert-dark="form.logoInvertDark" :size="40" />
						<input
							id="account-logo"
							v-model="form.logoUrl"
							class="input min-w-0 flex-1"
							type="url"
							inputmode="url"
							placeholder="https://example.com/logo.png"
						/>
					</div>
					<p class="mt-1 text-xs text-slate-500 dark:text-slate-400">
						Optional. The image is loaded from wherever it lives — nothing is uploaded or copied. Leave empty for the account's initial.
					</p>
					<label v-if="form.logoUrl.trim()" class="mt-2 flex items-center gap-2 text-sm text-slate-700 dark:text-slate-300">
						<input
							v-model="form.logoInvertDark"
							type="checkbox"
							class="size-4 rounded border-slate-300 accent-blue-600 dark:border-slate-700"
						/>
						Invert colours in dark mode
					</label>
				</div>

				<p v-if="error" class="rounded-lg bg-rose-50 px-3 py-2 text-sm text-rose-700 dark:bg-rose-500/10 dark:text-rose-400" role="alert">
					{{ error }}
				</p>

				<div class="flex justify-end gap-2 pt-2">
					<button type="button" class="btn-secondary" @click="dialogOpen = false">Cancel</button>
					<button type="submit" class="btn-primary">{{ editing ? 'Save changes' : 'Add account' }}</button>
				</div>
			</form>
		</ModalDialog>

		<ModalDialog :open="adjustDialogOpen" title="Adjust balance" @close="adjustDialogOpen = false">
			<form v-if="adjusting" class="space-y-4" @submit.prevent="saveAdjustment">
				<p class="text-sm text-slate-500 dark:text-slate-400">
					{{ adjusting.name }}'s current balance is
					<MoneyText :amount="adjusting.balance" :currency="adjusting.currency" class="font-medium text-slate-700 dark:text-slate-300" />.
					Enter what it should be instead — the difference is logged as its own transaction, dated today.
				</p>

				<div>
					<label class="label" for="adjust-balance">New balance</label>
					<input id="adjust-balance" v-model="adjustForm.balance" class="input tabular" inputmode="decimal" placeholder="0.00" />
				</div>

				<p v-if="adjustDifference !== null && adjustDifference !== 0" class="text-sm text-slate-500 dark:text-slate-400">
					Logs
					<MoneyText :amount="adjustDifference" :currency="adjusting.currency" signed class="font-medium" />
					as {{ adjustDifference > 0 ? 'income' : 'an expense' }}.
				</p>

				<div>
					<label class="label" for="adjust-payee">Payee (optional)</label>
					<input id="adjust-payee" v-model="adjustForm.payee" class="input" placeholder="Balance adjustment" />
				</div>

				<p
					v-if="adjustError"
					class="rounded-lg bg-rose-50 px-3 py-2 text-sm text-rose-700 dark:bg-rose-500/10 dark:text-rose-400"
					role="alert"
				>
					{{ adjustError }}
				</p>

				<div class="flex justify-end gap-2 pt-2">
					<button type="button" class="btn-secondary" @click="adjustDialogOpen = false">Cancel</button>
					<button type="submit" class="btn-primary">Save adjustment</button>
				</div>
			</form>
		</ModalDialog>
	</div>
</template>
