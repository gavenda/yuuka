-- UITFs beside PSE stocks.
--
-- A unit investment trust fund is held, priced and traded as a stock is — bought (subscribed), sold
-- (redeemed) and paid out on (an income distribution) — so it is another kind of security rather than
-- another model. Two things differ, and they are what this changes:
--
-- 1. Units are fractional. A trade's quantity becomes `units_e4`, ten-thousandths of a unit, for every
--    trade alike: a stock's whole shares are multiplied up, so nothing else about a stock changes.
-- 2. A fund's cash is what the trust bank says it was. It sells ₱10,000.00 of a fund and rounds the
--    units it gives for it, so the units times the NAVPU can miss the ₱10,000.00 by a few centavos.
--    `gross` keeps the cash before fees when it is known; NULL works it out from the units and price,
--    which is all a stock ever does.
--
-- Fund prices are market data like a stock's, scraped from uitf.com.ph (`server/funds.ts`) into the
-- same `securities` and `stock_prices`. A fund's symbol is `UITF-` and its id on that site.

ALTER TABLE securities ADD COLUMN kind TEXT NOT NULL DEFAULT 'stock' CHECK (kind IN ('stock', 'fund'));
-- The trust entity that runs a fund, as uitf.com.ph names it; NULL for a stock.
ALTER TABLE securities ADD COLUMN provider TEXT;
-- What the fund invests in (Money Market Fund, Equity Fund, …); NULL for a stock.
ALTER TABLE securities ADD COLUMN classification TEXT;

ALTER TABLE trades RENAME COLUMN shares TO units_e4;
UPDATE trades SET units_e4 = units_e4 * 10000;
ALTER TABLE trades ADD COLUMN gross INTEGER CHECK (gross IS NULL OR gross > 0);

-- The trust entities uitf.com.ph lists NAVPUs by, read off its own picker. The cron asks one a tick,
-- whichever is most out of date, those someone holds a fund of first; `checked_at` is when it last did.
CREATE TABLE fund_providers (
	id         INTEGER PRIMARY KEY,
	name       TEXT NOT NULL,
	checked_at TEXT
);
-- Which provider a fund belongs to, so the cron knows whose page to ask for a held fund's price.
ALTER TABLE securities ADD COLUMN provider_id INTEGER;
