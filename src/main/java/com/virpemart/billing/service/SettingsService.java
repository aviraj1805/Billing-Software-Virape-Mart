package com.virpemart.billing.service;

import java.time.Clock;
import java.util.List;
import java.util.Map;

import com.virpemart.billing.db.Database;
import com.virpemart.billing.db.DbTime;
import com.virpemart.billing.model.PaperSize;
import com.virpemart.billing.model.PrintAfterSave;
import com.virpemart.billing.model.PrinterSetup;
import com.virpemart.billing.model.ShopDetails;
import com.virpemart.billing.model.User;
import com.virpemart.billing.repository.AuditRepository;
import com.virpemart.billing.repository.SettingsRepository;

/**
 * Shop details for the bill heading and printer settings. Everyone can read them; only the owner can change them.
 * Every change is written to the audit log.
 */
public final class SettingsService {

    static final String SHOP_NAME = "shop.name";
    static final String SHOP_SECOND_LINE = "shop.second_line";
    static final String SHOP_ADDRESS = "shop.address";
    static final String SHOP_PHONE = "shop.phone";
    static final String SHOP_FOOTER = "shop.footer";
    static final String PRINTER_NAME = "printer.name";
    static final String PRINTER_PAPER = "printer.paper";
    static final String PRINTER_AFTER_SAVE = "printer.after_save";

    /** Shown until the owner saves the real shop details. */
    static final ShopDetails DEFAULT_SHOP = new ShopDetails("Virpe Mart", null, List.of(), null,
            List.of("Thank you! Please visit again."));

    private static final int MAX_LINE_LENGTH = 60;
    private static final int MAX_ADDRESS_LINES = 3;
    private static final int MAX_FOOTER_LINES = 2;
    private static final int MAX_PHONE_LENGTH = 40;

    private final Database database;
    private final SettingsRepository settings;
    private final AuditRepository audit;
    private final Session session;
    private final Clock clock;

    public SettingsService(Database database, SettingsRepository settings, AuditRepository audit, Session session,
                           Clock clock) {
        this.database = database;
        this.settings = settings;
        this.audit = audit;
        this.session = session;
        this.clock = clock;
    }

    // ------------------------------------------------------------------ shop details

    /** The saved shop details, or the defaults if the owner has not saved any yet. */
    public ShopDetails shopDetails() {
        session.requireSignedIn();
        Map<String, String> saved = database.query(settings::all);
        String name = saved.get(SHOP_NAME);
        if (name == null) {
            return DEFAULT_SHOP;
        }
        return new ShopDetails(name, Texts.clean(saved.get(SHOP_SECOND_LINE)), Texts.lines(saved.get(SHOP_ADDRESS)),
                Texts.clean(saved.get(SHOP_PHONE)), Texts.lines(saved.get(SHOP_FOOTER)));
    }

    /**
     * Checks typed shop details without saving them. Used by the form for the preview and by
     * {@link #saveShopDetails}.
     *
     * @throws ValidationException naming the field: name, secondLine, address, phone or footer
     */
    public static ShopDetails check(String name, String secondLine, String address, String phone, String footer) {
        String cleanName = Texts.clean(name);
        if (cleanName == null) {
            throw new ValidationException("name", "Please type the shop name.");
        }
        checkLength("name", "The shop name", cleanName);
        String cleanSecond = Texts.clean(secondLine);
        if (cleanSecond != null) {
            checkLength("secondLine", "The second line", cleanSecond);
        }
        List<String> addressLines = checkLines("address", "The address", address, MAX_ADDRESS_LINES);
        String cleanPhone = Texts.clean(phone);
        if (cleanPhone != null && cleanPhone.length() > MAX_PHONE_LENGTH) {
            throw new ValidationException("phone", "The phone text is too long. Use at most "
                    + MAX_PHONE_LENGTH + " letters.");
        }
        List<String> footerLines = checkLines("footer", "The closing line", footer, MAX_FOOTER_LINES);
        return new ShopDetails(cleanName, cleanSecond, addressLines, cleanPhone, footerLines);
    }

    /** Saves the shop details printed on every bill. Owner only. */
    public ShopDetails saveShopDetails(String name, String secondLine, String address, String phone, String footer) {
        User user = session.requireOwner();
        ShopDetails details = check(name, secondLine, address, phone, footer);
        database.runInTransaction(c -> {
            settings.put(c, SHOP_NAME, details.name());
            settings.put(c, SHOP_SECOND_LINE, orEmpty(details.secondLine()));
            settings.put(c, SHOP_ADDRESS, String.join("\n", details.addressLines()));
            settings.put(c, SHOP_PHONE, orEmpty(details.phone()));
            settings.put(c, SHOP_FOOTER, String.join("\n", details.footerLines()));
            audit.insert(c, user.id(), "SHOP_DETAILS_CHANGED", "settings", null,
                    "Shop name: " + details.name(), DbTime.now(clock));
        });
        return details;
    }

    // ------------------------------------------------------------------ printer

    /** The saved printer settings, or the defaults if the owner has not saved any yet. */
    public PrinterSetup printerSetup() {
        session.requireSignedIn();
        Map<String, String> saved = database.query(settings::all);
        return new PrinterSetup(
                Texts.clean(saved.get(PRINTER_NAME)),
                parse(PaperSize.class, saved.get(PRINTER_PAPER), PrinterSetup.DEFAULT.paper()),
                parse(PrintAfterSave.class, saved.get(PRINTER_AFTER_SAVE), PrinterSetup.DEFAULT.afterSave()));
    }

    /** Saves which printer prints bills and when. Owner only. */
    public PrinterSetup savePrinterSetup(PrinterSetup setup) {
        User user = session.requireOwner();
        if (setup.paper() == null) {
            throw new ValidationException("paper", "Please choose the paper in the printer.");
        }
        if (setup.afterSave() == null) {
            throw new ValidationException("afterSave", "Please choose what happens after a bill is saved.");
        }
        PrinterSetup clean = new PrinterSetup(Texts.clean(setup.printerName()), setup.paper(), setup.afterSave());
        database.runInTransaction(c -> {
            settings.put(c, PRINTER_NAME, orEmpty(clean.printerName()));
            settings.put(c, PRINTER_PAPER, clean.paper().name());
            settings.put(c, PRINTER_AFTER_SAVE, clean.afterSave().name());
            audit.insert(c, user.id(), "PRINTER_SETTINGS_CHANGED", "settings", null,
                    "Printer: " + (clean.printerName() == null ? "Windows default" : clean.printerName())
                            + ", paper: " + clean.paper().name() + ", after save: " + clean.afterSave().name(),
                    DbTime.now(clock));
        });
        return clean;
    }

    // ------------------------------------------------------------------ helpers

    private static void checkLength(String field, String what, String text) {
        if (text.length() > MAX_LINE_LENGTH) {
            throw new ValidationException(field, what + " is too long for the bill. Use at most "
                    + MAX_LINE_LENGTH + " letters per line.");
        }
    }

    private static List<String> checkLines(String field, String what, String text, int maxLines) {
        List<String> lines = Texts.lines(text);
        if (lines.size() > maxLines) {
            throw new ValidationException(field, what + " can have at most " + maxLines + " lines.");
        }
        for (String line : lines) {
            checkLength(field, what, line);
        }
        return lines;
    }

    private static String orEmpty(String text) {
        return text == null ? "" : text;
    }

    private static <E extends Enum<E>> E parse(Class<E> type, String text, E fallback) {
        if (text == null) {
            return fallback;
        }
        try {
            return Enum.valueOf(type, text);
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }
}
