-- V1: initial schema for Virpe Mart Billing.
--
-- Conventions:
--   * Money is INTEGER paise (Rs 45.50 = 4550). Columns end in _paise.
--   * Quantity is INTEGER thousandths (0.250 kg = 250). Columns end in _milli.
--   * Date-times are shop-local TEXT 'YYYY-MM-DDTHH:MM:SS'.
--   * Flags are INTEGER 0/1.
--   * Units: KG and L are loose (decimal quantity); PCS is packed (whole quantity).
--
-- NEVER edit this file after it is committed. Add V2__..., V3__... instead.

-- ---------------------------------------------------------------- settings
CREATE TABLE settings (
    key   TEXT PRIMARY KEY,
    value TEXT NOT NULL
);

-- ---------------------------------------------------------------- users
CREATE TABLE users (
    id            INTEGER PRIMARY KEY,
    username      TEXT    NOT NULL UNIQUE COLLATE NOCASE,
    display_name  TEXT    NOT NULL,
    password_hash TEXT    NOT NULL,
    role          TEXT    NOT NULL CHECK (role IN ('OWNER', 'STAFF')),
    active        INTEGER NOT NULL DEFAULT 1 CHECK (active IN (0, 1)),
    created_at    TEXT    NOT NULL,
    updated_at    TEXT    NOT NULL
);

-- ---------------------------------------------------------------- categories
CREATE TABLE categories (
    id     INTEGER PRIMARY KEY,
    name   TEXT    NOT NULL UNIQUE COLLATE NOCASE,
    active INTEGER NOT NULL DEFAULT 1 CHECK (active IN (0, 1))
);

-- ---------------------------------------------------------------- products
CREATE TABLE products (
    id          INTEGER PRIMARY KEY,
    code        TEXT    NOT NULL UNIQUE COLLATE NOCASE,
    name        TEXT    NOT NULL,
    name_mr     TEXT,
    category_id INTEGER REFERENCES categories (id),
    unit        TEXT    NOT NULL CHECK (unit IN ('KG', 'L', 'PCS')),
    pack_size   TEXT,
    rate_paise  INTEGER NOT NULL CHECK (rate_paise > 0),
    mrp_paise   INTEGER CHECK (mrp_paise IS NULL OR mrp_paise > 0),
    active      INTEGER NOT NULL DEFAULT 1 CHECK (active IN (0, 1)),
    created_at  TEXT    NOT NULL,
    updated_at  TEXT    NOT NULL
);

CREATE INDEX idx_products_name ON products (name COLLATE NOCASE);
CREATE INDEX idx_products_category ON products (category_id);

-- ---------------------------------------------------------------- customers
CREATE TABLE customers (
    id          INTEGER PRIMARY KEY,
    customer_no TEXT    NOT NULL UNIQUE,
    name        TEXT    NOT NULL,
    phone       TEXT    UNIQUE,
    address     TEXT,
    notes       TEXT,
    active      INTEGER NOT NULL DEFAULT 1 CHECK (active IN (0, 1)),
    created_at  TEXT    NOT NULL,
    updated_at  TEXT    NOT NULL
);

CREATE INDEX idx_customers_name ON customers (name COLLATE NOCASE);

-- ---------------------------------------------------------------- bills
-- customer_id NULL means a walk-in customer.
-- total = subtotal + round_off, and total = paid + to_account.
CREATE TABLE bills (
    id               INTEGER PRIMARY KEY,
    bill_no          INTEGER NOT NULL UNIQUE CHECK (bill_no > 0),
    created_at       TEXT    NOT NULL,
    customer_id      INTEGER REFERENCES customers (id),
    subtotal_paise   INTEGER NOT NULL CHECK (subtotal_paise >= 0),
    round_off_paise  INTEGER NOT NULL CHECK (round_off_paise BETWEEN -49 AND 50),
    total_paise      INTEGER NOT NULL CHECK (total_paise >= 0),
    paid_paise       INTEGER NOT NULL CHECK (paid_paise >= 0),
    to_account_paise INTEGER NOT NULL CHECK (to_account_paise >= 0),
    status           TEXT    NOT NULL DEFAULT 'FINAL' CHECK (status IN ('FINAL', 'CANCELLED')),
    cancel_reason    TEXT,
    cancelled_by     INTEGER REFERENCES users (id),
    cancelled_at     TEXT,
    created_by       INTEGER NOT NULL REFERENCES users (id),
    CHECK (total_paise = subtotal_paise + round_off_paise),
    CHECK (paid_paise + to_account_paise = total_paise),
    CHECK (customer_id IS NOT NULL OR to_account_paise = 0),
    CHECK (
        (status = 'FINAL' AND cancel_reason IS NULL AND cancelled_by IS NULL AND cancelled_at IS NULL)
        OR
        (status = 'CANCELLED' AND cancel_reason IS NOT NULL AND cancelled_by IS NOT NULL AND cancelled_at IS NOT NULL)
    )
);

CREATE INDEX idx_bills_created_at ON bills (created_at);
CREATE INDEX idx_bills_customer ON bills (customer_id, created_at);

-- ---------------------------------------------------------------- bill_items
-- product_id NULL means a one-off item typed on the bill.
-- name, name_mr, unit, pack_size, rate and MRP are a snapshot taken when the bill was saved.
-- original_rate_paise is the product's rate when the rate was changed on this bill, otherwise NULL.
CREATE TABLE bill_items (
    id                  INTEGER PRIMARY KEY,
    bill_id             INTEGER NOT NULL REFERENCES bills (id),
    line_no             INTEGER NOT NULL CHECK (line_no > 0),
    product_id          INTEGER REFERENCES products (id),
    name                TEXT    NOT NULL,
    name_mr             TEXT,
    unit                TEXT    NOT NULL CHECK (unit IN ('KG', 'L', 'PCS')),
    pack_size           TEXT,
    qty_milli           INTEGER NOT NULL CHECK (qty_milli > 0),
    rate_paise          INTEGER NOT NULL CHECK (rate_paise >= 0),
    original_rate_paise INTEGER CHECK (original_rate_paise IS NULL OR original_rate_paise >= 0),
    mrp_paise           INTEGER CHECK (mrp_paise IS NULL OR mrp_paise > 0),
    line_total_paise    INTEGER NOT NULL CHECK (line_total_paise >= 0),
    UNIQUE (bill_id, line_no),
    CHECK (unit <> 'PCS' OR qty_milli % 1000 = 0)
);

CREATE INDEX idx_bill_items_product ON bill_items (product_id);

-- ---------------------------------------------------------------- bill_payments
CREATE TABLE bill_payments (
    id           INTEGER PRIMARY KEY,
    bill_id      INTEGER NOT NULL REFERENCES bills (id),
    mode         TEXT    NOT NULL CHECK (mode IN ('CASH', 'UPI', 'CARD')),
    amount_paise INTEGER NOT NULL CHECK (amount_paise > 0)
);

CREATE INDEX idx_bill_payments_bill ON bill_payments (bill_id);

-- ---------------------------------------------------------------- customer_ledger
-- The customer's balance is SUM(amount_paise). Positive means the customer owes the store.
CREATE TABLE customer_ledger (
    id           INTEGER PRIMARY KEY,
    customer_id  INTEGER NOT NULL REFERENCES customers (id),
    entry_type   TEXT    NOT NULL CHECK (entry_type IN
                     ('OPENING', 'SALE_CREDIT', 'PAYMENT', 'CANCEL_REVERSAL', 'ADJUSTMENT')),
    amount_paise INTEGER NOT NULL CHECK (amount_paise <> 0),
    bill_id      INTEGER REFERENCES bills (id),
    payment_mode TEXT    CHECK (payment_mode IS NULL OR payment_mode IN ('CASH', 'UPI', 'CARD')),
    note         TEXT,
    created_at   TEXT    NOT NULL,
    created_by   INTEGER NOT NULL REFERENCES users (id),
    CHECK (entry_type <> 'SALE_CREDIT' OR (amount_paise > 0 AND bill_id IS NOT NULL)),
    CHECK (entry_type <> 'CANCEL_REVERSAL' OR (amount_paise < 0 AND bill_id IS NOT NULL)),
    CHECK (entry_type <> 'PAYMENT' OR (amount_paise < 0 AND payment_mode IS NOT NULL))
);

CREATE INDEX idx_ledger_customer ON customer_ledger (customer_id, created_at);
CREATE INDEX idx_ledger_bill ON customer_ledger (bill_id);

-- ---------------------------------------------------------------- audit_log
-- user_id NULL means an automatic action by the app itself.
CREATE TABLE audit_log (
    id         INTEGER PRIMARY KEY,
    created_at TEXT    NOT NULL,
    user_id    INTEGER REFERENCES users (id),
    action     TEXT    NOT NULL,
    entity     TEXT,
    entity_id  INTEGER,
    details    TEXT
);

CREATE INDEX idx_audit_created_at ON audit_log (created_at);

-- ---------------------------------------------------------------- safety guards
-- Saved money records can never be deleted or edited. The only allowed change to a bill
-- is FINAL -> CANCELLED with the cancel details filled in.

CREATE TRIGGER trg_bills_no_delete BEFORE DELETE ON bills
BEGIN
    SELECT RAISE(ABORT, 'Saved bills cannot be deleted.');
END;

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
)
BEGIN
    SELECT RAISE(ABORT, 'A saved bill cannot be changed. It can only be cancelled.');
END;

CREATE TRIGGER trg_bill_items_no_update BEFORE UPDATE ON bill_items
BEGIN
    SELECT RAISE(ABORT, 'Lines of a saved bill cannot be changed.');
END;

CREATE TRIGGER trg_bill_items_no_delete BEFORE DELETE ON bill_items
BEGIN
    SELECT RAISE(ABORT, 'Lines of a saved bill cannot be deleted.');
END;

CREATE TRIGGER trg_bill_payments_no_update BEFORE UPDATE ON bill_payments
BEGIN
    SELECT RAISE(ABORT, 'Payments of a saved bill cannot be changed.');
END;

CREATE TRIGGER trg_bill_payments_no_delete BEFORE DELETE ON bill_payments
BEGIN
    SELECT RAISE(ABORT, 'Payments of a saved bill cannot be deleted.');
END;

CREATE TRIGGER trg_ledger_no_update BEFORE UPDATE ON customer_ledger
BEGIN
    SELECT RAISE(ABORT, 'Account entries cannot be changed. Add a correcting entry instead.');
END;

CREATE TRIGGER trg_ledger_no_delete BEFORE DELETE ON customer_ledger
BEGIN
    SELECT RAISE(ABORT, 'Account entries cannot be deleted. Add a correcting entry instead.');
END;

CREATE TRIGGER trg_audit_no_update BEFORE UPDATE ON audit_log
BEGIN
    SELECT RAISE(ABORT, 'Audit records cannot be changed.');
END;

CREATE TRIGGER trg_audit_no_delete BEFORE DELETE ON audit_log
BEGIN
    SELECT RAISE(ABORT, 'Audit records cannot be deleted.');
END;
