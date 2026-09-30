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
| Savings | The bill shows "You saved Rs X" compared to MRP. |
| Discount | No whole-bill discount. |
| Payment modes | Cash, UPI and Card. One bill can be split across several modes. |
| Credit | An account customer can pay any amount at billing, including zero. The rest is added to their account. There is no credit limit. |
| Returns and mistakes | The owner cancels the whole bill with a reason and makes a new bill. Any credit from the cancelled bill is reversed automatically. |
| Bill numbers | Continuous: 1, 2, 3 and so on. Never reset and never reused, even for cancelled bills. |
| Hold bill | A bill can be put on hold to serve another customer, then resumed. Held bills are kept in memory only. |
| Users | The father logs in as OWNER. A helper logs in as STAFF. |
| Reports | Daily sales summary with the Cash, UPI and Card split. Sales for a date range. |
| Existing product list | Kept in Excel or Google Sheets, so the app provides a CSV import. |
| Backups | Kept on the store laptop only. The user accepted the risk of theft or disk failure. |
| Laptops | Developed on one laptop. The packaged app is installed on a different store laptop. |
| Printer | Model not known yet. Printing must work for both thermal receipt printers and normal A4 printers. |

## Assumptions

These were not asked directly. The user can change them at any time.

- Each product has one selling rate for every customer.
- A customer needs a name. Phone is optional but must be unique if given. Each customer gets an automatic number such as C0001. Address and notes are optional.
- Walk-in bills must be fully paid. Only account customers can take credit. The billing screen has a quick "add customer" button.
- At billing, the amount paid cannot be more than the bill total. Extra money against old dues is recorded through "Receive payment".
- A negative balance means the customer has paid in advance. It is shown as "Advance Rs X".
- The app warns before closing if any bills are on hold.

## Permissions

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

- **Before Phase 5 (printing)**: printer brand and model; shop name, address, phone and footer text for the bill.
- **Before Phase 8 (go-live)**: store laptop Windows version and RAM; the product Excel sheet.
