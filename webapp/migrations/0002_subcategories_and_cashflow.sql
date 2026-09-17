-- Subcategories, and categories that belong to transfers rather than spending.

-- One level only: a category with a parent may not itself be a parent. SQLite
-- cannot express that in a CHECK (it needs to look at another row), so the API
-- enforces it — every insert requires the parent to have no parent of its own.
ALTER TABLE categories ADD COLUMN parent_id TEXT REFERENCES categories (id) ON DELETE CASCADE;

-- Where a category may be used. 'standard' categorises spending and income;
-- 'transfer' categorises movements between the user's own accounts, which is
-- how an investment contribution is recorded. A transaction carries exactly one
-- category, so the two sets never mix on a single row.
ALTER TABLE categories ADD COLUMN applies_to TEXT NOT NULL DEFAULT 'standard' CHECK (applies_to IN ('standard', 'transfer'));

-- Names stay unique per user and kind, but only among siblings: two different
-- parents may each have an "Other".
--
-- COALESCE is what makes that work. SQLite treats NULLs as distinct in a UNIQUE
-- index, so indexing parent_id directly would let duplicate top-level
-- categories through — every one of them has a NULL parent.
DROP INDEX categories_user_name_kind_idx;
CREATE UNIQUE INDEX categories_user_name_kind_parent_idx ON categories (user_id, name, kind, COALESCE(parent_id, ''));

CREATE INDEX categories_parent_idx ON categories (parent_id);
CREATE INDEX categories_user_applies_idx ON categories (user_id, applies_to, kind, sort_order);
