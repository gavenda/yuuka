-- Lets a user budget a category once and have it apply to every month,
-- instead of typing the same plan in again each time. Fixed is the default —
-- most categories don't need a different plan every month, and monthly is
-- there for anyone who does.

ALTER TABLE users ADD COLUMN budget_mode TEXT NOT NULL DEFAULT 'fixed' CHECK (budget_mode IN ('fixed', 'monthly'));
