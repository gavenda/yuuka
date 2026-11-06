---
paths:
  - "**/savethechange/**"
  - "**/SaveTheChange*"
  - "webapp/server/routes/round-up.ts"
  - "webapp/server/routes/transactions.ts"
  - "webapp/server/routes/accounts.ts"
  - "webapp/src/lib/provisional.ts"
  - "app/src/main/java/**/data/local/Provisional.kt"
  - "app/src/main/java/**/repository/TransactionRepository.kt"
---

# Save the Change

- **"Save the Change" round-ups are an ordinary linked transfer, not a new
transaction shape**: `POST /transactions` is the only place it can trigger:
when the new row is an expense (negative amount) on an account that has opted
in (`accounts.round_up_source`), and the per-user `round_up_rules` row is
enabled with a destination account set, the gap between the amount and the
next `round_to` multiple (₱10 or ₱100 — a minor-unit multiple of 1000 or
10000, never centavos) is posted as its own transfer: two rows sharing a
`transfer_id`, payee `"Save the Change"`, both legs carrying whichever
category the rule names (`round_up_rules.category_id`) — a `transfer` one,
the same Cashflow tree an ordinary transfer uses, since a round-up is
one. Uncategorised when the rule names none. That payee is
synthetic and is never fed into `rememberPayee`, the same way a transfer's
derived "From → To" name isn't. A purchase that happens to occur on the
destination account itself simply doesn't round up — the rest of that
account's spending is unaffected. Editing a transaction, transfers and
balance adjustments never trigger it; only a plain `POST /transactions` does.

Which accounts take part is part of the rule to the person setting it, so in
both apps the whole of it — the switch, the ₱10/₱100 step, the accounts that round
up, where the change lands and what it is filed under — is on the Save the Change
screen, and the account form says nothing about round-ups. The opt-in is still
`accounts.round_up_source` on each account, set one account at a time with the
narrowest possible `PATCH`, so an edit of an account's name can never quietly
take it off. Settings and Save the Change carry no Save: every choice
on them is picked from a list, so it is written as it is made, the way the theme
switches always behaved. The display currency is the deliberate exception: it
relabels every figure in the app, so its list only marks a row and
writes on Save. The other exception is a rule switched on before a
destination is chosen — it cannot be saved, so it waits on the screen with the
error beside the field and is written the moment an account is picked.
