# Virpe Mart Billing — User guide

This guide is for everyday use at the shop counter. The screens are in English; product names can be in Marathi.

The menu on the left has five pages: **Billing**, **Products**, **Customers**, **History & Reports** and
**Settings**. The app always opens on **Billing**.

---

## A day at the shop

| When | What to do |
|---|---|
| Morning | Double-click **Virpe Mart** on the Desktop. (A backup is made automatically.) |
| All day | Make bills on the **Billing** page. Take khata payments on the **Customers** page. |
| Evening | **History & Reports → Reports → Today**: compare "Cash" with the cash in the drawer. |
| Closing | Close the app with the **X** at the top right. (The day's backup is updated automatically.) |
| Once a week | **Settings → Backups → Back up now…** to a pendrive. |

---

## 1. Making a bill (Billing page)

### Add items

1. Press **F2** (or click the big search box) and type part of the product name — English, Marathi or the
   product code. A list drops down.
2. Use **↓ ↑** to pick the product and press **Enter**.
3. Type the **quantity** and press **Enter**. The item is added to the bill.
   - Loose items (sold per kg or litre): type the weight in kg with a dot. `0.250` = 250 grams, `0.5` = half kg,
     `1.25` = one and a quarter kg. The rate is per kg.
   - Packets / pieces: whole numbers only, like `1`, `2`, `10`.
4. Repeat for every item. Adding the same product again adds to its quantity.

### Change or remove an item

Click the item in the list (or press ↓ from the empty search box), then:

- **Change qty (Enter)** — type a new quantity.
- **Change rate** — a different rate for this bill only. The product's normal rate does not change. The change
  is recorded in the Activity log.
- **Remove (Del)** — takes the item off the bill.

### Item not in the product list

Press **F4** (or **+ Item not in list**). Type the name, how it is sold, the quantity and the rate. It goes on this
bill only; it is **not** added to the product list.

### Who is the customer?

- **Walk-in customer** (pays now): nothing to do. If you want a name printed on the bill, type it in
  **Name on bill (optional)**.
- **Khata customer**: press **F3**, type the name, phone or customer number (like C0005), pick them and press
  **Enter**. Their dues and last bills appear. **Walk-in instead** goes back to a walk-in bill.
- **New khata customer**: click **+ New khata customer**.

The right side always shows the **Bill total** (rounded to the rupee), the saving against MRP, and for a khata
customer the **previous dues** and **total with dues**.

### Save and take payment — F12

Press **F12** (or **Save and pay**). A payment window opens.

- **Walk-in customer** must pay the full bill.
  - Click **All cash**, **All UPI** or **All card**, or type amounts in Cash, UPI and Card to split the payment.
  - Optional: type **Cash given by customer** to see how much change to give back.
- **Khata customer** can pay any amount, even nothing.
  - **Bill amount** fills in just this bill. **Full total with dues** fills in everything owed. **Nothing now**
    puts the whole bill on the khata (the app asks you to confirm).
  - If they pay more than this bill, the extra reduces their old dues.

Press **Enter** (or **Save bill**) to save. **Esc** goes back to the bill without saving.

After saving, the app asks **"Print?"**: **Enter** prints, **Esc** does not print. The top of the page shows a
green line such as "Bill 25 saved · Total ₹120.00 · Paid Cash ₹120.00 · Printed".

If printing fails, the bill is **still saved**. The line turns orange: "saved but NOT printed". Fix the printer
and use **Reprint bill**.

### Hold a bill (serve another customer first)

- **F8** or **Hold bill** puts the current bill aside and starts an empty bill.
- **Held bills (n)** at the top brings a held bill back.
- Held bills are kept only while the app is open. The app warns you if you try to close it with a held or
  unfinished bill.

### Other buttons

- **Clear bill** empties the current bill (it asks first).
- **Reprint bill** asks for a bill number (the newest is filled in) and shows the bill with a **Print** button.
  A reprinted bill says **DUPLICATE COPY**.
- For a khata customer, **double-click** one of their **Recent bills** to see or reprint it.

### Billing keys

| Key | Does |
|---|---|
| F2 | Go to product search |
| F3 | Go to khata customer search |
| F4 | Item not in the list |
| F8 | Hold the bill |
| F12 | Save and pay |
| Enter | Add the item / change quantity of the selected line / save in the payment window |
| Del | Remove the selected line |
| Esc | Close a window without saving |

---

## 2. Customers and khata (Customers page)

- **Search** by name, phone or customer number. Tick **Only customers with dues** to see who owes money.
  The page shows the total dues of the whole shop.
- Click a customer to see their **khata** on the right: every entry with date, amount added, amount paid and
  the running **Balance**. Red means they owe the shop; green means they have paid in advance.

Buttons:

| Button | Use |
|---|---|
| **+ Add customer** (Ctrl+N) | New customer: name, phone, address, notes, and **Old dues from paper khata** (what they owe today in the paper book). |
| **Receive payment** (Ctrl+P) | Customer pays money towards their khata (Cash, UPI or Card). The window shows the balance after payment. |
| **Edit details** | Change name, phone, address or notes. |
| **Correct balance** | Make the software match the paper khata. A reason is required. The correction is a new entry; nothing is erased. |
| **Bills** | All bills of this customer (their purchase history). Double-click a bill to see or reprint it. |
| **Switch off** | Hide a customer who no longer buys. Their khata is kept. They can be switched on again. |

Customers are never deleted.

---

## 3. Products (Products page)

- **Search** by name, Marathi name, pack size or code (Ctrl+F). Choose a category to see only that group.
- **+ Add product** (Ctrl+N): name, Marathi name, category, **Sold by** (kg, litre, or piece/packet), pack size,
  **Rate** and **MRP**.
  - For loose items the rate is **per kg** or **per litre**.
  - Each pack size is its own product: "Sugar" loose, "Sugar 1 kg", "Sugar 5 kg".
  - A warning appears if the rate is above MRP.
- **Edit** (or Enter): change a product. Old bills are not changed; they keep the rate and name they were billed at.
- **Switch off**: the product disappears from billing but stays on old bills. **Show switched-off** shows them.
- **Delete**: only for products that were never billed.
- **Categories…**: add, rename or switch off categories.
- **Import from Excel…**: load many products at once. See [product-import-guide.md](product-import-guide.md).

---

## 4. History & Reports

### Bills tab

- Shows today's bills. Use **Today**, **Yesterday**, **This month**, **All dates**, or pick **From** and **To**.
- The search box finds a customer name, customer number or phone. **Type a bill number** (like `25`) to find that
  bill on any date.
- **See / reprint (Enter)** or double-click shows the bill exactly as printed, with a **Print** button.

### Cancel a bill

Bills can never be edited. If a bill is wrong or the customer returns everything:

1. Select the bill and click **Cancel bill…**.
2. The window shows what will happen:
   - the bill stays in the list, marked **Cancelled**, and its number is never used again,
   - money paid for this bill must be **given back** to the customer (the amount is shown),
   - anything this bill put on the khata is **taken off the khata** automatically,
   - money paid towards *old* dues with this bill stays paid.
3. Type the **reason** and click **Cancel this bill**. (Pressing Enter never cancels by accident.)
4. If the customer still takes some items, make a new bill for them.

### Reports tab

Choose **Today**, **Yesterday**, **This month**, **Last month**, or any dates, then **Show**.

- **Bills** and **Total sales** — cancelled bills are not counted (they are shown separately).
- **Given on khata (credit)** — the part of the bills that went on customers' khata.
- **Money received** — Cash, UPI and Card:
  - **At billing**: paid with bills,
  - **Khata payments**: money received against khata dues,
  - **Total**: what should be in the drawer (Cash) or the bank (UPI, Card).
- **Day by day** — one row per day.

### Activity log tab

Every change is recorded: rate changes on bills, cancelled bills, reprints, product and customer changes, khata
corrections, settings, backups and restores. Records cannot be changed or deleted.

---

## 5. Settings

- **Shop details on the bill**: shop name, second line, address (up to 3 lines), phone, closing line
  (up to 2 lines). The **Preview** on the right shows the bill as it will print. Click **Save shop details**.
- **Bill printer**: printer, paper (58 mm, 80 mm or A4), and what happens after saving a bill
  ("Ask Print?", "Print every bill automatically", or "Do not print"). **Print a test bill** checks it.
  Click **Save printer settings**.
- **Backups**:
  - **Back up now to a folder or pendrive…** — do this weekly.
  - **Restore a backup…** — puts an older copy of the data back. The app shows the backup's date and number of
    bills, asks to confirm, then closes. Open it again to finish. The data it replaced is kept in the backups
    folder, so a restore can be undone. Finish or clear any open bill before restoring.

---

## 6. Good to know

- **Saved bills cannot be changed.** Mistakes are fixed by cancelling the bill and making a new one.
- **Bill numbers** go 1, 2, 3… and are never reused, even for cancelled bills.
- **The khata balance** is always worked out from the khata entries, so it is always correct. The paper khata
  can be kept alongside; use **Correct balance** if they differ.
- **Rounding**: the bill total is rounded to the nearest rupee.
- **The laptop's date and time** are printed on every bill. Keep the Windows clock correct (India time zone).
- **There is no password.** Anyone at the laptop can use every page. Everything is recorded in the Activity log.
- **If the app shows a message**, read it: it says what to do next. If something looks wrong, see
  [TROUBLESHOOTING.md](TROUBLESHOOTING.md).
