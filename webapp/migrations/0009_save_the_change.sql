-- "Save the Change": rounds an ordinary expense up to the nearest whole
-- currency unit and moves the difference into a chosen account, recorded as
-- an ordinary linked transfer.

-- Whether this account's own purchases round up. Opt-in per account: a
-- credit card used for bill-pay shouldn't round up just because the user
-- wants it on their day-to-day spending account.
ALTER TABLE accounts ADD COLUMN round_up_source INTEGER NOT NULL DEFAULT 0 CHECK (round_up_source IN (0, 1));

-- One row per user, upserted like `income_plans`/`payee_history` rather than
-- provisioned up front — a user who never opens the feature never gets a row,
-- and GET synthesises a disabled default when one is missing.
--
-- `destination_account_id` resets to NULL if that account is deleted, the
-- same as `users.default_account_id` (0008) — a missing destination reads as
-- the rule being off, rather than failing writes elsewhere.
CREATE TABLE round_up_rules (
	user_id                TEXT PRIMARY KEY REFERENCES users (id) ON DELETE CASCADE,
	enabled                INTEGER NOT NULL DEFAULT 0 CHECK (enabled IN (0, 1)),
	-- Minor-unit multiple to round up to: 1000 (₱10) or 10000 (₱100). Whole
	-- currency-unit rounding, not centavos.
	round_to               INTEGER NOT NULL DEFAULT 1000 CHECK (round_to IN (1000, 10000)),
	destination_account_id TEXT REFERENCES accounts (id) ON DELETE SET NULL,
	created_at             TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ', 'now')),
	updated_at             TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ', 'now'))
);
