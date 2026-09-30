# Changelog

All notable changes to this project are recorded here, newest first.

## [Unreleased]

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
