-- System categories: the two every person has and nobody can change, which the
-- server files a trade's cash under. `investments` is Cashflow › Investments, a
-- transfer category, for the cash of a buy or a sell; `investment_income` is a
-- top-level income category named Investments, for a dividend's. A system
-- category cannot be renamed, recoloured, moved, archived or deleted, and nor
-- can the parent holding it be deleted or archived, since that would take it
-- with it (`server/routes/categories.ts`).

ALTER TABLE categories ADD COLUMN system TEXT CHECK (system IN ('investments', 'investment_income'));

CREATE UNIQUE INDEX categories_user_system_idx ON categories (user_id, system) WHERE system IS NOT NULL;

-- 1. The Investments under a transfer parent that people already have becomes
--    the system one (the first, should there somehow be two), back on show if
--    it was archived, and so is its parent.
UPDATE categories
   SET system = 'investments', archived = 0, updated_at = strftime('%Y-%m-%dT%H:%M:%fZ', 'now')
 WHERE id IN (
	SELECT MIN(c.id) FROM categories c
	 WHERE c.kind = 'transfer' AND c.parent_id IS NOT NULL AND c.name = 'Investments' COLLATE NOCASE
	 GROUP BY c.user_id
 );

-- 2. Whoever deleted it, and Cashflow with it, gets Cashflow back first.
INSERT INTO categories (id, user_id, name, kind, color, sort_order)
SELECT 'cat_' || lower(hex(randomblob(16))), u.id, 'Cashflow', 'transfer', '#4a3aa7', 0
  FROM users u
 WHERE NOT EXISTS (SELECT 1 FROM categories WHERE user_id = u.id AND system = 'investments')
   AND NOT EXISTS (
	SELECT 1 FROM categories WHERE user_id = u.id AND kind = 'transfer' AND parent_id IS NULL AND name = 'Cashflow' COLLATE NOCASE
   );

-- 3. Then Investments under it, last among its children.
INSERT INTO categories (id, user_id, name, kind, color, sort_order, parent_id, system)
SELECT 'cat_' || lower(hex(randomblob(16))), p.user_id, 'Investments', 'transfer', p.color,
       (SELECT COALESCE(MAX(sort_order) + 1, 0) FROM categories WHERE parent_id = p.id),
       p.id, 'investments'
  FROM categories p
 WHERE p.id IN (
	SELECT MIN(id) FROM categories
	 WHERE kind = 'transfer' AND parent_id IS NULL AND name = 'Cashflow' COLLATE NOCASE
	 GROUP BY user_id
 )
   AND NOT EXISTS (SELECT 1 FROM categories WHERE user_id = p.user_id AND system = 'investments');

UPDATE categories
   SET archived = 0, updated_at = strftime('%Y-%m-%dT%H:%M:%fZ', 'now')
 WHERE archived = 1 AND id IN (SELECT parent_id FROM categories WHERE system = 'investments');

-- 4. A top-level income category named Investments becomes the income one;
--    whoever has none gets one, last among their income categories.
UPDATE categories
   SET system = 'investment_income', archived = 0, updated_at = strftime('%Y-%m-%dT%H:%M:%fZ', 'now')
 WHERE id IN (
	SELECT MIN(id) FROM categories
	 WHERE kind = 'income' AND parent_id IS NULL AND name = 'Investments' COLLATE NOCASE
	 GROUP BY user_id
 );

INSERT INTO categories (id, user_id, name, kind, color, sort_order, system)
SELECT 'cat_' || lower(hex(randomblob(16))), u.id, 'Investments', 'income', '#008300',
       (SELECT COALESCE(MAX(sort_order) + 1, 0) FROM categories WHERE user_id = u.id AND kind = 'income' AND parent_id IS NULL),
       'investment_income'
  FROM users u
 WHERE NOT EXISTS (SELECT 1 FROM categories WHERE user_id = u.id AND system = 'investment_income');

-- 5. Every trade's cash already written goes under them: a buy's and a sell's
--    under Investments the transfer, a dividend's under Investments the income.
UPDATE transactions
   SET category_id = (
	SELECT c.id FROM categories c
	 WHERE c.user_id = transactions.user_id
	   AND c.system = CASE (SELECT kind FROM trades WHERE transaction_id = transactions.id)
	                  WHEN 'dividend' THEN 'investment_income' ELSE 'investments' END
       ),
       updated_at = strftime('%Y-%m-%dT%H:%M:%fZ', 'now')
 WHERE id IN (SELECT transaction_id FROM trades);
