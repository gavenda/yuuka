# yuuka

Multi-user budgeting and financial tracking, running entirely on Cloudflare.

- **Cloudflare Workers** — one Worker serving the whole app, with the built site as Static Assets
- **The API** — the Worker script, in `/server`, built with [Hono](https://hono.dev)
- **Auth0** — sign-in and user identity, at `auth.gavenda.dev`
- **Cloudflare D1** — SQLite for accounts, categories, transactions and budgets
- **Cloudflare Workers KV** — Auth0 signing keys and cached monthly summaries
- **Cron Triggers** — a daily run at 00:00 UTC that posts due subscriptions
- **Vue 3 + Pinia + Tailwind CSS v4** — the frontend, with `@auth0/auth0-vue` for sign-in
- **A PWA** — installable, and opens offline on what it last saw (`vite-plugin-pwa`); changes still need a connection

Bun is the package manager; Wrangler drives everything on the Cloudflare side.

## Layout

```
yuuka/
├── server/
│   ├── index.ts           Worker entry: the Hono app answering /api/*, and the cron handler
│   └── …                  routes, schemas, helpers
├── src/                   Vue app
├── migrations/            D1 schema
├── public/                static assets copied verbatim
└── wrangler.jsonc         Worker config: bindings, assets, routes
```

The Worker runs `server/index.ts` for `/api/*` and serves `dist/` as the static
site for everything else, from a single origin. The frontend calls `/api` as a
relative path: there is no CORS layer, no API base URL to configure, and no
second deployment to keep in step.

## Getting started

```bash
bun install
```

### 1. Configure Auth0

At Auth0, create two things:

- **An API** — its _Identifier_ is the audience. The API only accepts tokens
  addressed to it, so this value must match on both sides.
- **A Single Page Application** — note its _Client ID_, and add these origins
  to its Allowed Callback URLs, Allowed Logout URLs and Allowed Web Origins.
  Auth0 returns to the site root, so they carry no path:

  ```
  http://localhost:5173        https://yuuka.gavenda.dev
  ```

The browser's half goes in `.env` (`cp .env.example .env`):

```
VITE_AUTH0_DOMAIN=<the auth0 domain>
VITE_AUTH0_CLIENT_ID=<the SPA client id>
VITE_AUTH0_AUDIENCE=<the API identifier>
```

The API's half is already in `wrangler.jsonc` under `vars` — set
`AUTH0_AUDIENCE` to that **same** identifier. A mismatch here is the usual cause
of every request coming back 401.

None of these are secrets: a single-page app cannot keep one, so the client id
and audience are public by design, and the API holds no auth secret at all — it
only verifies signatures against Auth0's published keys.

Users are separated from each other automatically — each sees only their own
books. Anyone who can sign in to the Auth0 tenant gets an account here, so
_who may use this deployment at all_ is controlled in Auth0 itself — turn off
public signup, or restrict the connection to named people — rather than in
this app.

### 2. Create the local database

```bash
bun run db:migrate:local
```

Signing in provisions the rest on the spot: a user row, the default account
types and categories, and sample accounts, transactions and budgets so the
dashboard has something on it. The sample data is invented — set
`SEED_DEMO_DATA` to `"false"` in `wrangler.jsonc` before the first sign-in to
start from an empty book with only the categories.

### 3. Run it

```bash
bun run dev
```

This starts both halves: the Worker on `:8788` for `/api`, and Vite on
`:5173` with HMR. Vite proxies `/api` through to the Worker, so
development matches production — same relative paths, same single origin. Open
<http://localhost:5173>.

To check the real thing end to end, `bun run preview` builds and serves the
built site and the Worker together on `:8788`, exactly as Workers will.

## Deploying

Create the Cloudflare resources once, then paste the IDs into `wrangler.jsonc`:

```bash
bunx wrangler d1 create yuuka
bunx wrangler kv namespace create CACHE
```

Then ship it:

```bash
bun run db:migrate    # migrate the remote D1
bun run deploy
```

There is no auth secret to deploy. Add the production origin to the Auth0
application, and point `AUTH0_AUDIENCE` at the real value first. One deployment
carries both the site and the API, so there is nothing to keep in sync between
them.

## Cloud Messaging

Both apps are offline-first: a change is written to the local database and sent
when there is a connection. That leaves one problem push solves — a change made
in one app leaves the other's copy quietly wrong until something makes it ask
again. Firebase Cloud Messaging is how the server says which parts of the
ledger moved, so the other app refetches those and nothing else.

It is entirely optional. With nothing configured, the Worker sends no messages,
neither client registers, and both still sync on open, on reconnect and on
pull-to-refresh. Set it up when you want the two to keep up with each other in
seconds rather than on next open.

### 1. Create the Firebase project

1. Open the [Firebase console](https://console.firebase.google.com) and
   **Add project**. Analytics is not used; skip it.
2. Note the **Project ID** (something like `yuuka-1a2b3`). Everything below
   hangs off it.

### 2. Register the Android app

1. In the console, **Project settings → General → Your apps → Add app →
   Android**.
2. Package name: `dev.gavenda.yuuka` — it must match `applicationId` in
   `app/build.gradle.kts` exactly.
3. Download `google-services.json` and put it at **`app/google-services.json`**
   (beside `app/build.gradle.kts`, not at the repository root).

That is the whole Android side. `app/build.gradle.kts` applies the
`com.google.gms.google-services` plugin only when that file is present, so a
checkout without it still builds — it just builds without push, and says so in
the Gradle log. The file contains no secrets, but it is per-project, so keep it
out of the repository if the project is shared.

### 3. Register the web app

1. **Project settings → General → Your apps → Add app → Web**.
2. Copy the config values into `webapp/.env` (see `.env.example`):
   `VITE_FIREBASE_API_KEY`, `VITE_FIREBASE_AUTH_DOMAIN`,
   `VITE_FIREBASE_PROJECT_ID`, `VITE_FIREBASE_MESSAGING_SENDER_ID`,
   `VITE_FIREBASE_APP_ID`.
3. **Project settings → Cloud Messaging → Web configuration → Web Push
   certificates → Generate key pair**. Copy the key pair's public key into
   `VITE_FIREBASE_VAPID_KEY`.

All six are public identifiers that the page ships anyway — they are not
secrets, which is why `public/firebase-messaging-sw.js` is allowed to take them
on its registration URL rather than repeating them.

Web push needs a secure context: it works on `https://` and on `localhost`, and
not on a plain-HTTP LAN address.

### 4. Give the Worker a service account

The server sends through FCM's HTTP v1 API, which authenticates as a service
account rather than with the old server key.

1. **Project settings → Service accounts → Generate new private key**. A JSON
   file downloads. It _is_ a secret — do not commit it.
2. Set three Worker secrets from it:

```bash
bunx wrangler secret put FCM_PROJECT_ID     # "project_id" in the JSON
bunx wrangler secret put FCM_CLIENT_EMAIL   # "client_email"
bunx wrangler secret put FCM_PRIVATE_KEY    # "private_key", newlines and all
```

`FCM_PRIVATE_KEY` is a PEM blob with real newlines in it. Pasting it into
`wrangler secret put` works; if whatever you pipe it through turns the newlines
into a literal `\n`, that is accepted too — `server/fcm.ts` puts them back.

For local development, put the same three in `webapp/.dev.vars`.

### 5. Check it

Sign in on both, make a change in one, and the other should catch up within a
second or two without being touched. If it does not:

- `wrangler tail` shows `FCM send failed` with the status FCM gave, which is
  usually a wrong project id or a key that was not pasted whole.
- A token FCM rejects as dead is deleted from the `devices` table on the spot,
  so a device that has been reinstalled sorts itself out on next start.
- On the web, an unregistered service worker or a refused notification
  permission both mean no token; the browser console says which.

Nothing anywhere depends on a message arriving. If push is broken, the apps are
exactly as up to date as they would be without it.

## API

All routes are under `/api`. Everything except `/api/health` requires an Auth0
access token in `Authorization: Bearer <token>`.

| Method                  | Path                         | Purpose                                           |
| ----------------------- | ---------------------------- | ------------------------------------------------- |
| `GET`                   | `/health`                    | Liveness check                                    |
| `GET`                   | `/auth/me`                   | The verified caller's subject and claims          |
| `GET/DELETE`            | `/payees`                    | Remembered payees, for autosuggest                |
| `GET/PATCH`             | `/settings`                  | Per-user preferences (display currency)           |
| `GET/PATCH`             | `/round-up`                  | "Save the Change" round-up rule                   |
| `GET/POST/PATCH/DELETE` | `/account-types`             | Account types, with usage counts                  |
| `GET/POST/PATCH/DELETE` | `/accounts`, `/accounts/:id` | Accounts, with derived balances                   |
| `POST`                  | `/accounts/:id/adjust`       | Log the gap to a target balance as income/expense |
| `GET/POST/PATCH/DELETE` | `/categories`                | Categories                                        |
| `GET/POST/PATCH/DELETE` | `/tags`                      | Tags, the labels a transaction can wear           |
| `GET/POST/PATCH/DELETE` | `/transactions`              | Transactions, filtered and paged                  |
| `POST`                  | `/transactions/transfer`     | Write both legs of a transfer                     |
| `GET/POST/PATCH/DELETE` | `/subscriptions`             | Monthly charges the daily cron posts for you      |
| `GET/PUT/DELETE`        | `/budgets`                   | Per-category monthly budgets                      |
| `GET`                   | `/summary?month=YYYY-MM`     | Totals, balances, budget vs actual, by day        |

`GET /transactions` accepts `month`, `from`, `to`, `accountId`, `categoryId`
(or `none` for uncategorised), `search`, `limit` and `offset`.

### Subscriptions

A subscription is a monthly charge (or credit) on an account of the user's
choosing, with a payee, category and notes. The Worker's `scheduled` handler
runs from a [Cron Trigger](https://developers.cloudflare.com/workers/configuration/cron-triggers/)
(`triggers.crons` in `wrangler.jsonc`, `0 0 * * *`) and posts each due
subscription as an ordinary transaction, flagged `automated: true`. Every run is
at 00:00 UTC, so there is no time of day to choose and the API refuses to give an
automated transaction one. Its day of the month is the start date's; a month too
short for it posts on the last day. To try it locally, `bun run dev` serves
`GET /__scheduled` on the Worker:

```bash
curl "http://localhost:8788/__scheduled?cron=0+0+*+*+*"
```

Signing in and out happen against Auth0 in the browser, so there is no login
endpoint: the API only ever verifies the access token a request carries.

## Scripts

| Command                    | What it does                                    |
| -------------------------- | ----------------------------------------------- |
| `bun run dev`              | Worker on :8788 and Vite on :5173               |
| `bun run preview`          | Build, then serve site + Worker as Workers does |
| `bun run test`             | Both test suites                                |
| `bun run typecheck`        | Typecheck the browser and server halves         |
| `bun run build`            | Build the static site                           |
| `bun run format`           | Prettier over the repo                          |
| `bun run db:migrate:local` | Apply migrations to the local D1                |
| `bun run deploy`           | Build and deploy the Worker                     |

## Tests

```bash
bun run test
```

The server suite runs in the real `workerd` runtime via
`@cloudflare/vitest-plugin`, against a migrated D1 database, mounting the same
Hono app the Worker ships, `server/index.ts`, as its entry.
It covers each resource, first-sign-in provisioning, token verification against
a JWKS the tests publish themselves, and a dedicated isolation suite that
attacks every route from the wrong side of the fence. The browser suite covers
the money, date, palette, privacy and chart-data helpers, and the navigation
guard.

Design notes and the invariants behind them live in [CLAUDE.md](CLAUDE.md).
