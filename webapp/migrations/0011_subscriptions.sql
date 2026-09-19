-- Subscriptions: a monthly charge (or credit) the Worker posts on the user's
-- behalf from a Cloudflare Cron Trigger, so it needs no attention once set up.
-- What it posts is an ordinary transaction — the user can edit or delete it.

-- The schedule is a day of the month, anchored on the start date the user
-- picked. `day_of_month` is stored rather than derived from `next_run_on` so a
-- clamped month doesn't drag the schedule with it: a subscription that starts on
-- 31 January runs on 28 February, and then on 31 March again.
--
-- `next_run_on` is the next date to post, and doubles as the compare-and-swap
-- token the cron advances — see server/subscriptions.ts.
--
-- Deleting the account takes its subscriptions with it (they're configuration,
-- not history). Deleting the category leaves the subscription uncategorised, the
-- same as a transaction.
CREATE TABLE subscriptions (
	id           TEXT PRIMARY KEY,
	user_id      TEXT NOT NULL REFERENCES users (id) ON DELETE CASCADE,
	account_id   TEXT NOT NULL REFERENCES accounts (id) ON DELETE CASCADE,
	category_id  TEXT REFERENCES categories (id) ON DELETE SET NULL,
	-- Signed minor units, like a transaction: negative is an outflow.
	amount       INTEGER NOT NULL CHECK (amount <> 0),
	payee        TEXT NOT NULL,
	notes        TEXT NOT NULL DEFAULT '',
	start_on     TEXT NOT NULL,
	day_of_month INTEGER NOT NULL CHECK (day_of_month BETWEEN 1 AND 31),
	next_run_on  TEXT NOT NULL,
	last_run_on  TEXT,
	enabled      INTEGER NOT NULL DEFAULT 1 CHECK (enabled IN (0, 1)),
	created_at   TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ', 'now')),
	updated_at   TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ', 'now'))
);

CREATE INDEX subscriptions_user_idx ON subscriptions (user_id, payee);
-- The cron's scan: everything enabled and due, across every user.
CREATE INDEX subscriptions_due_idx ON subscriptions (enabled, next_run_on);

-- Marks a transaction the cron posted, so the UI can lock the parts a
-- subscription decides for it (the time of day — every run is at 00:00 UTC).
-- There is deliberately no link back to the subscription: deleting one leaves
-- what it already posted as ordinary history.
ALTER TABLE transactions ADD COLUMN automated INTEGER NOT NULL DEFAULT 0 CHECK (automated IN (0, 1));
