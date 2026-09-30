# CLAUDE.md

Guidance for Claude Code when working in this repository.

## Project

Offline desktop billing software for Virpe Mart, the user's father's grocery store. It runs on one
Windows laptop with one printer. Two main areas: **Products** and **Billing**, with account (credit)
customers.

- Business rules: `docs/requirements.md` is the source of truth. Read it before changing behaviour.
- Technical design: `docs/architecture.md`.

## Working with the user

- The user is a **Java beginner**. Before each step, explain what and why in plain words. Keep code readable.
- The user sometimes writes in **Marathi**. Understand it, but reply in English.
- Work **one phase at a time** (see Phase status). Each phase: explain, implement, test, run the app,
  fix, verify, commit, update this file and `CHANGELOG.md`, then report.
- **Never assume business logic.** If a rule is not in `docs/requirements.md`, ask the user with
  concrete options and a recommended default, then record the answer there.
- **Keep it simple.** No Spring, no ORM, no web server, no extra frameworks or layers.
  Add a dependency only when it clearly earns its place, and say why.

## Commands

Run from the repo root on Windows. `JAVA_HOME` points to Temurin JDK 25
(`C:\Users\Admin\.jdks\jdk-25.0.4.1+1`); if a shell lacks it, set it for that command.

| Task | Command |
|---|---|
| Run all tests | `mvnw.cmd test` |
| Run the app (development) | `mvnw.cmd javafx:run` |
| Build the packaged app | `powershell -ExecutionPolicy Bypass -File scripts\package.ps1` |

The packaged app is written to `target\dist\VirpeMart\VirpeMart.exe` with a bundled Java runtime.

## Tech stack

Java 25, JavaFX 25.0.4 (FXML + CSS), SQLite via `sqlite-jdbc` and plain JDBC, Maven Wrapper,
SLF4J + Logback, Apache Commons CSV, JUnit Jupiter, `jpackage`. Library versions live in `pom.xml` properties.

- Non-modular project: do not add `module-info.java`.
- `Launcher` is the packaged entry point and must **not** extend `Application`. `App` is the JavaFX application.
- Printing uses Java 2D (`java.awt.print`) so thermal paper widths and Marathi text work.

## Architecture rules

- Layers: `ui` calls `service`, `service` calls `repository`, `repository` talks to SQLite. Never skip a layer.
- UI controllers contain no SQL and no business rules.
- Services hold validation, business rules, **role checks** (OWNER vs STAFF) and transactions.
  A multi-step write such as saving a bill is **one transaction**.
- Repositories hold all SQL. Use `PreparedStatement` parameters only. Never concatenate input into SQL.
- Slow work (backup, import, reports) runs off the JavaFX thread. Update controls only on the FX thread.

## Data rules: never break these

- Money is stored as **integer paise** (`long`). In Java use the `Money` type backed by `BigDecimal`.
  Never use `double` or `float` for money.
- Quantity is stored as **integer thousandths** (0.250 kg = 250). In Java use the `Quantity` type.
- Rounding is HALF_UP. Line totals round to the paisa. Bill total rounds to the nearest rupee and the
  difference is stored in `round_off`.
- Saved bills are **immutable**. Corrections are an owner cancel with a reason, then a new bill.
  Never hard-delete financial records.
- Bill lines store a snapshot of name, Marathi name, unit, rate and MRP.
- Customer balance is `SUM(customer_ledger.amount)`. Never store a separate balance column.
- Bill numbers are continuous and never reused, including cancelled bills.
- There is **no stock tracking and no GST**. Do not add them unless the user asks.

## Database and migrations

- Migration files: `src/main/resources/db/migration/V{n}__{description}.sql`.
- **Never edit a migration after it is committed.** Add a new one instead.
- The built-in runner records version and checksum in `schema_version`, and backs up the database first.
- Every connection sets `PRAGMA foreign_keys=ON`, `journal_mode=WAL` and a `busy_timeout`.

## Data locations

- Store laptop: `%LOCALAPPDATA%\VirpeMart\{data,backups,logs}`.
- Development: `-Dvirpemart.dataDir=<repo>\dev-data`, which is git-ignored. Never point a dev run at real shop data.
- Never commit databases, backups, logs or real customer data.

## Code conventions

- Package root `com.virpemart.billing`, with subpackages `config`, `db`, `model`, `repository`,
  `service`, `print` and `ui.<area>`.
- FXML files in `src/main/resources/fxml/` (kebab-case names). CSS in `src/main/resources/css/`.
- Naming: `XxxService`, `XxxRepository`, `XxxController`. Use Java records for simple immutable data.
- Every public class gets a short Javadoc saying what it does in plain words.
- User-facing messages are simple English and tell the user what to do next.
- Errors: invalid input raises a validation exception with a field message. A broken business rule
  raises a business-rule exception with a clear message. Unexpected errors are logged and shown as a
  friendly dialog. Never swallow an exception silently.

## Testing

- Add or update JUnit tests for every service or repository change.
- Service tests use a real temporary SQLite file. Do not mock the database.
- Cover money and rounding edge cases explicitly.
- Run `mvnw.cmd test` before every commit. All tests must pass.
- For UI changes, run the app and check the screen by hand. List the manual checks in the commit or PR.

## Git workflow

- `main` always works. Do each phase on a branch named `phase-N-short-name`.
- Small commits, imperative commit messages.
- Merge to `main` only after tests pass and the phase is verified. Use GitHub pull requests once the
  remote exists.
- At the end of each phase, update `CHANGELOG.md` and the Phase status table below.

## Phase status

| Phase | Scope | Status |
|---|---|---|
| 0. Setup | JDK, Maven Wrapper, skeleton, docs, packaging smoke test | Done |
| 1. Foundation | Paths, DB connection, transactions, migrations, V1 schema, Money/Quantity, logging, error handler, single-instance lock, session | Next |
| 2. Products | Categories, products, search, deactivate/delete rules, CSV import | Not started |
| 3. Customers and ledger | Customers, opening balance, receive payment, balance and ledger view | Not started |
| 4. Billing | Billing screen, search, loose qty, one-off items, rate change, totals, split payments, save, hold | Not started |
| 5. Printing | Shop settings, printer settings, receipt with Marathi, print and reprint | Not started |
| 6. History and reports | Bill history, cancel with reversal, purchase history, reports | Not started |
| 7. Users, security, backup | Login, first-run setup, staff users, audit view, backups, restore | Not started |
| 8. Packaging and go-live | Install on store laptop, import products, user guide, training | Not started |

## Pending inputs from the user

- Publish the repository to GitHub as a private repo.
- Printer brand and model, and shop details for the bill header: needed before Phase 5.
- Store laptop Windows version and RAM, and the product Excel sheet: needed before Phase 8.

## Glossary

- **Khata / udhaar**: a customer's credit account at the store.
- **Account customer**: a regular customer with a khata who may pay later.
- **Walk-in customer**: a customer without an account; must pay in full.
- **MRP**: Maximum Retail Price printed on a packet.
- **Loose item**: sold by weight at a rate per kg or per litre.
- **One-off item**: an item typed directly on a bill that is not in the product list.
- **OWNER / STAFF**: the father and his helper, with different permissions.
