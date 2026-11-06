---
paths:
  - "**/dashboard/**"
  - "**/Dashboard*"
  - "webapp/src/lib/dashboard.ts"
  - "webapp/server/routes/trend.ts"
  - "webapp/server/routes/summary.ts"
---

# Dashboard

- **The dashboard reads one month, and says what it is made of**: four sections under plain headings, each
left out when it has nothing to say, and every card in them left out when it has nothing to say either.
  - **The month on show** leads with one figure: what is safe to spend a day — what is left of the expense plan
    shared over the days left, today included, rounded down — while the month runs with some of its plan left,
    and its net otherwise. Its other figures follow in a row (income, spent, the net when it is not leading,
    and — once a month has any — what was set aside into transfer categories and what Save the Change rounded
    up), then the card against the plan (the expense budget used, with a mark for the day while the month runs,
    and income received against plan).
  - **Needs attention**: the categories over budget, the planned ones still within their plan but spending it
    faster than the month is passing (only while it runs), and the subscriptions still to post in the month
    under way.
  - **Breakdown**: spending by day, where the money went (each row saying how far it moved from the month
    before), who it went to (the summary's top five payees, transfers left out) and where it came from (two
    sources or more).
  - **Overall**: net worth, its twelve months, income and spending over the last six, the investments (their
    worth at the latest close, the unrealised and the day's gain, the three largest stocks, pressing it opens
    Investments), and every account in use with its balance in its own currency.

  From an 840dp / 840px page the last section stands in a column of its own beside the other three. What is
  worked out on the device lives in one place, `domain/Dashboard.kt` and `lib/dashboard.ts`, which mirror each
  other and are tested alike.
- **The trend is its own route**: `/api/trend` spans months, and the summary is cached per month and purged
only for the months a write touches, so a trend inside it would go stale on a back-dated write. It is not
cached on the server. Its net worth at a month's end is worked back from today's balances, which counts an
account opened since as if it had always held its starting balance, and keeps the last point equal to the
summary's figure. Both apps keep it locally in the summaries table under `trend:<month>`, so it goes whenever
the summaries go.
- **Net worth adds currencies as they stand**: it is never converted, so where the accounts hold more than one
currency the accounts card says so rather than let the total pass for one currency's figure.
