-- PSE investments.
--
-- Market data is shared by everyone and owned by no one: what a stock closed at
-- is public, so `securities` and `stock_prices` carry no `user_id`. They are
-- filled from PHISIX by the cron (`server/investments.ts`) and only ever read
-- by the routes.
--
-- A trade is a person's, and it is two things at once: the trade itself (what
-- was bought or sold, how many, at what price, for what fees) and the cash it
-- moved in the broker account, which is an ordinary transaction. The trade
-- points at that transaction and goes with it, so deleting the cash leg from
-- the transaction list deletes the trade, as deleting one leg of a transfer
-- deletes both. Buy and sell legs only turn cash into shares and back, so the
-- summary leaves them out of income and spending; a dividend's leg is income.

-- A stock as of its latest close. Prices are in ten-thousandths of a peso: the
-- PSE's smallest tick is ₱0.0001, and money is never a float.
CREATE TABLE securities (
	symbol     TEXT PRIMARY KEY CHECK (length(symbol) BETWEEN 1 AND 12),
	name       TEXT NOT NULL,
	price_e4   INTEGER NOT NULL CHECK (price_e4 >= 0),
	-- The day's move in hundredths of a percent, as PHISIX reports it.
	change_bp  INTEGER,
	-- The trading day `price_e4` is the close of.
	as_of      TEXT NOT NULL CHECK (as_of GLOB '[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9]'),
	updated_at TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ', 'now'))
);

-- One close a stock a trading day: the latest from the daily refresh, the past
-- backfilled for the stocks someone holds.
CREATE TABLE stock_prices (
	symbol   TEXT NOT NULL,
	day      TEXT NOT NULL CHECK (day GLOB '[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9]'),
	close_e4 INTEGER NOT NULL CHECK (close_e4 >= 0),
	PRIMARY KEY (symbol, day)
) WITHOUT ROWID;

-- Weekdays PHISIX had no close for, so the backfill does not ask again.
CREATE TABLE stock_price_gaps (
	symbol TEXT NOT NULL,
	day    TEXT NOT NULL,
	PRIMARY KEY (symbol, day)
) WITHOUT ROWID;

CREATE TABLE trades (
	id             TEXT PRIMARY KEY,
	user_id        TEXT NOT NULL,
	account_id     TEXT NOT NULL,
	symbol         TEXT NOT NULL REFERENCES securities (symbol),
	kind           TEXT NOT NULL CHECK (kind IN ('buy', 'sell', 'dividend')),
	occurred_on    TEXT NOT NULL CHECK (occurred_on GLOB '[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9]'),
	-- Whole shares. A dividend's are the shares it was paid on.
	shares         INTEGER NOT NULL CHECK (shares > 0),
	-- The price a share, or for a dividend what it paid a share.
	price_e4       INTEGER NOT NULL CHECK (price_e4 > 0),
	-- Centavos: a buy's or sell's commission, VAT, PSE and SCCP fees and sales
	-- tax together, or a dividend's withheld final tax.
	fees           INTEGER NOT NULL DEFAULT 0 CHECK (fees >= 0),
	-- The cash it moved. Deleting that row deletes the trade.
	transaction_id TEXT NOT NULL UNIQUE REFERENCES transactions (id) ON DELETE CASCADE,
	created_at     TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ', 'now')),
	updated_at     TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ', 'now')),
	FOREIGN KEY (user_id, account_id) REFERENCES accounts (user_id, id) ON DELETE CASCADE
);

CREATE INDEX trades_user_idx ON trades (user_id, symbol, occurred_on);
