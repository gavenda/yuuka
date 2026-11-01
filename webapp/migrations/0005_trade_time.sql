-- A trade records the time of day it was made, as a transaction does, and its
-- cash leg carries the same. NULL names no time — the trades entered before
-- this, and any entered without one — and sorts to the start of its day.
ALTER TABLE trades ADD COLUMN occurred_time TEXT CHECK (
	occurred_time IS NULL
	OR (occurred_time GLOB '[0-9][0-9]:[0-9][0-9]'
	    AND CAST(substr(occurred_time, 1, 2) AS INTEGER) < 24
	    AND CAST(substr(occurred_time, 4, 2) AS INTEGER) < 60)
);
