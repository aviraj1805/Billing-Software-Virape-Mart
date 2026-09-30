# How to load your product list from Excel

You can add many products at once from an Excel or Google Sheets file.
Nothing is saved until you check the preview and click **Import**.

## 1. Prepare the sheet

The **first row** must have column names. Use these names (or similar ones, see below):

| Column | Needed? | What to write | Example |
|---|---|---|---|
| Name | Yes | Product name in English | Toor Dal |
| Marathi Name | No | Product name in Marathi | तूर डाळ |
| Category | No | Group of the product | Dal & Pulses |
| Unit | Yes | How it is sold: **kg**, **litre** or **pcs** | kg |
| Pack Size | No | Size of the packet, empty for loose items | 1 kg |
| Rate | Yes | Your selling rate. For kg or litre items, the rate **per kg** or **per litre** | 120 |
| MRP | No | MRP printed on the packet | 130 |

Tips:

- **One product per row.** Each pack size is a separate row: "Sugar" loose (kg), "Sugar" 1 kg packet (pcs),
  "Sugar" 5 kg packet (pcs).
- **Unit** also understands kgs, kilo, ltr, liter, pc, piece, nos, pkt and packet.
- **Rate and MRP** can be written as 120, 120.50, ₹120 or Rs 120.
- **Category**: use a name from the app's list, such as Rice & Grains or Dal & Pulses.
  A new name creates a new category automatically.
- Other column names that also work: "Item Name" or "Product" for Name, "Price" or "Selling Price" for Rate,
  "Sold By" for Unit, "Packing" or "Weight" for Pack Size, "Type" for Category.
  Extra columns such as "Sr No" or "Supplier" are simply ignored.

Not sure? In the app, open **Products**, click **Import from Excel…**, then **Save blank template…**.
It saves a ready sheet with the right column names and three examples.

## 2. Save it as "CSV UTF-8"

The app reads CSV files. In Excel:

1. Click **File**, then **Save As**.
2. In "Save as type", choose **CSV UTF-8 (Comma delimited) (\*.csv)**.
3. Click **Save**. If Excel warns about features, click **Yes** to keep the CSV format.

Use exactly **CSV UTF-8**. The plain "CSV" option loses the Marathi names; the app will tell you if this happened.

In Google Sheets: **File**, then **Download**, then **Comma-separated values (.csv)**.

## 3. Import

1. In the app, open **Products** and click **Import from Excel…**.
2. Click **Choose file…** and pick your CSV file.
3. Check the preview:
   - **Will be added**: the product will be saved.
   - **Skipped** (yellow): the product is already in your list, or appears twice in the sheet.
   - **Problem** (red): something must be fixed, for example a rate that is not a number.
     Hover over the row to read the full message.
4. Click **Import**. Rows with problems are not imported. Fix them in Excel and import the same file again:
   products already imported are skipped automatically.

An import only **adds new products**. It never changes the rate of a product that already exists.
To change rates, edit the product in the Products screen.
