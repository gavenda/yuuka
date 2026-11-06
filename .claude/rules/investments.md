---
paths:
  - "**/investments/**"
  - "**/Investments*"
  - "**/*nvestment*"
  - "webapp/server/investments.ts"
  - "webapp/server/routes/investments.ts"
  - "webapp/src/lib/investments.ts"
  - "webapp/src/stores/investments.ts"
  - "webapp/migrations/**"
---

# Investments

- **PSE investments are trades plus the cash they moved**: A trade is a buy, a sell or a cash dividend of
one PSE stock in a broker account (any PHP account): whole shares, a price in ten-thousandths of a peso
(`price_e4`, since the smallest tick is ₱0.0001) and fees in centavos. Each writes one ordinary
transaction in that account — its cash leg, the stock as payee, filed under a system category (below) — and the trade goes with it:
deleting the leg from the transaction list deletes the trade, and the leg cannot be edited as a
transaction: pressing it in Transactions opens the trade's own form. The trade itself is edited, by pressing it: whole, from the same form, its leg worked out again and
moved rather than replaced. A trade records the time of day as a transaction does (`occurredOn` with an optional
`THH:MM`, untimed sorting to the start of its day), and the leg carries the same, so a day's trades are worked
through in the order they were made. A buy's and a sell's cash only turns money into shares and back, so like a transfer it is left
out of income, spending, the daily chart and the payees; a dividend's (net of the 10% final tax) is income. A dividend is picked from the stocks already traded, each in the broker account it was traded in —
which comes with it, so the form asks for no account, and names it beside the stock only where the same stock is
in two accounts — and its shares are
filled in with what the account held at the start of its day (`sharesHeldBefore`) — again whenever the stock, the
account or the date changes — and left editable with no limit, since shares sold after the ex-date still earn it.
Holdings are worked out from the trades, average cost, never stored: a sell takes shares out at the average
so far and realises the rest, and a history that would sell more than it holds at any point is refused (a
back-dated sell, or deleting a buy a later sell needs). Net worth adds the holdings at their latest close.
The broker's fees are worked out by the form from the PSE schedule (`pseFees`, `domain/Investments.kt`): 0.25%
commission with a ₱20 minimum, unless the account's preset bank waives the minimum (`minCommission` on the
preset, DragonFi's is 0 — a broker is a preset, so this is code, not a column); a custom account keeps the ₱20.
Fees round to the nearest centavo unless the preset `roundsFeesUp`: DragonFi charges the commission with its
VAT, and the PSE and SCCP fees together, each rounded up to the next centavo.
Prices are market data, shared by everyone: the server keeps them from PHISIX (see
[webapp/CLAUDE.md](../../webapp/CLAUDE.md)), and the client only reads them. A stock's logo is market data too: PSE Edge shows one on
each company's page under a file name that follows no pattern, so the cron reads it off the page of each stock
someone holds (`resolveLogos`, a few a tick, looked at again after 90 days) and keeps only the address
(`securities.logo_url`); the clients load the image from PSE Edge. A stock's screen stands it centred over the
name, on a white card of its own since the logos are JPEGs on white, and without one — or while it cannot be
loaded — the ticker's first letters on a tonal circle stand in. Investments shows the holdings and the five latest trades; "See all" beside the Activity heading opens every trade
on a screen of its own (`Route.InvestmentActivity`, `/investments/activity`), standing beside Investments as a holding
does. Every trade is already on the device, since the holdings are worked out from all of them, so that list is
never asked of the server a page at a time: it shows fifty and the next as its end comes within reach, as
Transactions does, and ends with Yuuka asleep (`ListEnd`, shared with Transactions). On Android this is
`InvestmentRepository` and `ui/investments/`; on the web, `stores/investments.ts` and `lib/investments.ts`.

- **Investments is a system category, twice over**: every person has two top-level categories named Investments,
one `transfer` and one `income` (`categories.system`, `'investments'` and `'investment_income'`), and the server
files a trade's cash under them — a buy's and a sell's under the first, a dividend's under the second, worked out
again when an edit changes its kind. Both apps do the same for the leg they write offline. Neither can be renamed,
recoloured, archived or deleted, nor take a subcategory; they stand at the top level, beside Cashflow rather than
under it, so nothing done to another category can reach them. On the Categories screen a system category is not
pressed and has nothing behind a swipe, and says "Kept for investments". Both stay pickable for an ordinary
transaction or transfer. A buy's or a sell's leg is not a transfer leg, so the cashflow figure still leaves it out;
a dividend counts under its income category. `migrations/0007_system_categories.sql` made them and filed every
existing trade's cash; `0008_top_level_investments.sql` moved the transfer one out from under Cashflow, merged it
into a top-level Investments someone already had, and took away a Cashflow 0007 had made only to hold it.
