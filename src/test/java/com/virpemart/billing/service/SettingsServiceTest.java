package com.virpemart.billing.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Path;
import java.sql.Statement;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.virpemart.billing.model.PaperSize;
import com.virpemart.billing.model.PrintAfterSave;
import com.virpemart.billing.model.PrinterSetup;
import com.virpemart.billing.model.ShopDetails;

class SettingsServiceTest {

    @TempDir
    Path temp;

    private TestFixture fixture;
    private SettingsService settings;

    @BeforeEach
    void setUp() {
        fixture = new TestFixture(temp);
        settings = fixture.services.settings();
    }

    @Test
    void defaultsUntilTheOwnerSavesDetails() {
        assertEquals(SettingsService.DEFAULT_SHOP, settings.shopDetails());
        assertEquals(PrinterSetup.DEFAULT, settings.printerSetup());
        assertEquals(PaperSize.ROLL_80, settings.printerSetup().paper());
        assertEquals(PrintAfterSave.ASK, settings.printerSetup().afterSave());
    }

    @Test
    void shopDetailsAreCleanedSavedAndAudited() {
        settings.saveShopDetails("  Virpe   Mart ", "विरपे मार्ट", "Main Road\n\n  Near Bus Stand \n", " 98765 43210 ",
                "Thank you\nVisit again");

        ShopDetails saved = settings.shopDetails();
        assertEquals("Virpe Mart", saved.name());
        assertEquals("विरपे मार्ट", saved.secondLine());
        assertEquals(List.of("Main Road", "Near Bus Stand"), saved.addressLines(), "blank lines are dropped");
        assertEquals("98765 43210", saved.phone());
        assertEquals(List.of("Thank you", "Visit again"), saved.footerLines());
        assertEquals(1, fixture.count("SELECT COUNT(*) FROM audit_log WHERE action = 'SHOP_DETAILS_CHANGED'"));
    }

    @Test
    void optionalShopFieldsCanBeEmptied() {
        settings.saveShopDetails("Virpe Mart", "x", "Road", "123", "Thanks");
        settings.saveShopDetails("Virpe Mart", " ", "", null, "");

        ShopDetails saved = settings.shopDetails();
        assertNull(saved.secondLine());
        assertEquals(List.of(), saved.addressLines());
        assertNull(saved.phone());
        assertEquals(List.of(), saved.footerLines());
    }

    @Test
    void shopDetailsAreChecked() {
        assertEquals("name", assertThrows(ValidationException.class,
                () -> settings.saveShopDetails(" ", null, null, null, null)).field());
        assertEquals("address", assertThrows(ValidationException.class,
                () -> settings.saveShopDetails("Shop", null, "1\n2\n3\n4", null, null)).field());
        assertEquals("footer", assertThrows(ValidationException.class,
                () -> settings.saveShopDetails("Shop", null, null, null, "a\nb\nc")).field());
        assertEquals("name", assertThrows(ValidationException.class,
                () -> settings.saveShopDetails("x".repeat(61), null, null, null, null)).field());
        assertEquals("phone", assertThrows(ValidationException.class,
                () -> settings.saveShopDetails("Shop", null, null, "9".repeat(41), null)).field());
    }

    @Test
    void onlyTheOwnerChangesSettings() {
        fixture.signInStaff();

        assertThrows(PermissionDeniedException.class,
                () -> settings.saveShopDetails("Shop", null, null, null, null));
        assertThrows(PermissionDeniedException.class, () -> settings.savePrinterSetup(PrinterSetup.DEFAULT));
        assertEquals(SettingsService.DEFAULT_SHOP, settings.shopDetails(), "staff can still read them");
    }

    @Test
    void printerSetupIsSavedAndAudited() {
        settings.savePrinterSetup(new PrinterSetup("  TVS RP 3160 ", PaperSize.ROLL_58, PrintAfterSave.ALWAYS));

        assertEquals(new PrinterSetup("TVS RP 3160", PaperSize.ROLL_58, PrintAfterSave.ALWAYS),
                settings.printerSetup());
        assertEquals(1, fixture.count("SELECT COUNT(*) FROM audit_log WHERE action = 'PRINTER_SETTINGS_CHANGED'"));
    }

    @Test
    void blankPrinterNameMeansTheWindowsDefaultPrinter() {
        settings.savePrinterSetup(new PrinterSetup(" ", PaperSize.A4, PrintAfterSave.NEVER));

        assertNull(settings.printerSetup().printerName());
    }

    @Test
    void printerSetupNeedsPaperAndChoice() {
        assertEquals("paper", assertThrows(ValidationException.class,
                () -> settings.savePrinterSetup(new PrinterSetup(null, null, PrintAfterSave.ASK))).field());
        assertEquals("afterSave", assertThrows(ValidationException.class,
                () -> settings.savePrinterSetup(new PrinterSetup(null, PaperSize.A4, null))).field());
    }

    @Test
    void unknownSavedValuesFallBackToDefaults() {
        fixture.database.runInTransaction(c -> {
            try (Statement s = c.createStatement()) {
                s.executeUpdate("INSERT INTO settings (key, value) VALUES ('printer.paper', 'ROLL_110')");
            }
        });

        assertEquals(PaperSize.ROLL_80, settings.printerSetup().paper());
    }
}
