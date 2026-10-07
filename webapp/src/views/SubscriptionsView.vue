<script setup lang="ts">
import { DELETE } from '@/lib/icons';
import ActionIcon from '@/components/ActionIcon.vue';
import AlertDialog from '@/components/AlertDialog.vue';
import ConnectedButtonGroup from '@/components/ConnectedButtonGroup.vue';
import EmptyState from '@/components/EmptyState.vue';
import FabButton from '@/components/FabButton.vue';
import FieldSupport from '@/components/FieldSupport.vue';
import FormDialog from '@/components/FormDialog.vue';
import MoneyText from '@/components/MoneyText.vue';
import PayeeInput from '@/components/PayeeInput.vue';
import PickerField from '@/components/PickerField.vue';
import SelectField from '@/components/SelectField.vue';
import SwipeReveal from '@/components/SwipeReveal.vue';
import TextField from '@/components/TextField.vue';
import { api, ApiError } from '@/lib/api';
import { formatLongDate } from '@/lib/dates';
import { useHarmonised } from '@/lib/harmonise';
import { parseMoney, toDecimalString } from '@/lib/money';
import { categoryOptions, namedOptions } from '@/lib/selectOptions';
import { showSnackbar } from '@/lib/snackbar';
import { monthlyTotal, nextDay, scheduleLabel, utcToday } from '@/lib/subscriptions';
import { supportId, useFormValidation } from '@/lib/validation';
import { useLedgerStore } from '@/stores/ledger';
import { useSubscriptionStore } from '@/stores/subscriptions';
import type { Payee, Subscription } from '@/types';
import { computed, nextTick, onMounted, reactive, ref } from 'vue';
import { t } from '@/i18n';

type Direction = 'expense' | 'income';

const DIRECTIONS: { value: Direction; label: string }[] = [
	{ value: 'expense', label: t('common.expense') },
	{ value: 'income', label: t('common.income') },
];

const ledger = useLedgerStore();
const store = useSubscriptionStore();
const harmonised = useHarmonised();

const dialogOpen = ref(false);
const editing = ref<Subscription | null>(null);
/** A failure that belongs to no one field — the save itself went wrong. Each field's own problem is drawn beside it. */
const error = ref<string | null>(null);

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
/** What the whole form opened with, so that closing it can tell an entry from an untouched form. */
const opened = ref('');
const dirty = computed(() => JSON.stringify(form) !== opened.value);
/** The subscription a delete has been asked for, while the question is still open. */
const pendingDelete = ref<Subscription | null>(null);

/** The API measures "not in the past" against the UTC calendar, so the picker does too. */
const earliest = utcToday();

const validation = useFormValidation({
	payee: () =>
		form.payee.trim()
			? form.payee.trim().length > 120
				? t('subscriptions.payeeTooLong', { max: 120 })
				: null
			: t('subscriptions.payeeRequired'),
	'subscription-amount': () => {
		if (!form.amount.trim()) return t('subscriptions.amountRequired');
		const minor = parseMoney(form.amount);
		if (minor === null) return t('subscriptions.amountNumber');
		return minor > 0 ? null : t('subscriptions.amountPositive');
	},
	'subscription-account': () =>
		ledger.activeAccounts.some((account) => account.id === form.accountId) ? null : t('subscriptions.chooseAccount'),
	// A date left as it was is fine even when it has since slipped into the past; choosing a new one is not.
	'subscription-notes': () => (form.notes.trim().length > 500 ? t('subscriptions.notesTooLong', { max: 500 }) : null),
	'subscription-start': () => {
		if (!form.startOn) return t('subscriptions.dateRequired');
		const unchanged = editing.value !== null && form.startOn === initialStartOn;
		return form.startOn < earliest && !unchanged ? t('subscriptions.startPast') : null;
	},
});
const { error: fieldError, touch } = validation;
const describe = (id: string): string | undefined => (fieldError(id) ? supportId(id) : undefined);

const categoryGroups = computed(() =>
	ledger.groupForPicker(form.direction === 'income' ? ledger.incomeCategories : ledger.expenseCategories),
);
const accountChoices = computed(() => namedOptions(ledger.activeAccounts));
const categoryChoices = computed(() => categoryOptions(categoryGroups.value, { value: '', label: t('common.uncategorized') }));

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
	validation.reset();

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
	opened.value = JSON.stringify(form);
	dialogOpen.value = true;
}

function openEdit(subscription: Subscription): void {
	editing.value = subscription;
	error.value = null;
	validation.reset();
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
	opened.value = JSON.stringify(form);
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
	if (!validation.isValid.value) return;
	const minor = parseMoney(form.amount) as number;

	const payload: Record<string, unknown> = {
		accountId: form.accountId,
		categoryId: form.categoryId || null,
		amount: form.direction === 'expense' ? -minor : minor,
		payee: form.payee.trim(),
		notes: form.notes,
	};

	// Editing the date restarts the schedule from it; leaving it alone keeps the schedule as it is.
	if (!editing.value || form.startOn !== initialStartOn) payload.startOn = form.startOn;

	try {
		if (editing.value) await api.updateSubscription(editing.value.id, payload);
		else await api.createSubscription(payload);

		dialogOpen.value = false;
		showSnackbar(editing.value ? t('subscriptions.updated') : t('subscriptions.added'));
		await store.refresh();
	} catch (caught) {
		error.value = caught instanceof ApiError ? caught.message : t('common.couldNotSave');
	}
}

async function togglePaused(subscription: Subscription): Promise<void> {
	try {
		await api.updateSubscription(subscription.id, { enabled: !subscription.enabled });
		showSnackbar(subscription.enabled ? t('subscriptions.napping') : t('subscriptions.awake'));
		await store.refresh();
	} catch (caught) {
		store.error = caught instanceof ApiError ? caught.message : t('subscriptions.couldNotUpdate');
	}
}

async function remove(): Promise<void> {
	const subscription = pendingDelete.value;
	if (!subscription) return;
	pendingDelete.value = null;

	try {
		await api.deleteSubscription(subscription.id);
		showSnackbar(t('subscriptions.deleted'));
		await store.refresh();
	} catch (caught) {
		store.error = caught instanceof ApiError ? caught.message : t('subscriptions.couldNotDelete');
	}
}

/** "Monthly on the 19th · next Oct 19, 2026"; a paused one has no next run to name. */
function scheduleOf(subscription: Subscription): string {
	const schedule = scheduleLabel(subscription.dayOfMonth);
	return subscription.enabled ? t('subscriptions.next', { schedule, date: formatLongDate(subscription.nextRunOn) }) : schedule;
}
</script>

<template>
	<div class="pt-3 pb-24">
		<p v-if="store.error" class="type-body-medium px-4 py-2 text-error" role="alert">{{ store.error }}</p>

		<!-- What the active subscriptions come to in a month, signed like the amounts: a net outflow is negative. -->
		<div v-if="store.subscriptions.length" class="px-4 py-2">
			<div class="type-title-medium flex items-center justify-between gap-4">
				<h2>{{ t('subscriptions.totalPerMonth') }}</h2>
				<MoneyText :amount="total" :currency="ledger.displayCurrency" tone="signed-alert" />
			</div>
			<p v-if="pausedCount > 0" class="type-body-small">{{ t('subscriptions.pausedNotCounted', { count: pausedCount }) }}</p>
		</div>

		<EmptyState
			v-else-if="!store.loading"
			fill
			class="mx-4 my-2"
			:title="t('subscriptions.emptyTitle')"
			:description="t('subscriptions.emptyDescription')"
		/>

		<ul class="group-rows px-4">
			<li v-for="subscription in store.subscriptions" :key="subscription.id">
				<SwipeReveal>
					<template #actions>
						<ActionIcon
							:icon="subscription.enabled ? 'pause' : 'resume'"
							:label="t(subscription.enabled ? 'subscriptions.pause' : 'subscriptions.resume', { name: subscription.payee })"
							@click="togglePaused(subscription)"
						/>
						<ActionIcon
							icon="delete"
							:label="t('common.delete', { name: subscription.payee })"
							danger
							@click="pendingDelete = subscription"
						/>
					</template>

					<!-- The whole row opens the edit form. Spans, not blocks, because it is a button. -->
					<button type="button" class="group-row state-layer focus-ring cursor-pointer" @click="openEdit(subscription)">
						<span class="flex gap-4 px-4 py-3">
							<span class="flex min-w-0 flex-1 flex-col gap-1">
								<span class="flex items-center gap-2">
									<span
										v-if="subscription.categoryColor"
										class="size-2 shrink-0 rounded-full"
										:style="{ backgroundColor: harmonised(subscription.categoryColor) }"
										aria-hidden="true"
									/>
									<span class="type-title-medium truncate">{{ subscription.payee }}</span>
									<span v-if="!subscription.enabled" class="type-label-small shrink-0">{{ t('subscriptions.paused') }}</span>
								</span>
								<span v-if="subscription.accountName || subscription.categoryName" class="type-body-small truncate">
									{{ [subscription.accountName, subscription.categoryName].filter(Boolean).join(' · ') }}
								</span>
								<span class="type-body-small">{{ scheduleOf(subscription) }}</span>
							</span>

							<MoneyText
								:amount="subscription.amount"
								:currency="currencyOf(subscription)"
								tone="signed-alert"
								class="type-title-medium shrink-0"
							/>
						</span>
					</button>
				</SwipeReveal>
			</li>
		</ul>

		<FormDialog
			:open="dialogOpen"
			:title="editing ? t('subscriptions.editSubscription') : t('subscriptions.newSubscription')"
			:save-enabled="validation.isValid.value"
			:dirty="dirty"
			@close="dialogOpen = false"
			@save="submit"
		>
			<div class="contents" @input="validation.onInput">
				<ConnectedButtonGroup
					:model-value="form.direction"
					:label="t('subscriptions.direction')"
					:options="DIRECTIONS"
					@update:model-value="setDirection"
				/>

				<PayeeInput
					v-model="form.payee"
					:label="t('subscriptions.payee')"
					:placeholder="t('subscriptions.payeePlaceholder')"
					:error="fieldError('payee')"
					@select="applyPayee"
					@blur="touch('payee')"
				/>

				<TextField
					id="subscription-amount"
					v-model="form.amount"
					:label="t('common.amount')"
					placeholder="0.00"
					inputmode="decimal"
					:error="fieldError('subscription-amount')"
					@blur="touch('subscription-amount')"
				/>

				<div>
					<SelectField
						id="subscription-account"
						v-model="form.accountId"
						:label="t('common.account')"
						:options="accountChoices"
						:invalid="Boolean(fieldError('subscription-account'))"
						:describedby="describe('subscription-account')"
						@blur="touch('subscription-account')"
					/>
					<FieldSupport id="subscription-account" :error="fieldError('subscription-account')" />
				</div>

				<SelectField id="subscription-category" v-model="form.categoryId" :label="t('common.category')" :options="categoryChoices" />

				<div>
					<!-- A date left as it was is fine even when it has since slipped into the past; choosing a new one is not. -->
					<PickerField
						id="subscription-start"
						v-model="form.startOn"
						:label="editing ? t('subscriptions.nextPostsOn') : t('subscriptions.startsOn')"
						type="date"
						:min="editing && initialStartOn < earliest ? undefined : earliest"
						:invalid="Boolean(fieldError('subscription-start'))"
						describedby="subscription-start-support"
						@blur="touch('subscription-start')"
					/>
					<FieldSupport
						id="subscription-start"
						:error="fieldError('subscription-start')"
						:hint="editing ? `${t('subscriptions.startHint')} ${t('subscriptions.restartHint')}` : t('subscriptions.startHint')"
					/>
				</div>

				<TextField
					id="subscription-notes"
					v-model="form.notes"
					:label="t('common.notes')"
					:placeholder="t('common.optional')"
					:error="fieldError('subscription-notes')"
					@blur="touch('subscription-notes')"
				/>

				<p v-if="error" class="type-body-small text-error" role="alert">{{ error }}</p>
			</div>
		</FormDialog>

		<AlertDialog :open="pendingDelete !== null" :title="t('subscriptions.deleteTitle')" :icon="DELETE" @close="pendingDelete = null">
			{{ t('subscriptions.deleteBody', { name: pendingDelete?.payee ?? '' }) }}
			<template #actions>
				<button type="button" class="btn-text" @click="pendingDelete = null">{{ t('common.noGoBack') }}</button>
				<button type="button" class="btn-text text-error" @click="remove">{{ t('common.yesDelete') }}</button>
			</template>
		</AlertDialog>

		<!-- A subscription needs an account to post to, so there is nothing to lead with until there is one. -->
		<FabButton v-if="ledger.activeAccounts.length" :label="t('subscriptions.newSubscription')" @click="openCreate" />
	</div>
</template>
