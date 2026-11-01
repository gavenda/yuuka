<script setup lang="ts">
import ConnectedButtonGroup from '@/components/ConnectedButtonGroup.vue';
import FieldSupport from '@/components/FieldSupport.vue';
import FormDialog from '@/components/FormDialog.vue';
import MoneyText from '@/components/MoneyText.vue';
import PickerField from '@/components/PickerField.vue';
import SecurityInput from '@/components/SecurityInput.vue';
import SelectField from '@/components/SelectField.vue';
import TextField from '@/components/TextField.vue';
import { ApiError } from '@/lib/api';
import { minCommission, roundsFeesUp } from '@/lib/banks';
import { currentTime, today } from '@/lib/dates';
import {
	boardLot,
	dividendTax,
	parsePrice,
	parseShares,
	positions,
	priceInput,
	pseFees,
	sharesHeldBefore,
	valueOf,
} from '@/lib/investments';
import { currencySymbol } from '@/lib/money';
import { MASK, useAmountVisibility } from '@/lib/privacy';
import { namedOptions } from '@/lib/selectOptions';
import { showSnackbar } from '@/lib/snackbar';
import { supportId, useFormValidation } from '@/lib/validation';
import { useInvestmentStore } from '@/stores/investments';
import type { Security, Trade, TradeKind } from '@/types';
import { computed, reactive, ref, watch } from 'vue';
import { t } from '@/i18n';

/**
 * A buy, a sell or a cash dividend (`TradeForm` on Android). The stock comes first and fills in the broker account
 * that holds it and its last close; the fees a PSE broker charges (or a dividend's final tax) are worked out as
 * the shares and price are typed, so what the trade costs or brings in is known before it is saved. Given
 * `editing`, it opens on that trade and puts it right, its cash leg with it.
 */
const props = defineProps<{ open: boolean; editing?: Trade | null; initialSymbol?: string | null; initialAccount?: string | null }>();
const emit = defineEmits<{ close: [] }>();

const store = useInvestmentStore();
const { hidden } = useAmountVisibility();

const KINDS: { value: TradeKind; label: string }[] = [
	{ value: 'buy', label: t('investments.buy') },
	{ value: 'sell', label: t('investments.sell') },
	{ value: 'dividend', label: t('investments.dividend') },
];

const form = reactive({
	kind: 'buy' as TradeKind,
	symbol: '',
	accountId: '',
	occurredOn: today(),
	occurredTime: currentTime(),
	shares: '',
	price: '',
});
const opened = ref('');
const error = ref<string | null>(null);
const dirty = computed(() => JSON.stringify(form) !== opened.value);

const security = computed(() => store.securitiesBySymbol.get(form.symbol) ?? null);
const held = computed(() =>
	store.holdings.find((holding) => holding.symbol === form.symbol && holding.position.accountId === form.accountId),
);
/** What can be sold now: what is held, plus what the sell being edited already takes out of it. */
const available = computed(() => {
	const editing = props.editing;
	const back = editing && editing.kind === 'sell' && editing.symbol === form.symbol && editing.accountId === form.accountId;
	return (held.value?.shares ?? 0) + (back ? editing.shares : 0);
});
/** What was held at the start of the day: what a dividend paid that day is most likely paid on. */
const heldBefore = computed(() =>
	form.symbol && form.accountId ? sharesHeldBefore(store.trades, form.accountId, form.symbol, form.occurredOn) : 0,
);

/**
 * What a dividend can be paid on: each stock in each broker account it was ever traded in, plus the one being
 * edited. Picking one picks its account too, so the account is named beside a stock only where the same stock is
 * in two accounts and the name is what tells them apart.
 */
const dividendSources = computed(() => {
	const names = new Map(store.accounts.map((account) => [account.id, account.name]));
	const keys = new Set([
		...store.trades.map((trade) => `${trade.accountId}|${trade.symbol}`),
		...(props.editing ? [`${props.editing.accountId}|${props.editing.symbol}`] : []),
	]);
	const pairs = [...keys]
		.map((key) => key.split('|') as [string, string])
		.filter(([accountId, symbol]) => names.has(accountId) && store.securitiesBySymbol.has(symbol));
	const counts = new Map<string, number>();
	for (const [, symbol] of pairs) counts.set(symbol, (counts.get(symbol) ?? 0) + 1);
	return pairs
		.sort(([a, x], [b, y]) => x.localeCompare(y) || (names.get(a) ?? '').localeCompare(names.get(b) ?? ''))
		.map(([accountId, symbol]) => {
			const name = store.securitiesBySymbol.get(symbol)!.name;
			const shared = (counts.get(symbol) ?? 0) > 1;
			return { value: `${accountId}|${symbol}`, label: `${symbol} · ${name}${shared ? ` · ${names.get(accountId)}` : ''}` };
		});
});
const dividendChoices = computed(() => [{ value: '', label: t('investments.chooseStock'), disabled: true }, ...dividendSources.value]);

/** The stock and the account a dividend is paid in, as one choice. */
const dividendSource = computed({
	get: () =>
		dividendSources.value.some((entry) => entry.value === `${form.accountId}|${form.symbol}`) ? `${form.accountId}|${form.symbol}` : '',
	set: (value: string) => {
		const [accountId, symbol] = value.split('|');
		if (!accountId || !symbol) return;
		form.accountId = accountId;
		form.symbol = symbol;
	},
});

const occurredOn = computed(() => (form.occurredTime ? `${form.occurredOn}T${form.occurredTime}` : form.occurredOn));
const shares = computed(() => parseShares(form.shares));
const price = computed(() => parsePrice(form.price));
const gross = computed(() => (shares.value !== null && price.value !== null ? valueOf(shares.value, price.value) : null));
const bank = computed(() => store.accounts.find((account) => account.id === form.accountId)?.bank);
const fees = computed(() =>
	gross.value === null ? null : pseFees(gross.value, form.kind === 'sell', minCommission(bank.value), roundsFeesUp(bank.value)),
);
const tax = computed(() => (gross.value === null ? 0 : dividendTax(gross.value)));

const sharesShown = (count: number) => (hidden.value ? MASK : count.toLocaleString('en-US'));

const validation = useFormValidation({
	'trade-stock': () => (security.value && (form.kind !== 'dividend' || dividendSource.value !== '') ? null : t('investments.chooseStock')),
	'trade-account': () => (store.accounts.some((account) => account.id === form.accountId) ? null : t('investments.chooseBroker')),
	'trade-shares': () => {
		if (shares.value === null) return t('investments.sharesWhole');
		if (form.kind === 'sell' && shares.value > available.value)
			return t('investments.sharesOverHeld', { shares: sharesShown(available.value) });
		return historyHolds() ? null : t('investments.sharesOverHistory');
	},
	'trade-price': () => (price.value === null ? t('investments.priceNumber') : null),
});
const { error: fieldError, touch } = validation;

/**
 * Whether the trades still stand with this one as the form has it: a back-dated sell, or a buy edited down or
 * moved after a sell that needs it, would sell more than is held at some point.
 */
function historyHolds(): boolean {
	if (shares.value === null || price.value === null || !form.accountId || !form.symbol) return true;
	const editing = props.editing;
	const now = new Date().toISOString();
	const trade: Trade = {
		id: editing?.id ?? '',
		accountId: form.accountId,
		accountName: null,
		symbol: form.symbol,
		name: null,
		kind: form.kind,
		occurredOn: occurredOn.value,
		shares: shares.value,
		price: price.value,
		fees: 0,
		amount: 0,
		transactionId: '',
		createdAt: editing?.createdAt ?? now,
		updatedAt: now,
	};
	return positions([...store.trades.filter((entry) => entry.id !== editing?.id), trade]) !== null;
}

watch(
	() => [props.open, props.editing?.id] as const,
	([open]) => {
		if (!open) return;
		const editing = props.editing;
		if (editing) {
			Object.assign(form, {
				kind: editing.kind,
				symbol: editing.symbol,
				accountId: editing.accountId,
				occurredOn: editing.occurredOn.slice(0, 10),
				occurredTime: editing.occurredOn.slice(11, 16),
				shares: String(editing.shares),
				price: priceInput(editing.price),
			});
		} else {
			const symbol = props.initialSymbol ?? '';
			const start = store.securitiesBySymbol.get(symbol);
			Object.assign(form, {
				kind: 'buy',
				symbol,
				accountId: props.initialAccount ?? [...store.brokerCash.keys()][0] ?? '',
				occurredOn: today(),
				occurredTime: currentTime(),
				shares: '',
				price: start ? priceInput(start.price) : '',
			});
		}
		error.value = null;
		validation.reset();
		opened.value = JSON.stringify(form);
	},
	{ immediate: true },
);

/**
 * A dividend is paid on what was held, so its shares are filled in from the history whenever the stock, the
 * account or the date changes — and left as they are otherwise, since a sell after the ex-date still earns the
 * dividend on the shares it sold. While those still read as the form opened with them, an edit keeps the shares it
 * was saved with.
 */
watch(
	() => [form.kind, form.symbol, form.accountId, form.occurredOn] as const,
	([kind, symbol, accountId, day]) => {
		if (kind !== 'dividend') return;
		const start = JSON.parse(opened.value || '{}') as Partial<typeof form>;
		if (start.kind === kind && start.symbol === symbol && start.accountId === accountId && start.occurredOn === day) return;
		form.shares = heldBefore.value > 0 ? String(heldBefore.value) : '';
	},
);

/** The stock fills in the account that already holds it, as a payee fills in the rest. */
function pick(picked: Security): void {
	form.symbol = picked.symbol;
	const holding = store.held.find((entry) => entry.symbol === picked.symbol);
	if (holding) form.accountId = holding.position.accountId;
	form.price = priceInput(picked.price);
	touch('trade-stock');
}

const lotHint = computed(() => {
	if (form.kind === 'dividend' || price.value === null) return undefined;
	const lot = boardLot(price.value);
	const odd = shares.value !== null && shares.value % lot !== 0;
	return t(odd ? 'investments.oddLotHint' : 'investments.boardLotHint', { lot: lot.toLocaleString('en-US') });
});

const accountChoices = computed(() => namedOptions(store.accounts));
const heldHint = computed(() =>
	held.value && held.value.shares > 0 ? t('investments.heldHint', { shares: sharesShown(held.value.shares) }) : undefined,
);

const gainOnSale = computed(() => {
	const holding = held.value;
	if (
		form.kind !== 'sell' ||
		!holding ||
		holding.shares === 0 ||
		shares.value === null ||
		shares.value > holding.shares ||
		!gross.value ||
		!fees.value
	)
		return null;
	return gross.value - fees.value.total - Math.round((holding.costBasis * shares.value) / holding.shares);
});

async function submit(): Promise<void> {
	if (!validation.isValid.value || shares.value === null || price.value === null || gross.value === null) return;
	error.value = null;
	try {
		const input = {
			accountId: form.accountId,
			symbol: form.symbol,
			name: security.value?.name ?? null,
			kind: form.kind,
			occurredOn: occurredOn.value,
			shares: shares.value,
			price: price.value,
			fees: form.kind === 'dividend' ? tax.value : (fees.value?.total ?? 0),
		};
		if (props.editing) await store.updateTrade(props.editing, input);
		else await store.createTrade(input);
		showSnackbar(t(props.editing ? 'investments.tradeUpdated' : 'investments.tradeAdded'));
		emit('close');
	} catch (caught) {
		error.value = caught instanceof ApiError ? caught.message : t('common.couldNotSave');
	}
}
</script>

<template>
	<FormDialog
		:open="open"
		:title="editing ? t('investments.editTrade') : t('investments.newTrade')"
		:save-enabled="validation.isValid.value"
		:dirty="dirty"
		@close="emit('close')"
		@save="submit"
	>
		<div class="contents" @input="validation.onInput">
			<ConnectedButtonGroup v-model="form.kind" :label="t('investments.tradeKind')" :options="KINDS" />

			<div v-if="form.kind === 'dividend'">
				<SelectField
					id="trade-stock"
					v-model="dividendSource"
					:label="t('investments.stock')"
					:options="dividendChoices"
					:invalid="Boolean(fieldError('trade-stock'))"
					:describedby="supportId('trade-stock')"
					@blur="touch('trade-stock')"
				/>
				<FieldSupport id="trade-stock" :error="fieldError('trade-stock')" />
			</div>
			<SecurityInput
				v-else
				:label="t('investments.stock')"
				:securities="store.securities"
				:selected="security"
				:error="fieldError('trade-stock')"
				@select="pick"
				@blur="touch('trade-stock')"
			/>

			<!-- A dividend's account comes with its stock. -->
			<div v-if="form.kind !== 'dividend'">
				<SelectField
					id="trade-account"
					v-model="form.accountId"
					:label="t('investments.broker')"
					:options="accountChoices"
					:invalid="Boolean(fieldError('trade-account'))"
					:describedby="supportId('trade-account')"
					@blur="touch('trade-account')"
				/>
				<FieldSupport id="trade-account" :error="fieldError('trade-account')" :hint="heldHint" />
			</div>

			<div class="flex gap-3">
				<PickerField id="trade-date" v-model="form.occurredOn" class="min-w-0 flex-1" :label="t('common.date')" type="date" />
				<PickerField
					id="trade-time"
					v-model="form.occurredTime"
					class="min-w-0 flex-1"
					:label="t('common.time')"
					type="time"
					:placeholder="t('common.optional')"
				/>
			</div>

			<TextField
				id="trade-shares"
				v-model="form.shares"
				:label="t('investments.shares')"
				inputmode="numeric"
				:hint="lotHint"
				:error="fieldError('trade-shares')"
				@blur="touch('trade-shares')"
			/>

			<TextField
				id="trade-price"
				v-model="form.price"
				:label="form.kind === 'dividend' ? t('investments.dividendPerShare') : t('investments.price')"
				inputmode="decimal"
				:prefix="currencySymbol('PHP')"
				:error="fieldError('trade-price')"
				@blur="touch('trade-price')"
			/>

			<!-- What the trade comes to, fee by fee, ending on the figure that moves the account's cash. -->
			<div v-if="gross !== null && fees" class="card mt-2 flex flex-col gap-2 p-5">
				<div class="flex justify-between">
					<span class="type-body-medium text-on-surface-variant">{{ t('investments.gross') }}</span>
					<MoneyText :amount="gross" class="type-body-large" />
				</div>
				<template v-if="form.kind === 'dividend'">
					<div class="flex justify-between">
						<span class="type-body-medium text-on-surface-variant">{{ t('investments.finalTax') }}</span>
						<MoneyText :amount="-tax" class="type-body-large" />
					</div>
					<hr class="border-outline-variant" />
					<div class="flex justify-between">
						<span class="type-body-medium text-on-surface-variant">{{ t('investments.netDividend') }}</span>
						<MoneyText :amount="gross - tax" class="type-title-medium" />
					</div>
				</template>
				<template v-else>
					<div class="flex justify-between">
						<span class="type-body-medium text-on-surface-variant">{{ t('investments.commission') }}</span>
						<MoneyText :amount="fees.commission" class="type-body-large" />
					</div>
					<div class="flex justify-between">
						<span class="type-body-medium text-on-surface-variant">{{ t('investments.vat') }}</span>
						<MoneyText :amount="fees.vat" class="type-body-large" />
					</div>
					<div class="flex justify-between">
						<span class="type-body-medium text-on-surface-variant">{{ t('investments.pseFee') }}</span>
						<MoneyText :amount="fees.pseFee" class="type-body-large" />
					</div>
					<div class="flex justify-between">
						<span class="type-body-medium text-on-surface-variant">{{ t('investments.sccp') }}</span>
						<MoneyText :amount="fees.sccp" class="type-body-large" />
					</div>
					<div v-if="form.kind === 'sell'" class="flex justify-between">
						<span class="type-body-medium text-on-surface-variant">{{ t('investments.salesTax') }}</span>
						<MoneyText :amount="fees.salesTax" class="type-body-large" />
					</div>
					<hr class="border-outline-variant" />
					<div class="flex justify-between">
						<span class="type-body-medium text-on-surface-variant">{{
							form.kind === 'sell' ? t('investments.netProceeds') : t('investments.totalCost')
						}}</span>
						<MoneyText :amount="form.kind === 'sell' ? gross - fees.total : gross + fees.total" class="type-title-medium" />
					</div>
					<div v-if="gainOnSale !== null" class="flex justify-between">
						<span class="type-body-medium text-on-surface-variant">{{ t('investments.gainOnSale') }}</span>
						<MoneyText :amount="gainOnSale" tone="signed-alert" explicit class="type-body-large" />
					</div>
				</template>
			</div>

			<p v-if="error" class="type-body-small text-error" role="alert">{{ error }}</p>
		</div>
	</FormDialog>
</template>
