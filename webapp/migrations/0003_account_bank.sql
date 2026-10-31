-- Preset banks for accounts.
--
-- An account may name one of the banks both apps ship with, by a short key
-- (`bdo`, `bpi`, …). The bank's name, its logo and whether that logo inverts
-- in dark mode live in the apps' code, not here, so changing a bank's logo
-- reaches every account that names it with no backfill — the same reason an
-- account references its type by id.
--
-- The server does not keep the list: it only checks the key's shape, so
-- adding a bank is a client release, never a migration. A key a client does
-- not know falls back to the account's initial, as a logo that fails to load
-- does. NULL is "Custom": the account's own `logo_url` and `logo_invert_dark`
-- apply, exactly as before.

ALTER TABLE accounts ADD COLUMN bank TEXT CHECK (bank IS NULL OR (length(bank) BETWEEN 1 AND 40 AND bank NOT GLOB '*[^a-z0-9-]*'));
