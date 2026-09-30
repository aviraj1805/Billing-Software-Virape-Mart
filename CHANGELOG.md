# Changelog

All notable changes to this project are recorded here, newest first.

## [Unreleased]

### Phase 4: Billing

- Billing screen, now the start screen. Keyboard flow: type a product (F2), Enter, quantity, Enter.
  Drop-down search by English name, Marathi name or code.
- Loose items in kg/litre with decimals; packets in whole numbers; same product again adds to its line.
- Change quantity or rate of a line (rate change for this bill only, recorded in the audit log),
  remove lines, and add items not in the product list (F4).
- Totals with round-off to the rupee and "You saved" against MRP.
- Customer panel: walk-in with an optional name on the bill, or a khata customer (F3) with dues and
  recent bills; quick "New khata customer".
- Khata customers see this bill, previous dues and total with dues.
- Save and pay (F12): Cash / UPI / Card in any mix, change calculator for walk-in, quick buttons and a
  "put on khata?" check for khata customers, extra money goes against old dues.
- Hold bills (F8) and continue them later; the app asks before closing with unsaved bills.
- Migration V3: bills store the printed customer name, dues before and balance after; the
  "bills can only be cancelled" guard now covers these too.
- 252 automated tests.

### Phase 3: Customers and khata

- Customers screen: search by name, phone or number; filter to customers with dues; total dues of the shop.
- Customer details with the khata on the right: dated entries with Added, Paid and a running balance,
  balance shown in red (dues) or green (advance).
- Add and edit customers with automatic numbers (C0001...), 10-digit phone check, and old dues from
  the paper khata entered once as the opening entry.
- Receive payment (Cash, UPI or Card) with a live "balance after payment" preview and advance warning.
- Owner-only balance corrections with a required reason, added as a new khata entry.
- Switch customers off/on (owner); customers are never deleted.
- The app now opens maximized. Shared form error handling (`FormErrors`) and amount parsing (`Amounts`).
- Typing 0 in an optional amount now means "none".
- 220 automated tests.

### Phase 2: Products

- Main window with a top bar and a menu: Billing, Products, Customers, History & Reports, Settings.
  Products is working; the other sections show when they arrive.
- Products screen: search as you type by English name, Marathi name, pack size or code (every word must
  match), category filter, show switched-off products. Keyboard: Ctrl+F, Ctrl+N, Enter, Delete, Esc.
- Add and edit products with automatic codes (P0001...), field-level error messages, and a warning when
  the rate is above MRP.
- Delete only products that were never billed; billed products can be switched off instead.
- Categories: 19 default grocery categories (migration V2); add, rename, switch off.
- Import from Excel (CSV UTF-8) with a full preview, common column names understood, duplicates skipped,
  new categories created, a blank template, and clear help when the file is .xlsx or not UTF-8.
- Every product and category change is written to the audit log.
- Confirmation dialogs now default to Cancel, so Enter never deletes by accident.
- Fixed: rupee amounts now use Indian grouping (₹1,25,000.00).
- Guide for the owner: `docs/product-import-guide.md`. Developer tool: `scripts/dev/ui-automation.ps1`.
- 181 automated tests.

### Phase 1: Foundation

- SQLite database with safe settings: foreign keys, WAL journal, full sync, immediate transactions.
- `Database` runs work in one transaction and undoes everything on any error.
- Migration runner with `index.txt`, checksums, "newer database" and "foreign file" protection,
  and an automatic backup before upgrading an existing database.
- V1 schema with all planned tables. Database triggers stop saved bills, lines, payments, ledger and
  audit records from being edited or deleted; bills can only be cancelled.
- `Money` (paise) and `Quantity` (thousandths) types with HALF_UP rounding.
- Data folders under `%LOCALAPPDATA%\VirpeMart`, or `dev-data` during development.
- Daily rolling log files, a global error handler with friendly messages, and a single-copy lock.
- Session with OWNER and STAFF permission checks; development runs sign in a `dev-owner` automatically.
- Fixed: `scripts/package.ps1` failed on its second run because `jpackage` marks the exe read-only.
- 109 automated tests.

### Phase 0: Setup

- Maven project on Java 25 and JavaFX 25.0.4, with the Maven Wrapper.
- Placeholder main window that shows the app version and a Marathi text sample.
- `AppInfo` reads the app name and version from the build.
- `scripts/package.ps1` builds a Windows app folder with a bundled Java runtime.
- Project documents: README, CLAUDE.md, requirements and architecture.
