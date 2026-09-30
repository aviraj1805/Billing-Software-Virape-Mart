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
| Build the packaged app and zip | `powershell -ExecutionPolicy Bypass -File scripts\package.ps1` |

The packaged app is written to `target\dist\VirpeMart\VirpeMart.exe` with a bundled Java runtime, and the zip to
deliver to `target\dist\VirpeMart-<version>-Windows.zip` (includes `Guides\`). The shop-facing guides are
`docs/INSTALLATION.md`, `docs/USER_GUIDE.md` and `docs/TROUBLESHOOTING.md`: update them whenever a screen, message or
file location changes, because the user gives them to an AI assistant when something goes wrong.
To test the package like a fresh laptop without touching real folders, start the extracted `VirpeMart.exe` with
`JAVA_HOME` empty, `PATH=C:\Windows\System32;C:\Windows` and `LOCALAPPDATA` pointing at a temporary folder.

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

- Migration files: `src/main/resources/db/migration/V{n}__{description}.sql`, and **each must be listed
  in `index.txt`** in the same folder. A test fails if a file is missing from the index.
- **Never edit a migration after it is committed.** Add a new one instead. The runner stops on a checksum mismatch.
- The built-in runner (`db.MigrationRunner`) records version and checksum in `schema_version`, and backs up
  an existing database before upgrading.
- `db.Database` sets foreign keys ON, WAL, synchronous FULL, busy timeout and IMMEDIATE transactions.
  Use `inTransaction` / `runInTransaction` for writes, `query` for reads, and `executeOutsideTransaction`
  only for statements like `VACUUM`.
- Repositories take the `Connection` as a method parameter; services decide the transaction boundary.
- Dates: `db.DbTime` text format `yyyy-MM-ddTHH:mm:ss`, shop-local. Services take a `java.time.Clock`
  so tests can fix the time (`TestDatabases.FIXED_CLOCK`).
- Product units: `KG` and `L` are loose (decimal quantity), `PCS` is packed (whole quantity).
- V1 triggers block deleting or editing bills, bill lines, payments, ledger and audit rows. The only allowed
  bill update is FINAL to CANCELLED with reason, user and time. Do not work around these triggers.

## Data locations

- Store laptop: `%LOCALAPPDATA%\VirpeMart\{data,backups,logs}`.
- Development: `mvnw.cmd javafx:run` passes `-Dvirpemart.dataDir=dev-data` (git-ignored). The
  `VIRPEMART_DATA_DIR` environment variable also works, e.g. to test the packaged exe against a temp folder.
  Never point a dev run at real shop data.
- There is **no login screen** (user's decision): every start signs in the first active OWNER
  (`service.OwnerBootstrap`, creates "Owner" on a new database; password hash `!` never matches). On dev-data that
  is the older `dev-owner` account. Keep the role checks in services anyway.
- Never commit databases, backups, logs or real customer data.

## Known pitfalls

- The project folder name contains a space. The JavaFX Maven plugin splits `<option>` values at spaces,
  so never put `${project.basedir}` in its options. Use paths relative to the project folder.
- `jpackage` marks `VirpeMart.exe` read-only, which blocks `mvnw clean`. `scripts\package.ps1` removes the old
  package first and clears the flag afterwards. If clean fails, delete `target\dist` with PowerShell `-Force`.
- Logging: `Startup` sets the `virpemart.logDir` system property before the first logger is created.
  Do not add `static` loggers to `App`, `Launcher` or `Startup`. Tests log WARN+ to the console only
  (`src/test/resources/logback-test.xml`).
- JavaFX runs from the classpath (non-modular), so `--enable-native-access=ALL-UNNAMED` is the right flag.
- A wrapped `Label` inside a `VBox` gets squeezed and shows "...". Give it `minHeight="-Infinity"`.
  Buttons next to a growing label need `minWidth="-Infinity"`.
- JavaFX hides hint (prompt) text in a focused box; `app.css` overrides this so hints stay visible.
- An `fx:include`d FXML with its own controller needs its own `fx:id="root"` if the controller uses `root`.
- Long `Alert` messages get cut off with "..." unless the dialog pane has `setMinHeight(Region.USE_PREF_SIZE)`.
- To refresh a screen when it is shown again, listen to `sceneProperty()` (a node inside a tab or an included
  file keeps its parent, so `parentProperty()` does not change).
- A focused JavaFX button takes the Enter key, even if another button is the default. In a window where Enter
  should press a button, focus that button when the window opens (see `ReceiptPreviewController`).
- The packaged runtime has less locale data than the JDK (e.g. "Sep" instead of "Sept"). Do not depend on
  locale formatting for anything important.

## Checking screens

After UI changes, run the app and drive it with `scripts\dev\ui-automation.ps1` (Windows UI Automation):
find windows and buttons by their text, type with `Send-Keys` (Marathi text works), and take screenshots
of any window with `Capture-Window`. Look at each screenshot before calling a screen done.
`Send-Keys` uses SendKeys syntax: `+` is Shift, `^` is Ctrl, `%` is Alt. Type a literal plus as `{+}`.
In a TextArea, Tab types a tab; use `^{TAB}` to move to the next field.
Call `Focus-Window` right before sending function keys (F12 etc.); if the window lost focus, keys go nowhere.
The search drop-down is a separate popup window: capture the whole screen to see it.
`Invoke-Button` waits until a dialog opened by that button closes; to open a dialog, `SetFocus()` the button and
send a space instead.
If the user has the app open too, both windows have the same title: start your test app with `Start-Process -PassThru`
and use `Find-ProcessWindow <pid>` so you never click in the user's app.
Test anything that cannot be undone (such as cancelling a bill) on a copy of `dev-data`: `javafx:run` always uses
`dev-data`, so run `com.virpemart.billing.Launcher` with `java -cp` (classpath from
`mvnw dependency:build-classpath` plus `target\classes`) and `-Dvirpemart.dataDir=<copy>`. Check printing with "Microsoft Print to PDF": Windows asks for a file name
(`Save Print Output As`), and the PDF shows exactly what the printer would get.

## Code conventions

- Package root `com.virpemart.billing`, with subpackages `config`, `db`, `model`, `repository`,
  `service`, `print` and `ui.<area>`.
- FXML files in `src/main/resources/fxml/` (kebab-case names). CSS in `src/main/resources/css/`.
- Naming: `XxxService`, `XxxRepository`, `XxxController`. Use Java records for simple immutable data.
- Every public class gets a short Javadoc saying what it does in plain words.
- User-facing messages are simple English and tell the user what to do next.
- Errors: invalid input raises `service.ValidationException` (with a field name). A broken business rule
  raises `service.BusinessRuleException`; a permission problem raises `service.PermissionDeniedException`.
  These are `UserFacingException`s: their message is shown as-is, so write it for the shop user.
  Anything else is a bug: `ui.common.ErrorHandler` logs it and shows a friendly dialog.
  Never swallow an exception silently.
- Permissions: services call `session.requireOwner()` or `session.requireSignedIn()` first.
- Controllers are created by `ui.common.ControllerFactory`; give a controller a constructor taking
  `AppContext`. Controllers use `context.services()` (the `service.Services` record, built in `Startup`).
  Add each new service to `Services.create`.
- UI helpers in `ui.common`: `Views.load` / `Views.dialog` (FXML + stylesheet), `Dialogs` (confirm, info,
  warning, askText), `Background.run` (slow work off the FX thread), `Format.money` (₹ with Indian
  lakh grouping; Java's locale formatting does not do this).
- `Dialogs.confirm` makes **Cancel the default button**, so Enter never deletes or saves a mistake. Keep it so.
- Forms: catch `ValidationException` and pass it to `ui.common.FormErrors`, which shows the message and
  highlights the named field (see `CustomerFormController`).
- Typed amounts: services use `Amounts.parsePositive` (accepts ₹, Rs, commas; 0 means "none" when optional).
- Khata: `LedgerService` (statement with running balance, payments, owner corrections) and `CustomerService`
  (customers, old dues as the OPENING entry). Never compute a balance anywhere else.
- Billing: `model.Cart` holds bill lines and ALL bill arithmetic (`Cart.totalsOf`); `BillingService.save`
  re-checks and recalculates everything in one transaction. `ui.common.SearchPopup` is the keyboard
  drop-down used for product and customer search. Billing is the start screen; `BillingController.unsavedWork`
  feeds the close-window warning.
- The main window opens maximized. Tables use `CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS` so columns share space;
  use short date formats (`Format.dateTimeShort`) and row tooltips for long text.
- Shared validation: a service `check(...)` method validates typed input once and is reused by forms and
  imports (see `ProductService.check`).
- Owner-only buttons are hidden for STAFF, but the service still enforces the rule.
- Printing: `print.ReceiptBuilder` decides the bill's lines (test it with `Receipt.toPlainText()`),
  `print.ReceiptRenderer` draws them (Nirmala UI font) for paper and for preview pictures, and
  `service.PrintService` prints, reprints (audited, "DUPLICATE COPY") and test-prints. Printing always runs
  after the bill is saved and off the FX thread. Tests use `FakePrinter`; never print in tests.
- History: `BillingService.searchBills` (a short number is a bill number on any date), `cancel` / `cancelPreview`
  (owner; CANCEL_REVERSAL for the khata part; counter money is given back). `ui.history.BillHistoryController` is
  the Bills tab and, via `openForCustomer`, a customer's purchase history. `MainWindowController` refreshes the
  open History tab each time it is shown (tab content does not get a new parent, so "reload when shown" there
  does not work).
- Reports: `ReportService.sales(from, to)` (owner) returns `SalesReport` of `DaySummary` rows; FINAL bills only;
  khata payments count on the day received. Work it out with `Background.run`.
- Date boxes: `DatePicker.setConverter(Format.dateInput())` for dd/MM/yyyy.
- Backups: `BackupService` (automatic `auto-YYYY-MM-DD.db` at open and close, retention, back up now, check and
  restore). Restore = `PendingRestore.stage`, close, `finish` at the next start; never delete the replaced data.
  `DatabaseCheck.inspect` checks a file read-only. `Startup` refuses a damaged file with `DamagedDataException`.
- Activity log: `AuditService.search` (owner); plain-word action names live in `ActivityLogController.ACTIONS`,
  so add one there for every new audit action code.
- `Services.create(database, session, clock, backupsDir)` for the app; tests use the overload with a printer.
- Settings: `SettingsService` reads and saves shop details and printer setup in the `settings` table (owner only).
- Money: `model.Money` (paise) and `model.Quantity` (thousandths). `Money.times(Quantity)` and
  `Money.roundToRupee()` hold the only rounding logic; do not round anywhere else.

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
| 1. Foundation | Paths, DB connection, transactions, migrations, V1 schema, Money/Quantity, logging, error handler, single-instance lock, session | Done |
| 2. Products | Categories, products, search, deactivate/delete rules, CSV import | Done |
| 3. Customers and ledger | Customers, opening balance, receive payment, balance and ledger view | Done |
| 4. Billing | Billing screen, search, loose qty, one-off items, rate change, totals, split payments, save, hold | Done |
| 5. Printing | Shop settings, printer settings, receipt with Marathi, print and reprint | Done (store printer test pending) |
| 6. History and reports | Bill history, cancel with reversal, purchase history, reports | Done |
| 7. Backup and audit | No login (user's decision), automatic owner sign-in, backups, restore, damaged-file check, activity log | Done |
| 8. Packaging and go-live | Version 1.0.0 zip (self-contained), installation guide, user guide, troubleshooting guide, GitHub | Done (real printer and shop laptop pending) |

## Pending inputs from the user

- GitHub: <https://github.com/aviraj1805/Billing-Software-Virape-Mart> (remote `origin`). It is **public**; the
  user originally wanted private. Never commit shop data there. Future work goes through pull requests.
- Printer brand and model, and a test print on the real store printer (Settings > Print a test bill).
  Do not tune paper size, margins, centring or spacing for a specific printer until the user gives the model
  and the real test-print problems. The bill wording follows the user's demo bill; do not add new text.
- Store laptop Windows version and RAM, and the product Excel sheet: needed before Phase 8.
  The user never pasted the sheet's header row; the import accepts common column names
  (see `docs/product-import-guide.md`). If their real sheet uses other names, add them to
  `ProductImportService.buildHeaderNames()`.

## Glossary

- **Khata / udhaar**: a customer's credit account at the store.
- **Account customer**: a regular customer with a khata who may pay later.
- **Walk-in customer**: a customer without an account; must pay in full.
- **MRP**: Maximum Retail Price printed on a packet.
- **Loose item**: sold by weight at a rate per kg or per litre.
- **One-off item**: an item typed directly on a bill that is not in the product list.
- **OWNER / STAFF**: roles in the code. With no login, the app always works as OWNER; STAFF is unused for now.
