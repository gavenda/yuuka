-- Tags: the user's own labels for a transaction, listed as chips beside its
-- notes. They sit alongside a category rather than replacing it — a category
-- decides where the money counts (budgets, summaries), a tag is only a label to
-- read by, so a transaction takes any number of them and no figure changes.
--
-- Names are unique per user regardless of case, so "Trip" and "trip" cannot
-- both exist and a name typed twice cannot split into two tags.
CREATE TABLE tags (
	id         TEXT PRIMARY KEY,
	user_id    TEXT NOT NULL REFERENCES users (id) ON DELETE CASCADE,
	name       TEXT NOT NULL,
	color      TEXT NOT NULL DEFAULT '#64748b',
	created_at TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ', 'now')),
	updated_at TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ', 'now'))
);

CREATE UNIQUE INDEX tags_user_name_idx ON tags (user_id, name COLLATE NOCASE);

-- Both sides cascade. Deleting a tag only takes the labels off; deleting a
-- transaction takes its links with it. Neither touches the other's history.
CREATE TABLE transaction_tags (
	transaction_id TEXT NOT NULL REFERENCES transactions (id) ON DELETE CASCADE,
	tag_id         TEXT NOT NULL REFERENCES tags (id) ON DELETE CASCADE,
	PRIMARY KEY (transaction_id, tag_id)
) WITHOUT ROWID;

CREATE INDEX transaction_tags_tag_idx ON transaction_tags (tag_id);
