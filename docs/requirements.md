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
| Rate change on a bill | Owner and helper can change a line's rate for that bill only. The product's saved rate does not change. Every change is recorded with who made it. |
| Round-off | The final total is rounded to the nearest rupee. |
| Savings | The billing screen shows "You saved Rs X" compared to MRP. It is **not printed** on the bill (changed after Phase 5). |
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
| Laptops | Developed on one laptop. The packaged app is installed on a different store laptop. |
| Printer | Model not known yet. Printing works with any printer installed in Windows: 58 mm or 80 mm receipt rolls, or A4 sheets. |

## Products (confirmed in Phase 2)

| Topic | Decision |
|---|---|
| Product codes | Created automatically: P0001, P0002, and so on. Codes never change. |
| Categories | The app starts with a list covering typical grocery items (Rice & Grains, Dal & Pulses, Sugar, Salt & Jaggery, Edible Oil & Ghee, Spices & Masala, and so on). The owner can add, rename and switch off categories. |
| Excel import | Clear, simple column names: Name, Marathi Name, Category, Unit, Pack Size, Rate, MRP. Common alternative names are understood. See [product-import-guide.md](product-import-guide.md). |

These product rules were chosen as sensible defaults and can be changed:

- Two products cannot have the same name, pack size and unit. The app points to the existing product instead.
- A rate above MRP shows a warning, because selling above MRP is not allowed. The owner can still save.
- A product that has never been billed can be deleted. A product on any bill can only be switched off:
  it disappears from billing, but old bills stay correct.
- An import only adds new products. It never changes existing products; to change a rate, edit the product.
  Rows that already exist are skipped. Unknown category names in the sheet create new categories.

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
- Typing 0 in an optional amount (old dues, MRP) means "none".

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
  - Each item prints **only the Marathi name**, then quantity x rate and the amount. No English name, pack size or
    MRP. An item without a Marathi name (for example a one-off item) prints its English name so the line is not blank.
  - Only the bill total is printed, labelled **एकूण**. Subtotal, round off and "You saved" are not printed (they are
    still saved with the bill and shown on screen).
  - A khata bill prints **मागील बाकी** (previous dues), **जमा** (all money paid with the bill, in any mix of Cash,
    UPI and Card, as one line) and **एकूण बाकी** (balance after the bill). An advance prints as a minus amount under
    the same labels. "This bill" and "Total with dues" are never printed.
  - These Marathi labels are the only wording for these lines; no English is printed next to them.
- Loose quantities print with three decimals (0.500 kg).
- Printing happens when the owner presses **Print** (the "Print?" question after saving, or the Print button when
  reprinting).
- Paper fit, centring, margins and spacing are **not tuned for a specific printer yet**. They will be adjusted after the
  printer is bought and a real test print is checked.
- Printing a bill again is marked **"DUPLICATE COPY"** and recorded in the audit log. Anyone can reprint.
- A printer problem never affects the saved bill: the screen says "saved but NOT printed" and the bill can be
  reprinted with "Reprint bill".

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
