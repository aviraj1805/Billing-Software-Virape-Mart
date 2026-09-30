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
repositories, commits, and returns the saved bill. The screen then calls `printService.print(bill)`.
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
| Passwords | PBKDF2-HMAC-SHA256, built into Java | No extra dependency |
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
| `products` | Short code, name, Marathi name, category, sale type LOOSE or PACKED, unit, pack size, rate, optional MRP, active flag |
| `customers` | Customer number, name, phone, address, notes, active flag |
| `bills` | Bill number, date and time, customer or walk-in, subtotal, round-off, total, paid now, added to account, status, cancel details, created by |
| `bill_items` | Bill, product or none for one-off items, snapshot fields, quantity, rate, original rate if changed, line total |
| `bill_payments` | Bill, mode CASH, UPI or CARD, amount |
| `customer_ledger` | Customer, entry type, signed amount, bill, payment mode, note, user, time |
| `audit_log` | Who did what and when: rate changes, product edits, cancellations, restores, settings |
| `schema_version` | Applied migrations and their checksums |

Ledger entry types: OPENING, SALE_CREDIT, PAYMENT, CANCEL_REVERSAL, ADJUSTMENT.

## Saving a bill

All of these happen in one database transaction. Either everything is saved or nothing is.

1. Take the next bill number.
2. Insert the bill, its lines and its payments.
3. If part of the total goes to the customer's account, insert a SALE_CREDIT ledger entry.
4. Insert audit rows, for example for changed rates.

Printing happens after the transaction commits, so a printer problem never loses a bill.

## Security

- No network ports are opened.
- OWNER and STAFF logins. Passwords are stored only as salted PBKDF2 hashes.
- First-run setup shows a one-time recovery code so a forgotten owner password cannot lock the shop out.
- Role checks happen in the service layer, not only by hiding buttons.
- All SQL uses parameters, which prevents SQL injection.
- Sensitive actions are written to the audit log.

## Error handling

| Situation | Handling |
|---|---|
| Invalid input | Friendly message next to the field; nothing saved |
| Business rule broken | Clear dialog explaining what to do next |
| Database error while saving | Transaction rolled back; friendly message; details in the log |
| Printer off or out of paper | Bill already saved; reprint later from History |
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

Development runs pass `-Dvirpemart.dataDir=<repo>\dev-data`, so test data never mixes with shop data.

## Backups

- A snapshot is taken automatically at the first start of each day and when the app closes.
- The app keeps 30 daily and 12 monthly snapshots.
- A backup is also taken before every schema upgrade and before every restore.
- The owner can back up to any folder, such as a pendrive, and restore from a backup.
- Updating the app replaces only the program folder, so data is never touched.
