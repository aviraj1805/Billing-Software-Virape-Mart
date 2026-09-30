# Changelog

All notable changes to this project are recorded here, newest first.

## [Unreleased]

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
