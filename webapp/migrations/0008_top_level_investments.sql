-- The system Investments (`system = 'investments'`) stands at the top level of
-- the transfer categories, beside Cashflow rather than under it. 0007 put it
-- under Cashflow, and made one there for whoever had none — including people
-- who had their own top-level Investments, who then had two.
--
-- 1. Whoever has a top-level transfer Investments of their own keeps it: it
--    becomes the system one, and everything filed under the one 0007 made
--    under Cashflow moves to it. That one then goes.
-- 2. Everyone else's moves up to the top level, last among their transfer
--    categories.
-- 3. A Cashflow that 0007 made only to hold it, and that holds nothing now,
--    goes too.

CREATE TABLE investments_merge (
	user_id TEXT PRIMARY KEY,
	from_id TEXT NOT NULL,
	to_id   TEXT NOT NULL
);

INSERT INTO investments_merge (user_id, from_id, to_id)
SELECT s.user_id, s.id, (
	SELECT MIN(t.id) FROM categories t
	 WHERE t.user_id = s.user_id AND t.kind = 'transfer' AND t.parent_id IS NULL AND t.system IS NULL
	   AND t.name = 'Investments' COLLATE NOCASE
)
  FROM categories s
 WHERE s.system = 'investments' AND s.parent_id IS NOT NULL
   AND EXISTS (
	SELECT 1 FROM categories t
	 WHERE t.user_id = s.user_id AND t.kind = 'transfer' AND t.parent_id IS NULL AND t.system IS NULL
	   AND t.name = 'Investments' COLLATE NOCASE
   );

UPDATE transactions
   SET category_id = (SELECT to_id FROM investments_merge WHERE from_id = transactions.category_id),
       updated_at = strftime('%Y-%m-%dT%H:%M:%fZ', 'now')
 WHERE category_id IN (SELECT from_id FROM investments_merge);

UPDATE subscriptions
   SET category_id = (SELECT to_id FROM investments_merge WHERE from_id = subscriptions.category_id)
 WHERE category_id IN (SELECT from_id FROM investments_merge);

UPDATE payee_history
   SET category_id = (SELECT to_id FROM investments_merge WHERE from_id = payee_history.category_id)
 WHERE category_id IN (SELECT from_id FROM investments_merge);

UPDATE round_up_rules
   SET category_id = (SELECT to_id FROM investments_merge WHERE from_id = round_up_rules.category_id)
 WHERE category_id IN (SELECT from_id FROM investments_merge);

-- The key moves before the row goes: one system Investments a person, always.
UPDATE categories SET system = NULL WHERE id IN (SELECT from_id FROM investments_merge);

UPDATE categories
   SET system = 'investments', archived = 0, updated_at = strftime('%Y-%m-%dT%H:%M:%fZ', 'now')
 WHERE id IN (SELECT to_id FROM investments_merge);

-- Remembered before it goes, for step 3.
CREATE TABLE investments_parents (id TEXT PRIMARY KEY, held_since TEXT NOT NULL);

INSERT INTO investments_parents (id, held_since)
SELECT parent_id, MIN(created_at) FROM categories
 WHERE parent_id IS NOT NULL AND (system = 'investments' OR id IN (SELECT from_id FROM investments_merge))
   -- 0007 named what it made `cat_` and 32 hex digits; what sign-up made is
   -- named after the person, `cat_` and 24, and a Cashflow made then with it
   -- was theirs from the start.
   AND length(id) = 36
 GROUP BY parent_id;

DELETE FROM categories WHERE id IN (SELECT from_id FROM investments_merge);

DROP TABLE investments_merge;

-- 2. Up to the top level, last among the transfer categories.
UPDATE categories
   SET parent_id = NULL,
       sort_order = (
	SELECT COALESCE(MAX(o.sort_order) + 1, 0) FROM categories o
	 WHERE o.user_id = categories.user_id AND o.kind = 'transfer' AND o.parent_id IS NULL
       ),
       updated_at = strftime('%Y-%m-%dT%H:%M:%fZ', 'now')
 WHERE system = 'investments' AND parent_id IS NOT NULL;

-- 3. A Cashflow made by 0007 was written a moment before the Investments it
--    was made for; one that holds nothing and nothing is filed under now goes.
--    A Cashflow someone already had is older than that, and stays.
DELETE FROM categories
 WHERE id IN (
	SELECT p.id FROM investments_parents p
	  JOIN categories c ON c.id = p.id
	 WHERE c.created_at >= strftime('%Y-%m-%dT%H:%M:%fZ', p.held_since, '-60 seconds')
	   AND length(c.id) = 36
 )
   AND NOT EXISTS (SELECT 1 FROM categories child WHERE child.parent_id = categories.id)
   AND NOT EXISTS (SELECT 1 FROM transactions WHERE category_id = categories.id)
   AND NOT EXISTS (SELECT 1 FROM budgets WHERE category_id = categories.id)
   AND NOT EXISTS (SELECT 1 FROM subscriptions WHERE category_id = categories.id)
   AND NOT EXISTS (SELECT 1 FROM round_up_rules WHERE category_id = categories.id);

DROP TABLE investments_parents;
