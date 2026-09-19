<script setup lang="ts">
import ConnectedButtonGroup from '@/components/ConnectedButtonGroup.vue';
import ActionIcon from '@/components/ActionIcon.vue';
import EmptyState from '@/components/EmptyState.vue';
import FabButton from '@/components/FabButton.vue';
import ModalDialog from '@/components/ModalDialog.vue';
import MoneyText from '@/components/MoneyText.vue';
import PayeeInput from '@/components/PayeeInput.vue';
import { api, ApiError } from '@/lib/api';
import { showSnackbar } from '@/lib/snackbar';
import { formatLongDate } from '@/lib/dates';
import { parseMoney, toDecimalString } from '@/lib/money';
import { monthlyTotal, nextDay, scheduleLabel, utcToday } from '@/lib/subscriptions';
import { useLedgerStore } from '@/stores/ledger';
import { useSubscriptionStore } from '@/stores/subscriptions';
import type { Payee, Subscription } from '@/types';
import { computed, nextTick, onMounted, reactive, ref } from 'vue';

type Direction = 'expense' | 'income';

const DIRECTIONS: { value: Direction; label: string }[] = [
	{ value: 'expense', label: 'Expense' },
	{ value: 'income', label: 'Income' },
];

const ledger = useLedgerStore();
const store = useSubscriptionStore();

const dialogOpen = ref(false);
const editing = ref<Subscription | null>(null);
const error = ref<string | null>(null);
const submitting = ref(false);

const form = reactive({
	direction: 'expense' as Direction,
	payee: '',
	amount: '',
	accountId: '',
	categoryId: '',
	startOn: '',
	notes: '',
});

/** What the date field held when the dialog opened, so an untouched date is not sent as a request to restart the schedule. */
let initialStartOn = '';

/** The API measures "not in the past" against the UTC calendar, so the picker does too. */
const earliest = utcToday();

const categoryGroups = computed(() =>
	ledger.groupForPicker(form.direction === 'income' ? ledger.incomeCategories : ledger.expenseCategories),
);
const selectable = computed(() => categoryGroups.value.flatMap((group) => [group.parent, ...group.children]));

/** What the active subscriptions come to each month; shown in the display currency, like every other aggregate. */
const total = computed(() => monthlyTotal(store.subscriptions));
const pausedCount = computed(() => store.subscriptions.filter((subscription) => !subscription.enabled).length);

/** A subscription's amount is in its own account's currency, the same as that account's balance. */
function currencyOf(subscription: Subscription): string {
	return ledger.accountsById.get(subscription.accountId)?.currency ?? ledger.displayCurrency;
}

onMounted(async () => {
	await Promise.all([ledger.load(), store.load()]);
});

function openCreate(): void {
	editing.value = null;
	error.value = null;

	const preferred = ledger.activeAccounts.find((account) => account.id === ledger.defaultAccountId);
	// Tomorrow, not today: today's run has already happened by the time anyone is here to set one up.
	initialStartOn = nextDay(earliest);

	Object.assign(form, {
		direction: 'expense',
		payee: '',
		amount: '',
		accountId: preferred?.id ?? ledger.activeAccounts[0]?.id ?? '',
		categoryId: '',
		startOn: initialStartOn,
		notes: '',
	});
	dialogOpen.value = true;
}

function openEdit(subscription: Subscription): void {
	editing.value = subscription;
	error.value = null;
	initialStartOn = subscription.nextRunOn;

	Object.assign(form, {
		direction: subscription.amount >= 0 ? 'income' : 'expense',
		payee: subscription.payee,
		amount: toDecimalString(Math.abs(subscription.amount)),
		accountId: subscription.accountId,
		categoryId: subscription.categoryId ?? '',
		startOn: subscription.nextRunOn,
		notes: subscription.notes,
	});
	dialogOpen.value = true;
}

async function setDirection(direction: Direction): Promise<void> {
	form.direction = direction;
	await nextTick();
	// A category belonging to the other direction is no longer on offer.
	if (!selectable.value.some((category) => category.id === form.categoryId)) form.categoryId = '';
}

/** A remembered payee fills in what it was last filed under. A transfer's history is not a subscription's to reuse. */
async function applyPayee(entry: Payee): Promise<void> {
	if (entry.kind === 'transfer') return;
	if (form.direction !== entry.kind) await setDirection(entry.kind);

	if (entry.accountId && ledger.activeAccounts.some((account) => account.id === entry.accountId)) form.accountId = entry.accountId;
	if (entry.categoryId && selectable.value.some((category) => category.id === entry.categoryId)) form.categoryId = entry.categoryId;
	if (entry.notes && !form.notes) form.notes = entry.notes;
}

async function submit(): Promise<void> {
	error.value = null;

	const minor = parseMoney(form.amount);
	if (minor === null || minor <= 0) {
		error.value = 'Enter an amount greater than zero.';
		return;
	}

	const payload: Record<string, unknown> = {
		accountId: form.accountId,
		categoryId: form.categoryId || null,
		amount: form.direction === 'expense' ? -minor : minor,
		payee: form.payee,
		notes: form.notes,
	};

	// Editing the date restarts the schedule from it; leaving it alone keeps the schedule as it is.
	if (!editing.value || form.startOn !== initialStartOn) payload.startOn = form.startOn;

	submitting.value = true;

	try {
		if (editing.value) await api.updateSubscription(editing.value.id, payload);
		else await api.createSubscription(payload);

		dialogOpen.value = false;
		showSnackbar(editing.value ? 'Changes saved' : 'Subscription added');
		await store.refresh();
	} catch (caught) {
		error.value = caught instanceof ApiError ? caught.message : 'Could not save the subscription.';
	} finally {
		submitting.value = false;
	}
}

async function togglePaused(subscription: Subscription): Promise<void> {
	try {
		await api.updateSubscription(subscription.id, { enabled: !subscription.enabled });
		await store.refresh();
	} catch (caught) {
		store.error = caught instanceof ApiError ? caught.message : 'Could not update the subscription.';
	}
}

async function remove(subscription: Subscription): Promise<void> {
	if (!confirm(`Delete ${subscription.payee}? Transactions it has already posted stay in your history.`)) return;

	try {
		await api.deleteSubscription(subscription.id);
		showSnackbar('Subscription deleted');
		await store.refresh();
	} catch (caught) {
		store.error = caught instanceof ApiError ? caught.message : 'Could not delete the subscription.';
	}
}
</script>

<template>
	<div class="space-y-5">
		<p class="text-sm text-on-surface-variant">
			Charges that post themselves each month at 00:00 UTC, as ordinary transactions you can still edit or delete.
		</p>

		<p v-if="store.subscriptions.length" class="text-sm text-on-surface-variant">
			Total per month
			<MoneyText :amount="total" :currency="ledger.displayCurrency" signed explicit class="ml-1 text-base font-medium" />
			<template v-if="pausedCount"> · {{ pausedCount }} paused, not counted</template>
		</p>

		<p v-if="store.error" class="banner-error" role="alert">
			{{ store.error }}
		</p>

		<EmptyState
			v-else-if="store.loaded && !store.subscriptions.length"
			title="No subscriptions yet"
			description="Set up rent, streaming or a loan payment once and it will be posted for you every month."
		>
			<button type="button" class="btn-primary" :disabled="!ledger.activeAccounts.length" @click="openCreate">Add a subscription</button>
		</EmptyState>

		<ul v-else class="grid gap-4 sm:grid-cols-2">
			<li
				v-for="subscription in store.subscriptions"
				:key="subscription.id"
				class="card group flex items-center justify-between gap-3 p-5"
				:class="subscription.enabled ? '' : 'opacity-70'"
			>
				<div class="min-w-0 flex-1">
					<p class="flex min-w-0 items-center gap-2 font-medium text-on-surface">
						<span
							class="h-2.5 w-2.5 shrink-0 rounded-full"
							:style="{ backgroundColor: subscription.categoryColor ?? '#898781' }"
							aria-hidden="true"
						/>
						<span class="truncate">{{ subscription.payee }}</span>
						<span
							v-if="!subscription.enabled"
							class="shrink-0 rounded bg-surface-container-high px-1.5 py-0.5 text-xs font-normal text-on-surface-variant"
						>
							Paused
						</span>
					</p>

					<p class="mt-0.5 truncate text-xs text-on-surface-variant">
						{{ subscription.accountName }}<template v-if="subscription.categoryName"> · {{ subscription.categoryName }}</template>
					</p>

					<MoneyText
						:amount="subscription.amount"
						:currency="currencyOf(subscription)"
						signed
						explicit
						class="mt-2 block text-lg font-medium"
					/>

					<p class="mt-1 text-xs text-on-surface-variant">
						{{ scheduleLabel(subscription.dayOfMonth)
						}}<template v-if="subscription.enabled"> · next {{ formatLongDate(subscription.nextRunOn) }}</template>
					</p>
				</div>

				<div class="row-actions">
					<ActionIcon icon="edit" :label="`Edit ${subscription.payee}`" @click="openEdit(subscription)" />
					<ActionIcon
						:icon="subscription.enabled ? 'pause' : 'resume'"
						:label="`${subscription.enabled ? 'Pause' : 'Resume'} ${subscription.payee}`"
						@click="togglePaused(subscription)"
					/>
					<ActionIcon icon="delete" :label="`Delete ${subscription.payee}`" danger @click="remove(subscription)" />
				</div>
			</li>
		</ul>

		<ModalDialog :open="dialogOpen" :title="editing ? 'Edit subscription' : 'New subscription'" @close="dialogOpen = false">
			<form class="space-y-4" @submit.prevent="submit">
				<ConnectedButtonGroup
					:model-value="form.direction"
					label="Kind of subscription"
					:options="DIRECTIONS"
					@update:model-value="setDirection"
				/>

				<PayeeInput v-model="form.payee" label="Payee" placeholder="Who gets paid, e.g. Netflix" @select="applyPayee" />

				<div class="field">
					<label class="label" for="subscription-amount">Amount</label>
					<input id="subscription-amount" v-model="form.amount" class="input tabular" inputmode="decimal" placeholder="0.00" required />
				</div>

				<div class="grid gap-4 sm:grid-cols-2">
					<div class="field">
						<label class="label" for="subscription-account">Account</label>
						<select id="subscription-account" v-model="form.accountId" class="input" required>
							<option value="" disabled>Select an account</option>
							<option v-for="account in ledger.activeAccounts" :key="account.id" :value="account.id">{{ account.name }}</option>
						</select>
					</div>

					<div class="field">
						<label class="label" for="subscription-category">Category</label>
						<select id="subscription-category" v-model="form.categoryId" class="input">
							<option value="">Uncategorized</option>
							<template v-for="group in categoryGroups" :key="group.parent.id">
								<option :value="group.parent.id">{{ group.parent.name }}</option>
								<option v-for="child in group.children" :key="child.id" :value="child.id">&nbsp;&nbsp;&nbsp;{{ child.name }}</option>
							</template>
						</select>
					</div>
				</div>

				<div class="field">
					<label class="label" for="subscription-start">{{ editing ? 'Next posts on' : 'Starts on' }}</label>
					<input
						id="subscription-start"
						v-model="form.startOn"
						type="date"
						class="input"
						:min="editing && editing.nextRunOn < earliest ? undefined : earliest"
						required
					/>
					<p class="mt-1 text-xs text-on-surface-variant">
						Posts at 00:00 UTC on this day each month; a month too short for it posts on its last day.
						<template v-if="editing">Changing the date restarts the schedule from it.</template>
					</p>
				</div>

				<div class="field">
					<label class="label" for="subscription-notes">Notes</label>
					<input id="subscription-notes" v-model="form.notes" class="input" placeholder="Optional" />
				</div>

				<p v-if="error" class="banner-error" role="alert">
					{{ error }}
				</p>

				<div class="flex justify-end gap-2 pt-2">
					<button type="button" class="btn-text" @click="dialogOpen = false">Cancel</button>
					<button type="submit" class="btn-primary" :disabled="submitting">
						{{ editing ? 'Save changes' : 'Add subscription' }}
					</button>
				</div>
			</form>
		</ModalDialog>

		<FabButton label="Add subscription" :disabled="!ledger.activeAccounts.length" @click="openCreate" />
	</div>
</template>
