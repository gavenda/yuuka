# CLAUDE.md

Guidance for working in this repo. The README covers setup, deploying and the
route list; this file covers the decisions behind the code and the invariants
that must survive a change.

## Commands

```bash
bun run dev         # Functions on :8788, Vite on :5173
bun run test        # both suites: browser (vitest) and API (workerd + D1)
bun run typecheck   # vue-tsc for the browser half, tsc for functions/
bun run format      # prettier; tabs, single quotes, 140 columns
```

Run `bun run typecheck` and `bun run test` before calling a change done. Prettier
owns formatting — do not hand-align anything it will rewrite.

## Shape of the app

One Pages project serves both halves from a single origin. `functions/api/[[route]].ts`
is a catch-all that mounts the Hono app on `/api/*`, so routing lives in Hono
rather than in the filesystem: adding an endpoint means adding a route in
`functions/api/_lib/routes/`, not a file at a path. Its siblings live under
`_lib/`, and the leading underscore is what keeps Pages from publishing them as
routes of their own. The frontend calls `/api` relatively — there is no base URL
to configure and no CORS layer to maintain.

Frontend state is three Pinia stores in `src/stores`: `ledger` (accounts, types,
categories, settings — loaded once and shared), `budget` and `transactions`.
There is deliberately no auth store; see below.

## Money

**Money is never a float.** Every amount is a signed integer in the currency's
minor unit (cents). Outflows are negative and inflows positive, so an account
balance is `starting_balance + SUM(amount)` and a month's net is a plain sum.
Decimal strings exist only at the UI edge, where `parseMoney` splits on the
decimal point rather than multiplying by 100 — `45.99` becomes exactly `4599`.

**Two different currency questions, answered separately.** Each account records
the currency it actually holds. The _display currency_ is a user preference —
what net worth, the monthly summary and budgets are shown in — and defaults to
PHP. Never infer it from whichever account happens to be first. Any three-letter
code is accepted; `Intl` renders an unknown one as the code itself, and
`formatMoney` falls back to the amount plus the code rather than throwing,
because a malformed value would otherwise blank every figure on the page.

**Every figure renders through `displayMoney`, not `formatMoney`.** An eye
toggle in the header masks all amounts at once — the "someone is looking over my
shoulder" switch — and routing every figure through one helper is what stops the
toggle missing one. It is a local preference, not a server-side setting: it
protects the current screen, not the data. Only the digits are masked, the
currency stays visible, and the mask is a fixed width — one that grew with the
amount would give away the magnitude it exists to hide. Edit forms still show the
value being edited.

## Ledger model

**Transfers are two linked rows.** Moving money between your own accounts writes
one negative and one positive transaction sharing a `transfer_id`. Both are
excluded from income and spending totals, because a transfer is neither; deleting
either side deletes the pair, so the two accounts can never disagree.

**Categories nest one level, budgets stay on the parent.** A subcategory inherits
its parent's kind and scope, so an "Investments" under "Cashflow" is always a
transfer category. Its spending counts towards the parent's plan, and
`/api/summary` returns top-level categories only — each carrying its children's
figures — so summing the list cannot double-count. A transaction holds a single
category column, which makes "the main or the sub, never both" true by
construction rather than by validation.

**A budget's month is a per-user mode, not a per-budget choice.** `budget_mode`
on `users` is `'fixed'` (the default) or `'monthly'`, set from Settings.
Fixed budgets are stored under one shared sentinel month (`FIXED_BUDGET_MONTH`
in `budget-mode.ts`) rather than a second table, so the existing
`(category_id, month)` unique index is still what stops a category from
carrying two plans at once. `/api/budgets` and `/api/summary` both resolve
the caller's actual month through `budgetMonthKey()` before touching the
table, so a fixed budget answers every month's summary with the same planned
figure; switching modes doesn't move data between the two, it just changes
which stored row future reads and writes land on. A budget's `month` in API
responses is `null` when it is fixed, since it no longer belongs to one.
Changing the mode invalidates every cached summary for that user, not just
the current month, because it changes what "planned" means everywhere at
once. `/api/income-plan` follows the same `budgetMonthKey()` resolution for
the same reason: planned income is what a percentage-based budget is a share
of, so it would be inconsistent for it to stay per-month while the budgets
built on it go fixed.

**Cashflow categories belong to transfers.** A category records where it may be
used — `standard` for spending and income, `transfer` for movements between your
own accounts — and the two sets never appear in the same picker. Because a
transfer writes a matching pair of rows, counting both sides would always come to
zero; the cashflow figure measures the outflow leg. It stays out of income,
spending, the daily chart and the expense budget total: budgeting a movement is
not budgeting spending.

**Account types are the user's own vocabulary.** Everyone starts with the usual
set — Checking, Savings, Cash, Credit Card, Investment, Loan — and can rename,
extend or retire it. Accounts reference a type by id, so a rename propagates with
no backfill. Nothing calculates differently per type; it is a label for grouping
and reading, which is exactly why it can be user-defined without touching any
arithmetic. A type still in use cannot be deleted — the API reports how many
accounts hold it and points at archiving.

**The payee is the first field, and it fills in the rest.** Saving a named
transaction records what it was filed under — account, category, notes, and which
of the three shapes it was. Typing that name again offers it back and restores
the lot, switching the form's mode to match. Newest use wins rather than merging:
the question is "what did I do last time", and a half-updated row answers it
wrongly. Names match case-insensitively. A transfer left unnamed describes itself
(`Checking → Savings`); that composed name is derived, so it is never remembered.

**Deleting a category never destroys history.** Transactions fall back to
uncategorised (`ON DELETE SET NULL`); only budgets cascade. Deleting an account
_would_ take its transactions with it, so the API answers `409` until the caller
repeats the request with `?includeTransactions=true`.

**A balance adjustment is an ordinary, uncategorised transaction.** `POST
/accounts/:id/adjust` takes the balance the account should read, not the
amount to post — it computes the difference itself, in the same statement
that reads the account's current balance and writes the transaction, so a
transaction landing in between cannot make the posted amount wrong. Nothing
distinguishes the resulting row from one entered by hand; it counts towards
income or spending exactly like any other uncategorised transaction, because
that is what it is. A request that would post a zero amount is rejected
rather than silently writing nothing.

**"Save the Change" round-ups are an ordinary linked transfer, not a new
transaction shape.** `POST /transactions` is the only place it can trigger:
when the new row is an expense (negative amount) on an account that has opted
in (`accounts.round_up_source`), and the per-user `round_up_rules` row is
enabled with a destination account set, the gap between the amount and the
next `round_to` multiple (₱10 or ₱100 — a minor-unit multiple of 1000 or
10000, never centavos) is posted as its own transfer: two rows sharing a
`transfer_id`, payee `"Save the Change"`, both legs carrying whichever
category the rule names (`round_up_rules.category_id`) — a transfer-scope
one, the same Cashflow tree an ordinary transfer uses, since a round-up is
one. Uncategorised when the rule names none. That payee is
synthetic and is never fed into `rememberPayee`, the same way a transfer's
derived "From → To" name isn't. A purchase that happens to occur on the
destination account itself simply doesn't round up — the rest of that
account's spending is unaffected. Editing a transaction, transfers and
balance adjustments never trigger it; only a plain `POST /transactions` does.
`round_up_rules` is not provisioned on sign-in — it follows the same
GET-with-default / upsert pattern as `income_plans` and `payee_history`
rather than a guaranteed row, since most users never touch the feature.

**Account logos are linked, not uploaded.** An account may carry an image URL the
browser loads from wherever it lives — no upload, no copy, no storage beyond the
string. The scheme is restricted to http(s), because the value lands in an
`<img src>` and anything else invites `data:` payloads and similar surprises. A
URL that 404s falls back to the account's initial, and the tag carries
`referrerpolicy="no-referrer"` so browsing does not leak which account is being
viewed to the image's host. Inverting it in dark mode is a per-account opt-in
(`logoInvertDark`), not a blanket filter — a dark mark on a transparent
background needs it to stay visible, but a colour logo inverted the same way
would come out wrong.

## Security invariants

These are the ones to be most careful with; the isolation suite exists to keep
them true.

**Every row has an owner, and it comes from the token.** Accounts, categories,
transactions and budgets all carry a `user_id`: the Auth0 `sub`. Read it from the
verified access token, never from a request body, path or query parameter, so
there is no input a caller could change to reach another user's data. Every read
filters on it, and every write referencing an account or category checks
ownership _in the same statement that performs the write_, so the check cannot be
overtaken between looking and writing. Someone else's row answers `404`, not
`403` — the API never confirms an id exists for a different owner.

**The API holds no auth state.** Auth0 issues the access token; every protected
route verifies its RS256 signature against the tenant's published keys, plus
issuer, audience and expiry. Anyone who can sign in to the tenant is a valid
user of this deployment — restricting who that is happens in Auth0, not here.

**Token verification fails closed.** An unrecognised key id looks like a key
rotation, so the JWKS is refetched once; if it is still missing, or Auth0 cannot
be reached, deny the request rather than admitting it unverified. A malformed
token is rejected outright, without a network round trip.

**The user id is part of every cache key.** KV caches Auth0's signing keys for an
hour and the `/api/summary` response per user and month. A summary cache shared
between users would leak one person's figures to another, so that key is never
allowed to be optional. Any write touching a month purges it — both months when a
transaction moves across a boundary.

## Auth in the browser

**Sign-in is the SDK's job, not ours.** `@auth0/auth0-vue` owns the login
redirect, the code exchange, token refresh and the reactive session state, so
there is no auth store and no callback route — `useAuth0()` is the single source
of truth and Auth0 returns to the site root. The plugin spots the `code` on the
URL during startup, exchanges it, strips the query and navigates on.

It is installed _after_ the router, which matters: on startup it looks for
`$router` and `push`es to the route the user originally asked for. The other way
round it would rewrite history behind Vue Router's back and the two would
disagree about the current page. The navigation guard waits on the SDK's
`isLoading` for the same reason — the first navigation happens while the code is
still being exchanged, so deciding early would bounce an arriving user straight
back to sign-in.

## Provisioning

Signing in provisions the account: a `users` row, the default account types and
categories, and — unless `SEED_DEMO_DATA` is `"false"` — sample accounts,
transactions and budgets dated into the month they joined, so the first dashboard
has figures on it. Steady state is a single indexed `SELECT` per request.

Provisioning is idempotent, and it has to be: the first page load fires several
API calls at once, so they race here. Every id is derived from the subject rather
than random — with random ids a losing batch's `INSERT OR IGNORE` drops its
categories while its transactions still point at them, and the whole batch dies
on a foreign key. Derived ids make a second run resolve to the same rows and do
nothing.

## Charts

**Registered piece by piece.** Only the bar controller, the two scales and the
tooltip are registered; importing `chart.js/auto` would pull in the line, pie and
radar controllers nothing renders. The library belongs in the lazily-loaded
dashboard chunk, not the entry bundle. Each chart keeps a "Show data" table
alongside it as the non-visual route to the same numbers.

**Colours are validated, not chosen by eye.** The eight category colours in
`src/lib/palette.ts` are a fixed-order categorical palette checked against this
app's own light and dark surfaces for lightness, chroma, colour-vision-deficiency
separation and contrast, with a spec test guarding them. Adjacent slots stay
distinguishable for protan, deutan and tritan vision, and every coloured mark
carries a visible text label — colour never carries meaning alone.

## Tests

292 tests: 225 against the API in `test/`, 67 over the browser helpers as
`*.spec.ts` beside the code they cover.

The server suite runs in `workerd` against a migrated D1 database and mounts the
same Hono app the Pages Function does, through `test/worker-entry.ts`, so routing
is exercised as it ships. `test/helpers.ts` gives you an authed client;
`test/tokens.ts` mints RS256 tokens and publishes the matching JWKS into KV, so
expiry, audience, issuer, tampering and unknown signing keys are all exercised
without network access.

When you add a route or change an ownership check, add a case to
`test/isolation.spec.ts` as well — that suite is the one that proves another user
cannot reach the data.
