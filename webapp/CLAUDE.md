# CLAUDE.md

Technical guidance for the web app and its API. The README covers setup,
deploying and the route list; this file covers how the webapp is built and the
invariants that must survive a change. What the application does — money rules,
the ledger model, budgets, transfers, round-ups, payees — is described in the
root [CLAUDE.md](../CLAUDE.md), and applies here as much as to the Android app.

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

## Money helpers

`parseMoney` splits the input on the decimal point rather than multiplying by
100, so `45.99` becomes exactly `4599`. `formatMoney` goes through `Intl`, which
renders an unknown currency code as the code itself, and falls back to the amount
plus the code rather than throwing.

Every figure renders through `displayMoney`, not `formatMoney`, so the header's
eye toggle cannot miss one. The preference is kept locally in the browser
(`src/lib/privacy.ts`), never sent to the API. The rules behind it are in the
root CLAUDE.md.

## Storage and API details

**Budget mode is stored per user, not per budget.** `budget_mode` on `users` is
`'fixed'` (the default) or `'monthly'`. Fixed budgets are stored under one shared
sentinel month (`FIXED_BUDGET_MONTH` in `budget-mode.ts`) rather than a second
table, so the existing `(category_id, month)` unique index is still what stops a
category from carrying two plans at once. `/api/budgets` and `/api/summary` both
resolve the caller's actual month through `budgetMonthKey()` before touching the
table, and `/api/income-plan` does the same. Changing the mode invalidates every
cached summary for that user, not just the current month, because it changes what
"planned" means everywhere at once.

**Some per-user tables have no guaranteed row.** `round_up_rules` is not
provisioned on sign-in — it follows the same GET-with-default / upsert pattern as
`income_plans` and `payee_history`, since most users never touch the feature.

**Account logos load with `referrerpolicy="no-referrer"`.** The URL lands in an
`<img src>`, and the policy stops browsing from leaking which account is being
viewed to the image's host.

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

What a new user is given — default types and categories, demo data — is described
in the root CLAUDE.md. Signing in creates a `users` row and seeds the rest, and
demo data is skipped when `SEED_DEMO_DATA` is `"false"`. Steady state is a single
indexed `SELECT` per request.

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
