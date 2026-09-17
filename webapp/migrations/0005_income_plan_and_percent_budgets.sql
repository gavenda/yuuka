-- Lets a user set a planned total income for the month, then budget a
-- category as a percentage of it instead of typing a fixed amount.

-- One planned income figure per user and month, mirroring how `budgets` keys
-- on category and month.
CREATE TABLE income_plans (
	id         TEXT PRIMARY KEY,
	user_id    TEXT NOT NULL REFERENCES users (id) ON DELETE CASCADE,
	month      TEXT NOT NULL,
	amount     INTEGER NOT NULL DEFAULT 0,
	created_at TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ', 'now')),
	updated_at TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ', 'now'))
);

CREATE UNIQUE INDEX income_plans_user_month_idx ON income_plans (user_id, month);

-- A budget row is either a fixed amount or a share of the month's planned
-- income — never both. `percent_bp` is basis points (1 = 0.01%) so a share is
-- stored exactly, the same reasoning that keeps money off floats. When set,
-- it takes precedence over `amount`, which the API keeps at 0.
ALTER TABLE budgets ADD COLUMN percent_bp INTEGER CHECK (percent_bp IS NULL OR (percent_bp >= 0 AND percent_bp <= 10000));
