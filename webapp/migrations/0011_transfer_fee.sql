-- A transfer may cost something to make: a bank's or a remittance service's
-- fee. It is spending, not a movement between the user's own accounts, so it is
-- its own ordinary expense row on the source account — `transfer_id` stays NULL,
-- which is what every income and spending figure already reads — linked to the
-- transfer it was paid for. Dropping the transfer (deleting either leg fires
-- `transfers_leg_deleted`) takes the fee with it; deleting the fee alone only
-- takes the fee off.
--
-- A cross-currency transfer needs nothing here: each leg already holds its own
-- amount in its own account's currency, and the rate is what they come to
-- divided, so it is never stored a second time.
ALTER TABLE transactions ADD COLUMN fee_transfer_id TEXT REFERENCES transfers (id) ON DELETE CASCADE;

CREATE INDEX transactions_fee_transfer_idx ON transactions (fee_transfer_id);
