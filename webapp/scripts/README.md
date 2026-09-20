# Database scripts

Read-only SQL kept out of `migrations/`, which wrangler applies wholesale.

## `audit.sql`

43 checks for states the schema permits but the application's rules forbid:
transfers that are not a balanced pair, rows whose account belongs to another
user, subcategories that drifted from their parent's kind or scope, malformed
dates, and so on. Every row it returns with `violations > 0` is something to
repair before a migration that will refuse it.

```bash
# against a local copy (see below)
sqlite3 -column -header copy.db < scripts/audit.sql

# straight at production
bunx wrangler d1 execute yuuka --remote --file=scripts/audit.sql
```

Two of the checks — `budget_duplicate_per_category_month` and
`round_to_off_menu` — cannot fire against a live database, because a unique
index and a CHECK already make those states unreachable. They are kept for the
staging tables inside a rebuild migration, where constraints are off.

## `verify.sql`

Emits `metric|value` rows covering every figure a migration must not move:
per-account balances, net worth, per-month income, expense and cashflow,
per-category and per-day totals, budgets and income plans, transfer pairing,
tag links, subscription schedules and payee history.

Run it before and after, and diff:

```bash
sqlite3 -noheader -list before.db < scripts/verify.sql | sort > before.txt
sqlite3 -noheader -list after.db  < scripts/verify.sql | sort > after.txt
diff before.txt after.txt   # must be empty
```

It deliberately reads only columns whose names survive a rebuild, and takes the
date through `substr(occurred_on, 1, 10)` so it means the same thing whether
that column still carries a `THH:MM` time or not. Columns that get renamed are
checked separately.

## Working on a copy

D1 exports are plain SQL, so a production copy is two commands:

```bash
bunx wrangler d1 export yuuka --remote --output=prod.sql
sqlite3 copy.db < prod.sql
```

Rehearse anything destructive there first, and remember that D1 Time Travel can
restore the real database to any minute in the last 30 days.
