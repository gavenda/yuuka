-- Migration verification checksums.
--
-- Run against the pre-migration export and again against the rebuilt database.
-- The two outputs must be byte-identical, or the rebuild lost or changed
-- something. Diff them with `diff before.txt after.txt`.
--
-- Deliberately written only against columns whose names survive the rebuild
-- (`amount`, `occurred_on`, `account_id`, `category_id`, `transfer_id`, tags),
-- and it reads the date through `substr(occurred_on, 1, 10)` so it means the
-- same thing before the split (`YYYY-MM-DDTHH:MM`) and after (`YYYY-MM-DD`).
-- `automated` / `source` is checked separately, since that column is renamed.

SELECT 'count.users'            AS metric, CAST(COUNT(*) AS TEXT) AS value FROM users
UNION ALL SELECT 'count.account_types',    CAST(COUNT(*) AS TEXT) FROM account_types
UNION ALL SELECT 'count.accounts',         CAST(COUNT(*) AS TEXT) FROM accounts
UNION ALL SELECT 'count.categories',       CAST(COUNT(*) AS TEXT) FROM categories
UNION ALL SELECT 'count.transactions',     CAST(COUNT(*) AS TEXT) FROM transactions
UNION ALL SELECT 'count.budgets',          CAST(COUNT(*) AS TEXT) FROM budgets
UNION ALL SELECT 'count.income_plans',     CAST(COUNT(*) AS TEXT) FROM income_plans
UNION ALL SELECT 'count.tags',             CAST(COUNT(*) AS TEXT) FROM tags
UNION ALL SELECT 'count.transaction_tags', CAST(COUNT(*) AS TEXT) FROM transaction_tags
UNION ALL SELECT 'count.subscriptions',    CAST(COUNT(*) AS TEXT) FROM subscriptions
UNION ALL SELECT 'count.payee_history',    CAST(COUNT(*) AS TEXT) FROM payee_history
UNION ALL SELECT 'count.round_up_rules',   CAST(COUNT(*) AS TEXT) FROM round_up_rules

-- Money, the figure that must never move.
UNION ALL
SELECT 'balance.' || a.id, CAST(a.starting_balance + COALESCE(SUM(t.amount), 0) AS TEXT)
FROM accounts a LEFT JOIN transactions t ON t.account_id = a.id
GROUP BY a.id
UNION ALL
SELECT 'networth.' || u.id, CAST(COALESCE(SUM(a.starting_balance + COALESCE(b.total, 0)), 0) AS TEXT)
FROM users u
LEFT JOIN accounts a ON a.user_id = u.id
LEFT JOIN (SELECT account_id, SUM(amount) AS total FROM transactions GROUP BY account_id) b ON b.account_id = a.id
GROUP BY u.id

-- The monthly summary's headline figures, per month, transfers excluded.
UNION ALL
SELECT 'income.' || substr(occurred_on, 1, 7), CAST(SUM(amount) AS TEXT)
FROM transactions WHERE transfer_id IS NULL AND amount > 0
GROUP BY substr(occurred_on, 1, 7)
UNION ALL
SELECT 'expense.' || substr(occurred_on, 1, 7), CAST(SUM(amount) AS TEXT)
FROM transactions WHERE transfer_id IS NULL AND amount < 0
GROUP BY substr(occurred_on, 1, 7)
UNION ALL
-- Cashflow: the outflow leg of transfer-categorised movements.
SELECT 'cashflow.' || substr(t.occurred_on, 1, 7), CAST(SUM(-t.amount) AS TEXT)
FROM transactions t JOIN categories c ON c.id = t.category_id
WHERE t.transfer_id IS NOT NULL AND c.kind = 'transfer' AND t.amount < 0
GROUP BY substr(t.occurred_on, 1, 7)
UNION ALL
SELECT 'bycat.' || COALESCE(category_id, 'none'), CAST(SUM(amount) AS TEXT)
FROM transactions GROUP BY COALESCE(category_id, 'none')
UNION ALL
SELECT 'daily.' || substr(occurred_on, 1, 10), CAST(SUM(amount) AS TEXT)
FROM transactions GROUP BY substr(occurred_on, 1, 10)

-- Plans. `month` is rewritten ('fixed' -> NULL), so normalise it here.
UNION ALL
SELECT 'budget.' || category_id || '.' || CASE WHEN month IS NULL OR month = 'fixed' THEN 'default' ELSE month END,
       CAST(amount AS TEXT) || '/' || COALESCE(CAST(percent_bp AS TEXT), '-')
FROM budgets
UNION ALL
SELECT 'incomeplan.' || CASE WHEN month IS NULL OR month = 'fixed' THEN 'default' ELSE month END,
       CAST(amount AS TEXT) || '/' || mode || '/' || COALESCE(CAST(gross_amount AS TEXT), '-')
FROM income_plans

-- Transfer pairing, tags, schedules, payees.
UNION ALL
SELECT 'transfer.' || transfer_id, CAST(COUNT(*) AS TEXT) || '/' || CAST(SUM(amount) AS TEXT)
FROM transactions WHERE transfer_id IS NOT NULL GROUP BY transfer_id
UNION ALL
SELECT 'tagged.' || tt.transaction_id, GROUP_CONCAT(tt.tag_id, ',')
FROM (SELECT transaction_id, tag_id FROM transaction_tags ORDER BY tag_id) tt
GROUP BY tt.transaction_id
UNION ALL
SELECT 'sub.' || id, payee || '/' || CAST(amount AS TEXT) || '/' || start_on || '/' ||
       CAST(day_of_month AS TEXT) || '/' || next_run_on || '/' || CAST(enabled AS TEXT)
FROM subscriptions
UNION ALL
SELECT 'payee.' || id, payee || '/' || kind || '/' || CAST(used_count AS TEXT)
FROM payee_history
UNION ALL
SELECT 'roundup.' || user_id, CAST(enabled AS TEXT) || '/' || CAST(round_to AS TEXT) || '/' ||
       COALESCE(destination_account_id, '-') || '/' || COALESCE(category_id, '-')
FROM round_up_rules;
