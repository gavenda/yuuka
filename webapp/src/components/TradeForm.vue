<script setup lang="ts">
import { Card } from 'tailwind-material3';
import ConnectedButtonGroup from '@/components/ConnectedButtonGroup.vue';
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
	isFund,
	parsePrice,
	parseUnits,
	positions,
	priceInput,
	pseFees,
	tradeWord,
	UNIT,
	unitsFor,
	unitsHeldBefore,
	unitsInput,
	valueOf,
} from '@/lib/investments';
import { currencySymbol, parseMoney, toDecimalString } from '@/lib/money';
import { displayUnits } from '@/lib/privacy';
import { namedOptions } from '@/lib/selectOptions';
import { showSnackbar } from '@/lib/snackbar';
import { useFormValidation } from '@/lib/validation';
import { useInvestmentStore } from '@/stores/investments';
import type { Security, Trade, TradeKind } from '@/types';
import { computed, reactive, ref, watch } from 'vue';
import { t } from '@/i18n';

/**
 * A buy, a sell or a cash dividend of a PSE stock, or a subscription, a redemption or a distribution of a UITF
 * (`TradeForm` on Android). The security comes first and fills in the account that holds it and its last close
 * or NAVPU.
 *
 * For a stock, the fees a PSE broker charges (or a dividend's final tax) are worked out as the shares and price
 * are typed, so what the trade costs or brings in is known before it is saved. A fund is bought for an amount,
 * as a trust bank sells it: the amount and the NAVPU fill in the units, which stay editable to match the
 * confirmation, and the amount is the cash. Its fees, if any, are typed. Given `editing`, it opens on that trade
 * and puts it right, its cash leg with it.
 */
const props = defineProps<{ open: boolean; editing?: Trade | null; initialSymbol?: string | null; initialAccount?: string | null }>();
const emit = defineEmits<{ close: [] }>();

const store = useInvestmentStore();

const form = reactive({
	kind: 'buy' as TradeKind,
	symbol: '',
	accountId: '',
	occurredOn: today(),
	occurredTime: currentTime(),
	units: '',
	price: '',
	/** A fund's subscription or redemption: the cash, as the trust bank said it. */
	amount: '',
	/** A fund's fees, typed; a stock's are worked out. */
	fees: '',
});
const opened = ref('');
const error = ref<string | null>(null);
const dirty = computed(() => JSON.stringify(form) !== opened.value);

const fund = computed(() => isFund(form.symbol));
/** A fund's subscription or redemption is entered as an amount, which the units follow. */
const byAmount = computed(() => fund.value && form.kind !== 'dividend');

const KINDS = computed<{ value: TradeKind; label: string }[]>(() =>
	(['buy', 'sell', 'dividend'] as const).map((kind) => ({ value: kind, label: t(`investments.${tradeWord(kind, form.symbol)}`) })),
);

const security = computed(() => store.securitiesBySymbol.get(form.symbol) ?? null);
const held = computed(() =>
	store.holdings.find((holding) => holding.symbol === form.symbol && holding.position.accountId === form.accountId),
);
/** What can be sold now: what is held, plus what the sell being edited already takes out of it. */
const available = computed(() => {
	const editing = props.editing;
	const back = editing && editing.kind === 'sell' && editing.symbol === form.symbol && editing.accountId === form.accountId;
	return (held.value?.units ?? 0) + (back ? editing.units : 0);
});
/** What was held at the start of the day: what a dividend or a distribution paid that day is most likely paid on. */
const heldBefore = computed(() =>
	form.symbol && form.accountId ? unitsHeldBefore(store.trades, form.accountId, form.symbol, form.occurredOn) : 0,
);

/**
 * What a dividend or a distribution can be paid on: each security in each account it was ever traded in, plus the
 * one being edited. Picking one picks its account too, so the account is named beside a security only where the
 * same one is in two accounts and the name is what tells them apart.
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
			const label = isFund(symbol) ? name : `${symbol} · ${name}`;
			return { value: `${accountId}|${symbol}`, label: `${label}${shared ? ` · ${names.get(accountId)}` : ''}` };
		});
});
const dividendChoices = computed(() => [{ value: '', label: t('investments.chooseSecurity'), disabled: true }, ...dividendSources.value]);

/** The security and the account a dividend is paid in, as one choice. */
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
const units = computed(() => parseUnits(form.units, !fund.value));
const price = computed(() => parsePrice(form.price));
const amount = computed(() => {
	const value = parseMoney(form.amount);
	return value !== null && value > 0 ? value : null;
});
/** A fund's typed fees: blank is none. */
const typedFees = computed(() => {
	if (form.fees.trim() === '') return 0;
	const value = parseMoney(form.fees);
	return value !== null && value >= 0 ? value : null;
});
/** The cash before fees: a fund's amount, or the units at the price. */
const gross = computed(() => {
	if (byAmount.value) return amount.value;
	return units.value !== null && price.value !== null ? valueOf(units.value, price.value) : null;
});
const bank = computed(() => store.accounts.find((account) => account.id === form.accountId)?.bank);
/** A stock's PSE fees; a fund has no schedule. */
const fees = computed(() =>
	gross.value === null || fund.value
		? null
		: pseFees(gross.value, form.kind === 'sell', minCommission(bank.value), roundsFeesUp(bank.value)),
);
const tax = computed(() => (gross.value === null ? 0 : dividendTax(gross.value)));
/** What comes off the gross: a fund's typed fees, a stock dividend's final tax, or a stock trade's PSE fees. */
const feeTotal = computed(() => {
	if (fund.value) return typedFees.value ?? 0;
	return form.kind === 'dividend' ? tax.value : (fees.value?.total ?? 0);
});

const validation = useFormValidation({
	'trade-stock': () =>
		security.value && (form.kind !== 'dividend' || dividendSource.value !== '') ? null : t('investments.chooseSecurity'),
	'trade-account': () =>
		store.accounts.some((account) => account.id === form.accountId)
			? null
			: t(fund.value ? 'investments.chooseAccount' : 'investments.chooseBroker'),
	'trade-amount': () => (!byAmount.value || amount.value !== null ? null : t('investments.amountNumber')),
	'trade-units': () => {
		if (units.value === null) return t(fund.value ? 'investments.unitsNumber' : 'investments.sharesWhole');
		if (form.kind === 'sell' && units.value > available.value)
			return t('investments.sharesOverHeld', { shares: displayUnits(available.value) });
		return historyHolds() ? null : t(fund.value ? 'investments.unitsOverHistory' : 'investments.sharesOverHistory');
	},
	'trade-price': () => (price.value === null ? t('investments.priceNumber') : null),
	'trade-fees': () => {
		if (!fund.value) return null;
		if (typedFees.value === null) return t('investments.feesNumber');
		const net = gross.value === null ? null : form.kind === 'buy' ? gross.value + typedFees.value : gross.value - typedFees.value;
		return net !== null && net <= 0 ? t('investments.feesTooHigh') : null;
	},
});
const { error: fieldError, touch } = validation;

/**
 * Whether the trades still stand with this one as the form has it: a back-dated sell, or a buy edited down or
 * moved after a sell that needs it, would sell more than is held at some point.
 */
function historyHolds(): boolean {
	if (units.value === null || price.value === null || !form.accountId || !form.symbol) return true;
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
		units: units.value,
		price: price.value,
		gross: byAmount.value ? amount.value : null,
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
			const editingFund = isFund(editing.symbol);
			const cash = editing.gross ?? valueOf(editing.units, editing.price);
			Object.assign(form, {
				kind: editing.kind,
				symbol: editing.symbol,
				accountId: editing.accountId,
				occurredOn: editing.occurredOn.slice(0, 10),
				occurredTime: editing.occurredOn.slice(11, 16),
				units: unitsInput(editing.units),
				price: priceInput(editing.price),
				amount: editingFund && editing.kind !== 'dividend' ? toDecimalString(cash) : '',
				fees: editingFund && editing.fees > 0 ? toDecimalString(editing.fees) : '',
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
				units: '',
				price: start ? priceInput(start.price) : '',
				amount: '',
				fees: '',
			});
		}
		error.value = null;
		validation.reset();
		opened.value = JSON.stringify(form);
	},
	{ immediate: true },
);

/** The fields as the form opened, to tell a change the person made from the form being filled in. */
const openedAs = () => JSON.parse(opened.value || '{}') as Partial<typeof form>;

/**
 * A dividend is paid on what was held, so its units are filled in from the history whenever the security, the
 * account or the date changes — and left as they are otherwise, since a sell after the ex-date still earns the
 * dividend on the shares it sold. While those still read as the form opened with them, an edit keeps the units it
 * was saved with.
 */
watch(
	() => [form.kind, form.symbol, form.accountId, form.occurredOn] as const,
	([kind, symbol, accountId, day]) => {
		if (kind !== 'dividend') return;
		const start = openedAs();
		if (start.kind === kind && start.symbol === symbol && start.accountId === accountId && start.occurredOn === day) return;
		form.units = heldBefore.value > 0 ? unitsInput(heldBefore.value) : '';
	},
);

/**
 * A fund's units follow its amount and NAVPU, as the trust bank works them out, whenever either changes; they are
 * left editable for a confirmation that rounded them otherwise. While both still read as the form opened with
 * them, an edit keeps the units it was saved with.
 */
watch(
	() => [byAmount.value, form.amount, form.price] as const,
	([entered, typed, nav]) => {
		if (!entered || amount.value === null || price.value === null) return;
		const start = openedAs();
		if (start.amount === typed && start.price === nav && start.symbol === form.symbol) return;
		form.units = unitsInput(unitsFor(amount.value, price.value));
	},
);

/** The security fills in the account that already holds it, as a payee fills in the rest. */
function pick(picked: Security): void {
	form.symbol = picked.symbol;
	const holding = store.held.find((entry) => entry.symbol === picked.symbol);
	if (holding) form.accountId = holding.position.accountId;
	form.price = priceInput(picked.price);
	touch('trade-stock');
}

const lotHint = computed(() => {
	if (fund.value) return byAmount.value ? t('investments.unitsHint') : undefined;
	if (form.kind === 'dividend' || price.value === null) return undefined;
	const lot = boardLot(price.value);
	const odd = units.value !== null && (units.value / UNIT) % lot !== 0;
	return t(odd ? 'investments.oddLotHint' : 'investments.boardLotHint', { lot: lot.toLocaleString('en-US') });
});

const accountChoices = computed(() => namedOptions(store.accounts));
const heldHint = computed(() =>
	held.value && held.value.units > 0 ? t('investments.heldHint', { shares: displayUnits(held.value.units) }) : undefined,
);

const priceLabel = computed(() => {
	if (form.kind === 'dividend') return t(fund.value ? 'investments.distributionPerUnit' : 'investments.dividendPerShare');
	return t(fund.value ? 'investments.navpu' : 'investments.price');
});

/** The figure the trade moves the account's cash by, and what it is called. */
const net = computed(() => {
	if (gross.value === null) return null;
	if (form.kind === 'buy') return { label: t('investments.totalCost'), amount: gross.value + feeTotal.value };
	if (form.kind === 'sell') return { label: t('investments.netProceeds'), amount: gross.value - feeTotal.value };
	return { label: t(fund.value ? 'investments.netDistribution' : 'investments.netDividend'), amount: gross.value - feeTotal.value };
});

const gainOnSale = computed(() => {
	const holding = held.value;
	if (form.kind !== 'sell' || !holding || holding.units === 0 || units.value === null || units.value > holding.units || !gross.value)
		return null;
	return gross.value - feeTotal.value - Math.round((holding.costBasis * units.value) / holding.units);
});

async function submit(): Promise<void> {
	if (!validation.isValid.value || units.value === null || price.value === null || gross.value === null) return;
	error.value = null;
	try {
		const input = {
			accountId: form.accountId,
			symbol: form.symbol,
			name: security.value?.name ?? null,
			kind: form.kind,
			occurredOn: occurredOn.value,
			units: units.value,
			price: price.value,
			gross: byAmount.value ? amount.value : null,
			fees: feeTotal.value,
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
					:label="t('investments.security')"
					:options="dividendChoices"
					:error="fieldError('trade-stock')"
					@blur="touch('trade-stock')"
				/>
			</div>
			<SecurityInput
				v-else
				:label="t('investments.security')"
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
					:label="fund ? t('investments.fundAccount') : t('investments.broker')"
					:options="accountChoices"
					:hint="heldHint"
					:error="fieldError('trade-account')"
					@blur="touch('trade-account')"
				/>
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

			<!-- A fund is bought for an amount and a NAVPU, which the units follow; a stock for a number of shares at a price. -->
			<TextField
				v-if="byAmount"
				id="trade-amount"
				v-model="form.amount"
				:label="t('investments.amount')"
				inputmode="decimal"
				:prefix="currencySymbol('PHP')"
				:error="fieldError('trade-amount')"
				@blur="touch('trade-amount')"
			/>

			<TextField
				v-if="fund"
				id="trade-price"
				v-model="form.price"
				:label="priceLabel"
				inputmode="decimal"
				:prefix="currencySymbol('PHP')"
				:error="fieldError('trade-price')"
				@blur="touch('trade-price')"
			/>

			<TextField
				id="trade-units"
				v-model="form.units"
				:label="fund ? t('investments.units') : t('investments.shares')"
				:inputmode="fund ? 'decimal' : 'numeric'"
				:hint="lotHint"
				:error="fieldError('trade-units')"
				@blur="touch('trade-units')"
			/>

			<TextField
				v-if="!fund"
				id="trade-price"
				v-model="form.price"
				:label="priceLabel"
				inputmode="decimal"
				:prefix="currencySymbol('PHP')"
				:error="fieldError('trade-price')"
				@blur="touch('trade-price')"
			/>

			<TextField
				v-if="fund"
				id="trade-fees"
				v-model="form.fees"
				:label="t('investments.fees')"
				inputmode="decimal"
				:prefix="currencySymbol('PHP')"
				:placeholder="t('common.optional')"
				:hint="t('investments.feesHint')"
				:error="fieldError('trade-fees')"
				@blur="touch('trade-fees')"
			/>

			<!-- What the trade comes to, fee by fee, ending on the figure that moves the account's cash. -->
			<Card :padded="false" v-if="gross !== null && net" class="mt-2 flex flex-col gap-2 p-5">
				<div class="flex justify-between">
					<span class="type-body-medium text-on-surface-variant">{{ t('investments.gross') }}</span>
					<MoneyText :amount="gross" class="type-body-large" />
				</div>
				<div v-if="fund" class="flex justify-between">
					<span class="type-body-medium text-on-surface-variant">{{ t('investments.fees') }}</span>
					<MoneyText :amount="form.kind === 'buy' ? feeTotal : -feeTotal" class="type-body-large" />
				</div>
				<div v-else-if="form.kind === 'dividend'" class="flex justify-between">
					<span class="type-body-medium text-on-surface-variant">{{ t('investments.finalTax') }}</span>
					<MoneyText :amount="-tax" class="type-body-large" />
				</div>
				<template v-else-if="fees">
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
				</template>
				<hr class="border-outline-variant" />
				<div class="flex justify-between">
					<span class="type-body-medium text-on-surface-variant">{{ net.label }}</span>
					<MoneyText :amount="net.amount" class="type-title-medium" />
				</div>
				<div v-if="gainOnSale !== null" class="flex justify-between">
					<span class="type-body-medium text-on-surface-variant">{{ t('investments.gainOnSale') }}</span>
					<MoneyText :amount="gainOnSale" tone="signed-alert" explicit class="type-body-large" />
				</div>
			</Card>

			<p v-if="error" class="type-body-small text-error" role="alert">{{ error }}</p>
		</div>
	</FormDialog>
</template>
