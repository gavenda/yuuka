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
import { today } from '@/lib/dates';
import { boardLot, dividendTax, parsePrice, parseShares, priceInput, pseFees, valueOf } from '@/lib/investments';
import { currencySymbol } from '@/lib/money';
import { MASK, useAmountVisibility } from '@/lib/privacy';
import { namedOptions } from '@/lib/selectOptions';
import { showSnackbar } from '@/lib/snackbar';
import { supportId, useFormValidation } from '@/lib/validation';
import { useInvestmentStore } from '@/stores/investments';
import type { Security, TradeKind } from '@/types';
import { computed, reactive, ref, watch } from 'vue';
import { t } from '@/i18n';

/**
 * A buy, a sell or a cash dividend (`TradeForm` on Android). The stock comes first and fills in the broker account
 * that holds it and its last close; the fees a PSE broker charges (or a dividend's final tax) are worked out as
 * the shares and price are typed, so what the trade costs or brings in is known before it is saved.
 */
const props = defineProps<{ open: boolean; initialSymbol?: string | null; initialAccount?: string | null }>();
const emit = defineEmits<{ close: [] }>();

const store = useInvestmentStore();
const { hidden } = useAmountVisibility();

const KINDS: { value: TradeKind; label: string }[] = [
	{ value: 'buy', label: t('investments.buy') },
	{ value: 'sell', label: t('investments.sell') },
	{ value: 'dividend', label: t('investments.dividend') },
];

const form = reactive({ kind: 'buy' as TradeKind, symbol: '', accountId: '', occurredOn: today(), shares: '', price: '' });
const opened = ref('');
const error = ref<string | null>(null);
const dirty = computed(() => JSON.stringify(form) !== opened.value);

const security = computed(() => store.securitiesBySymbol.get(form.symbol) ?? null);
const held = computed(() =>
	store.holdings.find((holding) => holding.symbol === form.symbol && holding.position.accountId === form.accountId),
);
const shares = computed(() => parseShares(form.shares));
const price = computed(() => parsePrice(form.price));
const gross = computed(() => (shares.value !== null && price.value !== null ? valueOf(shares.value, price.value) : null));
const fees = computed(() => (gross.value === null ? null : pseFees(gross.value, form.kind === 'sell')));
const tax = computed(() => (gross.value === null ? 0 : dividendTax(gross.value)));

const sharesShown = (count: number) => (hidden.value ? MASK : count.toLocaleString('en-US'));

const validation = useFormValidation({
	'trade-stock': () => (security.value ? null : t('investments.chooseStock')),
	'trade-account': () => (store.accounts.some((account) => account.id === form.accountId) ? null : t('investments.chooseBroker')),
	'trade-shares': () => {
		if (shares.value === null) return t('investments.sharesWhole');
		const holding = held.value?.shares ?? 0;
		return form.kind !== 'buy' && shares.value > holding ? t('investments.sharesOverHeld', { shares: sharesShown(holding) }) : null;
	},
	'trade-price': () => (price.value === null ? t('investments.priceNumber') : null),
});
const { error: fieldError, touch } = validation;

watch(
	() => props.open,
	(open) => {
		if (!open) return;
		const symbol = props.initialSymbol ?? '';
		const start = store.securitiesBySymbol.get(symbol);
		Object.assign(form, {
			kind: 'buy',
			symbol,
			accountId: props.initialAccount ?? [...store.brokerCash.keys()][0] ?? '',
			occurredOn: today(),
			shares: '',
			price: start ? priceInput(start.price) : '',
		});
		error.value = null;
		validation.reset();
		opened.value = JSON.stringify(form);
	},
	{ immediate: true },
);

/** The stock fills in the account that already holds it, as a payee fills in the rest. */
function pick(picked: Security): void {
	form.symbol = picked.symbol;
	const holding = store.held.find((entry) => entry.symbol === picked.symbol);
	if (holding) form.accountId = holding.position.accountId;
	if (form.kind !== 'dividend') form.price = priceInput(picked.price);
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
		await store.createTrade({
			accountId: form.accountId,
			symbol: form.symbol,
			name: security.value?.name ?? null,
			kind: form.kind,
			occurredOn: form.occurredOn,
			shares: shares.value,
			price: price.value,
			fees: form.kind === 'dividend' ? tax.value : (fees.value?.total ?? 0),
		});
		showSnackbar(t('investments.tradeAdded'));
		emit('close');
	} catch (caught) {
		error.value = caught instanceof ApiError ? caught.message : t('common.couldNotSave');
	}
}
</script>

<template>
	<FormDialog
		:open="open"
		:title="t('investments.newTrade')"
		:save-enabled="validation.isValid.value"
		:dirty="dirty"
		@close="emit('close')"
		@save="submit"
	>
		<div class="contents" @input="validation.onInput">
			<ConnectedButtonGroup v-model="form.kind" :label="t('investments.tradeKind')" :options="KINDS" />

			<SecurityInput
				:label="t('investments.stock')"
				:securities="store.securities"
				:selected="security"
				:error="fieldError('trade-stock')"
				@select="pick"
				@blur="touch('trade-stock')"
			/>

			<div>
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

			<PickerField id="trade-date" v-model="form.occurredOn" :label="t('common.date')" type="date" />

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
