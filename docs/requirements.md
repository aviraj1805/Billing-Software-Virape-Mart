# Requirements

Confirmed with the user in September 2026. This is the source of truth for business rules.
If something is not covered here, ask the user before assuming.

## Purpose

Offline billing software for the Virpe Mart grocery store. It runs on one Windows laptop with one printer.
It has two main areas:

1. **Products**: the owner manages the list of items the store sells.
2. **Billing**: create bills for walk-in and account (credit) customers, and print them.

## Confirmed decisions

| Topic | Decision |
|---|---|
| GST | Store is not GST-registered. Bills are simple cash memos with no tax fields. |
| Stock | **Not tracked.** Products hold name, rate and descriptive information only. |
| Products | Owner defines each item with rate, unit and pack size. Each variant is a separate product, for example Sugar Loose, Sugar 1 kg pack, Sugar 5 kg pack. Searching "sugar" shows all of them. |
| Loose items | Rate is fixed per kg (or per litre). Quantity is typed in kg with up to 3 decimals, for example 0.250 or 0.500. |
| Barcodes and scales | Not used. Products are found by name or a short product code. |
| Language | English screens. Each product can also have a Marathi name, which is searchable and printed on the bill. |
| One-off items | If an item is not in the product list, it can be typed directly on a bill with name, rate and quantity. It is saved on that bill only. No product is created. |
| Product search list | (The user's request, 2026-10-07, version 1.3.0.) The drop-down under the search box shows up to **12 whole rows** (fewer if the screen has less room), is wide enough for the longest row (near the right edge of the screen it opens further left), and never scrolls sideways. **Up/Down** move one row and scroll only as far as needed; **Page Up/Page Down** move 11 rows; **Enter** or a **click** on a row chooses; the scroll bar and the mouse wheel only scroll. The search itself (what matches, and in which order) is unchanged. The customer search uses the same drop-down. |
| Rate change on a bill | Owner and helper can change a line's rate for that bill only. The product's saved rate does not change. Every change is recorded with who made it. Since version 1.2.0 (the user's choice, 2026-10-06) the Billing screen has a **Rate box next to Qty** (Product, Qty, Rate), filled in with the product's current saved rate: Enter in Qty moves to Rate, Enter in Rate adds the item. Typing another rate there is the same as a rate change for this bill only. |
| Round-off | The final total is rounded to the nearest rupee. |
| MRP | **Not shown anywhere** (the user's choice, 2026-10-06, version 1.2.0): no MRP column or box in Billing or Products, no "You saved" line, and MRP is not printed. The MRP already stored for a product is **kept untouched** in the database (editing a product keeps it), and bill lines still keep it in their snapshot. New products have no MRP; an MRP column in an imported sheet is ignored. |
| Discount | No whole-bill discount. |
| Payment modes | Cash, UPI and Card. One bill can be split across several modes. |
| Credit | An account customer can pay any amount at billing, including zero. The rest is added to their account. There is no credit limit. |
| Returns and mistakes | The owner cancels the whole bill with a reason and makes a new bill. Any credit from the cancelled bill is reversed automatically. |
| Bill numbers | Continuous: 1, 2, 3 and so on. Never reset and never reused, even for cancelled bills. |
| Hold bill | A bill can be put on hold to serve another customer, then resumed. Held bills are kept in memory only. |
| Users | **No login screen** (decided in Phase 7: one laptop, practically no risk of other people using it). The app always works as the owner, so the helper can use every screen. Every change is still recorded in the activity log. |
| Reports | Daily sales summary with the Cash, UPI and Card split. Sales for a date range. |
| Existing product list | Kept in Excel or Google Sheets, so the app provides a CSV import. |
| Backups | Kept on the store laptop only. The user accepted the risk of theft or disk failure. |
| Laptops | Developed on one laptop. The packaged app is installed on the store laptop and on a second laptop (the user's sister's). **Each laptop keeps its own data**; there is no syncing of data. A new version reaches each laptop as the same zip, installed by the steps in INSTALLATION.md section 10, and must keep that laptop's data. Prefer changes without a database upgrade; when one is needed, it must be backward-compatible and back up first (the migration runner does). |
| Printer | Model not known yet. Printing works with any printer installed in Windows: 58 mm or 80 mm receipt rolls, or A4 sheets. |

## Products (confirmed in Phase 2)

| Topic | Decision |
|---|---|
| Product codes | Created automatically: P0001, P0002, and so on. Codes never change. |
| Categories | **Removed** (the user's choice, 2026-10-06, version 1.2.0): no Categories button, filter, column or form box, and the import ignores a Category column. The `categories` table and each product's stored category are **kept untouched** in the database (editing a product keeps its category); they are simply not used. |
| Excel import | Clear, simple column names: Name, Marathi Name, Unit, Pack Size, Rate. Common alternative names are understood. See [product-import-guide.md](product-import-guide.md). |
| Download to Excel | (The user's choice, 2026-10-06, version 1.2.0.) **Products** and **Customers** each have a **Download to Excel…** button (owner). It saves a **CSV file that opens in Excel** (UTF-8 with the byte-order mark, so Marathi shows), not an .xlsx file, so no extra library is needed. Products: Code, Name, Marathi Name, Unit (kg/litre/pcs), Pack Size, Rate, Active (Yes/No), all products including switched-off ones, **visible fields only** (no hidden MRP or category); the file can be imported back. Customers: Customer No, Name, Phone, Address, Notes, Balance (minus = advance, from the khata ledger), Active, Added on. Downloads only read data; each is recorded in the activity log. |

These product rules were chosen as sensible defaults and can be changed:

- Two products cannot have the same name, pack size and unit. The app points to the existing product instead.
- A product that has never been billed can be deleted. A product on any bill can only be switched off:
  it disappears from billing, but old bills stay correct.
- An import only adds new products. It never changes existing products; to change a rate, edit the product.
  Rows that already exist are skipped. Category and MRP columns in the sheet are ignored.

## Customers and khata (confirmed in Phase 3)

| Topic | Decision |
|---|---|
| Customer details | Name, phone, address and notes. Nothing else is needed. Customer numbers are automatic: C0001, C0002, and so on. |
| Paper khata | The khata is kept both in the software and in the paper book. |
| Old dues | When a customer is added, the owner copies their current dues from the paper khata into "Old dues from paper khata". From then on everything happens in the software. |
| Corrections | The owner can correct a balance to match the paper khata. A reason is required, and the correction is added as a new khata entry; nothing is erased. |

These customer rules were chosen as sensible defaults and can be changed:

- Phone numbers are stored as 10 digits. "+91", spaces, dashes and a leading 0 are removed. Two customers cannot share a phone number; two customers may share a name.
- Owner and helper can add and edit customers and receive payments. Only the owner can correct balances and switch customers off or on.
- Customers are never deleted, only switched off, so their khata is always kept. Switching off a customer who still owes money shows a warning.
- A payment larger than the dues is allowed; the extra is kept as advance and shown in green.
- Typing 0 in an optional amount (old dues) means "none".

## Billing (confirmed in Phase 4)

| Topic | Decision |
|---|---|
| Khata customer bills | The screen and the printed bill show this bill's amount, the previous dues, and the total with dues, so the customer and owner see both the current bill and the full amount owed. |
| Bill numbers | Start at 1 and continue forever. |
| Walk-in names | For a walk-in customer the owner may type a name that is printed on the bill only (no account is created), or leave it blank. |

These billing rules were chosen as sensible defaults and can be changed:

- For a khata customer, the payment starts empty. Quick buttons fill "Bill amount", "Full total with dues" or "Nothing now". Saving with nothing paid asks "Put the whole bill on the khata?" first.
- A khata customer may pay more than this bill: the extra is recorded in the khata as "Paid with bill N", against old dues (or kept as advance).
- A walk-in customer pays exactly the bill total, in any mix of Cash, UPI and Card. "Cash given by customer" shows the change to give back.
- The bill saves each line's product name, Marathi name, unit, pack size, rate and MRP, plus the dues before and the balance after, so reprints always match.
- Adding the same product again increases its quantity on the existing line.
- Held bills are kept only while the app is open; closing the app with an open or held bill asks first.

### जमा: money paid without items (the user's choices, 2026-10-07, version 1.3.0)

| Topic | Decision |
|---|---|
| Purpose | A customer with khata dues comes only to pay. The bill shows what they paid and the dues left. |
| How it exists | A **built-in item**, not a row in the product list (products must have a rate above zero, and changing that would need a database upgrade). It does not appear in Products. **No database change**: on a bill it is saved like a one-off item (no product), named जमा, 1 piece at rate 0. |
| Finding it | Product search: typing **जमा** or **jama** lists it first; typing only its start (ज, जम, j, ja, jam) lists it last, so it never pushes a real product down. |
| Adding it | Choosing it adds it to the bill at once (no Qty or Rate to type). A bill has it at most once. |
| Rate | **Locked at 0.** Change qty and Change rate refuse it; the money paid is typed in the payment window. Only the जमा line may have rate 0: products and one-off items still need a rate above zero, on screen and when saving. A one-off item cannot be named जमा. |
| Who can use it | **No extra rules**: any bill, khata or walk-in, alone or with other items. A walk-in bill with only जमा is a ₹0 bill (a walk-in customer pays exactly the bill). |
| Money | Uses the existing khata rule: for a khata customer, money beyond the bill total is a khata payment ("Paid with bill N") against old dues, or an advance. Reports count it as money received that day. Cancelling the bill keeps it paid, like any dues payment made with a bill. |
| Screen and print | The line shows and prints its name only: "1. जमा" with Qty, Rate and Amount blank. Below it the usual khata lines: एकूण ₹0, मागील बाकी, जमा (money paid), एकूण बाकी. |

## Printing (built in Phase 5)

The printer model and the exact bill heading were not known yet, so both are **settings the owner fills in**
(Settings screen) instead of fixed values. These defaults were chosen and can be changed:

- The bill heading is the shop name, an optional second line (for example the name in Marathi), up to 3 address
  lines, a phone number, and up to 2 closing lines at the bottom. Until the owner saves them, the bill shows
  "Virpe Mart" and "Thank you! Please visit again."
- After a bill is saved, the app **asks "Print?"** (Enter prints, Esc skips). The owner can switch this to
  "Print every bill automatically" or "Do not print".
- Paper starts as an **80 mm roll**. 58 mm rolls and A4 sheets can be chosen.
- Corrected by the user after the first Phase 5 test print (the demo bill is the reference; do not add other wording):
  - Items print as a table with four columns: **Item, Qty, Rate, Amount** (changed by the user on 2026-10-03; before,
    each item took two lines with "quantity x rate" under the name). Example: `1. टाटा मीठ   2   28   56`.
    A long item name wraps inside the Item column; Qty, Rate and Amount stay on its first line.
  - Each item prints **only the Marathi name**. No English name, pack size or MRP. An item without a Marathi name
    (for example a one-off item) prints its English name so the line is not blank.
  - The customer line is the customer's name **exactly as it was saved**, with the phone number on the right:
    `उमेश विरपे        No. 9876501234`. No customer number (C0003) and no "Customer:" label.
  - **Bold** (changed by the user on 2026-10-07, version 1.3.0): the customer's name and phone number are bold; the
    bill number and date-time are normal (before, it was the other way round). A walk-in name is bold too.
    (Changed by the user on 2026-10-06, version 1.2.0: the owner types customer names in Marathi, and the automatic
    English-to-Marathi conversion of version 1.1.0 is removed. A name is never converted or changed.)
  - A walk-in customer's typed name is printed the same way: as typed, no label, no phone.
  - The name and phone are saved with the bill, so a reprint always matches. Bills saved by version 1.1.0 also
    stored a converted Marathi copy (`bills.customer_name_mr`); it stays in the database untouched but is no longer
    printed: every bill, old or new, prints the name as saved.
  - Only the bill total is printed, labelled **एकूण**. Subtotal and round off are not printed (they are
    still saved with the bill and shown on screen).
  - A khata bill prints **मागील बाकी** (previous dues), **जमा** (all money paid with the bill, in any mix of Cash,
    UPI and Card, as one line) and **एकूण बाकी** (balance after the bill). An advance prints as a minus amount under
    the same labels. "This bill" and "Total with dues" are never printed.
  - These Marathi labels are the only wording for these lines; no English is printed next to them.
- **Numbers without needless zeros** (the user's choice, 2026-10-06, version 1.2.0), on the printed bill and on every
  screen: whole rupees show as `₹50`, `₹155`, `₹1,250`; paise show without a trailing zero: `₹45.5`, `₹12.25`.
  Quantities: `1 kg`, `2 kg`, `1.5 kg`, `0.25 kg`, `0.333 kg`.
- **Units after a quantity** (the user's choice, 2026-10-07, version 1.3.0), on the Billing screen and on the printed
  bill: `kg` for kg, **`Ltr`** for litre and **`Pkt`** for packet/piece items: `2 kg`, `5 Ltr`, `3 Pkt`. (Before,
  litre showed as "litre" and packets printed no unit.) Rates read "₹50 / Ltr", "₹28 / Pkt". Only the text changed:
  units are stored as before (KG, L, PCS). Only the text is shorter: amounts and quantities
  are stored and calculated exactly as before (paise and thousandths), and nothing is rounded for display.
  The activity log keeps its two-decimal text (`44.00 -> 46.00`), so old and new entries read the same.
- Printing happens when the owner presses **Print** (the "Print?" question after saving, or the Print button when
  reprinting).
- Paper fit, centring, margins and spacing are **not tuned for a specific printer yet**. They will be adjusted after the
  printer is bought and a real test print is checked.
- Printing a bill again gives **exactly the same paper** as the first print (changed by the user on 2026-10-07,
  version 1.3.0: the "DUPLICATE COPY" line is removed, and there is no other reprint mark). Printing never changes
  the saved bill. Each reprint is still recorded in the audit log. Anyone can reprint. A cancelled bill is still
  marked "*** CANCELLED BILL ***".
- A printer problem never affects the saved bill: the screen says "saved but NOT printed" and the bill can be
  reprinted from "Recent bills" (F9).
- **Send on WhatsApp** (the user's choice, 2026-10-06, version 1.2.0): a button in **Recent bills (F9)** only.
  The shop laptop uses the **WhatsApp Desktop** app. WhatsApp offers no way for another program to attach a photo,
  so the agreed flow is: the app draws the bill as a picture (exactly like the printed bill, same paper width), copies it to the Windows clipboard, saves a copy as
  `%LOCALAPPDATA%\VirpeMart\bill-images\bill-<number>.png`, and opens WhatsApp (link `whatsapp://send`). The owner
  searches the customer's chat, presses Ctrl+V, then Enter. The app never chooses the chat and never sends by itself.
  A cancelled bill cannot be sent. Each picture made is recorded in the activity log ("Bill sent on WhatsApp").
  Printing is not affected.

## History and reports (built in Phase 6)

These rules were chosen as sensible defaults and can be changed:

- **Bill history** (owner and helper): bills of one day, a date range or all dates, newest first. The search box
  finds the name on the bill, customer number or phone. A short number (up to 7 digits) finds only that bill
  number, on any date. Any bill can be seen and reprinted.
- **Cancel a bill** (owner only, reason required, any date):
  - The bill is kept, marked CANCELLED and still listed. Its number is never used again.
  - The part of the bill that went on the khata is taken off it automatically (a "Bill N cancelled" khata entry).
  - Money paid for the bill at the counter is **given back** to the customer. The app says how much.
  - Money paid towards old dues together with the bill **stays paid**, because it was not for this bill.
  - If the customer still takes some items, the owner makes a new bill.
  - Every cancel is recorded in the audit log. A cancelled bill cannot be "un-cancelled".
- **Purchase history**: the "Bills" button on a customer shows all of that customer's bills.
- **Recent bills** (chosen by the user on 2026-10-03): the Billing screen's "Recent bills (F9)" button (it replaced
  "Reprint bill") opens a list of the **newest 20 bills of any day**, newest first, with the newest already selected,
  so F9 then Enter shows the last bill ready to print. Arrow keys choose an earlier bill. **Cancelled bills are
  listed, marked CANCELLED.** The search box finds older bills by bill number, name or phone. From the list a bill
  can be seen and printed (anyone), corrected or cancelled (owner).
- **Correct a bill** (owner only, chosen by the user on 2026-10-03). Saved bills are never edited, so "Correct bill"
  is: cancel the bill with a reason (exactly like "Cancel a bill"; the reason is filled in as "Bill corrected" and
  can be changed), then its customer and items open on the Billing screen to be changed and saved as a **new bill
  with a new number**.
  - Items keep the rate charged on the old bill. Name, Marathi name and MRP come from today's product list, so a rate
    that differs from today's list shows as a changed rate (and is recorded as a rate change when saved).
  - A walk-in name comes back; a khata customer comes back unless switched off (then the owner is told).
  - The payment is entered again when the new bill is saved: the money paid for the old bill goes with it, so the
    customer does not pay twice.
  - A bill already open on the Billing screen is put on hold first, so nothing is lost.
- **Reports** (owner only): one day or a date range (up to one year), with Today, Yesterday, This month and Last
  month shortcuts. They show the number of bills, total sales, credit given on khata, and money received split
  into Cash, UPI and Card: money paid at billing plus khata payments received that day. Cancelled bills are
  shown separately and are not counted in sales or money received.

## Backups and activity log (built in Phase 7)

These rules were chosen as sensible defaults and can be changed:

- **Automatic backups** on this laptop: one file per day, made when the app opens and brought up to date when it
  closes. Daily backups are kept for 30 days; older ones only as the last backup of each month, for 12 months.
- **Back up now**: Settings > Backups copies the data to any folder, for example a pendrive.
- **Restore a backup**: Settings > Backups. The app shows when the backup was saved and how many bills it has, asks
  to confirm, then closes. The backup is put in place when the app is opened again. The data it replaces is never
  deleted; it is kept in the backups folder as "replaced-...", so a restore can be undone.
- **Damaged data file**: when the app opens, it checks the data file. If it is damaged, the app offers to restore the
  newest good backup (the damaged file is kept).
- **Activity log** (History & Reports): every change to rates, bills, products, customers, khata corrections,
  settings and backups, with date, time and who did it. Records cannot be changed or deleted.

## Assumptions

These were not asked directly. The user can change them at any time.

- Each product has one selling rate for every customer.
- A customer needs a name. Phone is optional but must be unique if given. Each customer gets an automatic number such as C0001. Address and notes are optional.
- Walk-in bills must be fully paid. Only account customers can take credit. The billing screen has a quick "add customer" button.
- At billing, the amount paid cannot be more than the bill total. Extra money against old dues is recorded through "Receive payment".
- A negative balance means the customer has paid in advance. It is shown as "Advance Rs X".
- The app warns before closing if any bills are on hold.

## Permissions

Because there is no login, the app always works as the OWNER and everything below is available at the counter.
The STAFF column is kept in the code (services still check roles), so helper accounts could be added later if
the shop ever wants them.

| Action | OWNER | STAFF |
|---|---|---|
| Make bills, add one-off items, change a line rate | Yes | Yes |
| Add and edit customers, receive payments | Yes | Yes |
| View bill history, reprint bills | Yes | Yes |
| Add, edit, delete or import products | Yes | No |
| Cancel bills | Yes | No |
| Reports | Yes | No |
| Settings, users, backup and restore | Yes | No |

## Inputs still needed

- **Printer brand and model**: needed to test a real printout on the store printer. The owner types the shop
  details in Settings; please confirm the exact wording.
- **Before Phase 8 (go-live)**: store laptop Windows version and RAM; the product Excel sheet
  (it can be imported any time, following [product-import-guide.md](product-import-guide.md)).
