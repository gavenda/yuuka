-- Some logos are dark marks on a transparent background and disappear against
-- a dark surface. This is a per-account opt-in rather than a blanket filter,
-- because a colour logo inverted the same way would come out wrong.

ALTER TABLE accounts ADD COLUMN logo_invert_dark INTEGER NOT NULL DEFAULT 0 CHECK (logo_invert_dark IN (0, 1));
