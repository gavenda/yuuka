# CLAUDE.md

Guidance for working in this repo. The README covers setup, deploying and the
route list; this file covers the decisions behind the code and the invariants
that must survive a change. It describes how the application behaves, for the
web app and the Android app alike. 

Technical detail specific to the web app and
its API (commands, architecture, security, auth, tests) lives in
[webapp/CLAUDE.md](webapp/CLAUDE.md).

## Important

The following bullets and sections are important and should be adhered:

- When the user says android app, solely focus on the android app. Unless stated otherwise.
- Even if the user says android app, you can only touch the API part of webapp when making API changes.
- Versions of the android app and web app are synced. So whenever an android only bump is made, both versions will be affected.
- When an emulator is running, and you want to visually check, always ask the user if they want to visually check before proceeding.
- Any user interface must change both apps.

### Deployment

- A simple tag push to remote will build the android app in a GitHub Actions CI environment.
- A separate commit bump before the actual version tagging.
- If a change in the database or api changes, always run `bun run db:migrate && bun run deploy` after pushing the tags to origin.

#### Version Bumping

- A working-tree commit, then a separate version-bump commit, then a lightweight tag.

### Android

If Idea MCP is available, use `execute_run_configuration` to run the application,
configuration name is `app` for running the application in the emulator.

### IDE

If Idea MCP is available, use it for the following:

- Analyze function paths (`analyze_calls`)
- Building (`build_project`)
- Linting (`line_files`)
- Getting symbol info (`get_symbol_info`)
- Creating new file (`create_new_file`)
- Listing directories (`list_directory_tree`)
- Reformatting (`reformat_file`)
- Applying patches (`apply_patch`)
- Read file (`read_file`)
- Search file (`search_file`)
- Search text (`search_text`)
- Search regex (`search_regex`)
- Search symbol (`search_symbol`)
- Refactor rename (`rename_refactoring`)
- Get repositories (`get_repositories`)
- Git Status (`git_status`)
- Launching build scripts (`get_run_configurations`, `execute_run_configuration`)

When debugging, use `get_run_configuration` to get the debug config and use `execute_run_configuration` to run it.

Otherwise, opt-in for shell commands.

## Money

- **Money is never a float**: Every amount is a signed integer in the currency's
minor unit (cents). Outflows are negative and inflows positive, so an account
balance is `starting_balance + SUM(amount)` and a month's net is a plain sum.
Decimal strings exist only at the UI edge, where the input is split on the
decimal point rather than multiplied by 100 — `45.99` becomes exactly `4599`.

- **Two different currency questions, answered separately**: Each account records
the currency it actually holds. The _display currency_ is a user preference —
what net worth, the monthly summary and budgets are shown in — and defaults to
PHP. Never infer it from whichever account happens to be first. Any three-letter
code is accepted, and formatting one the platform does not know must degrade to
the amount plus the code rather than throw, because a malformed value would
otherwise blank every figure on the page.

- **Every figure renders through `displayMoney`, not `formatMoney`**: An eye
toggle in the header masks all amounts at once — the "someone is looking over my
shoulder" switch — and routing every figure through one helper is what stops the
toggle missing one. (`displayMoney` in the web app; `AmountVisibility.displayMoney`,
usually via `MoneyText`, in the Android app.) It is a local preference, not a
server-side setting: it protects the current screen, not the data. Only the
digits are masked, the currency stays visible, and the mask is a fixed width —
one that grew with the amount would give away the magnitude it exists to hide.
Edit forms still show the value being edited.

## Ledger

- **Transfers are two linked rows**: Moving money between your own accounts writes
one negative and one positive transaction sharing a `transfer_id`. Both are
excluded from income and spending totals, because a transfer is neither; deleting
either side deletes the pair, so the two accounts can never disagree.

- **Categories nest one level, budgets stay on the parent**: A subcategory inherits
its parent's kind, so an "Investments" under "Cashflow" is always a
transfer category. Its spending counts towards the parent's plan, and
`/api/summary` returns top-level categories only — each carrying its children's
figures — so summing the list cannot double-count. Filtering the transactions by a parent takes its
subcategories with it, on the server and in both apps' caches alike; a subcategory picked on its own is only itself. A transaction holds a single
category column, which makes "the main or the sub, never both" true by
construction rather than by validation.

- **A budget's month is a per-user mode, not a per-budget choice**: The budget mode
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

- **Tags are labels beside the category, never instead of it**: A user manages
their own tags (a name and a palette colour, unique per user whatever the case)
on a Tags screen of their own — a long list would otherwise drown the
categories, so it is searchable once it grows — and a transaction wears any number of them, up to
ten. They are drawn as chips at the right end of the notes row of a transaction
card. A category decides where money counts; a tag decides nothing, so no
income, spending, budget, balance or summary figure ever reads them (which is
why tagging never invalidates a cached summary). The one figure a tag has is its own:
the Tags screen shows what the transactions wearing it come to, signed like them and added up as they stand
in the display currency, a transfer counting by its outflow leg. The two legs of a transfer wear
the same tags, so it reads the same from either account. Saving a transaction
sends the whole set, which replaces what it wore; an edit that leaves `tagIds`
out keeps them. Deleting a tag only takes the label off — the transactions stay
— and deleting a transaction leaves its tags. Search matches a tag's name as it
matches a payee or notes. Round-ups and automated subscription rows wear none,
and the payee history does not remember them.

- **Cashflow categories belong to transfers**: A category's `kind` says what it is
for: `income` and `expense` categorise spending and income, `transfer`
categorises movements between your own accounts. They are three values of one
field rather than a kind and a separate scope — a transfer category had no
meaningful kind of its own — and the two sets never appear in the same picker. Because a
transfer writes a matching pair of rows, counting both sides would always come to
zero; the cashflow figure measures the outflow leg. It stays out of income,
spending, the daily chart and the expense budget total: budgeting a movement is
not budgeting spending.

- **Account types are the user's own vocabulary**: Everyone starts with the usual
set — Checking, Savings, Cash, Credit Card, Investment, Loan — and can rename,
extend or retire it. Accounts reference a type by id, so a rename propagates with
no backfill. Nothing calculates differently per type; it is a label for grouping
and reading, which is exactly why it can be user-defined without touching any
arithmetic. A type still in use cannot be deleted — the API reports how many
accounts hold it and points at archiving. The types are managed on a screen of
their own, opened from a row in Settings (`/settings/account-types` on the web,
`Route.AccountTypes` on Android) rather than from the Accounts screen: Settings
stays the marked destination while it is on show, and its leading action is
"New type". Their order is the person's too: an active type is dragged by the handle at the end of its row (or moved with the arrow keys, or a screen reader's Move up and Move down), the order is written the moment it is dropped as one `PUT /account-types/order` naming them first to last, and Accounts groups by it; archived types are not ordered and stay after the active ones. Settings is its list and it is the detail: where the page beside the
rail has room for both (see Navigation), the two stand side by side.

- **The payee is the first field, and it fills in the rest**: Saving a named
transaction records what it was filed under — account, category, notes, and which
of the three shapes it was. Typing that name again offers it back and restores
the lot, switching the form's mode to match. Newest use wins rather than merging:
the question is "what did I do last time", and a half-updated row answers it
wrongly. Names match case-insensitively. A transfer left unnamed describes itself
(`Checking → Savings`); that composed name is derived, so it is never remembered.

- **Subscriptions post ordinary transactions, once a month, at 00:00 UTC**: in [.claude/rules/subscriptions.md](.claude/rules/subscriptions.md), loaded when working on its files.

- **PSE investments, and Investments as a system category**: in [.claude/rules/investments.md](.claude/rules/investments.md), loaded when working on its files.

- **Deleting a category never destroys history**: Transactions fall back to
uncategorised (`ON DELETE SET NULL`); only budgets cascade. Deleting an account
_would_ take its transactions with it, so the API answers `409` until the caller
repeats the request with `?includeTransactions=true`.

- **A balance adjustment is an ordinary, uncategorised transaction**: `POST
/accounts/:id/adjust` takes the balance the account should read, not the
amount to post — it computes the difference itself, in the same statement
that reads the account's current balance and writes the transaction, so a
transaction landing in between cannot make the posted amount wrong. Nothing
distinguishes the resulting row from one entered by hand; it counts towards
income or spending exactly like any other uncategorised transaction, because
that is what it is. A request that would post a zero amount is rejected
rather than silently writing nothing.

- **"Save the Change" round-ups are an ordinary linked transfer**: in [.claude/rules/save-the-change.md](.claude/rules/save-the-change.md), loaded when working on its files.

- **Account logos are linked, and a preset bank is a key**: in [.claude/rules/banks.md](.claude/rules/banks.md), loaded when working on its files.

## Navigation

How both apps lay out navigation, app bars, search, the transaction list, forms, dialogs and swipe actions is in
[.claude/rules/navigation.md](.claude/rules/navigation.md), loaded when working on UI files. Read it before any UI change.

## Dashboard

What the dashboard shows, the trend route and how net worth is added up are in
[.claude/rules/dashboard.md](.claude/rules/dashboard.md), loaded when working on dashboard files.

## Brand

The app is Hayase Yuuka, in her pyjamas, and what makes it look and sound like her is written down in
[BRANDING.md](BRANDING.md): the three colours and where each goes, her voice, her portraits and the moods they
come in, About, the cat print, the icons, and the script that cuts every image. Read it before a change that
touches a colour, a line of copy with any character in it, her face or an icon, and update it in the same
change. What it asks of any screen:

- **The theme is the brand's and nobody's wallpaper**: three seeds in `ui/theme/Color.kt`, the web's roles
generated from them. There is no dynamic colour, and a setting for it is not to come back.
- **A connected button group is one colour**: as Material 3 Expressive asks, every button takes Material's
own toggle colours — the surface container at rest, primary when selected — whatever its place in the row.
`ConnectedButtonGroup` does it in both apps; a caller never passes a colour.
- **Copy with any character in it is Yuuka's**: "I", to "you", never "Sensei", and no cat puns. Labels,
validation messages and confirmations stay plain.
- **Her face is for a screen with nothing to show, the end of the transaction list and the investment activity, About, and the sign-out dialog, and the foot of a new transaction, account, category, tag or subscription's form**: through
`YuukaPortrait`, which `EmptyState` requires a mood for. Never beside a figure, in any other form, a chart or a dialog.
- **Every image comes from `scripts/brand_assets.py`**: the source art in `branding/` is deliberately not in
the repository, and nothing the script writes is edited by hand.

## Sync

- **Both apps are offline-first. A change is written locally and sent afterwards,
never the other way round**: A save does not wait for the network and cannot
fail for want of one: the row the user made goes into the local database, the
call that would have been made online goes into an outbox, and the screen — which
reads the local database — updates at once. The queue drains as a single
`POST /api/sync/batch`, in the order the changes were made, which is what makes
"spend from the account I just made" work after an hour with no signal. It
drains on every write, when the network comes back, and (on Android) under a
WorkManager connectivity constraint so a queue built on a plane is sent even if
the app is never reopened.

- **The server is still the source of truth; what is written locally is a good
guess**: The client works out what the API would have filled in — an account's
name, a category's colour, a balance that has moved, the "Save the Change"
round-up a purchase triggers — so the figures read correctly in the moment. Once
the batch lands, the parts of the ledger it touched are refetched and the
server's answer replaces every one of those guesses. Two of them are worth
knowing about: a running balance is right for the newest row on an account and
approximate for one back-dated into the middle of its history, and a month's
summary is not recomputed locally at all, because it depends on every
transaction in the month.

- **The client names the rows it creates**: A transaction entered offline may
reference an account that is itself still queued, so ids are generated on the
client in the server's own shape (`txn_…`, `acc_…`) and the server stores the
name it was given. Nothing downstream can tell which side generated an id. It is
also what makes sending the same batch twice safe: a repeated create resolves to
the row that is already there, and deleting something already gone reports
success.

- **Last write wins, judged by when the user acted**: Each queued operation
carries the moment the change was made, not the moment it was sent. If the row
it names has moved on since — the other device edited it, and that reached the
server first — the operation is dropped rather than applied. A change made on a
plane loses to one made on the ground an hour later instead of winning by
arriving second. There is no conflict UI; a dropped operation just leaves the
newer value in place.

- **A rejection arrives late, and is still reported**: A name the server considers
a duplicate is only known to be one when the batch lands. That is surfaced as a
snackbar, and the refresh that follows takes the optimistic row back off the
screen. Both apps also say how much has not reached the server yet — a count
from the queue, not a guess.

- **A form is reactive: it answers for its own errors, live, beside the field**: A
save never waits for the server, so everything the API would refuse for the form's
own values — an empty or over-long name, a name already taken (tags, account types
and sibling categories are unique), an amount that is not a positive number, a
missing or identical account, a colour that is not `#rrggbb`, a currency that is
not three letters, a logo that is not http(s), more than ten tags — is checked on
the device, mirroring the API's rules, so it never comes back later as a snackbar
after the form has closed. The rules are re-run on every change, and the save
button is disabled the moment any of them fails and enabled the moment the last is
fixed, so an invalid form cannot be submitted at all. Following Material 3, the
reason is said beside the field: once a field has been touched (typed in, chosen
from or left) it takes the error colour and shows its error in place of its hint,
read out to a screen reader, and the error clears as soon as the value is
acceptable. An untouched field stays quiet, so a blank form is not covered in red
before anyone has done anything, though its save button is already off. A banner is
kept only for a failure that belongs to no field (the save itself). The rules live
in `useFormValidation` (`lib/validation.ts`, with `TextField.vue` and `FieldSupport.vue`) on the web
and in `FormValidation` (`ui/common/FormValidation.kt`, with `YuukaTextField` and
`DropdownField`) on Android; the shared rules are `domain/Validation.kt`.

- **The queue is the one thing that is not a cache**: Everything else stored
locally can be thrown away and refetched; a queued change exists nowhere else.
On Android it therefore lives in its own Room database (`OutboxDatabase`),
because the cache database falls back to a destructive migration and a queue
must not; in the browser the cache is an IndexedDB database (`yuuka-cache`) laid
out as the Room one is — a store per table, keyed and indexed the same way, read
and written row by row — that is cleared, trimmed and dropped on a layout change
in the same way, and the queue is a second database of its own (`yuuka-outbox`)
that none of that reaches. It is
discarded when the user signs out, and when a different person signs in — but
**not** when a token merely expires, because that is the same person, who will
sign in again and still wants their work. That is why clearing the cache on an
expired session keeps the record of whose it was.

- **The other device is told, not left to find out**: A write pushes a data-only
Firebase Cloud Messaging message naming which slices of the ledger moved
(`accounts`, `transactions`, `summary`, …), and the receiving app refetches
those and nothing else — a transaction added in the browser should not cost the
phone its subscription list. The device that made the change is left out, since
it already has the answer. Nothing is ever shown: the user made the change
themselves, so a notification would be noise. Push is an optimisation and
nothing may be built on a message arriving — delivery is not promised, and a
deployment with no FCM configured simply never sends one. Setting it up is in
[webapp/README.md](webapp/README.md).

- **Pull-to-refresh is still a full sync, not a top-up**:: The per-screen loads
only fold rows in, so something deleted elsewhere would otherwise linger here
indefinitely. A full sync replaces accounts, types, categories, tags, settings,
the round-up rule, subscriptions and payees from the API, and only once that has
succeeded discards the cached transactions, budgets, income plans and summaries,
so the screens on show load theirs again. A failed sync (offline, expired
session) leaves the last-seen ledger untouched rather than empty. A slice
refresh — what a push or a landed batch asks for — is the same mechanism aimed
at less of the ledger. Both apps do this to the same tables (`SyncRepository` on
Android, `lib/sync.ts` on the web).

## Provisioning

Signing in for the first time provisions the account: the default account types
and categories, and — unless demo seeding is switched off — sample accounts,
transactions and budgets dated into the month the user joined, so the first
dashboard has figures on it. How this stays safe under concurrent requests is in
[webapp/CLAUDE.md](webapp/CLAUDE.md).

### Yuuka API

The api lives in [webapp](webapp). More details available inside it's respective [CLAUDE.md](webapp/CLAUDE.md).
