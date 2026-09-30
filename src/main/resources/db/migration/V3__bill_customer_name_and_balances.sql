-- V3: extra facts saved on each bill, so a reprint always shows exactly what the customer saw.
--
--   customer_name          name printed on the bill: the khata customer's name at the time of the bill,
--                          or the optional name typed for a walk-in customer (NULL = no name).
--   previous_balance_paise khata balance just before this bill (khata customers only, else NULL).
--   balance_after_paise    khata balance just after this bill and any payment made with it
--                          (khata customers only, else NULL).
--
-- NEVER edit this file after it is committed.

ALTER TABLE bills ADD COLUMN customer_name TEXT;
ALTER TABLE bills ADD COLUMN previous_balance_paise INTEGER;
ALTER TABLE bills ADD COLUMN balance_after_paise INTEGER;

-- The guard from V1 must also protect the new columns: a saved bill can only be cancelled.
DROP TRIGGER trg_bills_only_cancel;

CREATE TRIGGER trg_bills_only_cancel BEFORE UPDATE ON bills
WHEN NOT (
    OLD.status = 'FINAL' AND NEW.status = 'CANCELLED'
    AND NEW.id = OLD.id
    AND NEW.bill_no = OLD.bill_no
    AND NEW.created_at = OLD.created_at
    AND NEW.customer_id IS OLD.customer_id
    AND NEW.subtotal_paise = OLD.subtotal_paise
    AND NEW.round_off_paise = OLD.round_off_paise
    AND NEW.total_paise = OLD.total_paise
    AND NEW.paid_paise = OLD.paid_paise
    AND NEW.to_account_paise = OLD.to_account_paise
    AND NEW.created_by = OLD.created_by
    AND NEW.customer_name IS OLD.customer_name
    AND NEW.previous_balance_paise IS OLD.previous_balance_paise
    AND NEW.balance_after_paise IS OLD.balance_after_paise
)
BEGIN
    SELECT RAISE(ABORT, 'A saved bill cannot be changed. It can only be cancelled.');
END;
