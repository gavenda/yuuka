-- yuuka schema
--
-- Money is stored as signed integers in the currency's minor unit (cents).
-- Outflows are negative, inflows are positive, so an account balance is just
-- starting_balance + SUM(amount).
--
-- Every row belongs to exactly one user, identified by their Auth0 `sub`. The
-- API reads that from a verified access token and filters every query by it.

CREATE TABLE users (
	id               TEXT PRIMARY KEY,
	-- The currency every aggregate figure is shown in: net worth, the monthly
	-- summary, budgets. Accounts carry their own currency for what they hold;
	-- this is what the reader is shown.
	display_currency TEXT NOT NULL DEFAULT 'PHP',
	created_at       TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ', 'now')),
	updated_at       TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ', 'now'))
);

-- Account types are the user's own vocabulary, not a fixed enum: everyone
-- starts with the usual set and is free to rename, add to or retire it.
CREATE TABLE account_types (
	id         TEXT PRIMARY KEY,
	user_id    TEXT NOT NULL REFERENCES users (id) ON DELETE CASCADE,
	name       TEXT NOT NULL,
	sort_order INTEGER NOT NULL DEFAULT 0,
	archived   INTEGER NOT NULL DEFAULT 0 CHECK (archived IN (0, 1)),
	created_at TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ', 'now')),
	updated_at TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ', 'now'))
);

CREATE UNIQUE INDEX account_types_user_name_idx ON account_types (user_id, name);
CREATE INDEX account_types_user_idx ON account_types (user_id, sort_order, name);

CREATE TABLE accounts (
	id               TEXT PRIMARY KEY,
	user_id          TEXT NOT NULL REFERENCES users (id) ON DELETE CASCADE,
	name             TEXT NOT NULL,
	-- No ON DELETE action: a type still in use must not vanish from under its
	-- accounts. The API checks first and explains, rather than letting the
	-- foreign key fail opaquely.
	type_id          TEXT NOT NULL REFERENCES account_types (id),
	currency         TEXT NOT NULL DEFAULT 'PHP',
	-- An optional http(s) image URL shown beside the account. Remote by design:
	-- nothing is fetched or stored server-side, the browser just loads it.
	logo_url         TEXT,
	starting_balance INTEGER NOT NULL DEFAULT 0,
	archived         INTEGER NOT NULL DEFAULT 0 CHECK (archived IN (0, 1)),
	created_at       TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ', 'now')),
	updated_at       TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ', 'now'))
);

CREATE INDEX accounts_user_idx ON accounts (user_id, archived, name);
CREATE INDEX accounts_type_idx ON accounts (type_id);

CREATE TABLE categories (
	id         TEXT PRIMARY KEY,
	user_id    TEXT NOT NULL REFERENCES users (id) ON DELETE CASCADE,
	name       TEXT NOT NULL,
	kind       TEXT NOT NULL CHECK (kind IN ('income', 'expense')),
	color      TEXT NOT NULL DEFAULT '#64748b',
	sort_order INTEGER NOT NULL DEFAULT 0,
	archived   INTEGER NOT NULL DEFAULT 0 CHECK (archived IN (0, 1)),
	created_at TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ', 'now')),
	updated_at TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ', 'now'))
);

-- Names are unique per user and kind: two people may both have a "Groceries".
CREATE UNIQUE INDEX categories_user_name_kind_idx ON categories (user_id, name, kind);
CREATE INDEX categories_user_kind_idx ON categories (user_id, kind, sort_order);

CREATE TABLE transactions (
	id          TEXT PRIMARY KEY,
	user_id     TEXT NOT NULL REFERENCES users (id) ON DELETE CASCADE,
	account_id  TEXT NOT NULL REFERENCES accounts (id) ON DELETE CASCADE,
	-- Deleting a category never destroys spending history; the rows simply
	-- become uncategorised.
	category_id TEXT REFERENCES categories (id) ON DELETE SET NULL,
	amount      INTEGER NOT NULL,
	occurred_on TEXT NOT NULL,
	payee       TEXT NOT NULL DEFAULT '',
	notes       TEXT NOT NULL DEFAULT '',
	-- The two legs of a transfer share one id, so neither can be orphaned.
	transfer_id TEXT,
	created_at  TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ', 'now')),
	updated_at  TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ', 'now'))
);

-- Every transaction lookup starts from the owner, so each index leads with it.
CREATE INDEX transactions_user_date_idx     ON transactions (user_id, occurred_on DESC);
CREATE INDEX transactions_user_account_idx  ON transactions (user_id, account_id, occurred_on DESC);
CREATE INDEX transactions_user_category_idx ON transactions (user_id, category_id, occurred_on DESC);
CREATE INDEX transactions_transfer_idx      ON transactions (transfer_id);

-- One planned amount per category per month ('YYYY-MM').
CREATE TABLE budgets (
	id          TEXT PRIMARY KEY,
	user_id     TEXT NOT NULL REFERENCES users (id) ON DELETE CASCADE,
	category_id TEXT NOT NULL REFERENCES categories (id) ON DELETE CASCADE,
	month       TEXT NOT NULL,
	amount      INTEGER NOT NULL DEFAULT 0,
	created_at  TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ', 'now')),
	updated_at  TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ', 'now'))
);

CREATE UNIQUE INDEX budgets_category_month_idx ON budgets (category_id, month);
CREATE INDEX budgets_user_month_idx ON budgets (user_id, month);
