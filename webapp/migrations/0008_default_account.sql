-- Lets a user pick which account a new transaction opens on, instead of
-- always defaulting to whichever active account happens to sort first.

-- The account may be archived or deleted later; falling back to null rather
-- than failing means the create form just resumes picking the first active
-- account, the same as before this setting existed.
ALTER TABLE users ADD COLUMN default_account_id TEXT REFERENCES accounts (id) ON DELETE SET NULL;
