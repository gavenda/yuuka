-- Rebuild: the schema the current feature set actually wants.
--
-- Everything here is one D1 migration, so it is one transaction: if any step
-- fails the database is left exactly as it was. The old tables are copied into
-- unconstrained staging tables, dropped, recreated in their final shape, and
-- refilled from staging. That avoids `ALTER TABLE ... RENAME`, which rewrites
-- foreign key references in *other* tables and is treacherous when half the
-- schema is being replaced at once.
--
-- What changes, and why:
--
--   * `occurred_on` was two types in one column — a date, or a date with a
--     `THH:MM` time. Every aggregate worked around it with `substr()`, and
--     `?to=<day>` silently dropped that day's timed rows. Now a date column and
--     a separate nullable time.
--   * A transfer was two rows held together by a shared id and application
--     code. Now a `transfers` parent row: deleting either leg deletes the pair
--     through a trigger and a cascade, not through a DELETE the API remembers
--     to issue. It also carries `kind`, which retires the magic payee string
--     `'Save the Change'` as the only way to recognise a round-up.
--   * `automated` was a boolean where an origin was wanted. Now `source`.
--     Balance adjustments deliberately have no value of their own: the ledger
--     model says an adjustment must be indistinguishable from a typed row.
--   * Ownership was enforced only by the API writing careful SQL. A
--     transaction's account is now a composite foreign key, and a tag link
--     carries its owner, so a cross-user row cannot be written at all.
--   * The one-level category rule and a child's inherited kind and scope were
--     API-only. Now triggers.
--   * Fixed budgets and income plans were stored under a sentinel month
--     `'fixed'` — a string that is not a month, in a month column. Now NULL,
--     with partial unique indexes doing what the sentinel did.
--
-- Considered and rejected: `updated_at` triggers, which would let the routes
-- stop threading `NOW_SQL` through every update. The routes read
-- `meta.changes` to detect a guard that matched nothing, and making that
-- number depend on trigger behaviour is not worth saving a column assignment.
--
-- Also deliberately unchanged: money stays a signed integer in minor units;
-- `user_id` stays denormalised onto every row, because every read filters on
-- it directly and that is the security invariant; amounts in different
-- currencies are still summed as they stand, without conversion.

PRAGMA defer_foreign_keys = true;

-- ---------------------------------------------------------------- 1. staging
-- `CREATE TABLE ... AS SELECT` keeps the values and drops the constraints,
-- which is what makes the order of everything below irrelevant.

CREATE TABLE _mig_users            AS SELECT * FROM users;
CREATE TABLE _mig_account_types    AS SELECT * FROM account_types;
CREATE TABLE _mig_accounts         AS SELECT * FROM accounts;
CREATE TABLE _mig_categories       AS SELECT * FROM categories;
CREATE TABLE _mig_transactions     AS SELECT * FROM transactions;
CREATE TABLE _mig_budgets          AS SELECT * FROM budgets;
CREATE TABLE _mig_income_plans     AS SELECT * FROM income_plans;
CREATE TABLE _mig_tags             AS SELECT * FROM tags;
CREATE TABLE _mig_transaction_tags AS SELECT * FROM transaction_tags;
CREATE TABLE _mig_subscriptions    AS SELECT * FROM subscriptions;
CREATE TABLE _mig_payee_history    AS SELECT * FROM payee_history;
CREATE TABLE _mig_round_up_rules   AS SELECT * FROM round_up_rules;

-- ------------------------------------------------------------- 2. out with it

DROP TABLE transaction_tags;
DROP TABLE tags;
DROP TABLE payee_history;
DROP TABLE round_up_rules;
DROP TABLE subscriptions;
DROP TABLE income_plans;
DROP TABLE budgets;
DROP TABLE transactions;
DROP TABLE categories;
DROP TABLE accounts;
DROP TABLE account_types;
DROP TABLE users;

-- ----------------------------------------------------------- 3. the new shape

CREATE TABLE users (
	id                 TEXT PRIMARY KEY,
	-- The currency every aggregate figure is shown in. Accounts carry their own
	-- currency for what they hold; this is what the reader is shown.
	display_currency   TEXT NOT NULL DEFAULT 'PHP' CHECK (length(display_currency) = 3),
	budget_mode        TEXT NOT NULL DEFAULT 'fixed' CHECK (budget_mode IN ('fixed', 'monthly')),
	-- Resolved at DML time, so referring to `accounts` before it exists is fine.
	default_account_id TEXT REFERENCES accounts (id) ON DELETE SET NULL,
	created_at         TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ', 'now')),
	updated_at         TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ', 'now'))
);

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
	-- accounts. The API checks first and explains.
	type_id          TEXT NOT NULL REFERENCES account_types (id),
	currency         TEXT NOT NULL DEFAULT 'PHP' CHECK (length(currency) = 3),
	-- Loaded straight into an <img src>, so the scheme is constrained here as
	-- well as in the API: anything else invites `data:` payloads.
	logo_url         TEXT CHECK (logo_url IS NULL OR logo_url LIKE 'http://%' OR logo_url LIKE 'https://%'),
	logo_invert_dark INTEGER NOT NULL DEFAULT 0 CHECK (logo_invert_dark IN (0, 1)),
	round_up_source  INTEGER NOT NULL DEFAULT 0 CHECK (round_up_source IN (0, 1)),
	starting_balance INTEGER NOT NULL DEFAULT 0,
	archived         INTEGER NOT NULL DEFAULT 0 CHECK (archived IN (0, 1)),
	created_at       TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ', 'now')),
	updated_at       TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ', 'now')),
	-- What the transactions composite foreign key points at. `id` alone is
	-- already unique; this pair is what lets a child row name its owner too.
	UNIQUE (user_id, id)
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
	parent_id  TEXT REFERENCES categories (id) ON DELETE CASCADE,
	-- Where a category may be used: 'standard' categorises spending and income,
	-- 'transfer' categorises movements between the user's own accounts.
	applies_to TEXT NOT NULL DEFAULT 'standard' CHECK (applies_to IN ('standard', 'transfer')),
	created_at TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ', 'now')),
	updated_at TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ', 'now'))
);

-- Unique among siblings. COALESCE is load-bearing: SQLite treats NULLs as
-- distinct in a unique index, so indexing `parent_id` directly would let
-- duplicate top-level categories through, every one of them having a NULL parent.
CREATE UNIQUE INDEX categories_user_name_kind_parent_idx ON categories (user_id, name, kind, COALESCE(parent_id, ''));
CREATE INDEX categories_parent_idx ON categories (parent_id);
CREATE INDEX categories_user_applies_idx ON categories (user_id, applies_to, kind, sort_order);

-- A movement between the user's own accounts, recorded as a matching pair of
-- transactions. The parent row is what makes "exactly one owner" and "delete
-- one leg, lose both" structural rather than something the API remembers.
CREATE TABLE transfers (
	id         TEXT PRIMARY KEY,
	user_id    TEXT NOT NULL REFERENCES users (id) ON DELETE CASCADE,
	-- 'round_up' is a "Save the Change" posting. Recognising one used to mean
	-- matching a payee string.
	kind       TEXT NOT NULL DEFAULT 'manual' CHECK (kind IN ('manual', 'round_up')),
	created_at TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ', 'now'))
);

CREATE INDEX transfers_user_idx ON transfers (user_id);

CREATE TABLE transactions (
	id            TEXT PRIMARY KEY,
	user_id       TEXT NOT NULL,
	account_id    TEXT NOT NULL,
	-- Deleting a category never destroys history; the rows become uncategorised.
	category_id   TEXT REFERENCES categories (id) ON DELETE SET NULL,
	amount        INTEGER NOT NULL CHECK (amount <> 0),
	-- A date, and only a date.
	occurred_on   TEXT NOT NULL CHECK (occurred_on GLOB '[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9]'),
	-- `HH:MM`, or NULL for a row that names no time of day.
	occurred_time TEXT CHECK (
		occurred_time IS NULL
		OR (occurred_time GLOB '[0-9][0-9]:[0-9][0-9]'
		    AND CAST(substr(occurred_time, 1, 2) AS INTEGER) < 24
		    AND CAST(substr(occurred_time, 4, 2) AS INTEGER) < 60)
	),
	payee         TEXT NOT NULL DEFAULT '',
	notes         TEXT NOT NULL DEFAULT '',
	transfer_id   TEXT REFERENCES transfers (id) ON DELETE CASCADE,
	-- Where the row came from. There is no 'adjustment': a balance adjustment
	-- is an ordinary uncategorised transaction and must stay indistinguishable
	-- from one entered by hand.
	source        TEXT NOT NULL DEFAULT 'manual' CHECK (source IN ('manual', 'subscription', 'round_up')),
	created_at    TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ', 'now')),
	updated_at    TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ', 'now')),
	-- Every subscription run posts at 00:00 UTC, so it never names a time.
	CHECK (source <> 'subscription' OR occurred_time IS NULL),
	-- A round-up is posted as a transfer, always.
	CHECK (source <> 'round_up' OR transfer_id IS NOT NULL),
	-- The account must belong to the same user as the row. Previously this held
	-- only because every write said so.
	FOREIGN KEY (user_id, account_id) REFERENCES accounts (user_id, id) ON DELETE CASCADE,
	-- What `transaction_tags` points at, so a tag link names one owner for both
	-- of its sides. A composite foreign key needs a unique key to match.
	UNIQUE (user_id, id)
);

-- Every lookup starts from the owner, so each index leads with it. The date
-- pair is the list's sort order, so a page is an index scan.
CREATE INDEX transactions_user_date_idx     ON transactions (user_id, occurred_on DESC, occurred_time DESC, created_at DESC);
CREATE INDEX transactions_user_account_idx  ON transactions (user_id, account_id, occurred_on DESC, occurred_time DESC);
CREATE INDEX transactions_user_category_idx ON transactions (user_id, category_id, occurred_on DESC);
CREATE INDEX transactions_transfer_idx      ON transactions (transfer_id);
-- Covers `SUM(amount) GROUP BY account_id`: account balances and net worth are
-- read on every dashboard, and this keeps them off the table entirely.
CREATE INDEX transactions_account_amount_idx ON transactions (account_id, amount);

-- One plan per category. `month` NULL is the plan that answers every month;
-- a `YYYY-MM` belongs to that month alone. Which one a user reads and writes
-- is their `budget_mode`.
CREATE TABLE budgets (
	id          TEXT PRIMARY KEY,
	user_id     TEXT NOT NULL REFERENCES users (id) ON DELETE CASCADE,
	category_id TEXT NOT NULL REFERENCES categories (id) ON DELETE CASCADE,
	month       TEXT CHECK (month IS NULL OR month GLOB '[0-9][0-9][0-9][0-9]-[0-1][0-9]'),
	amount      INTEGER NOT NULL DEFAULT 0 CHECK (amount >= 0),
	-- Basis points (1 = 0.01%), so a share of planned income is stored exactly —
	-- the same reasoning that keeps money off floats. When set it takes
	-- precedence and `amount` stays 0.
	percent_bp  INTEGER CHECK (percent_bp IS NULL OR (percent_bp >= 0 AND percent_bp <= 10000)),
	created_at  TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ', 'now')),
	updated_at  TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ', 'now')),
	CHECK (percent_bp IS NULL OR amount = 0)
);

-- Two partial indexes in place of the sentinel month: one plan per category
-- for "every month", and one per category per named month.
CREATE UNIQUE INDEX budgets_default_idx ON budgets (user_id, category_id) WHERE month IS NULL;
CREATE UNIQUE INDEX budgets_month_idx   ON budgets (user_id, category_id, month) WHERE month IS NOT NULL;
CREATE INDEX budgets_user_month_idx     ON budgets (user_id, month);

CREATE TABLE income_plans (
	id           TEXT PRIMARY KEY,
	user_id      TEXT NOT NULL REFERENCES users (id) ON DELETE CASCADE,
	month        TEXT CHECK (month IS NULL OR month GLOB '[0-9][0-9][0-9][0-9]-[0-1][0-9]'),
	amount       INTEGER NOT NULL DEFAULT 0 CHECK (amount >= 0),
	-- Whether `amount` was typed or derived from a gross salary, so reopening
	-- the editor knows which mode it was in. `amount` is still the only figure
	-- anything budgets from.
	mode         TEXT NOT NULL DEFAULT 'fixed' CHECK (mode IN ('gross', 'fixed')),
	gross_amount INTEGER,
	created_at   TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ', 'now')),
	updated_at   TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ', 'now')),
	-- Cleared whenever the mode is 'fixed', required when it is 'gross'.
	CHECK ((mode = 'gross') = (gross_amount IS NOT NULL))
);

CREATE UNIQUE INDEX income_plans_default_idx ON income_plans (user_id) WHERE month IS NULL;
CREATE UNIQUE INDEX income_plans_month_idx   ON income_plans (user_id, month) WHERE month IS NOT NULL;

CREATE TABLE tags (
	id         TEXT PRIMARY KEY,
	user_id    TEXT NOT NULL REFERENCES users (id) ON DELETE CASCADE,
	name       TEXT NOT NULL,
	color      TEXT NOT NULL DEFAULT '#64748b',
	created_at TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ', 'now')),
	updated_at TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ', 'now')),
	UNIQUE (user_id, id)
);

CREATE UNIQUE INDEX tags_user_name_idx ON tags (user_id, name COLLATE NOCASE);

-- Carries its owner so both composite foreign keys can check it: a link
-- between one user's transaction and another's tag is unwritable.
CREATE TABLE transaction_tags (
	user_id        TEXT NOT NULL,
	transaction_id TEXT NOT NULL,
	tag_id         TEXT NOT NULL,
	PRIMARY KEY (transaction_id, tag_id),
	FOREIGN KEY (user_id, transaction_id) REFERENCES transactions (user_id, id) ON DELETE CASCADE,
	FOREIGN KEY (user_id, tag_id) REFERENCES tags (user_id, id) ON DELETE CASCADE
) WITHOUT ROWID;

CREATE INDEX transaction_tags_tag_idx ON transaction_tags (tag_id);

CREATE TABLE subscriptions (
	id           TEXT PRIMARY KEY,
	user_id      TEXT NOT NULL,
	account_id   TEXT NOT NULL,
	-- Deleting the category leaves the subscription uncategorised, as for a
	-- transaction. Deleting the account takes it: it is configuration, not history.
	category_id  TEXT REFERENCES categories (id) ON DELETE SET NULL,
	amount       INTEGER NOT NULL CHECK (amount <> 0),
	payee        TEXT NOT NULL,
	notes        TEXT NOT NULL DEFAULT '',
	start_on     TEXT NOT NULL CHECK (start_on GLOB '[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9]'),
	-- Stored rather than derived from `next_run_on`, so a month too short for it
	-- (31 January, then 28 February) does not drag the schedule down with it.
	day_of_month INTEGER NOT NULL CHECK (day_of_month BETWEEN 1 AND 31),
	-- The next date to post, and the compare-and-swap token the cron advances.
	next_run_on  TEXT NOT NULL CHECK (next_run_on GLOB '[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9]'),
	last_run_on  TEXT CHECK (last_run_on IS NULL OR last_run_on GLOB '[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9]'),
	enabled      INTEGER NOT NULL DEFAULT 1 CHECK (enabled IN (0, 1)),
	created_at   TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ', 'now')),
	updated_at   TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ', 'now')),
	FOREIGN KEY (user_id, account_id) REFERENCES accounts (user_id, id) ON DELETE CASCADE
);

CREATE INDEX subscriptions_user_idx ON subscriptions (user_id, payee);
-- The cron's scan: everything enabled and due, across every user.
CREATE INDEX subscriptions_due_idx ON subscriptions (enabled, next_run_on);

CREATE TABLE payee_history (
	id            TEXT PRIMARY KEY,
	user_id       TEXT NOT NULL REFERENCES users (id) ON DELETE CASCADE,
	payee         TEXT NOT NULL,
	kind          TEXT NOT NULL CHECK (kind IN ('expense', 'income', 'transfer')),
	-- These may be deleted later; the suggestion then carries one less piece of
	-- the form rather than disappearing.
	account_id    TEXT REFERENCES accounts (id) ON DELETE SET NULL,
	to_account_id TEXT REFERENCES accounts (id) ON DELETE SET NULL,
	category_id   TEXT REFERENCES categories (id) ON DELETE SET NULL,
	notes         TEXT NOT NULL DEFAULT '',
	used_count    INTEGER NOT NULL DEFAULT 1,
	last_used_at  TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ', 'now')),
	created_at    TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ', 'now')),
	updated_at    TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ', 'now'))
);

-- NOCASE so "Corner Market" and "corner market" are one payee; the most
-- recently typed spelling is what is kept.
CREATE UNIQUE INDEX payee_history_user_payee_idx ON payee_history (user_id, payee COLLATE NOCASE);
CREATE INDEX payee_history_user_rank_idx ON payee_history (user_id, used_count DESC, last_used_at DESC);

CREATE TABLE round_up_rules (
	user_id                TEXT PRIMARY KEY REFERENCES users (id) ON DELETE CASCADE,
	enabled                INTEGER NOT NULL DEFAULT 0 CHECK (enabled IN (0, 1)),
	-- A whole-currency-unit multiple in minor units. The old CHECK listed 1000
	-- and 10000, which hard-codes a currency with two decimal places; which
	-- multiples are offered is a question for the API, not the schema.
	round_to               INTEGER NOT NULL DEFAULT 1000 CHECK (round_to > 0),
	destination_account_id TEXT REFERENCES accounts (id) ON DELETE SET NULL,
	-- A round-up is a transfer, so this is a transfer-scope category. Resets to
	-- NULL (uncategorised) if it is deleted.
	category_id            TEXT REFERENCES categories (id) ON DELETE SET NULL,
	created_at             TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ', 'now')),
	updated_at             TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ', 'now'))
);

-- ------------------------------------------------------------- 4. the data
-- Foreign keys are deferred, so this order is for readability, not necessity.

INSERT INTO users (id, display_currency, budget_mode, default_account_id, created_at, updated_at)
SELECT id, display_currency, budget_mode, NULL, created_at, updated_at FROM _mig_users;

INSERT INTO account_types (id, user_id, name, sort_order, archived, created_at, updated_at)
SELECT id, user_id, name, sort_order, archived, created_at, updated_at FROM _mig_account_types;

INSERT INTO accounts (id, user_id, name, type_id, currency, logo_url, logo_invert_dark, round_up_source,
                      starting_balance, archived, created_at, updated_at)
SELECT id, user_id, name, type_id, currency, NULLIF(logo_url, ''), logo_invert_dark, round_up_source,
       starting_balance, archived, created_at, updated_at
FROM _mig_accounts;

-- Set once the accounts exist, rather than relying on the deferral.
UPDATE users SET default_account_id = (SELECT default_account_id FROM _mig_users WHERE _mig_users.id = users.id);

INSERT INTO categories (id, user_id, name, kind, color, sort_order, archived, parent_id, applies_to, created_at, updated_at)
SELECT id, user_id, name, kind, color, sort_order, archived, parent_id, applies_to, created_at, updated_at
FROM _mig_categories;

-- One parent row per group of legs. A group whose legs all carry the round-up
-- payee is a "Save the Change" posting; this is the last time that string
-- decides anything.
INSERT INTO transfers (id, user_id, kind, created_at)
SELECT transfer_id, user_id,
       CASE WHEN MIN(payee) = 'Save the Change' AND MAX(payee) = 'Save the Change' THEN 'round_up' ELSE 'manual' END,
       MIN(created_at)
FROM _mig_transactions
WHERE transfer_id IS NOT NULL
GROUP BY transfer_id, user_id;

INSERT INTO transactions (id, user_id, account_id, category_id, amount, occurred_on, occurred_time,
                          payee, notes, transfer_id, source, created_at, updated_at)
SELECT id, user_id, account_id, category_id, amount,
       substr(occurred_on, 1, 10),
       CASE WHEN length(occurred_on) > 10 THEN substr(occurred_on, 12, 5) END,
       payee, notes, transfer_id,
       CASE
         WHEN automated = 1 THEN 'subscription'
         WHEN transfer_id IS NOT NULL AND payee = 'Save the Change' THEN 'round_up'
         ELSE 'manual'
       END,
       created_at, updated_at
FROM _mig_transactions;

INSERT INTO tags (id, user_id, name, color, created_at, updated_at)
SELECT id, user_id, name, color, created_at, updated_at FROM _mig_tags;

INSERT INTO transaction_tags (user_id, transaction_id, tag_id)
SELECT t.user_id, l.transaction_id, l.tag_id
FROM _mig_transaction_tags l
JOIN _mig_transactions t ON t.id = l.transaction_id;

-- 'fixed' was a sentinel standing in for "every month"; NULL says it outright.
INSERT INTO budgets (id, user_id, category_id, month, amount, percent_bp, created_at, updated_at)
SELECT id, user_id, category_id, NULLIF(month, 'fixed'), amount, percent_bp, created_at, updated_at
FROM _mig_budgets;

INSERT INTO income_plans (id, user_id, month, amount, mode, gross_amount, created_at, updated_at)
SELECT id, user_id, NULLIF(month, 'fixed'), amount, mode,
       CASE WHEN mode = 'gross' THEN gross_amount END,
       created_at, updated_at
FROM _mig_income_plans;

INSERT INTO subscriptions (id, user_id, account_id, category_id, amount, payee, notes, start_on,
                           day_of_month, next_run_on, last_run_on, enabled, created_at, updated_at)
SELECT id, user_id, account_id, category_id, amount, payee, notes, start_on,
       day_of_month, next_run_on, last_run_on, enabled, created_at, updated_at
FROM _mig_subscriptions;

INSERT INTO payee_history (id, user_id, payee, kind, account_id, to_account_id, category_id, notes,
                           used_count, last_used_at, created_at, updated_at)
SELECT id, user_id, payee, kind, account_id, to_account_id, category_id, notes,
       used_count, last_used_at, created_at, updated_at
FROM _mig_payee_history;

INSERT INTO round_up_rules (user_id, enabled, round_to, destination_account_id, category_id, created_at, updated_at)
SELECT user_id, enabled, round_to, destination_account_id, category_id, created_at, updated_at
FROM _mig_round_up_rules;

-- --------------------------------------------------------- 5. the invariants
-- Created after the backfill so they do not fire on data that is only being
-- moved. From here they hold for every write.

-- Deleting either leg deletes the parent, whose cascade takes the other leg.
-- Foreign key actions run regardless of `recursive_triggers`, which is what
-- makes this safe without turning that pragma on.
CREATE TRIGGER transfers_leg_deleted
AFTER DELETE ON transactions
WHEN OLD.transfer_id IS NOT NULL
BEGIN
	DELETE FROM transfers WHERE id = OLD.transfer_id;
END;

-- Categories nest exactly one level, and a child inherits its parent's kind and
-- scope. Neither can be expressed as a CHECK, which cannot see another row.
CREATE TRIGGER categories_hierarchy_insert
BEFORE INSERT ON categories
WHEN NEW.parent_id IS NOT NULL
BEGIN
	SELECT RAISE(ABORT, 'A subcategory cannot have children.')
	WHERE (SELECT parent_id FROM categories WHERE id = NEW.parent_id) IS NOT NULL;

	SELECT RAISE(ABORT, 'A subcategory inherits its parent kind and scope.')
	WHERE (SELECT kind || '/' || applies_to FROM categories WHERE id = NEW.parent_id) <> NEW.kind || '/' || NEW.applies_to;
END;

CREATE TRIGGER categories_hierarchy_update
BEFORE UPDATE ON categories
WHEN NEW.parent_id IS NOT NULL
BEGIN
	SELECT RAISE(ABORT, 'A subcategory cannot have children.')
	WHERE (SELECT parent_id FROM categories WHERE id = NEW.parent_id) IS NOT NULL;

	SELECT RAISE(ABORT, 'A subcategory inherits its parent kind and scope.')
	WHERE (SELECT kind || '/' || applies_to FROM categories WHERE id = NEW.parent_id) <> NEW.kind || '/' || NEW.applies_to;
END;

-- A category that has children cannot become a child itself.
CREATE TRIGGER categories_no_reparent_with_children
BEFORE UPDATE OF parent_id ON categories
WHEN NEW.parent_id IS NOT NULL AND OLD.parent_id IS NULL
BEGIN
	SELECT RAISE(ABORT, 'A category with subcategories cannot become one.')
	WHERE EXISTS (SELECT 1 FROM categories WHERE parent_id = NEW.id);
END;

-- ------------------------------------------------------------ 6. tidy up

DROP TABLE _mig_round_up_rules;
DROP TABLE _mig_payee_history;
DROP TABLE _mig_subscriptions;
DROP TABLE _mig_transaction_tags;
DROP TABLE _mig_tags;
DROP TABLE _mig_income_plans;
DROP TABLE _mig_budgets;
DROP TABLE _mig_transactions;
DROP TABLE _mig_categories;
DROP TABLE _mig_accounts;
DROP TABLE _mig_account_types;
DROP TABLE _mig_users;
