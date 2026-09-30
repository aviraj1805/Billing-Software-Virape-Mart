package com.virpemart.billing.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.virpemart.billing.model.AuditEntry;
import com.virpemart.billing.model.PaperSize;
import com.virpemart.billing.model.PrintAfterSave;
import com.virpemart.billing.model.PrinterSetup;

class AuditServiceTest {

    private static final LocalDate SEP_30 = LocalDate.of(2026, 9, 30);

    @TempDir
    Path temp;

    private TestFixture fixture;
    private AuditService audit;

    @BeforeEach
    void setUp() {
        fixture = new TestFixture(temp);
        audit = fixture.services.audit();
        fixture.services.products().create(new ProductInput("Sugar", null, null, "kg", null, "44", null));
        fixture.services.settings().savePrinterSetup(new PrinterSetup(null, PaperSize.A4, PrintAfterSave.ASK));
    }

    @Test
    void newestRecordsComeFirstWithWhoDidIt() {
        List<AuditEntry> entries = audit.search(SEP_30, SEP_30, null, 100);

        assertEquals(List.of("PRINTER_SETTINGS_CHANGED", "PRODUCT_CREATED"),
                entries.stream().map(AuditEntry::action).toList());
        assertEquals("Owner", entries.getFirst().userName());
    }

    @Test
    void recordsCanBeSearchedAndLimitedToDates() {
        assertEquals(1, audit.search(null, null, " sugar ", 100).size());
        assertEquals(1, audit.search(null, null, "printer", 100).size(), "the action code also matches");
        assertEquals(0, audit.search(SEP_30.plusDays(1), null, null, 100).size());
        assertEquals(1, audit.search(null, null, null, 1).size(), "limit");
    }

    @Test
    void onlyTheOwnerSeesTheLogAndDatesMustBeInOrder() {
        assertEquals("from", assertThrows(ValidationException.class,
                () -> audit.search(SEP_30, SEP_30.minusDays(1), null, 10)).field());

        fixture.signInStaff();
        assertThrows(PermissionDeniedException.class, () -> audit.search(null, null, null, 10));
    }
}
