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
   product code. A list drops down (up to 12 products at a time; scroll for more).
2. Use **↓ ↑** to pick the product and press **Enter**. **Page Down / Page Up** jump 11 rows. With the mouse, scroll
   with the wheel or the scroll bar and **click** the product. **Esc** closes the list.
3. Type the **quantity** and press **Enter**. The cursor moves to the **Rate** box (₹), which already shows the
   product's normal rate.
   - Loose items (sold per kg or litre): type the weight in kg with a dot. `0.250` = 250 grams, `0.5` = half kg,
     `1.25` = one and a quarter kg. The rate is per kg.
   - Packets / pieces: whole numbers only, like `1`, `2`, `10`.
   - The quantity shows with its unit, on the screen and on the printed bill: `2 kg`, `5 Ltr`, `3 Pkt`.
4. Press **Enter** again to add the item at that rate. To charge a different rate on this bill, just type the new
   rate (it replaces the old one) and press **Enter**. The product's normal rate does **not** change; the line shows
   the rate with a `*`, and the change is recorded in the Activity log.
5. Repeat for every item. Adding the same product again at its normal rate adds to its quantity.

### Change or remove an item

Click the item in the list (or press ↓ from the empty search box), then:

- **Change qty (Enter)** — type a new quantity.
- **Change rate** — change the rate of an item already on the bill, for this bill only. The product's normal rate
  does not change. The change is recorded in the Activity log.
- **Remove (Del)** — takes the item off the bill.

### Money paid, no items: जमा

When a khata customer comes **only to pay** (no shopping):

1. Choose the customer (**F3**).
2. In the product search type **जमा** (or **jama**) and press **Enter**. The line **जमा** is added at once: it has no
   quantity, rate or amount, and the bill total stays **₹0**.
3. Press **F12** and type the money received in **Cash / UPI / Card**, then **Enter**.

The printed bill shows **1. जमा**, then **मागील बाकी** (dues before), **जमा** (money paid) and **एकूण बाकी** (dues
left). The payment is in the customer's khata like any payment. जमा can also be added to a bill with items; it adds
nothing to the total. Its rate cannot be changed: always type the money in the payment window.

### Item not in the product list

Press **F4** (or **+ Item not in list**). Type the name, how it is sold, the quantity and the rate. It goes on this
bill only; it is **not** added to the product list.

### Who is the customer?

- **Walk-in customer** (pays now): nothing to do. If you want a name printed on the bill, type it in
  **Name on bill (optional)**.
- The printed bill shows the customer's name **exactly as it is saved** (a name saved in Marathi prints in Marathi)
  and, for a khata customer, their phone number. To change how a name prints, edit it in **Customers**.
- **Khata customer**: press **F3**, type the name, phone or customer number (like C0005), pick them and press
  **Enter**. Their dues and last bills appear. **Walk-in instead** goes back to a walk-in bill.
- **New khata customer**: click **+ New khata customer**.

The right side always shows the **Bill total** (rounded to the rupee), and for a khata customer the
**previous dues** and **total with dues**.

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
green line such as "Bill 25 saved · Total ₹120 · Paid Cash ₹120 · Printed".

If printing fails, the bill is **still saved**. The line turns orange: "saved but NOT printed". Fix the printer
and print it from **Recent bills (F9)**.

### Hold a bill (serve another customer first)

- **F8** or **Hold bill** puts the current bill aside and starts an empty bill.
- **Held bills (n)** at the top brings a held bill back.
- Held bills are kept only while the app is open. The app warns you if you try to close it with a held or
  unfinished bill.

### Other buttons

- **Clear bill** empties the current bill (it asks first).
- For a khata customer, **double-click** one of their **Recent bills** to see or reprint it.

### Recent bills (F9): see, print, send on WhatsApp or correct an earlier bill

Press **F9** (or click **Recent bills (F9)** at the top). A list of the newest 20 bills opens, newest at the top and
already chosen.

- **See / print (Enter)**: shows the bill exactly as printed, with a **Print** button. So **F9 then Enter** brings
  up the last bill. Use the **arrow keys** to choose the 2nd, 3rd or any earlier bill. A bill printed again looks
  exactly like the first print (no "duplicate" mark).
- **Older bill**: just type its bill number (or the customer's name or phone); the list shows the matching bills.
- **Send on WhatsApp**: sends the chosen bill to the customer as a photo.
  1. Click **Send on WhatsApp**. The app copies the bill as a picture and opens WhatsApp.
  2. In WhatsApp, search the customer's name and open their chat.
  3. Press **Ctrl+V**: the bill photo appears. Press **Enter** to send it.

  The photo looks exactly like the printed bill. Nothing is printed. A cancelled bill
  cannot be sent. A copy of the photo is also kept in `%LOCALAPPDATA%\VirpeMart\bill-images`.
- **Correct bill…**: for a bill with a mistake (wrong item, wrong quantity, wrong customer). The old bill is
  cancelled (it stays in the list marked **CANCELLED**; its number is never used again) and all its items and its
  customer come back on the Billing screen. Change what is wrong and press **Save and pay (F12)**: it is saved as a
  new bill with a new number. Enter the payment again; the customer does not pay twice. If another bill was open on
  the screen, it is put on hold first (**Held bills**).
- **Cancel bill…**: when the customer returns everything. The app says how much money to give back and takes the
  bill off the khata.
- **Esc** closes the list.

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
| **Download to Excel…** | Saves every customer (switched-off ones too) as a file that opens in Excel: number, name, phone, address, notes, **balance** (dues; a minus amount is an advance), active and the date added. Only a copy: changing the file does not change the app. |
| **+ Add customer** (Ctrl+N) | New customer: name, phone, address, notes, and **Old dues from paper khata** (what they owe today in the paper book). |
| **Receive payment** (Ctrl+P) | Customer pays money towards their khata (Cash, UPI or Card). The window shows the balance after payment. |
| **Edit details** | Change name, phone, address or notes. |
| **Correct balance** | Make the software match the paper khata. A reason is required. The correction is a new entry; nothing is erased. |
| **Bills** | All bills of this customer (their purchase history). Double-click a bill to see or reprint it. |
| **Switch off** | Hide a customer who no longer buys. Their khata is kept. They can be switched on again. |

Customers are never deleted.

---

## 3. Products (Products page)

- **Search** by name, Marathi name, pack size or code (Ctrl+F).
- **+ Add product** (Ctrl+N): name, Marathi name, **Sold by** (kg, litre, or piece/packet), pack size,
  and **Rate**.
  - For loose items the rate is **per kg** or **per litre**.
  - Each pack size is its own product: "Sugar" loose, "Sugar 1 kg", "Sugar 5 kg".
- **Edit** (or Enter): change a product. Old bills are not changed; they keep the rate and name they were billed at.
- **Switch off**: the product disappears from billing but stays on old bills. **Show switched-off** shows them.
- **Delete**: only for products that were never billed.
- **Import from Excel…**: load many products at once. See [product-import-guide.md](product-import-guide.md).
- **Download to Excel…**: saves the whole product list (switched-off products too) as a file that opens in Excel:
  Code, Name, Marathi Name, Unit, Pack Size, Rate and Active. Choose where to save it (for example a pendrive), then
  double-click the file to open it. It is only a copy: changing the file does not change the app.

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
