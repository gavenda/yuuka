# yuuka

Multi-user budgeting and financial tracking, running entirely on Cloudflare.

- **Cloudflare Pages** — one project serving the whole app
- **Pages Functions** — the API, in `/functions`, built with [Hono](https://hono.dev)
- **Auth0** — sign-in and user identity, at `auth.gavenda.dev`
- **Cloudflare D1** — SQLite for accounts, categories, transactions and budgets
- **Cloudflare Workers KV** — Auth0 signing keys and cached monthly summaries
- **Vue 3 + Pinia + Tailwind CSS v4** — the frontend, with `@auth0/auth0-vue` for sign-in
- **Chart.js** — the dashboard charts

Bun is the package manager; Wrangler drives everything on the Cloudflare side.

## Layout

```
yuuka/
├── functions/
│   └── api/
│       ├── [[route]].ts   catch-all: mounts the Hono app on /api/*
│       └── _lib/          the API itself — routes, schemas, helpers
├── src/                   Vue app
├── migrations/            D1 schema
├── public/                static assets copied verbatim
└── wrangler.jsonc         Pages config: bindings + build output
```

Pages compiles everything under `functions/` into one Function and serves
`dist/` as the static site, **from a single origin**. The frontend calls `/api`
as a relative path: there is no CORS layer, no API base URL to configure, and no
second deployment to keep in step.

`functions/api/[[route]].ts` is a catch-all, so routing lives in Hono rather than
in the filesystem — adding an endpoint means adding a route, not a file. Its
siblings live in `_lib/`, and the underscore is what keeps Pages from publishing
them as routes of their own.

## Design notes

**Money is never a float.** Every amount is a signed integer in the currency's
minor unit (cents). Outflows are negative and inflows positive, so an account
balance is `starting_balance + SUM(amount)` and a month's net is a plain sum.
Decimal strings exist only at the UI edge, where `parseMoney` splits on the
decimal point rather than multiplying by 100 — `45.99` becomes exactly `4599`.

**Transfers are two linked rows.** Moving money between your own accounts writes
one negative and one positive transaction sharing a `transfer_id`. Both are
excluded from income and spending totals, because a transfer is neither; deleting
either side deletes the pair, so the two accounts can never disagree.

**Two different currency questions, answered separately.** Each account records
the currency it actually holds. The _display currency_ is a user preference —
what net worth, the monthly summary and budgets are shown in — and defaults to
PHP. Previously the display currency was guessed from whichever account happened
to be first, which is only right by accident. Any three-letter code is accepted;
`Intl` renders an unknown one as the code itself, and `formatMoney` falls back
to the amount plus the code rather than throwing, because a malformed value
would otherwise blank every figure on the page.

**Amounts can be hidden globally.** An eye toggle in the header masks every
figure at once — the "someone is looking over my shoulder" switch. It is a local
preference rather than a server-side setting: it protects the current screen,
not the data, so it applies instantly and does not follow the account onto
someone else's device. Every figure renders through one `displayMoney` helper
rather than `formatMoney` directly, so the toggle cannot miss one. Only the
digits are masked — the currency stays visible, or a masked column would be
anonymous dots with no indication of what is being measured — and the mask is a
fixed width, because one that grew with the amount would give away the magnitude
it is meant to hide. Edit forms still show the value being edited —
masking a field you are typing into would make it unusable.

**Account logos are linked, not uploaded.** An account may carry an image URL,
which the browser loads directly from wherever it lives — there is no upload,
no copy and no storage beyond the string. The scheme is restricted to http(s),
because the value ends up in an `<img src>` and anything else there invites
`data:` payloads and similar surprises. A URL that 404s or is not an image
falls back to the account's initial rather than a broken-image glyph, and the
tag carries `referrerpolicy="no-referrer"` so browsing does not leak which
account is being viewed to the image's host.

**Account types are the user's own vocabulary.** Everyone starts with the usual
set — Checking, Savings, Cash, Credit Card, Investment, Loan — and can rename,
extend or retire it from there. Accounts reference a type by id, so a rename
propagates with no backfill. Nothing in the app calculates differently per type;
it is a label for grouping and reading, which is exactly why it can be
user-defined without changing any arithmetic. A type still in use cannot be
deleted — the API says how many accounts hold it and points at archiving, which
hides it from the picker while leaving those accounts intact.

**The payee is the first field, and it fills in the rest.** Saving a named
transaction records what it was filed under — account, category, notes, and
which of the three shapes it was. Typing that name again offers it back, and
choosing it restores the lot, switching the form's mode to match. The newest use
wins rather than merging: the question being answered is "what did I do last
time", and a half-updated row would answer it wrongly. Names are matched
case-insensitively, so "corner market" and "Corner Market" are one payee.

A transfer's payee reads as its name. Left blank it describes itself —
`Checking → Savings` — which is more use in a list than the word "Transfer"
repeated. That composed name is not remembered: it is derived, so offering it
back would be noise.

**Categories nest one level, budgets stay on the parent.** A subcategory
inherits its parent's kind and scope, so an "Investments" under "Cashflow" is
always a transfer category. Its spending counts towards the parent's plan, and
the summary returns top-level categories only — each carrying its children's
figures — so summing the list cannot double-count. A transaction holds a single
category column, which is what makes "the main or the sub, never both" true by
construction rather than by validation.

**Cashflow categories belong to transfers.** Moving money into an investment
account is a transfer, not spending, so it was previously unbudgetable. A
category now records where it may be used — `standard` for spending and income,
`transfer` for movements — and the two sets never appear in the same picker. A
transfer writes a matching pair of rows, so counting both sides would always
come to zero; the cashflow figure measures the outflow leg. It stays out of
income, spending, the daily chart and the expense budget total: budgeting a
movement is not budgeting spending.

**Deleting a category never destroys history.** Transactions fall back to
uncategorised (`ON DELETE SET NULL`); only budgets cascade. Deleting an account
_would_ take its transactions with it, so the API refuses until you ask twice.

**Every row has an owner, and it comes from the token.** Accounts, categories,
transactions and budgets all carry a `user_id`: the Auth0 `sub`. It is read from
the verified access token and never from a request body, path or query
parameter, so there is no input a caller could change to reach another user's
data. Every read filters on it, and every write that references an account or
category checks ownership in the same statement that performs the write, so the
check cannot be overtaken between looking and writing. Someone else's row
answers `404`, not `403` — the API never confirms that an id exists for a
different owner.

**Signing in provisions the account.** A new subject gets a `users` row, the
default account types and categories, and — unless `SEED_DEMO_DATA` is
`"false"` — sample accounts,
transactions and budgets dated into the month they joined, so the first
dashboard has figures on it rather than zeroes. Steady-state cost is a single
indexed `SELECT` per request.

Provisioning is idempotent, and it has to be: the first page load fires several
API calls at once, so they race here. Every id is derived from the subject
rather than random — with random ids a losing batch's `INSERT OR IGNORE` drops
its categories while its transactions still point at them, and the whole batch
dies on a foreign key. Deriving them makes a second run resolve to the same rows
and do nothing.

**Sign-in is the SDK's job, not ours.** `@auth0/auth0-vue` owns the login
redirect, the code exchange, token refresh and the reactive session state, so
there is no auth store in `src/stores` and no callback route — `useAuth0()` is
the single source of truth and Auth0 returns to the site root. The plugin spots
the `code` on the URL during startup, exchanges it, strips the query and
navigates on.

It is installed _after_ the router, which matters: on startup it looks for
`$router` and `push`es to the route the user originally asked for. Installed the
other way round it would rewrite history behind Vue Router's back and the two
would disagree about the current page. The navigation guard waits on the SDK's
`isLoading` for the same reason — the first navigation happens while the code is
still being exchanged, so deciding early would bounce an arriving user straight
back to sign-in.

**The API holds no auth state.** Auth0 issues the access token; every protected
route verifies its RS256 signature against the tenant's published keys, plus the
issuer, audience and expiry. There are no sessions to store and no password to
leak. A valid token from an unlisted subject gets a 403, not a 401 — the holder
is genuine, just not this deployment's owner.

**Token verification fails closed.** An unrecognised key id looks like a key
rotation, so the JWKS is refetched once; if it is still missing, or Auth0 cannot
be reached, the request is denied rather than admitted unverified. A malformed
token is rejected outright, without a network round trip.

**KV does two jobs.** It caches Auth0's signing keys for an hour, keeping JWKS
off the hot path, and caches the `/api/summary` response per user and month —
purged by any write that touches that month, including both months when a
transaction moves across a boundary. The user id is part of the cache key: a
summary cache shared between users would leak one person's figures to another,
so that key is never allowed to be optional.

**Charts are Chart.js, registered piece by piece.** Only the bar controller,
the two scales and the tooltip are registered — importing `chart.js/auto` would
pull in the line, pie and radar controllers nothing renders. The library lands
in the lazily-loaded dashboard chunk rather than the entry bundle. Each chart
keeps a "Show data" table alongside it, which is the non-visual route to the
same numbers.

**Chart colours are validated, not chosen by eye.** The eight category colours
are a fixed-order categorical palette checked against this app's own light and
dark surfaces for lightness, chroma, colour-vision-deficiency separation and
contrast. Adjacent slots stay distinguishable for protan, deutan and tritan
vision, and every coloured mark carries a visible text label — colour never
carries meaning alone.

## Getting started

```bash
bun install
```

### 1. Configure Auth0

At <https://auth.gavenda.dev>, create two things:

- **An API** — its _Identifier_ is the audience. The API only accepts tokens
  addressed to it, so this value must match on both sides.
- **A Single Page Application** — note its _Client ID_, and add these origins
  to its Allowed Callback URLs, Allowed Logout URLs and Allowed Web Origins.
  Auth0 returns to the site root, so they carry no path:

  ```
  http://localhost:5173        https://<project>.pages.dev
  ```

Then fill in the two halves. The browser's copy goes in `.env`
(`cp .env.example .env`):

```
VITE_AUTH0_DOMAIN=auth.gavenda.dev
VITE_AUTH0_CLIENT_ID=<the SPA client id>
VITE_AUTH0_AUDIENCE=<the API identifier>
```

and the API's copy is already in `wrangler.jsonc` under `vars` — set
`AUTH0_AUDIENCE` to that **same** identifier. A mismatch here is the usual cause
of every request coming back 401.

None of these are secrets: a single-page app cannot keep one, so the client id
and audience are public by design and the API holds no auth secret at all — it
only verifies signatures against Auth0's published keys.

Users are separated from each other automatically — each sees only their own
books. `ALLOWED_SUBJECTS` in `wrangler.jsonc` is a separate, optional gate on
_who may use this deployment at all_:

```jsonc
// Empty: anyone who can sign in to the tenant gets their own books.
"ALLOWED_SUBJECTS": "",
// Or restrict it to named people:
"ALLOWED_SUBJECTS": "auth0|abc123,auth0|def456",
```

Leave it empty only if you are happy for anyone who can sign in to the tenant to
register. If your tenant allows public signup, that is the whole internet. A
valid token from an unlisted subject gets `403`; find yours from
`GET /api/auth/me`.

### 2. Create the local database

```bash
bun run db:migrate:local
```

That is all the setup there is. Signing in provisions your account on the spot:
a user row, the default categories, and sample accounts, transactions and
budgets so the dashboard has something on it.

The sample data is not yours — it is two invented accounts and four invented
transactions. To start from an empty book instead, set `SEED_DEMO_DATA` to
`"false"` in `wrangler.jsonc` before signing in for the first time; you then get
the categories and nothing else.

### 3. Run it

```bash
bun run dev
```

This starts both halves: Pages Functions on `:8788` for `/api`, and Vite on
`:5173` with HMR. Vite proxies `/api` through to the Functions runtime, so
development matches production — same relative paths, same single origin. Open
<http://localhost:5173>.

To check the real thing end to end, `bun run preview` builds and serves the
built site and the Functions together on `:8788`, exactly as Pages will.

## Deploying

Create the Cloudflare resources once, then paste the IDs into `wrangler.jsonc`:

```bash
bunx wrangler pages project create yuuka
bunx wrangler d1 create yuuka
bunx wrangler kv namespace create CACHE
```

Then ship it:

```bash
bun run db:migrate    # migrate the remote D1
bun run deploy
```

There is no auth secret to deploy. Remember to add the production origin
to the Auth0 application, and to point `AUTH0_AUDIENCE` and `ALLOWED_SUBJECTS`
in `wrangler.jsonc` at the real values before deploying.

One deployment carries both the site and the API, so there is nothing to keep in
sync between them.

## API

All routes are under `/api`. Everything except `/api/health` requires an Auth0
access token in `Authorization: Bearer <token>`.

| Method                  | Path                         | Purpose                                    |
| ----------------------- | ---------------------------- | ------------------------------------------ |
| `GET`                   | `/health`                    | Liveness check                             |
| `GET`                   | `/auth/me`                   | The verified caller's subject and claims   |
| `GET/DELETE`            | `/payees`                    | Remembered payees, for autosuggest         |
| `GET/PATCH`             | `/settings`                  | Per-user preferences (display currency)    |
| `GET/POST/PATCH/DELETE` | `/account-types`             | Account types, with usage counts           |
| `GET/POST/PATCH/DELETE` | `/accounts`, `/accounts/:id` | Accounts, with derived balances            |
| `GET/POST/PATCH/DELETE` | `/categories`                | Categories                                 |
| `GET/POST/PATCH/DELETE` | `/transactions`              | Transactions, filtered and paged           |
| `POST`                  | `/transactions/transfer`     | Write both legs of a transfer              |
| `GET/PUT/DELETE`        | `/budgets`                   | Per-category monthly budgets               |
| `GET`                   | `/summary?month=YYYY-MM`     | Totals, balances, budget vs actual, by day |

`GET /transactions` accepts `month`, `from`, `to`, `accountId`, `categoryId`
(or `none` for uncategorised), `search`, `limit` and `offset`.

Signing in and out happen against Auth0 in the browser, so there is no login
endpoint: the API only ever verifies the access token a request carries.

## Scripts

| Command                    | What it does                                     |
| -------------------------- | ------------------------------------------------ |
| `bun run dev`              | Functions on :8788 and Vite on :5173             |
| `bun run preview`          | Build, then serve site + Functions as Pages does |
| `bun run test`             | Both test suites                                 |
| `bun run typecheck`        | Typecheck the browser and server halves          |
| `bun run build`            | Build the static site                            |
| `bun run format`           | Prettier over the repo                           |
| `bun run db:migrate:local` | Apply migrations to the local D1                 |
| `bun run deploy`           | Build and deploy to Pages                        |

## Tests

260 tests: 200 against the API — running in the real `workerd` runtime via
`@cloudflare/vitest-plugin`, against a migrated D1 database — and 60 over the
frontend's money, date, palette, privacy and chart-data helpers, and its
navigation guard.

Twenty cover payee memory — casing, newest-use-wins, per-user privacy, and a
transfer naming itself — plus ten over the suggestion ranking. Fourteen cover
subcategories — inheritance, the one-level limit, sibling name
collisions, and the budget rollup — and ten cover Cashflow, including that a
transfer category is refused on spending and a standard one on a transfer. Ten
cover account logos, including the refusal of `javascript:`, `data:`,
`file:` and scheme-relative URLs. Nine cover the display currency — the PHP default, changing and canonicalising
it, refusing malformed codes, accounts keeping their own currency regardless,
and one user's preference not touching another's. Eighteen cover account
types — the default set, adding and renaming, rename
propagation, the in-use delete refusal, archiving, and one user's types being
unreachable by another. Eight more cover first-sign-in provisioning, including the concurrent case that the
dashboard's parallel requests produce.

Nineteen more are a dedicated isolation suite that attacks every
route from the wrong side of the fence: reading, editing and deleting another
user's rows, filtering by their account id, writing transactions against their
accounts and categories, transferring money out of their account, and checking
that a cached summary never crosses between users.

The guard tests matter because the guard is the only thing between a visitor and
someone's finances: they cover every protected route while signed out, the
remembered redirect target, and the two cases where the SDK has not settled yet:
a reload with an existing session, and a user arriving back from Auth0 at the
root mid-exchange.

Nineteen more are a dedicated isolation suite that attacks every route from
the wrong side of the fence: reading, editing and deleting another user's rows,
filtering by their account id, writing transactions against their accounts and
categories, transferring money out of their account, and checking that a cached
summary never crosses between users.

The auth tests mint their own RS256 tokens and publish the matching JWKS into
KV, so token verification is exercised for real — expired tokens, wrong
audience, wrong issuer, tampered payloads and unknown signing keys all have
cases — without any network access.

```bash
bun run test
```

The server suite mounts the same Hono app the Pages Function does, through a
thin entry in `test/worker-entry.ts`, so routing is exercised exactly as it ships.
