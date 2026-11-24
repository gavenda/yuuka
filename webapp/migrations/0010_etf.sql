-- ETFs beside PSE stocks and UITFs.
--
-- An exchange-traded fund bought through a foreign broker (Interactive Brokers) is held, priced and
-- traded as a stock is — a buy, a sell, a cash dividend, average cost — so it is a third kind of
-- security rather than another model. It differs in two ways, and they are what this adds:
--
-- 1. It is priced in its own currency: a US-listed ETF in dollars, a UCITS ETF on the LSE in dollars
--    or pounds. `currency` says which, and a trade of one is made in an account that holds it. Every
--    PSE stock and UITF is in pesos, the default.
-- 2. It trades on a market whose fees and dividend tax differ: `exchange` is `US` or `LSE` for an ETF
--    and NULL for a stock or a fund, which only ever trade in Manila.
--
-- Its symbol is `ETF-` and its Yahoo Finance symbol (`ETF-VOO`, `ETF-VWRA.L`); no PSE symbol has a
-- hyphen. ETFs are not all listed ahead of time — there are thousands — so one is added to `securities`
-- the first time someone looks it up (`server/etfs.ts`), and its prices are kept from then on.
--
-- SQLite cannot change a CHECK in place, so `securities` is made again with `kind` taking `etf`. `trades` names
-- it by symbol, so the rows are put aside, the table dropped and made afresh under the same name, and the rows put
-- back. With the foreign keys checked at the end, dropping it counts every trade as pointing nowhere and putting
-- the rows back counts each as found again. (Building a new table and renaming it would leave the trades
-- counted as pointing nowhere.)

PRAGMA defer_foreign_keys = true;

CREATE TABLE securities_kept AS SELECT * FROM securities;

DROP TABLE securities;

CREATE TABLE securities (
	symbol          TEXT PRIMARY KEY CHECK (length(symbol) BETWEEN 1 AND 12),
	name            TEXT NOT NULL,
	price_e4        INTEGER NOT NULL CHECK (price_e4 >= 0),
	change_bp       INTEGER,
	as_of           TEXT NOT NULL CHECK (as_of GLOB '[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9]'),
	updated_at      TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ', 'now')),
	logo_url        TEXT CHECK (logo_url IS NULL OR logo_url GLOB 'https://*'),
	logo_checked_at TEXT,
	kind            TEXT NOT NULL DEFAULT 'stock' CHECK (kind IN ('stock', 'fund', 'etf')),
	provider        TEXT,
	classification  TEXT,
	provider_id     INTEGER,
	-- What the price is in: PHP for a stock or a fund, an ETF's own currency otherwise.
	currency        TEXT NOT NULL DEFAULT 'PHP' CHECK (currency GLOB '[A-Z][A-Z][A-Z]'),
	-- Where an ETF trades, which decides its broker's fees and the tax on its dividends; NULL for the PSE.
	exchange        TEXT CHECK (exchange IS NULL OR exchange IN ('US', 'LSE'))
);

INSERT INTO securities (symbol, name, price_e4, change_bp, as_of, updated_at, logo_url, logo_checked_at, kind, provider, classification, provider_id)
SELECT symbol, name, price_e4, change_bp, as_of, updated_at, logo_url, logo_checked_at, kind, provider, classification, provider_id FROM securities_kept;

DROP TABLE securities_kept;
