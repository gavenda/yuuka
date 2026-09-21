-- Push targets for the offline-first sync.
--
-- A mutation reaches the server from whichever app the user happened to be
-- holding; the other one finds out by a data-only FCM message rather than by
-- polling or by a full sync on next open. That needs somewhere to keep the
-- registration tokens, which is all this table is: one row per install per
-- user, keyed by the token itself because that is what FCM addresses.
--
-- `device_id` is the install's own stable identifier, not the token. It is
-- what a write echoes back so the originating device can ignore its own
-- change, and it survives the token being rotated. `platform` exists for
-- diagnostics only; nothing branches on it.
--
-- Tokens expire and are reassigned. FCM answers UNREGISTERED for a dead one,
-- and the sender deletes it here — so this table is pruned by use, not by a
-- cron.

CREATE TABLE devices (
	token       TEXT PRIMARY KEY,
	user_id     TEXT NOT NULL REFERENCES users (id) ON DELETE CASCADE,
	device_id   TEXT NOT NULL,
	platform    TEXT NOT NULL CHECK (platform IN ('android', 'web')),
	created_at  TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ', 'now')),
	last_seen_at TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ', 'now'))
);

-- Fanning out a change reads every token this user has except the origin's,
-- which is exactly this index.
CREATE INDEX devices_user_idx ON devices (user_id, device_id);

-- One install holds one token per user: signing in as someone else on the same
-- device must not leave the previous owner addressable through it.
CREATE UNIQUE INDEX devices_install_idx ON devices (user_id, device_id);
