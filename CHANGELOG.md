# Changelog

All notable changes to this project are recorded here, newest first.

## [Unreleased]

## [1.4.0] - 2026-10-18

Changes asked for by the owner. **No database change**: the schema stays at version 4, and all existing data
(customers, khata, products, bills, settings) is kept exactly as it was. Install it on each laptop with
INSTALLATION.md section 10; each keeps its own data. Going back to 1.3.0 needs no restore.

### Billing

- **Marathi names only**: the product search list shows the Marathi name and the rate (`साखर · ₹52`), without the
  English name, code or unit. The bill list shows the Marathi name, Qty, Rate and Amount. Searching by English name
  or code still works; the Products screen still shows everything.
- **No units after quantities**: `0.250` instead of `0.25 kg`, `3` instead of `3 Pkt`. A part of a kg or litre has
  three decimals (`0.250`, `1.500`); a whole number has none (`1`, `2`). The unit label next to the Qty box is gone.
- **Change a line in its own row**: double-click a line (or Enter, or "Change item (Enter)", which replaces
  "Change qty"). It goes up into the boxes at the top and its row stays empty until Enter puts it back into the same
  row with the new qty, rate or product. Esc puts it back as it was. Save waits until it is back; Hold, Clear, F4 and
  Recent bills put it back first. An "item not in list" opens its window filled in.
- Line numbers come from the row position (two identical lines used to show the same number).

### Printed bill

- Qty prints the number only, with the same rule (`0.250`, `1`, `3`). Applies to reprints of old bills too.

### Reports

- **Total sales is hidden** as `₹ ••••`. One click on the small grey eye next to it shows it, another click hides it.
  It is hidden again each time Reports is opened.

## [1.3.0] - 2026-10-07

Changes asked for by the owner. **No database change**: the schema stays at version 4, and all existing data
(customers, khata, products, bills) is kept exactly as it was. Install it on each laptop (the shop laptop and the
second laptop) with INSTALLATION.md section 10; each keeps its own data. Going back to 1.2.0 needs no restore.

### Billing

- **Product search list**: bigger (up to 12 whole rows, 16 px text), as wide as its longest row, never scrolls
  sideways. Up/Down scroll only as far as needed (the list no longer jumps), Page Up/Page Down jump 11 rows, and a
  click on the scroll bar now only scrolls (before, it chose the highlighted product and closed the list). Near the
  right edge of the screen (the customer search) the list opens further left so rows are not cut off. The search
  itself is unchanged.
- **जमा**: a built-in item for a khata customer who only pays money. Type जमा or jama in product search and press
  Enter: it is added at once, at rate 0 (locked). Type the money in the payment window; the bill prints मागील बाकी,
  जमा and एकूण बाकी as usual. It is not a product row, so the product list and the database are unchanged. Zero
  rates are still refused for products and one-off items.
- **Units**: quantities show `2 kg`, `5 Ltr`, `3 Pkt` (before: "5 litre", "3 pc").

### Printed bill

- Customer name and phone number are **bold**; bill number and date-time are normal (swapped).
- **No "DUPLICATE COPY"** line on a reprint: printing again gives exactly the same paper. Reprints are still
  recorded in the Activity log.
- Quantities print with their unit: `2 kg`, `5 Ltr`, `3 Pkt` (packets printed no unit before). The जमा line prints
  its name only.

## [1.2.0] - 2026-10-06

Changes asked for by the owner after two days of use at the shop. **No database change**: the schema stays at
version 4, and all existing data (customers, khata, products, bills) is kept exactly as it was.

### Printed bill

- The customer's name prints **exactly as saved** (the owner types names in Marathi). The automatic
  English-to-Marathi conversion and its word list are removed. `bills.customer_name_mr`, filled by 1.1.0, is kept
  but no longer read or written; old bills reprint with the saved name.
- Numbers without needless zeros, on the bill and on every screen: `₹50` instead of `₹50.00`, `₹45.5`,
  `1 kg` / `1.5 kg` / `0.25 kg` instead of `1.000 kg`. Display only: amounts are stored and calculated as before.

### Billing

- **Rate box next to Qty** (Product | Qty | Rate), filled in with the product's current rate. Enter in Qty moves to
  Rate; Enter in Rate adds the item. A different rate is for this bill only; the product's rate never changes.
- **Send on WhatsApp** in Recent bills (F9): the bill is copied as a picture (same design as the printed bill,
  without "DUPLICATE COPY") and WhatsApp Desktop opens; open the customer's chat, press Ctrl+V, then Enter.
  A copy is kept in `%LOCALAPPDATA%\VirpeMart\bill-images`. Cancelled bills cannot be sent.

### Products and customers

- **MRP is hidden everywhere** (Billing, Products, product form, import). Stored MRP values are kept; editing a
  product keeps its MRP.
- **Categories are removed** (button, window, filter, column, form box, import column). The `categories` table and
  each product's category are kept; editing a product keeps its category.
- **Download to Excel…** on Products and Customers: CSV files that open in Excel (Marathi included). The product
  file can be imported back.

## [1.1.0] - 2026-10-03

### App logo

- The app has its own logo (white "वि" on dark green with a gold bar): on `VirpeMart.exe`, the Desktop shortcut,
  the taskbar and every window. Icon file: `src/main/packaging/VirpeMart.ico`; window icons in
  `src/main/resources/icons/`.

### Recent bills and Correct bill

- The Billing screen's "Reprint bill" button is now **Recent bills (F9)**: the newest 20 bills, newest first and
  already selected (F9 then Enter shows the last bill). Arrow keys pick an earlier bill; typing finds older bills by
  number, name or phone. Cancelled bills are listed, marked CANCELLED.
- From the list: **See / print**, **Correct bill…** and **Cancel bill…** (owner).
- **Correct bill** (`BillingService.correct`): cancels the bill with a reason and loads its customer and items on the
  Billing screen, to change and save as a new bill. Items keep the rate charged; an open bill is put on hold first.
- The Billing title no longer shrinks to "..." next to a long message.

### Printed bill: item columns and Marathi customer name

- Items print as one row each in four columns: **Item, Qty, Rate, Amount**. Long item names wrap inside the Item
  column.
- The customer line is the name in Marathi letters with the phone number on the right
  (`उमेश विरपे   No. 9876501234`). The customer number (C0003) and the "Customer:" label are no longer printed.
- The Marathi name is made automatically from the English name (`print.MarathiTransliterator`), with a word list of
  common Maharashtrian names and name endings in `src/main/resources/print/marathi-names.txt`.
- Migration V4 saves the printed Marathi name and the phone with each new bill, so reprints always match. Bills
  saved before this change reprint with the name they were saved with.

## [1.0.0] - 2026-09-30

### Phase 8: Packaging and delivery

- Version 1.0.0. `scripts\package.ps1` now also copies the guides into `VirpeMart\Guides` and writes one zip,
  `target\dist\VirpeMart-1.0.0-Windows.zip`, ready for a pendrive or a GitHub release.
- The package is self-contained: its own Java runtime and the Microsoft C++ runtime files; the launcher needs
  only Windows itself. Checked by running the extracted zip with no Java, no PATH and a new, empty data folder.
- New guides: `docs/INSTALLATION.md` (a laptop with nothing installed), `docs/USER_GUIDE.md` (everyday use) and
  `docs/TROUBLESHOOTING.md` (every error message with its fix, plus technical facts for whoever helps).
- Code published to <https://github.com/aviraj1805/Billing-Software-Virape-Mart>.

### Phase 7: Backups, restore and activity log

- No login screen (the user's decision). The app signs in the shop owner automatically at every start, creating an
  "Owner" account on a new database. Before this, the app only worked on development data.
- Automatic daily backup on this laptop when the app opens and when it closes; 30 days plus one per month for a
  year are kept.
- Settings > Backups: last backup time, "Back up now to a folder or pendrive", and "Restore a backup" (checked,
  confirmed, finished when the app opens again; the replaced data is kept).
- The app checks the data file at every start and offers the newest good backup if it is damaged.
- History & Reports > Activity log: every recorded change in plain words, with date range and search.
- Long error messages are no longer cut off.
- 330 automated tests.

### Phase 6: History and reports

- History & Reports screen with two tabs. Bills: find bills by date range, bill number, customer name, customer
  number or phone; see and reprint them; the owner can cancel a bill with a reason.
- Cancelling keeps the bill (marked Cancelled, number never reused), takes its khata part back automatically,
  tells the owner how much money to give back, and records it in the audit log.
- Customers screen: "Bills" button shows a customer's purchase history.
- Reports tab (owner): daily summary and date-range sales with bills, sales, credit given, and money received by
  Cash, UPI and Card (at billing and khata payments), plus a day-by-day table. Cancelled bills are not counted.
- 308 automated tests.

### Phase 5: Printing

- Printed labels in Marathi: एकूण (bill total), मागील बाकी (previous dues), जमा (paid now, one line for all
  modes), एकूण बाकी (balance). "Total with dues" is no longer printed.

- Bill content corrected after the first test print: items print only the Marathi name, quantity x rate and
  amount; subtotal, round off, MRP, "You saved" and "This bill" are no longer printed.

- Settings screen (owner): shop details for the bill heading (name, second line such as the Marathi name,
  address, phone, closing lines) and the bill printer (any Windows printer; 58 mm roll, 80 mm roll or A4).
  A live preview shows a sample bill as it will print, and "Print a test bill" checks the printer before saving.
- Bills are drawn with the Windows "Nirmala UI" font, so Marathi item names print correctly.
- Khata bills print this bill, previous dues, total with dues, paid now and the balance.
- After saving a bill: ask "Print?" (default), print automatically, or do not print. Printing runs in the
  background; if it fails, the bill stays saved and the screen says so.
- "Reprint bill" button and double-click on a customer's recent bill: preview with a Print button.
  Reprints say "DUPLICATE COPY" and are recorded in the audit log.
- 288 automated tests.

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
