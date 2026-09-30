# Virpe Mart Billing — Troubleshooting

Use this page when the app does not start, shows an error, or prints wrongly.

**Getting help from Claude chat or another AI assistant:** start a new chat and attach (or paste) this file,
[INSTALLATION.md](INSTALLATION.md) and [USER_GUIDE.md](USER_GUIDE.md). Then describe the problem using the
checklist in section 1. Section 4 gives the assistant the technical facts it needs.

---

## 1. What to collect before asking for help

1. **What you did** and **what you expected**, in a sentence or two.
2. **The exact message** on screen. Take a photo or screenshot (Windows key + Shift + S).
3. **App version**: shown at the bottom left of the app, for example "Version 1.0.0".
4. **Windows version**: press Windows key + R, type `winver`, press Enter.
5. **The log file**, if the app wrote one:
   - press Windows key + R, paste `%LOCALAPPDATA%\VirpeMart\logs`, press Enter,
   - open `virpemart.log` with Notepad and copy the **last 50 lines** (older days are in
     `virpemart.YYYY-MM-DD.log`).
   The log contains no passwords. It may contain product and customer names.

---

## 2. Safety rules while fixing anything

- **Never delete or edit** anything in `%LOCALAPPDATA%\VirpeMart\data`. That is the shop's data.
- Before trying a fix, **copy the whole folder** `%LOCALAPPDATA%\VirpeMart` to a pendrive (close the app first).
- Deleting and re-extracting the program folder `C:\VirpeMart` is always safe; it holds no data.
- Never open `virpemart.db` in another program and change it. Saved bills and khata entries are protected and
  must only be changed through the app.

---

## 3. Problems and fixes

### Starting the app

| What you see | Why | What to do |
|---|---|---|
| Blue box **"Windows protected your PC"** | The app is new and not signed by a big company. | Click **More info**, then **Run anyway**. Only needed once. |
| Double-clicking does nothing, or a window flashes and closes | Usually the program was started from **inside the zip**, or files are missing. | Extract the zip to `C:\` first (INSTALLATION step 3) and start `C:\VirpeMart\VirpeMart.exe`. Check that the folders `app` and `runtime` are next to it. If an antivirus removed files, restore them from its quarantine, add `C:\VirpeMart` as an exception, and extract again. |
| **"Virpe Mart Billing is already open. Look for it on the taskbar…"** | Only one copy may run at a time, to protect the data. | Click the app on the taskbar. If it is not there: open Task Manager (Ctrl + Shift + Esc), end **VirpeMart**, and start again. |
| **"The app could not create its data folder. Please check that the disk is not full."** | The disk is full, or Windows blocks the folder. | Free some disk space. Make sure you are using a normal Windows account (not a guest account). |
| **"The app could not start because its data folder is not accessible."** | Another program (often backup/sync software or an antivirus) is locking the folder. | Restart the laptop and try again. Exclude `%LOCALAPPDATA%\VirpeMart` from sync and antivirus scanning. |
| **"The app could not open its database. Nothing was changed."** plus a reason | See the reason under the message. | Common reasons are below. Always collect the log (section 1). |
| … **"This database was created by a newer version of the app … Please install the newer app version."** | The data was used with a newer version, and an older program was installed. | Install the newest zip (INSTALLATION step 10). |
| … **"This database file was not created by Virpe Mart Billing."** | A different file was put in the data folder. | Put the correct `virpemart.db` back, or restore a backup (below). |
| **"The shop data file is damaged and cannot be opened."** with **Restore this backup** | The data file was damaged, for example by a disk problem or a power cut. | Read the backup's date and bill count. Click **Restore this backup**, then open the app again. Bills made after that backup must be entered again from the paper book. The damaged file is kept as `backups\replaced-...db`; keep it and ask for help if important bills are missing. |
| Same message but **"No good backup was found on this laptop"** | No usable backup on the laptop. | Do not delete anything. If you have a pendrive backup, ask for help to restore it by hand (section 4, "Restore by hand"). |
| **"A backup could not be put in place. Close other programs and open the app again."** | A restore was waiting, but a file was locked. | Close other programs (and any open folder windows of `VirpeMart`), then open the app again. Nothing was deleted. |
| First start is slow (10–20 s) | The app unpacks its screen drivers the first time. | Normal. Later starts are faster. |

### While using the app

| What you see | Why | What to do |
|---|---|---|
| **"Something went wrong, but your saved bills are safe."** | An unexpected error (a bug). | Click OK and try again. If it repeats, close and reopen the app, then send the log (section 1) and the steps that cause it. |
| An orange line **"Bill N is saved but was NOT printed"** and **"Printing did not work…"** | Printer off, no paper, cable loose, or wrong printer chosen. The bill **is saved**. | Fix the printer, then **Billing → Reprint bill** with that number. |
| **"The printer "…" was not found in Windows."** | The printer chosen in Settings was removed or renamed in Windows. | Install the printer driver again, or choose the printer again in **Settings → Bill printer**, then **Save printer settings**. |
| **"Windows has no default printer."** | No printer is installed or set as default. | Install the printer (INSTALLATION step 7) or choose a printer in Settings. |
| Printed bill is cut off, too small, shifted, or too long | The printer or paper width is not set up yet for this printer model. | Check **Settings → Paper** matches the roll (58 mm or 80 mm). Print a test bill and send a photo of it plus the printer model (section 4, "Printing"). |
| Marathi letters show as empty boxes | The Windows font "Nirmala UI" is missing (very old Windows). | Use Windows 10 or 11. |
| Screen text is cut off or windows are too big | Windows display scaling is very high on a small screen. | Windows Settings → Display → Scale: try 100% or 125%. |
| Wrong date or time on bills | The laptop clock or time zone is wrong. The app uses the laptop's clock. | Windows Settings → Time & language → set the time zone to **(UTC+05:30) Chennai, Kolkata, Mumbai, New Delhi** and turn on "Set time automatically". Bills already saved keep their time. |
| Excel import shows errors | The sheet was not saved as CSV UTF-8, or column names are unusual. | Follow [product-import-guide.md](product-import-guide.md). Rows with errors are shown with the reason; fix them in Excel and import again. Existing products are never changed by an import. |
| A khata balance looks wrong | A payment or opening due was typed wrongly. | Open **Customers**, check the khata entries. Use **Correct balance** with a reason. |
| A bill is wrong | Saved bills cannot be edited. | **History & Reports → Bills → Cancel bill…** with a reason, then make a new bill. |
| Need an old copy of the data | For example after a big mistake. | **Settings → Backups → Restore a backup…** (see the User guide). The current data is kept, so this can be undone. |

---

## 4. Technical facts (for the person or AI helping)

### The program

- Java 25 + JavaFX 25 desktop app, packaged with `jpackage` as a Windows **app-image** with its own Java runtime.
  No installer, no Java needed on the laptop, no internet, no network ports.
- Folder layout after extracting: `C:\VirpeMart\VirpeMart.exe` (launcher), `app\` (the app's jar files and
  `VirpeMart.cfg`), `runtime\` (the bundled Java, including the Microsoft C++ runtime DLLs), `Guides\`.
- Source code: <https://github.com/aviraj1805/Billing-Software-Virape-Mart>. Build and rules for developers:
  `README.md` and `CLAUDE.md` in the repository.
- The launcher runs `com.virpemart.billing.Launcher` with `--enable-native-access=ALL-UNNAMED`.
- JavaFX unpacks its native DLLs on first start into `%USERPROFILE%\.openjfx\cache`. Deleting that folder is safe;
  it is recreated.

### Data folder

`%LOCALAPPDATA%\VirpeMart` (for example `C:\Users\<name>\AppData\Local\VirpeMart`):

| Path | Meaning |
|---|---|
| `data\virpemart.db` | The SQLite database (WAL mode, so `virpemart.db-wal` and `-shm` may exist while the app runs) |
| `data\app.lock` | Single-instance lock. Released automatically when the app ends; safe to delete only when the app is closed. |
| `data\restore-pending.db`, `restore-pending.txt` | A restore waiting for the next start |
| `backups\auto-YYYY-MM-DD.db` | Automatic daily backup: made at start, updated at close; 30 days + last of each month for 12 months |
| `backups\VirpeMart-backup-YYYY-MM-DD-HHMMSS.db` | "Back up now" copies (usually on a pendrive) |
| `backups\pre-upgrade-vN-to-vM-*.db` | Made before a schema upgrade |
| `backups\replaced-YYYY-MM-DD-HHMMSS.db` | Data that a restore replaced (kept on purpose) |
| `logs\virpemart.log` | Today's log; older days `virpemart.YYYY-MM-DD.log`, kept 30 days |

The environment variable `VIRPEMART_DATA_DIR` (or `-Dvirpemart.dataDir=...`) makes the app use another folder; the
top bar then shows "DEVELOPMENT DATA". It must **not** be set on the shop laptop.

### How the app behaves

- **No login** (the owner's decision). At every start the app signs in the first active OWNER account; a new
  database gets an account called "Owner". The Activity log records actions under that name.
- **Start-up order**: create folders → start logging → single-instance lock → finish a waiting restore → SQLite
  `quick_check` of the data file (damaged → offer newest good backup) → schema upgrade with a backup first →
  sign in → automatic backup.
- **Money** is stored in paise and quantities in thousandths (0.250 kg = 250), so there are no rounding errors.
- **Saved bills, bill lines, payments, khata entries and audit records cannot be changed or deleted**; database
  triggers enforce this. The only allowed change to a bill is FINAL → CANCELLED with a reason.
- **Customer balance** = sum of the khata entries (there is no stored balance to fix).

### Restore by hand (only if the app cannot start and has no good backup)

1. Close the app. Copy the whole `%LOCALAPPDATA%\VirpeMart` folder to a pendrive as a safety copy.
2. Copy the good backup file (for example `VirpeMart-backup-....db` from the pendrive) into
   `%LOCALAPPDATA%\VirpeMart\data\` and rename it to exactly `restore-pending.db`.
3. Start the app. It moves the current data to `backups\replaced-...db` and puts the backup in place.

### Printing

- Bills are drawn with Java 2D using the Windows font "Nirmala UI" and sent through the printer's Windows driver.
- Paper setting: 58 mm roll (48 mm printed width), 80 mm roll (72 mm printed width) or A4 (150 mm column).
  For rolls the app asks the driver for a page exactly as long as the bill.
- **Not yet tuned for a specific printer model.** When a real printer is available, report: printer brand and
  model, paper width, a photo of the test bill, and what is wrong (horizontal position, top margin, width,
  text wrapping, spacing, cut position). The layout code is in `print/ReceiptRenderer.java` and
  `print/SystemReceiptPrinter.java`.
- A quick check without paper: install nothing; choose the printer **Microsoft Print to PDF** in Settings, print a
  test bill, save the PDF and open it. It shows exactly what the printer receives.
