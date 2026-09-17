-- Remembers what a payee was last filed under, so typing it again can fill in
-- the rest of the form.

CREATE TABLE payee_history (
	id            TEXT PRIMARY KEY,
	user_id       TEXT NOT NULL REFERENCES users (id) ON DELETE CASCADE,
	payee         TEXT NOT NULL,
	-- Which shape of transaction this payee was last used on, so selecting a
	-- suggestion can put the form into the right mode.
	kind          TEXT NOT NULL CHECK (kind IN ('expense', 'income', 'transfer')),
	-- The referenced rows may be deleted later; the suggestion then simply
	-- carries one less piece of the form rather than disappearing.
	account_id    TEXT REFERENCES accounts (id) ON DELETE SET NULL,
	to_account_id TEXT REFERENCES accounts (id) ON DELETE SET NULL,
	category_id   TEXT REFERENCES categories (id) ON DELETE SET NULL,
	notes         TEXT NOT NULL DEFAULT '',
	used_count    INTEGER NOT NULL DEFAULT 1,
	last_used_at  TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ', 'now')),
	created_at    TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ', 'now')),
	updated_at    TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ', 'now'))
);

-- One row per payee per user. NOCASE so "Corner Market" and "corner market"
-- are the same payee; the most recently typed spelling is what gets kept.
CREATE UNIQUE INDEX payee_history_user_payee_idx ON payee_history (user_id, payee COLLATE NOCASE);

-- Suggestions are offered most-used first, then most-recent.
CREATE INDEX payee_history_user_rank_idx ON payee_history (user_id, used_count DESC, last_used_at DESC);
