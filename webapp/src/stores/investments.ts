import { api, isNetworkError } from '@/lib/api';
import { readPrices, readSecurities, readTrades, replaceSecurities, replaceTrades, savePrices } from '@/lib/cache';
import { holdings as holdingsOf, positions, type Holding } from '@/lib/investments';
import { useLedgerStore } from '@/stores/ledger';
import type { PricePoint, Security, Trade } from '@/types';
import { defineStore } from 'pinia';
import { computed, ref } from 'vue';
import { t } from '@/i18n';

/**
 * PSE investments: every stock at its latest close, the person's trades, and the closes of the stocks whose
 * screens have been opened. The twin of Android's `InvestmentRepository` and `InvestmentsViewModel`. Shown from
 * the browser's local copy while the API is asked, as the subscriptions are.
 */
export const useInvestmentStore = defineStore('investments', () => {
	const securities = ref<Security[]>([]);
	const trades = ref<Trade[]>([]);
	const prices = ref<Record<string, PricePoint[]>>({});
	const loaded = ref(false);
	const loading = ref(false);
	const error = ref<string | null>(null);
	let synced = false;
	let revision = 0;

	const ledger = useLedgerStore();

	const securitiesBySymbol = computed(() => new Map(securities.value.map((security) => [security.symbol, security])));

	/** Every position, sold-out ones included. */
	const holdings = computed<Holding[]>(() =>
		holdingsOf(trades.value, securitiesBySymbol.value, (id) => {
			const account = ledger.accounts.find((entry) => entry.id === id);
			return account ? { name: account.name, bank: account.bank } : undefined;
		}),
	);

	/** The positions still holding shares. */
	const held = computed(() => holdings.value.filter((holding) => holding.shares > 0));

	/** The accounts a trade can be made in: active ones holding pesos. */
	const accounts = computed(() => ledger.accounts.filter((account) => !account.archived && account.currency === 'PHP'));

	/** The cash each broker account in use holds. */
	const brokerCash = computed(() => {
		const cash = new Map<string, number>();
		for (const trade of trades.value) {
			if (!cash.has(trade.accountId))
				cash.set(trade.accountId, ledger.accounts.find((account) => account.id === trade.accountId)?.balance ?? 0);
		}
		return cash;
	});

	/** The latest trading day a held stock was priced at. */
	const pricesAsOf = computed(() =>
		held.value.reduce<string | null>(
			(latest, holding) => (holding.asOf && (!latest || holding.asOf > latest) ? holding.asOf : latest),
			null,
		),
	);

	const yearStart = () => `${new Date().getFullYear()}-01-01`;

	/** What selling has realised since 1 January, average cost. */
	const realisedThisYear = computed(() => {
		const sum = (list: Trade[]) => (positions(list) ?? []).reduce((total, position) => total + position.realised, 0);
		return sum(trades.value) - sum(trades.value.filter((trade) => trade.occurredOn < yearStart()));
	});

	/** Dividends received since 1 January, after the final tax. */
	const dividendsThisYear = computed(() =>
		trades.value
			.filter((trade) => trade.kind === 'dividend' && trade.occurredOn >= yearStart())
			.reduce((total, trade) => total + trade.amount, 0),
	);

	async function showCopy(): Promise<void> {
		const asked = revision;
		const [cachedSecurities, cachedTrades] = await Promise.all([readSecurities(), readTrades()]);
		if (revision !== asked || (!cachedSecurities && !cachedTrades)) return;
		revision++;
		securities.value = cachedSecurities ?? [];
		trades.value = cachedTrades ?? [];
		loaded.value = true;
	}

	/** Resolves whether the API answered; a failure is said in `error` rather than thrown. */
	async function load(force = false): Promise<boolean> {
		if (synced && !force) return true;
		loading.value = true;
		error.value = null;
		const copy = loaded.value ? null : showCopy();
		try {
			const [freshSecurities, freshTrades] = await Promise.all([api.listSecurities(), api.listTrades()]);
			revision++;
			securities.value = freshSecurities.securities;
			trades.value = freshTrades.trades;
			loaded.value = true;
			synced = true;
			void replaceSecurities(freshSecurities.securities);
			void replaceTrades(freshTrades.trades);
			return true;
		} catch (caught) {
			await copy;
			if (!(loaded.value && isNetworkError(caught))) {
				error.value = caught instanceof Error ? caught.message : t('investments.loadError');
			}
			return false;
		} finally {
			loading.value = false;
		}
	}

	/** The trades alone, which is all a write moves. */
	async function refreshTrades(): Promise<void> {
		try {
			const fresh = (await api.listTrades()).trades;
			revision++;
			trades.value = fresh;
			void replaceTrades(fresh);
		} catch {
			// Background refresh: the copy on screen stands.
		}
	}

	/** A year of a stock's closes: the copy first, then the API's answer folded in. */
	async function loadPrices(symbol: string): Promise<void> {
		const cached = await readPrices(symbol);
		if (cached && !prices.value[symbol]) prices.value = { ...prices.value, [symbol]: cached };
		const from = new Date();
		from.setFullYear(from.getFullYear() - 1);
		try {
			const fresh = (await api.prices(symbol, from.toISOString().slice(0, 10))).prices;
			const byDay = new Map((prices.value[symbol] ?? []).map((point) => [point.day, point]));
			for (const point of fresh) byDay.set(point.day, point);
			prices.value = { ...prices.value, [symbol]: [...byDay.values()].sort((a, b) => a.day.localeCompare(b.day)) };
			void savePrices(symbol, fresh);
		} catch {
			// Offline: the copy, if any, stands.
		}
	}

	async function createTrade(input: Parameters<typeof api.createTrade>[0]): Promise<void> {
		await api.createTrade(input);
		await showSaved();
	}

	async function deleteTrade(id: string): Promise<void> {
		await api.deleteTrade(id);
		await showSaved();
	}

	/** Whether the history still stands without this trade: a later sell may need its shares. */
	function canDelete(id: string): boolean {
		return positions(trades.value.filter((trade) => trade.id !== id)) !== null;
	}

	/** Shows the trades as the local copy now holds them — what a save has just written there. */
	async function showSaved(): Promise<void> {
		const cached = await readTrades();
		revision++;
		trades.value = cached ?? [];
		loaded.value = true;
		await ledger.hydrate();
	}

	function refresh(): Promise<boolean> {
		return load(true);
	}

	function reset(): void {
		revision++;
		securities.value = [];
		trades.value = [];
		prices.value = {};
		loaded.value = false;
		synced = false;
		error.value = null;
	}

	return {
		securities,
		securitiesBySymbol,
		trades,
		prices,
		holdings,
		held,
		accounts,
		brokerCash,
		pricesAsOf,
		realisedThisYear,
		dividendsThisYear,
		loaded,
		loading,
		error,
		load,
		refresh,
		refreshTrades,
		loadPrices,
		createTrade,
		deleteTrade,
		canDelete,
		showSaved,
		reset,
	};
});
