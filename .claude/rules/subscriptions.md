---
paths:
  - "**/subscriptions/**"
  - "**/Subscriptions*"
  - "webapp/server/subscriptions.ts"
  - "webapp/server/routes/subscriptions.ts"
  - "webapp/server/routes/transactions.ts"
---

# Subscriptions

- **Subscriptions post ordinary transactions, once a month, at 00:00 UTC**: A
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
  - A subscription takes an `income` or `expense` category, never a `transfer`
    one, as for any ordinary transaction. Automated rows do not trigger "Save the
    Change" and do not teach the payee history: both follow what a person enters,
    not what a schedule does.

  The Subscriptions screen states what the active subscriptions come to in a month
(`monthlyTotal`, in both apps), signed like the amounts: a net outflow is
negative. A paused subscription posts nothing, so it is left out and the screen
says how many were. Like net worth it is added up as it stands and shown in the
display currency — amounts are not converted between currencies.
