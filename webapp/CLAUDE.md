# CLAUDE.md

Technical guidance for the web app and its API. The README covers setup,
deploying and the route list; this file covers how the webapp is built and the
invariants that must survive a change. What the application does — money rules,
the ledger model, budgets, transfers, round-ups, payees — is described in the
root [CLAUDE.md](../CLAUDE.md), and applies here as much as to the Android app.

## Commands

```bash
bun run dev         # Worker on :8788, Vite on :5173
bun run test        # both suites: browser (vitest) and API (workerd + D1)
bun run typecheck   # vue-tsc for the browser half, tsc for server/
bun run format      # prettier; tabs, single quotes, 140 columns
```

Run `bun run typecheck` and `bun run test` before calling a change done. Prettier
owns formatting — do not hand-align anything it will rewrite.

## Shape of the app

One Worker serves both halves from a single origin. `server/index.ts` is its entry
and exports a `fetch` handler (the Hono app, which owns every `/api/*` route) plus
a `scheduled` handler for the cron trigger (see Subscriptions below). Routing
lives in Hono rather than in the filesystem: adding an endpoint means adding a route in
`server/routes/`, not a file at a path. `wrangler.jsonc` serves the Vite build in
`dist/` as Static Assets, with `run_worker_first: ["/api/*"]` sending only API
paths to the Worker and `not_found_handling: "single-page-application"` handing
every other non-file path to the SPA shell. Both are load-bearing: without the
first, whether a request reaches the Worker is decided by its
`Sec-Fetch-Mode: navigate` header, so opening a `/api/*` URL in the browser would
get the SPA shell (the app's own `fetch()` calls would still get through) instead
of the API's JSON, 404s included; without the second, deep links would 404 on
reload. There is no `public/_redirects` — it would conflict with the SPA fallback.

The same fallback answers a missing file with the SPA shell too (200,
`text/html`), so a tab opened before a deploy asks for a route chunk whose hashed
name no longer exists and gets HTML back. `main.ts` handles Vite's
`vite:preloadError` by reloading for the new build (`lib/staleChunk.ts`), at most
once per ten seconds so a chunk that is really gone cannot loop the page. The frontend calls
`/api` relatively — there is no base URL to configure and no CORS layer to
maintain.

Frontend state is three Pinia stores in `src/stores`: `ledger` (accounts, types,
categories, settings — loaded once and shared), `budget` and `transactions`.
There is deliberately no auth store; see below.

## Installable and offline

The app is a PWA, and it behaves like the Android app: the screens paint from a
local copy, and the API stays the source of truth. Two separate pieces do it, and
neither caches the other's job.

**The service worker caches the app, never the data.** `vite-plugin-pwa`
(`generateSW`, configured in `vite.config.ts`) precaches the whole build — lazy
route chunks included, so any screen opens offline — and answers a navigation
with the cached `index.html`, except under `/api/`, which is not a page and is
never cached. Figures reach the screen only through the local copy below, so
there is one place to reason about staleness.

`registerType` is `'prompt'`: a new build downloads and waits, and `App.vue`
offers a "Reload" strip (`lib/pwa.ts`). Taking it over reloads the page, which
would throw away a half-filled form, so it is never done on its own; until it is
taken a tab keeps running the build it started with, files and all. The
stale-chunk reload above is still needed — it covers a tab whose chunk was
deleted by a deploy before its worker ever updated. Do not put a long
`Cache-Control` on `sw.js`: Static Assets serves it with `max-age=0,
must-revalidate`, which is what lets a new worker be noticed. The icons are the
Android launcher art: `pwa-192.png` and `pwa-512.png` are the same 432px crop of
the adaptive foreground over `#2A78D6` that `yuuka.png` is, and
`pwa-maskable-512.png` is the whole 648px canvas, since Android's safe zone is
stricter than a maskable icon's.

**The local copy is a cache, and losing it costs nothing.** `lib/cache.ts` keeps
it in `localStorage` under `yuuka.cache.*`, and every function there is
best-effort and never throws — blocked storage, a full quota or a corrupt entry
all read as "nothing saved", and the store loads as it always did. A full quota
drops the oldest entries first. Entries carry a version (`VERSION`); bump it when
a stored shape changes and older ones read as missing.

Each store follows the same shape: on `load`, show the copy if there is one, ask
the API, replace what is on screen with the answer and write it back. Three rules
keep that honest:

- **Only an unreachable API is forgiven.** A failure with `ApiError.status === 0`
  (`isNetworkError`; `request()` turns a failed `fetch` into it) leaves the copy
  on screen without an error. An answer from the API — a 500, a 404 — is still
  reported, because then the copy is not merely out of date, it is contradicted.
  With nothing to show, nothing is forgiven.
- **A copy on screen is not a fresh one.** The stores track that the API has
  answered (`synced` in `ledger`/`subscriptions`, `freshFor` in `budget`)
  separately from having something to show, so a copy shown offline is fetched
  again next time rather than treated as loaded.
- **Snapshots are bounded and dropped after writes.** Summaries are kept per month
  and transaction pages per filter set — first page only, never what "load more"
  appended — each family capped by `writeCache`'s `keep`. `budget.refresh()` and
  `transactions.refresh()` are what every write already calls, and once the API
  has answered they drop the other snapshots: a write can move figures in a month
  or filter the person is not looking at, and only the API knows.

Writes are not queued. There is no offline outbox, as in the Android app: a
write goes to the API first and fails with "Network error" when offline, and the
copy only ever changes by what the API returned. `App.vue` shows an offline strip
(`lib/online.ts`, a hint only — nothing decides on `navigator.onLine`) and runs
`fullSync` (`lib/sync.ts`) when the browser reports a connection again, which
fetches whatever is already loaded and leaves anything unopened alone.

The copy holds one person's books, so it is bound to them. The router guard calls
`adoptCacheFor(sub)` once the session has settled, which clears it when a
different person (or nobody identifiable) has signed in, and `signOut` and the
401 handler in `main.ts` clear it outright. Payee suggestions are not kept: they
are typing-driven, and saving needs the API anyway.

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

**Subscriptions are posted by a cron, and every run must be safe to repeat.**
`wrangler.jsonc` declares `triggers.crons: ["0 0 * * *"]`, which calls the
`scheduled` handler in `server/index.ts` → `runDueSubscriptions`
(`server/subscriptions.ts`). Cron Triggers run in UTC, which is why an automated
transaction is stored as a bare `YYYY-MM-DD` and the flag `transactions.automated`
— not the string — is what tells the UI to lock the time; `PATCH /transactions/:id`
refuses to give an automated row a time of day. The handler uses
`controller.scheduledTime`, not the wall clock, so a late tick still posts for the
day it was meant for.

A tick can be retried or overlap another, so a run cannot be allowed to post
twice: each occurrence is one atomic `DB.batch` of an `INSERT OR IGNORE … SELECT
… FROM subscriptions WHERE next_run_on = ?` followed by the `UPDATE` that
advances `next_run_on`. That column is the compare-and-swap token — whichever
invocation loses finds it already moved and writes nothing — and the transaction
id is derived from the subscription and the date (`stableId`), so even a
bypassed guard would collide on the primary key. Because the insert copies the
subscription's own row, what posts is what it holds at that instant. It also
means a user deleting an automated transaction sticks: the schedule has moved on.
`day_of_month` is stored rather than derived from `next_run_on`, so a clamped
month (31 → 28) does not drag the schedule down with it. A tick catches up at
most `MAX_CATCH_UP` missed runs per subscription; one failing subscription is
logged and does not stop the rest.

`server/routes/subscriptions.ts` only manages the schedule and never writes a
transaction. Its writes check ownership of the account and category in the same
statement (`applies_to = 'standard'`), like `POST /transactions`. The cron is
covered by calling `runDueSubscriptions(env, now)` with a fixed clock, plus one
test through the real `scheduled` export; seed rows directly for dates in the
past, since the API refuses a start date before today.

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

456 tests: 333 against the API in `test/`, 123 over the browser helpers as
`*.spec.ts` beside the code they cover. `src/testing/memoryStorage.ts` gives a
spec an in-memory `localStorage` to install — Node's own needs a backing file.

The server suite runs in `workerd` against a migrated D1 database and mounts the
same Hono app the Worker ships, `server/index.ts` being its `main`, so routing is
exercised as it ships. `test/helpers.ts` gives you an authed client;
`test/tokens.ts` mints RS256 tokens and publishes the matching JWKS into KV, so
expiry, audience, issuer, tampering and unknown signing keys are all exercised
without network access.

When you add a route or change an ownership check, add a case to
`test/isolation.spec.ts` as well — that suite is the one that proves another user
cannot reach the data.
