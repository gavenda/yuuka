-- Phase 0: pre-rebuild data audit.
--
-- Every row this returns with violations > 0 is something the current schema
-- permits and the rebuilt schema will refuse. Repair them in the old schema
-- before the rebuild runs, or the rebuild aborts (correctly) on a constraint.
--
-- Read-only. One statement, so it works with `wrangler d1 execute --file`.

-- ---------------------------------------------------------------- transfers
SELECT 'transfer_groups_not_two_legs' AS check_name, COUNT(*) AS violations FROM (
	SELECT transfer_id FROM transactions WHERE transfer_id IS NOT NULL GROUP BY transfer_id HAVING COUNT(*) <> 2
)
UNION ALL
SELECT 'transfer_groups_nonzero_sum', COUNT(*) FROM (
	SELECT transfer_id FROM transactions WHERE transfer_id IS NOT NULL GROUP BY transfer_id HAVING SUM(amount) <> 0
)
UNION ALL
SELECT 'transfer_groups_multi_user', COUNT(*) FROM (
	SELECT transfer_id FROM transactions WHERE transfer_id IS NOT NULL GROUP BY transfer_id HAVING COUNT(DISTINCT user_id) <> 1
)
UNION ALL
SELECT 'transfer_groups_mixed_category', COUNT(*) FROM (
	SELECT transfer_id FROM transactions WHERE transfer_id IS NOT NULL
	GROUP BY transfer_id HAVING COUNT(DISTINCT COALESCE(category_id, '')) <> 1
)
UNION ALL
SELECT 'transfer_groups_mixed_date', COUNT(*) FROM (
	SELECT transfer_id FROM transactions WHERE transfer_id IS NOT NULL
	GROUP BY transfer_id HAVING COUNT(DISTINCT substr(occurred_on, 1, 10)) <> 1
)
UNION ALL
-- Both legs must wear the same tags, so links = legs x distinct tags.
SELECT 'transfer_groups_mismatched_tags', COUNT(*) FROM (
	SELECT t.transfer_id
	FROM transactions t
	LEFT JOIN transaction_tags tt ON tt.transaction_id = t.id
	WHERE t.transfer_id IS NOT NULL
	GROUP BY t.transfer_id
	HAVING COUNT(tt.tag_id) <> COUNT(DISTINCT t.id) * COUNT(DISTINCT tt.tag_id)
)
UNION ALL
-- The round-up transform keys off this payee, so a non-transfer wearing it
-- would be mistyped as `kind = 'round_up'`.
SELECT 'save_the_change_payee_not_transfer', COUNT(*)
FROM transactions WHERE payee = 'Save the Change' AND transfer_id IS NULL

-- --------------------------------------------------------------- ownership
UNION ALL
SELECT 'txn_account_other_user', COUNT(*)
FROM transactions t LEFT JOIN accounts a ON a.id = t.account_id
WHERE a.id IS NULL OR a.user_id <> t.user_id
UNION ALL
SELECT 'txn_category_other_user', COUNT(*)
FROM transactions t JOIN categories c ON c.id = t.category_id
WHERE c.user_id <> t.user_id
UNION ALL
SELECT 'account_type_other_user', COUNT(*)
FROM accounts a LEFT JOIN account_types ty ON ty.id = a.type_id
WHERE ty.id IS NULL OR ty.user_id <> a.user_id
UNION ALL
SELECT 'category_parent_other_user', COUNT(*)
FROM categories c JOIN categories p ON p.id = c.parent_id
WHERE p.user_id <> c.user_id
UNION ALL
SELECT 'budget_category_other_user', COUNT(*)
FROM budgets b LEFT JOIN categories c ON c.id = b.category_id
WHERE c.id IS NULL OR c.user_id <> b.user_id
UNION ALL
SELECT 'tag_link_other_user', COUNT(*)
FROM transaction_tags tt
JOIN transactions t ON t.id = tt.transaction_id
JOIN tags g ON g.id = tt.tag_id
WHERE t.user_id <> g.user_id
UNION ALL
SELECT 'default_account_other_user', COUNT(*)
FROM users u JOIN accounts a ON a.id = u.default_account_id
WHERE a.user_id <> u.id
UNION ALL
SELECT 'subscription_account_other_user', COUNT(*)
FROM subscriptions s LEFT JOIN accounts a ON a.id = s.account_id
WHERE a.id IS NULL OR a.user_id <> s.user_id
UNION ALL
SELECT 'subscription_category_other_user', COUNT(*)
FROM subscriptions s JOIN categories c ON c.id = s.category_id
WHERE c.user_id <> s.user_id
UNION ALL
SELECT 'round_up_refs_other_user', COUNT(*)
FROM round_up_rules r
LEFT JOIN accounts a ON a.id = r.destination_account_id
LEFT JOIN categories c ON c.id = r.category_id
WHERE (r.destination_account_id IS NOT NULL AND (a.id IS NULL OR a.user_id <> r.user_id))
   OR (r.category_id IS NOT NULL AND (c.id IS NULL OR c.user_id <> r.user_id))
UNION ALL
SELECT 'payee_history_refs_other_user', COUNT(*)
FROM payee_history p
LEFT JOIN accounts a  ON a.id  = p.account_id
LEFT JOIN accounts a2 ON a2.id = p.to_account_id
LEFT JOIN categories c ON c.id = p.category_id
WHERE (p.account_id    IS NOT NULL AND (a.id  IS NULL OR a.user_id  <> p.user_id))
   OR (p.to_account_id IS NOT NULL AND (a2.id IS NULL OR a2.user_id <> p.user_id))
   OR (p.category_id   IS NOT NULL AND (c.id  IS NULL OR c.user_id  <> p.user_id))

-- ------------------------------------------------------- category hierarchy
UNION ALL
SELECT 'category_grandchildren', COUNT(*)
FROM categories c JOIN categories p ON p.id = c.parent_id
WHERE p.parent_id IS NOT NULL
UNION ALL
SELECT 'child_kind_differs_from_parent', COUNT(*)
FROM categories c JOIN categories p ON p.id = c.parent_id
WHERE c.kind <> p.kind
UNION ALL
SELECT 'child_scope_differs_from_parent', COUNT(*)
FROM categories c JOIN categories p ON p.id = c.parent_id
WHERE c.applies_to <> p.applies_to
UNION ALL
SELECT 'budget_on_child_category', COUNT(*)
FROM budgets b JOIN categories c ON c.id = b.category_id
WHERE c.parent_id IS NOT NULL

-- ------------------------------------------------------------ category scope
UNION ALL
-- A transfer leg takes a transfer-scope category; everything else standard.
SELECT 'txn_scope_mismatch', COUNT(*)
FROM transactions t JOIN categories c ON c.id = t.category_id
WHERE (t.transfer_id IS NULL     AND c.applies_to <> 'standard')
   OR (t.transfer_id IS NOT NULL AND c.applies_to <> 'transfer')
UNION ALL
SELECT 'subscription_scope_mismatch', COUNT(*)
FROM subscriptions s JOIN categories c ON c.id = s.category_id
WHERE c.applies_to <> 'standard'
UNION ALL
SELECT 'round_up_category_not_transfer', COUNT(*)
FROM round_up_rules r JOIN categories c ON c.id = r.category_id
WHERE c.applies_to <> 'transfer'

-- ------------------------------------------------------------------- dates
UNION ALL
SELECT 'occurred_on_malformed', COUNT(*)
FROM transactions
WHERE occurred_on NOT GLOB '[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9]'
  AND occurred_on NOT GLOB '[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9]T[0-9][0-9]:[0-9][0-9]'
UNION ALL
SELECT 'occurred_on_not_a_real_day', COUNT(*)
FROM transactions
WHERE date(substr(occurred_on, 1, 10)) IS NULL OR date(substr(occurred_on, 1, 10)) <> substr(occurred_on, 1, 10)
UNION ALL
-- Automated rows post at 00:00 UTC and must stay bare dates.
SELECT 'automated_row_carries_time', COUNT(*)
FROM transactions WHERE automated = 1 AND length(occurred_on) > 10
UNION ALL
SELECT 'subscription_dates_malformed', COUNT(*)
FROM subscriptions
WHERE start_on    NOT GLOB '[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9]'
   OR next_run_on NOT GLOB '[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9]'
   OR (last_run_on IS NOT NULL AND last_run_on NOT GLOB '[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9]')
UNION ALL
SELECT 'subscription_anchor_inconsistent', COUNT(*)
FROM subscriptions WHERE day_of_month <> CAST(strftime('%d', start_on) AS INTEGER)
UNION ALL
SELECT 'subscription_next_run_before_start', COUNT(*)
FROM subscriptions WHERE next_run_on < start_on
UNION ALL
-- Informational: the cron has not caught these up yet.
SELECT 'subscription_overdue', COUNT(*)
FROM subscriptions WHERE enabled = 1 AND next_run_on < strftime('%Y-%m-%d', 'now')

-- ------------------------------------------------------ budgets and planning
UNION ALL
SELECT 'budget_month_malformed', COUNT(*)
FROM budgets WHERE month <> 'fixed' AND month NOT GLOB '[0-9][0-9][0-9][0-9]-[0-1][0-9]'
UNION ALL
SELECT 'income_plan_month_malformed', COUNT(*)
FROM income_plans WHERE month <> 'fixed' AND month NOT GLOB '[0-9][0-9][0-9][0-9]-[0-1][0-9]'
UNION ALL
SELECT 'budget_duplicate_per_category_month', COUNT(*) FROM (
	SELECT user_id, category_id, month FROM budgets GROUP BY user_id, category_id, month HAVING COUNT(*) > 1
)
UNION ALL
SELECT 'budget_percent_and_amount_both_set', COUNT(*)
FROM budgets WHERE percent_bp IS NOT NULL AND amount <> 0
UNION ALL
SELECT 'income_plan_gross_without_mode', COUNT(*)
FROM income_plans WHERE (mode = 'fixed' AND gross_amount IS NOT NULL) OR (mode = 'gross' AND gross_amount IS NULL)

-- -------------------------------------------------------------- value shapes
UNION ALL
SELECT 'zero_amount_transactions', COUNT(*) FROM transactions WHERE amount = 0
UNION ALL
SELECT 'currency_not_three_letters', COUNT(*) FROM (
	SELECT 1 FROM accounts WHERE currency NOT GLOB '[A-Za-z][A-Za-z][A-Za-z]'
	UNION ALL
	SELECT 1 FROM users WHERE display_currency NOT GLOB '[A-Za-z][A-Za-z][A-Za-z]'
)
UNION ALL
SELECT 'logo_url_not_http', COUNT(*)
FROM accounts
WHERE logo_url IS NOT NULL AND logo_url <> '' AND logo_url NOT GLOB 'http://*' AND logo_url NOT GLOB 'https://*'
UNION ALL
SELECT 'round_to_off_menu', COUNT(*) FROM round_up_rules WHERE round_to NOT IN (1000, 10000)
UNION ALL
SELECT 'payee_history_synthetic_payee', COUNT(*)
FROM payee_history WHERE payee IN ('Save the Change') OR payee GLOB '* → *'
UNION ALL
SELECT 'orphaned_user_rows', COUNT(*) FROM (
	SELECT 1 FROM accounts       WHERE user_id NOT IN (SELECT id FROM users)
	UNION ALL SELECT 1 FROM categories    WHERE user_id NOT IN (SELECT id FROM users)
	UNION ALL SELECT 1 FROM transactions  WHERE user_id NOT IN (SELECT id FROM users)
	UNION ALL SELECT 1 FROM budgets       WHERE user_id NOT IN (SELECT id FROM users)
	UNION ALL SELECT 1 FROM tags          WHERE user_id NOT IN (SELECT id FROM users)
	UNION ALL SELECT 1 FROM subscriptions WHERE user_id NOT IN (SELECT id FROM users)
);
