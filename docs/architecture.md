# Architecture

## Overview

One offline desktop program. No web server, no network ports, no internet needed.

```
UI layer        JavaFX screens: Billing, Products, Customers, History/Reports, Settings
                Only calls services. Never contains SQL.
Service layer   Business rules, validation, role checks, transactions.
Repository      All SQL. PreparedStatement parameters only.
SQLite file     One database file on the laptop.
```

Everything runs in one process, so layers talk through plain method calls. For example, the billing
screen calls `billingService.saveBill(draft)`. The service validates, opens one transaction, calls the
repositories, commits, and returns the saved bill. The screen then calls `printing.printBill(billNo, false)` on a background thread.
Slow work such as backup, import and reports runs on a background thread so the screen never freezes.

**Why a desktop app and not a web app:** there is one laptop, the shop must work offline, and receipt
printing needs direct printer control. A desktop app has nothing to host and no ports to secure. If a
second counter is ever needed, the service layer can be exposed through a small REST API later.

## Technology

| Concern | Choice | Reason |
|---|---|---|
| Language | Java 25 LTS (Eclipse Temurin) | Long-term support, stable |
| UI | JavaFX 25, FXML + CSS | Modern desktop UI, fast tables, keyboard handling |
| Database | SQLite through `sqlite-jdbc` | Single file, no administration, crash-safe |
| Database access | Plain JDBC in thin repositories | Transparent; an ORM is too heavy here |
| Schema changes | Small built-in migration runner | Versioned SQL files, checksums, backup first |
| Build | Maven Wrapper | Standard; no separate Maven install |
| Logging | SLF4J + Logback | Rolling log files for diagnosing problems |
| CSV import | Apache Commons CSV | Correct quoting and UTF-8 Marathi text |
| Printing | Java 2D printing through the Windows driver | Custom thermal paper widths and Marathi text |
| Tests | JUnit, service tests on a real temporary SQLite file | Tests real SQL and transactions |
| Packaging | `jpackage` app image with a bundled Java runtime | Store laptop needs no Java install |

The app is non-modular: there is no `module-info.java`. `Launcher` is the packaged entry point and
starts `App`, the JavaFX application class. This is required when JavaFX is on the classpath.

## Data rules

- **Money** is stored as integer paise: Rs 45.50 is stored as 4550. Java uses `BigDecimal` inside
  small `Money` and `Quantity` types. `double` and `float` are never used for money.
- **Quantity** is stored as integer thousandths: 0.250 kg is stored as 250.
- **Line total** is quantity times rate, rounded HALF_UP to the paisa.
- **Bill total** is the subtotal rounded to the nearest rupee. `round_off` stores the difference.
- **Savings** is the sum of (MRP minus rate) times quantity, for lines where MRP is higher than the rate.
- **Saved bills never change.** Nothing financial is ever hard-deleted.
- **Bill lines keep a snapshot** of name, Marathi name, unit, rate and MRP, so old bills stay correct
  after a product changes.
- **Customer balance** is the sum of that customer's ledger entries. There is no editable balance field.
  A positive balance means the customer owes the store.

## Planned tables

| Table | Holds |
|---|---|
| `settings` | Shop name, address, phone, footer, printer, paper width |
| `users` | Username, display name, password hash and salt, role OWNER or STAFF, active flag |
| `categories` | Category name, active flag |
| `products` | Short code, name, Marathi name, category, unit (KG or L for loose items, PCS for packed items), pack size text, rate, optional MRP, active flag |
| `customers` | Customer number, name, phone, address, notes, active flag |
| `bills` | Bill number, date and time, customer or walk-in, subtotal, round-off, total, paid now, added to account, status, cancel details, created by |
| `bill_items` | Bill, product or none for one-off items, snapshot fields, quantity, rate, original rate if changed, line total |
| `bill_payments` | Bill, mode CASH, UPI or CARD, amount |
| `customer_ledger` | Customer, entry type, signed amount, bill, payment mode, note, user, time |
| `audit_log` | Who did what and when: rate changes, product edits, cancellations, restores, settings |
| `schema_version` | Applied migrations and their checksums |

Ledger entry types: OPENING (old dues from the paper khata), SALE_CREDIT, PAYMENT, CANCEL_REVERSAL,
ADJUSTMENT (owner correction with a reason). A customer's statement is the list of entries, oldest first,
with the running balance after each one.

Dates and times are stored as shop-local text such as `2026-09-30T14:05:09`, which sorts correctly
for date-range searches. The full schema is in `src/main/resources/db/migration/V1__initial_schema.sql`.

### Database guards

The database itself enforces the most important rules, so even a bug in the app cannot break them:

- Saved bills, bill lines, bill payments, ledger entries and audit records cannot be deleted or edited.
- The only allowed change to a bill is FINAL to CANCELLED, and only with a reason, a user and a time.
- A bill's totals must add up: total equals subtotal plus round-off, and paid plus to-account equals total.
- A walk-in bill cannot put money on an account. Round-off must be between -0.49 and +0.50.
- Packed (PCS) lines must have whole quantities. Payment modes must be CASH, UPI or CARD.
- Ledger signs must match the entry type: payments are negative, sale credit is positive.
- A product that appears on any bill cannot be deleted (foreign key).

### Migrations

Schema changes are SQL files in `src/main/resources/db/migration/`, listed in order in `index.txt`.
At startup the runner:

1. refuses to touch an SQLite file that was not created by this app;
2. refuses to run if an applied migration file was edited (SHA-256 checksum, line endings ignored);
3. refuses a database created by a newer app version;
4. backs up an existing database before upgrading it;
5. applies each new migration in its own transaction.

## Saving a bill

All of these happen in one database transaction. Either everything is saved or nothing is.

1. Recalculate every line and the totals from the product list (`Cart.totalsOf`); the screen's numbers are not trusted.
2. Take the next bill number: the highest bill number plus one. Bills are never deleted, so numbers never repeat.
3. Split the money received: payments fill the bill first, in the order entered; anything beyond the bill
   total is a khata payment against old dues (`BillingService.allocate`).
4. Insert the bill (with `customer_name`, `customer_name_mr` (the name in Marathi letters, made by
   `print.MarathiTransliterator`), `customer_phone`, `previous_balance_paise`, `balance_after_paise` for reprints),
   its lines (snapshots) and the payments that paid the bill.
5. Khata customers: a SALE_CREDIT entry for the unpaid part, and PAYMENT entries ("Paid with bill N")
   for money paid against old dues.
6. Insert audit rows for changed rates.

Printing happens after the transaction commits, so a printer problem never loses a bill.

## Printing

Printing is split so that the content can be tested without a printer:

1. `print.ReceiptBuilder` turns a saved bill (`BillingService.bill(billNo)`, read back exactly as saved) and the
   shop details into a list of lines: text, "left text + amount" pairs, item table rows (Item, Qty, Rate, Amount)
   and dashed rules.
2. `print.ReceiptRenderer` draws those lines with Java 2D. It uses the Windows font "Nirmala UI", which has
   English and Marathi letters and the rupee sign; Java's text layout joins Marathi letters correctly. Long text
   wraps to the paper width (48 mm on a 58 mm roll, 72 mm on an 80 mm roll, a 150 mm column on A4). In the item
   table, Qty, Rate and Amount are right-aligned columns as wide as their widest value; the item name wraps in the
   space left.
   The same drawing makes the on-screen preview picture, so the preview matches the paper.
3. `print.SystemReceiptPrinter` sends it to the Windows printer chosen in Settings, through the printer's own
   driver. For a roll it asks for paper exactly as long as the bill; if the driver cannot do that, a long bill
   continues on the next page (`ReceiptPrintable`).
4. `service.PrintService` ties it together, records reprints in the audit log and turns printer errors into a
   clear message (`PrintFailedException`). Tests use a fake `ReceiptPrinter`.

## Cancelling a bill and reports

- `BillingService.cancel` runs in one transaction: mark the bill CANCELLED (the only update the database
  trigger allows), add a CANCEL_REVERSAL khata entry for the amount that went on the khata, and write an audit row.
  `cancelPreview` shows the same effect before the owner confirms.
- `BillingService.correct` = `cancel`, then returns a `BillCorrection` (customer id, walk-in name, lines at the rates
  charged with today's product details). The Billing screen loads it into a new `Cart`; saving it is an ordinary new
  bill. A saved bill is never changed in place. The Billing screen's Recent bills window (F9) offers it.
- `ReportService.sales` reads `ReportRepository.daily`: bills and their payments are grouped by the bill's day
  (FINAL bills only), and khata PAYMENT entries by the day they were received.

## Security

- No network ports are opened.
- No login screen (the user's decision): at every start `OwnerBootstrap` signs in the first active OWNER account,
  creating "Owner" on a new database. Its password hash is "!", which can never match a password.
- Role checks still happen in the service layer, so helper accounts could be added later without changing rules.
- All SQL uses parameters, which prevents SQL injection.
- Sensitive actions are written to the audit log.

## Error handling

| Situation | Handling |
|---|---|
| Invalid input | Friendly message next to the field; nothing saved |
| Business rule broken | Clear dialog explaining what to do next |
| Database error while saving | Transaction rolled back; friendly message; details in the log |
| Printer off or out of paper | Bill already saved; amber "NOT printed" note; reprint with "Reprint bill" |
| Power cut during billing | SQLite commits atomically: a bill is fully saved or not at all |
| App opened twice | A single-instance lock stops the second copy |
| Unexpected error | Friendly dialog; full details in the log file |
| Damaged database at startup | Integrity check; offer restore from the latest backup |

## Files and folders at runtime

| What | Store laptop | Development |
|---|---|---|
| Database | `%LOCALAPPDATA%\VirpeMart\data\virpemart.db` | `dev-data\` in the repo, git-ignored |
| Backups | `%LOCALAPPDATA%\VirpeMart\backups\` | `dev-data\backups\` |
| Logs | `%LOCALAPPDATA%\VirpeMart\logs\` | `dev-data\logs\` |

Development runs pass `-Dvirpemart.dataDir=dev-data`, so test data never mixes with shop data.
The `VIRPEMART_DATA_DIR` environment variable does the same, which is handy for testing the packaged app.

The development data has an older `dev-owner` account; as the first owner it is the one signed in there.

## Backups

- `BackupService` takes a `VACUUM INTO` snapshot, `auto-YYYY-MM-DD.db`, when the app opens (if today's is missing)
  and replaces it when the app closes. It keeps 30 days, then the last file of each month for 12 months. Files with
  other names are never touched.
- A backup is also taken before every schema upgrade (`pre-upgrade-...db`).
- The owner can back up to any folder, such as a pendrive (`VirpeMart-backup-...db`).
- Restore works in two steps because the open data file cannot be swapped: `DatabaseCheck` checks the chosen file
  (read-only), `PendingRestore.stage` copies it next to the data file and the app closes; at the next start
  `PendingRestore.finish` moves the current data to `backups\replaced-...db` and puts the backup in place, before the
  database is opened. The audit log records `BACKUP_RESTORED`.
- At every start `DatabaseCheck` runs SQLite's `quick_check`. A damaged file stops the start with
  `DamagedDataException`, and `App` offers the newest good backup.
- Updating the app replaces only the program folder, so data is never touched.
