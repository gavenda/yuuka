-- Remembers whether a month's planned income was typed directly or derived
-- from a gross salary (the Philippines take-home pay calculator on the Budget
-- page), so reopening the editor doesn't need browser storage to know which
-- mode it was in or what gross figure produced the saved amount.
--
-- `amount` is still the only figure anything else budgets from; `gross_amount`
-- is kept purely to prefill the field, and is cleared whenever mode is 'fixed'.
ALTER TABLE income_plans ADD COLUMN mode TEXT NOT NULL DEFAULT 'fixed' CHECK (mode IN ('gross', 'fixed'));
ALTER TABLE income_plans ADD COLUMN gross_amount INTEGER;
