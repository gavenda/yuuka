-- One `kind` instead of a kind and a scope.
--
-- `kind` (income | expense) and `applies_to` (standard | transfer) described a
-- 2x2 grid in which only three cells mean anything: (income, transfer) has
-- never existed and has no reading, and for a transfer category the `kind` is
-- dead weight — `/api/summary` ignores it and measures the outflow leg instead.
-- Three values in one column say the same thing without the unreachable cell.
--
-- It also removes an arbitrary restriction. The uniqueness index is
-- (user_id, name, kind, parent) and never included `applies_to`, so a
-- "Savings" expense category and a "Savings" transfer category collided. Now
-- they differ by kind, so a name can be used once in each sense.
--
-- `categories` has to be rebuilt rather than altered: the CHECK on `kind` is
-- part of the table definition, and dropping `applies_to` would leave an index
-- and two triggers referring to a column that is gone.
--
-- The awkward part is that rebuilding it cannot avoid a cascade.
--
--   * DROP TABLE performs an implicit DELETE FROM, and that *runs foreign key
--     actions*. Dropping `categories` deletes every row in `budgets`
--     (ON DELETE CASCADE) and uncategorises every transaction, subscription,
--     payee and round-up rule (ON DELETE SET NULL).
--   * `PRAGMA defer_foreign_keys` does not help. It delays *checking* a
--     violation until commit; it does not stop an action being performed.
--   * Renaming instead of dropping does not help either. With foreign keys on,
--     RENAME rewrites the REFERENCES clauses in other tables to follow the new
--     name, so they end up pointing at the staging table and the cascade
--     happens when that is dropped. `PRAGMA legacy_alter_table` does not
--     suppress that rewrite.
--   * `PRAGMA foreign_keys = off`, which is how SQLite's own 12-step procedure
--     avoids all of this, is a no-op inside a transaction — and a migration is
--     one.
--
-- 0013 sidestepped the problem by dropping every table child-first and
-- refilling all of them, so there was never anything left to cascade into.
-- Rebuilding one table in isolation has no such luck, so this migration takes
-- the cascade deliberately: it copies out what will be lost, lets it happen,
-- and puts it back. `scripts/verify.sql` is what proves it did.

PRAGMA defer_foreign_keys = true;

-- ------------------------------------------------- 1. what the cascade takes
CREATE TABLE _mig_categories AS SELECT * FROM categories;
CREATE TABLE _mig_budgets    AS SELECT * FROM budgets;
CREATE TABLE _mig_txn_cat    AS SELECT id, category_id FROM transactions   WHERE category_id IS NOT NULL;
CREATE TABLE _mig_sub_cat    AS SELECT id, category_id FROM subscriptions  WHERE category_id IS NOT NULL;
CREATE TABLE _mig_payee_cat  AS SELECT id, category_id FROM payee_history  WHERE category_id IS NOT NULL;
CREATE TABLE _mig_rule_cat   AS SELECT user_id, category_id FROM round_up_rules WHERE category_id IS NOT NULL;

-- --------------------------------------------------------- 2. the rebuild
DROP TABLE categories;

CREATE TABLE categories (
	id         TEXT PRIMARY KEY,
	user_id    TEXT NOT NULL REFERENCES users (id) ON DELETE CASCADE,
	name       TEXT NOT NULL,
	-- What the category is for. 'income' and 'expense' categorise spending and
	-- income; 'transfer' categorises movements between the user's own accounts,
	-- which is how an investment contribution is recorded. A transaction carries
	-- exactly one category, so it is always one of the three, never a mixture.
	kind       TEXT NOT NULL CHECK (kind IN ('income', 'expense', 'transfer')),
	color      TEXT NOT NULL DEFAULT '#64748b',
	sort_order INTEGER NOT NULL DEFAULT 0,
	archived   INTEGER NOT NULL DEFAULT 0 CHECK (archived IN (0, 1)),
	parent_id  TEXT REFERENCES categories (id) ON DELETE CASCADE,
	created_at TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ', 'now')),
	updated_at TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ', 'now'))
);

INSERT INTO categories (id, user_id, name, kind, color, sort_order, archived, parent_id, created_at, updated_at)
SELECT id, user_id, name,
       CASE WHEN applies_to = 'transfer' THEN 'transfer' ELSE kind END,
       color, sort_order, archived, parent_id, created_at, updated_at
FROM _mig_categories;

-- Unique among siblings. COALESCE is load-bearing: SQLite treats NULLs as
-- distinct in a unique index, so indexing `parent_id` directly would let
-- duplicate top-level categories through, every one of them having a NULL parent.
CREATE UNIQUE INDEX categories_user_name_kind_parent_idx ON categories (user_id, name, kind, COALESCE(parent_id, ''));
CREATE INDEX categories_parent_idx ON categories (parent_id);
CREATE INDEX categories_user_kind_idx ON categories (user_id, kind, sort_order);

-- --------------------------------------------------- 3. undo the cascade
-- The categories are back under the same ids, so every reference below is to a
-- row that exists again.

INSERT INTO budgets (id, user_id, category_id, month, amount, percent_bp, created_at, updated_at)
SELECT id, user_id, category_id, month, amount, percent_bp, created_at, updated_at FROM _mig_budgets;

UPDATE transactions
SET category_id = (SELECT category_id FROM _mig_txn_cat WHERE _mig_txn_cat.id = transactions.id)
WHERE id IN (SELECT id FROM _mig_txn_cat);

UPDATE subscriptions
SET category_id = (SELECT category_id FROM _mig_sub_cat WHERE _mig_sub_cat.id = subscriptions.id)
WHERE id IN (SELECT id FROM _mig_sub_cat);

UPDATE payee_history
SET category_id = (SELECT category_id FROM _mig_payee_cat WHERE _mig_payee_cat.id = payee_history.id)
WHERE id IN (SELECT id FROM _mig_payee_cat);

UPDATE round_up_rules
SET category_id = (SELECT category_id FROM _mig_rule_cat WHERE _mig_rule_cat.user_id = round_up_rules.user_id)
WHERE user_id IN (SELECT user_id FROM _mig_rule_cat);

-- ----------------------------------------------------- 4. the invariants
-- Categories nest exactly one level, and a child inherits its parent's kind.
-- Neither can be expressed as a CHECK, which cannot see another row. One less
-- column to compare than before, since the scope is now part of the kind.

CREATE TRIGGER categories_hierarchy_insert
BEFORE INSERT ON categories
WHEN NEW.parent_id IS NOT NULL
BEGIN
	SELECT RAISE(ABORT, 'A subcategory cannot have children.')
	WHERE (SELECT parent_id FROM categories WHERE id = NEW.parent_id) IS NOT NULL;

	SELECT RAISE(ABORT, 'A subcategory inherits its parent kind.')
	WHERE (SELECT kind FROM categories WHERE id = NEW.parent_id) <> NEW.kind;
END;

CREATE TRIGGER categories_hierarchy_update
BEFORE UPDATE ON categories
WHEN NEW.parent_id IS NOT NULL
BEGIN
	SELECT RAISE(ABORT, 'A subcategory cannot have children.')
	WHERE (SELECT parent_id FROM categories WHERE id = NEW.parent_id) IS NOT NULL;

	SELECT RAISE(ABORT, 'A subcategory inherits its parent kind.')
	WHERE (SELECT kind FROM categories WHERE id = NEW.parent_id) <> NEW.kind;
END;

-- A category that has children cannot become a child itself.
CREATE TRIGGER categories_no_reparent_with_children
BEFORE UPDATE OF parent_id ON categories
WHEN NEW.parent_id IS NOT NULL AND OLD.parent_id IS NULL
BEGIN
	SELECT RAISE(ABORT, 'A category with subcategories cannot become one.')
	WHERE EXISTS (SELECT 1 FROM categories WHERE parent_id = NEW.id);
END;

-- ------------------------------------------------------------ 5. tidy up
DROP TABLE _mig_rule_cat;
DROP TABLE _mig_payee_cat;
DROP TABLE _mig_sub_cat;
DROP TABLE _mig_txn_cat;
DROP TABLE _mig_budgets;
DROP TABLE _mig_categories;
