-- Lets a user choose which category "Save the Change" round-ups post under,
-- instead of always landing uncategorised.

-- A round-up is an ordinary linked transfer (see routes/transactions.ts), so
-- the category has to be one that applies to transfers — the same Cashflow
-- tree an ordinary transfer between your own accounts uses — not a standard
-- spending/income category. Resets to NULL (uncategorised) if the category is
-- deleted, the same as `destination_account_id` resetting when its account is.
ALTER TABLE round_up_rules ADD COLUMN category_id TEXT REFERENCES categories (id) ON DELETE SET NULL;
