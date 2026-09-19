# CLAUDE.md

Guidance for working in this repo. The README covers setup, deploying and the
route list; this file covers the decisions behind the code and the invariants
that must survive a change. It describes how the application behaves, for the
web app and the Android app alike. Technical detail specific to the web app and
its API (commands, architecture, security, auth, tests) lives in
[webapp/CLAUDE.md](webapp/CLAUDE.md).

## Important
The following bullets and sections are important and should be adhered:
- When the user says android app, solely focus on the android app. Unless stated otherwise.
- Even if the user says android app, you can only touch the API part of webapp when making API changes.
- Versions of the android app and web app are synced. So whenever an android only bump is made, both versions will be affected.
- When an emulator is running, and you want to visually check, always ask the user if they want to visually check before proceeding.

### Deployment
- A simple tag push to remote will build the android app in a GitHub Actions CI environment.
- A separate commit bump before the actual version tagging.
- If a change in the database or api changes, always run `bun run db:migrate && bun run deploy` after pushing the tags to origin.

### IDE
If Idea MCP is available, use it for the following:
- Analyze function paths (analyze_calls)
- Building (build_project)
- Linting (line_files)
- Getting symbol info (get_symbol_info)
- Creating new file (create_new_file)
- Listing directories (list_directory_tree)
- Reformatting (reformat_file)
- Applying patches (apply_patch)
- Read file (read_file)
- Search file (search_file)
- Search text (search_text)
- Search regex (search_regex)
- Search symbol (search_symbol)
- Refactor rename (rename_refactoring)
- Get repositories (get_repositories)
- Git Status (git_status)
- Launching build scripts (get_run_configurations, execute_run_configuration)

When debugging, use `get_run_configuration` to get the debug config and use `execute_run_configuration` to run it.

Otherwise, opt-in for shell commands.

## Money

**Money is never a float.** Every amount is a signed integer in the currency's
minor unit (cents). Outflows are negative and inflows positive, so an account
balance is `starting_balance + SUM(amount)` and a month's net is a plain sum.
Decimal strings exist only at the UI edge, where the input is split on the
decimal point rather than multiplied by 100 — `45.99` becomes exactly `4599`.

**Two different currency questions, answered separately.** Each account records
the currency it actually holds. The _display currency_ is a user preference —
what net worth, the monthly summary and budgets are shown in — and defaults to
PHP. Never infer it from whichever account happens to be first. Any three-letter
code is accepted, and formatting one the platform does not know must degrade to
the amount plus the code rather than throw, because a malformed value would
otherwise blank every figure on the page.

**Every figure renders through `displayMoney`, not `formatMoney`.** An eye
toggle in the header masks all amounts at once — the "someone is looking over my
shoulder" switch — and routing every figure through one helper is what stops the
toggle missing one. (`displayMoney` in the web app; `AmountVisibility.displayMoney`,
usually via `MoneyText`, in the Android app.) It is a local preference, not a
server-side setting: it protects the current screen, not the data. Only the
digits are masked, the currency stays visible, and the mask is a fixed width —
one that grew with the amount would give away the magnitude it exists to hide.
Edit forms still show the value being edited.

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

**A budget's month is a per-user mode, not a per-budget choice.** The budget mode
is `'fixed'` (the default) or `'monthly'`, set from Settings. A fixed budget
answers every month's summary with the same planned figure; a monthly one
belongs to the month it was set for. Switching modes doesn't move data between
the two, it just changes which stored budgets future reads and writes land on,
and a category can never carry two plans at once. A budget's `month` in API
responses is `null` when it is fixed, since it no longer belongs to one.
Planned income (`/api/income-plan`) follows the same mode: it is what a
percentage-based budget is a share of, so it would be inconsistent for it to stay
per-month while the budgets built on it go fixed. How the mode is stored and
resolved is in [webapp/CLAUDE.md](webapp/CLAUDE.md).

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

**Subscriptions post ordinary transactions, once a month, at 00:00 UTC.** A
subscription is an account, a payee, a category, notes, a signed amount and a
start date — no more; it is not a new kind of transaction. The start date's day
of the month is the anchor for every later run, and a month too short for it
posts on its last day (the anchor is kept, so 31 January runs on 28 February and
then 31 March again). The Worker's cron does the posting, never the client, and
what it writes is a plain single-account row flagged `automated`: it counts in
income, spending and balances like any other, and can be edited or deleted like
one. Every run is at 00:00 UTC, so nobody chooses a time of day — the flag is
what lets both apps lock the time field, and the API refuses to give an
automated row a time. The date can still be edited. The rules that keep it
honest:

- A start date must be today (UTC) or later. Past dates are refused, so setting
  one up never backfills history; a run that was missed catches up on the next
  tick, bounded, and resuming a paused subscription skips whatever fell due
  while it was paused rather than posting a backlog.
- A run is posted at most once. Deleting an automated transaction is a decision
  the schedule respects — it has already moved on and does not bring it back.
- Deleting a subscription keeps everything it already posted (history), and the
  same goes for its category being deleted (the subscription becomes
  uncategorised). Deleting its account deletes it — it is configuration, not
  history.
- Categories are standard-scope only, as for any transaction. Automated rows do
  not trigger "Save the Change" and do not teach the payee history: both follow
  what a person enters, not what a schedule does.

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

**Account logos are linked, not uploaded.** An account may carry an image URL the
client loads from wherever it lives — no upload, no copy, no storage beyond the
string. The scheme is restricted to http(s), because the value ends up in an
image load and anything else invites `data:` payloads and similar surprises. A
URL that fails to load falls back to the account's initial. Inverting the logo in
dark mode is a per-account opt-in (`logoInvertDark`), not a blanket filter — a
dark mark on a transparent background needs it to stay visible, but a colour logo
inverted the same way would come out wrong.

## Sync

**The server is the source of truth; the local copy is a cache — in the Android
app and in the web app, which is installable and opens offline.**
Pull-to-refresh is a full sync, not a top-up. The per-screen loads only ever
fold rows in, so something deleted elsewhere (the web app, another device)
would otherwise linger here indefinitely. A full sync replaces accounts, types,
categories, settings, the round-up rule, subscriptions and payees from the API,
and only once that has succeeded discards the cached transactions, budgets,
income plans and summaries, so the screens on show load theirs again. A failed sync (offline,
expired session) leaves the last-seen ledger untouched rather than empty.

The web app does the same with what it saved in the browser: a screen shows what
was last seen at once, then the API's answer replaces it, and an unreachable API
leaves the saved copy on screen rather than an error. Nothing is queued while
offline — a change is made against the API, so it fails until there is a
connection, and the saved copy only ever changes by what the API returned. It is
one person's books, so it is discarded on sign-out and when someone else signs in.
How it is built is in [webapp/CLAUDE.md](webapp/CLAUDE.md).

## Provisioning

Signing in for the first time provisions the account: the default account types
and categories, and — unless demo seeding is switched off — sample accounts,
transactions and budgets dated into the month the user joined, so the first
dashboard has figures on it. How this stays safe under concurrent requests is in
[webapp/CLAUDE.md](webapp/CLAUDE.md).

## graphify

This project has a knowledge graph at graphify-out/ with god nodes, community structure, and cross-file relationships.

Rules:
- For codebase questions, first run `graphify query "<question>"` when graphify-out/graph.json exists. Use `graphify path "<A>" "<B>"` for relationships and `graphify explain "<concept>"` for focused concepts. These return a scoped subgraph, usually much smaller than GRAPH_REPORT.md or raw grep output.
- If graphify-out/wiki/index.md exists, use it for broad navigation instead of raw source browsing.
- Read graphify-out/GRAPH_REPORT.md only for broad architecture review or when query/path/explain do not surface enough context.
- After modifying code, run `graphify update .` to keep the graph current (AST-only, no API cost).

### Yuuka API

The api lives in [webapp](webapp). More details available inside it's respective [CLAUDE.md](webapp/CLAUDE.md).
