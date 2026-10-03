# Installing Virpe Mart Billing

This guide installs the billing software on a Windows laptop that has **nothing technical installed**.
You do **not** need Java, Maven, Git or anything else. Everything the app needs is inside one zip file.

Time needed: about 10 minutes.

---

## 1. What the laptop needs

| Need | Details |
|---|---|
| Windows | Windows 10 or Windows 11, **64-bit** (almost every laptop sold after 2015) |
| Free disk space | About 500 MB |
| Memory (RAM) | 4 GB or more |
| Screen | 1366 × 768 or larger |
| Internet | Not needed. The app works fully offline. |
| Printer | Optional. Install its Windows driver first (see step 7). |

To check the Windows version: press the **Windows key + R**, type `winver`, press **Enter**.

---

## 2. Get the zip file

The app comes as one file named like **`VirpeMart-1.1.0-Windows.zip`** (about 70 MB).

Get it in one of these ways:

- **From GitHub:** open <https://github.com/aviraj1805/Billing-Software-Virape-Mart/releases>, click the newest
  release, and under **Assets** click the `VirpeMart-...-Windows.zip` file to download it.
- **From a pendrive:** copy the zip from the pendrive to the laptop's **Downloads** folder.

> The zip is made on the developer laptop with
> `powershell -ExecutionPolicy Bypass -File scripts\package.ps1`
> and is written to `target\dist\`. See "For the developer" at the end.

---

## 3. Unzip it to `C:\`

1. Open the **Downloads** folder (or wherever the zip is).
2. **Right-click** the zip file and choose **Extract All…**.
3. In the box that asks where to extract, type exactly:

   ```
   C:\
   ```

4. Click **Extract**. If Windows asks for permission, click **Continue** / **Yes**.
5. You now have a folder **`C:\VirpeMart`** containing `VirpeMart.exe`, a `Guides` folder, and two folders named
   `app` and `runtime`.

**Important:**

- Do **not** double-click `VirpeMart.exe` while you are still *inside* the zip. It will not work there.
  Always extract first.
- Do not move or delete the `app` and `runtime` folders. The program needs them.

---

## 4. Start the app the first time

1. Open `C:\VirpeMart`.
2. Double-click **`VirpeMart.exe`**.
3. The first time, Windows may show a blue box **"Windows protected your PC"**. This happens because the app is
   new and not from a big company. It is safe:
   - click **More info**,
   - then click **Run anyway**.

   This question comes only once.
4. The first start can take 10 to 20 seconds. Later starts are faster.
5. The app opens full screen on the **Billing** page. At the top right it shows **Owner** and today's date.
   There is no password or login.

If something else happens, see [TROUBLESHOOTING.md](TROUBLESHOOTING.md).

---

## 5. Put a shortcut on the Desktop

1. Open `C:\VirpeMart`.
2. **Right-click** `VirpeMart.exe`.
   - On Windows 11: click **Show more options**, then **Send to**, then **Desktop (create shortcut)**.
   - On Windows 10: click **Send to**, then **Desktop (create shortcut)**.
3. On the Desktop, right-click the new shortcut, choose **Rename**, and type `Virpe Mart`.

The shortcut shows the app's logo: a white **वि** on a dark green square. From now on, open the app by
double-clicking **Virpe Mart** on the Desktop. Tip: right-click the shortcut and choose **Pin to taskbar** too.

---

## 6. First-time setup inside the app (once)

Do these in order. The [User guide](USER_GUIDE.md) explains each screen in detail.

1. **Settings → Shop details on the bill**: type the shop name, address, phone and closing line exactly as they
   should appear on the bill. Click **Save shop details**.
2. **Products → Import from Excel…**: load the product list. Follow
   [product-import-guide.md](product-import-guide.md). You can also add products one by one with **+ Add product**.
3. **Customers → + Add customer**: add each khata customer. In **Old dues from paper khata** type what they owe
   today in the paper book.
4. **Settings → Bill printer**: see the next step.
5. Make one practice bill and check it in **History & Reports**.

---

## 7. Printer (when you have one)

1. Install the printer's **Windows driver** first, from the CD in the box or the printer maker's website.
   Print a Windows test page to be sure it works.
2. In the app open **Settings → Bill printer**:
   - **Printer**: choose your printer (or leave "Windows default printer"),
   - **Paper**: 58 mm roll, 80 mm roll, or A4 sheet,
   - click **Print a test bill** and check the paper,
   - click **Save printer settings**.

If the lines do not fit the paper or the position is wrong, note exactly what you see (take a photo) and
share it together with [TROUBLESHOOTING.md](TROUBLESHOOTING.md).

---

## 8. Where the shop's data is kept

The program folder `C:\VirpeMart` contains **only the program**. The shop's data (bills, khata, products,
backups) is kept separately, in the Windows user's own folder:

```
%LOCALAPPDATA%\VirpeMart
```

To open it: press **Windows key + R**, paste `%LOCALAPPDATA%\VirpeMart`, press **Enter**. Inside:

| Folder | What is in it |
|---|---|
| `data` | `virpemart.db` — all the shop's data. **Never delete or edit this file.** |
| `backups` | Automatic daily backups (`auto-YYYY-MM-DD.db`) and other backups |
| `logs` | `virpemart.log` — a diary of what the app did, useful when something goes wrong |

Because the data is separate, you can update or reinstall the program without losing anything.

**Each Windows user account has its own data.** Always use the app from the same Windows account.

---

## 9. Backups (please do this weekly)

The app makes a backup on the laptop every day by itself. If the laptop is lost, stolen or its disk fails, those
backups are lost too. So once a week:

1. Plug in a pendrive.
2. In the app: **Settings → Backups → Back up now to a folder or pendrive…**
3. Choose the pendrive and click **Select Folder**. The app says "Backup saved: …".

---

## 10. Installing a newer version

1. Make a pendrive backup (step 9) to be extra safe.
2. **Close the app.**
3. Delete the old program folder `C:\VirpeMart` (this does **not** touch the shop's data).
4. Unzip the new zip to `C:\` exactly as in step 3.
5. Start the app. If the new version needs to upgrade the data, it first saves a backup
   (`backups\pre-upgrade-...db`) and then upgrades by itself.

The Desktop shortcut keeps working if the new folder is again `C:\VirpeMart`.

---

## 11. Moving to another laptop

1. On the old laptop: **Settings → Backups → Back up now…** to a pendrive. Close the app.
2. On the new laptop: install the app (steps 2 to 5).
3. **Settings → Backups → Restore a backup…**, open the pendrive, choose the `VirpeMart-backup-....db` file,
   check the date and bill count shown, and click **Restore and close**.
4. Open the app again. All bills, khata and products are back. Enter the shop and printer settings if needed.

---

## 12. Removing the app

1. Close the app. Delete `C:\VirpeMart` and the Desktop shortcut.
2. The shop's data stays in `%LOCALAPPDATA%\VirpeMart`. Delete that folder **only** if you are sure the data is
   not needed (make a pendrive backup first).

---

## For the developer: making the zip

On the development laptop (with the project folder and JDK 25):

```powershell
powershell -ExecutionPolicy Bypass -File scripts\package.ps1
```

It runs all tests, builds the app with its own Java inside, copies these guides into `VirpeMart\Guides`, and
writes `target\dist\VirpeMart-<version>-Windows.zip`.

To publish it on GitHub: open the repository page, **Releases → Draft a new release**, create a tag such as
`v1.1.0`, drag the zip into "Attach binaries", and click **Publish release**. Do not commit the zip into the code
(it is too large and is rebuilt every time).
