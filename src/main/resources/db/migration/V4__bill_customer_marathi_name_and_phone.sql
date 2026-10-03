-- V4: the customer's details as printed on the bill, saved with each new bill so a reprint always matches.
--
--   customer_name_mr  the customer's name in Marathi letters as printed on the bill (made automatically
--                     from customer_name). NULL on bills saved before V4: those print customer_name.
--   customer_phone    the khata customer's phone number at the time of the bill, or NULL.
--
-- NEVER edit this file after it is committed.

ALTER TABLE bills ADD COLUMN customer_name_mr TEXT;
ALTER TABLE bills ADD COLUMN customer_phone TEXT;

-- The guard from V3 must also protect the new columns: a saved bill can only be cancelled.
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
    AND NEW.customer_name_mr IS OLD.customer_name_mr
    AND NEW.customer_phone IS OLD.customer_phone
)
BEGIN
    SELECT RAISE(ABORT, 'A saved bill cannot be changed. It can only be cancelled.');
END;
