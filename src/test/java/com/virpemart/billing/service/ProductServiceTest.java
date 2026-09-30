package com.virpemart.billing.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import com.virpemart.billing.model.Category;
import com.virpemart.billing.model.Money;
import com.virpemart.billing.model.Product;
import com.virpemart.billing.model.ProductDetails;
import com.virpemart.billing.model.Unit;

class ProductServiceTest {

    @TempDir
    Path temp;

    private TestFixture fixture;
    private ProductService products;
    private long dalCategory;

    @BeforeEach
    void setUp() {
        fixture = new TestFixture(temp);
        products = fixture.services.products();
        dalCategory = fixture.services.categories().list(false).stream()
                .filter(c -> c.name().equals("Dal & Pulses")).map(Category::id).findFirst().orElseThrow();
    }

    private static ProductInput input(String name, String unit, String packSize, String rate, String mrp) {
        return new ProductInput(name, null, null, unit, packSize, rate, mrp);
    }

    // ------------------------------------------------------------------ adding

    @Test
    void createsProductsWithAutomaticCodes() {
        Product sugar = products.create(new ProductInput("Sugar", "साखर", null, "kg", "", "44", ""));
        Product salt = products.create(input("Tata Salt", "pcs", "1 kg", "28", "30"));

        assertEquals("P0001", sugar.code());
        assertEquals("P0002", salt.code());
        assertEquals("साखर", sugar.nameMr(), "Marathi text is kept exactly");
        assertEquals(Unit.KG, sugar.unit());
        assertEquals(Money.ofRupees(44), sugar.rate());
        assertNull(sugar.mrp());
        assertNull(sugar.packSize(), "blank pack size is stored as nothing");
        assertEquals(Money.ofRupees(30), salt.mrp());
        assertEquals("Tata Salt 1 kg", salt.displayName());
        assertEquals(2, fixture.count("SELECT COUNT(*) FROM audit_log WHERE action = 'PRODUCT_CREATED'"));
    }

    @Test
    void cleansUpTypedText() {
        Product p = products.create(new ProductInput("  Toor   Dal ", " तूर डाळ ", dalCategory, " KGS ", " ", "Rs. 1,20.50", "₹ 130"));

        assertEquals("Toor Dal", p.name());
        assertEquals("तूर डाळ", p.nameMr());
        assertEquals(Unit.KG, p.unit());
        assertEquals(Money.ofPaise(12050), p.rate());
        assertEquals(Money.ofRupees(130), p.mrp());
        assertEquals("Dal & Pulses", p.categoryName());
    }

    @ParameterizedTest(name = "{5}")
    @CsvSource({
            "'', kg, 44, '', name, blank name",
            "Sugar, '', 44, '', unit, no unit",
            "Sugar, dozen, 44, '', unit, unknown unit",
            "Sugar, kg, '', '', rate, no rate",
            "Sugar, kg, abc, '', rate, rate not a number",
            "Sugar, kg, 0, '', rate, zero rate",
            "Sugar, kg, -5, '', rate, negative rate",
            "Sugar, kg, 44.555, '', rate, three decimals",
            "Sugar, kg, 44, 0, mrp, zero MRP",
            "Sugar, kg, 44, x, mrp, MRP not a number"
    })
    void rejectsBadInputNamingTheField(String name, String unit, String rate, String mrp, String field, String why) {
        ValidationException error = assertThrows(ValidationException.class,
                () -> products.create(input(name, unit, null, rate, mrp)));

        assertEquals(field, error.field(), why);
        assertEquals(0, fixture.count("SELECT COUNT(*) FROM products"), "nothing saved");
    }

    @Test
    void checkWarnsAboutRateAboveMrpWithoutSaving() {
        ProductDetails details = products.check(input("Biscuit", "pcs", "100 g", "12", "10"));

        assertTrue(details.rateAboveMrp());
        assertEquals(0, fixture.count("SELECT COUNT(*) FROM products"));
    }

    @Test
    void sameNamePackAndUnitIsADuplicate() {
        products.create(input("Sugar", "kg", null, "44", null));
        products.create(input("Sugar", "pcs", "1 kg", "46", "48"));
        products.create(input("Sugar", "pcs", "5 kg", "220", null));

        ValidationException error = assertThrows(ValidationException.class,
                () -> products.create(input("SUGAR", "pcs", "1kg", "45", null)));

        assertEquals("name", error.field());
        assertTrue(error.getMessage().contains("P0002"), error.getMessage());
    }

    @Test
    void switchedOffDuplicateSuggestsSwitchingItOn() {
        Product sugar = products.create(input("Sugar", "kg", null, "44", null));
        products.setActive(sugar.id(), false);

        ValidationException error = assertThrows(ValidationException.class,
                () -> products.create(input("Sugar", "kg", null, "44", null)));

        assertTrue(error.getMessage().contains("Switch it back on"), error.getMessage());
    }

    @Test
    void unknownCategoryIsRejected() {
        ValidationException error = assertThrows(ValidationException.class,
                () -> products.create(new ProductInput("Sugar", null, 9999L, "kg", null, "44", null)));

        assertEquals("category", error.field());
    }

    // ------------------------------------------------------------------ editing

    @Test
    void updateChangesProductAndRecordsWhatChanged() {
        Product sugar = products.create(input("Sugar", "kg", null, "44", null));

        Product updated = products.update(sugar.id(), input("Sugar", "kg", null, "46", "50"));

        assertEquals(Money.ofRupees(46), updated.rate());
        assertEquals("P0001", updated.code(), "code never changes");
        String details = fixture.database.query(c -> {
            try (var s = c.createStatement();
                 var rs = s.executeQuery("SELECT details FROM audit_log WHERE action = 'PRODUCT_UPDATED'")) {
                rs.next();
                return rs.getString(1);
            }
        });
        assertEquals("P0001: rate 44.00 -> 46.00; MRP (none) -> 50.00", details);
    }

    @Test
    void updateWithNoChangesWritesNothing() {
        Product sugar = products.create(input("Sugar", "kg", null, "44", null));

        products.update(sugar.id(), input(" Sugar ", "KG", "", "44.00", ""));

        assertEquals(0, fixture.count("SELECT COUNT(*) FROM audit_log WHERE action = 'PRODUCT_UPDATED'"));
    }

    @Test
    void updateCannotCreateADuplicate() {
        products.create(input("Sugar", "kg", null, "44", null));
        Product jaggery = products.create(input("Jaggery", "kg", null, "60", null));

        assertThrows(ValidationException.class, () -> products.update(jaggery.id(), input("sugar", "kg", null, "60", null)));
    }

    // ------------------------------------------------------------------ switching off and deleting

    @Test
    void switchedOffProductsAreHiddenFromNormalSearch() {
        Product sugar = products.create(input("Sugar", "kg", null, "44", null));

        products.setActive(sugar.id(), false);

        assertTrue(products.search("sugar", null, false, 50).isEmpty());
        assertEquals(1, products.search("sugar", null, true, 50).size());

        products.setActive(sugar.id(), true);
        assertEquals(1, products.search("sugar", null, false, 50).size());
    }

    @Test
    void neverBilledProductCanBeDeleted() {
        Product sugar = products.create(input("Sugar", "kg", null, "44", null));

        products.delete(sugar.id());

        assertTrue(products.find(sugar.id()).isEmpty());
        assertEquals(1, fixture.count("SELECT COUNT(*) FROM audit_log WHERE action = 'PRODUCT_DELETED'"));
    }

    @Test
    void billedProductCannotBeDeleted() {
        Product sugar = products.create(input("Sugar", "kg", null, "44", null));
        fixture.putOnABill(sugar.id());

        assertTrue(products.isOnAnyBill(sugar.id()));
        BusinessRuleException error = assertThrows(BusinessRuleException.class, () -> products.delete(sugar.id()));

        assertTrue(error.getMessage().contains("Switch it off instead"), error.getMessage());
        assertTrue(products.find(sugar.id()).isPresent());
    }

    // ------------------------------------------------------------------ searching

    @Test
    void searchFindsAllTypesOfAProduct() {
        products.create(new ProductInput("Sugar", "साखर", null, "kg", null, "44", null));
        products.create(input("Sugar", "pcs", "1 kg", "46", "48"));
        products.create(input("Sugar", "pcs", "5 kg", "220", null));
        products.create(input("Salt", "pcs", "1 kg", "28", "30"));
        products.create(input("Brown Sugar Cubes", "pcs", "250 g", "90", null));

        assertEquals(4, products.search("sugar", null, false, 50).size(), "every kind of sugar");
        assertEquals(4, products.search("SUG", null, false, 50).size(), "part of a word, any case");
        assertEquals(1, products.search("साखर", null, false, 50).size(), "Marathi name");
        assertEquals(1, products.search("sugar 5 kg", null, false, 50).size(), "every word must match");
        assertEquals(2, products.search("1 kg", null, false, 50).size(), "pack size");
        assertEquals("P0004", products.search("p0004", null, false, 50).getFirst().code(), "code");
        assertEquals(5, products.search("  ", null, false, 50).size(), "blank shows everything");
    }

    @Test
    void searchPutsNamesStartingWithTheTextFirst() {
        products.create(input("Brown Sugar", "kg", null, "90", null));
        products.create(input("Sugar", "kg", null, "44", null));

        List<Product> found = products.search("sugar", null, false, 50);

        assertEquals("Sugar", found.getFirst().name());
    }

    @Test
    void searchTreatsPercentAndUnderscoreAsNormalCharacters() {
        products.create(input("Sugar", "kg", null, "44", null));

        assertTrue(products.search("%", null, false, 50).isEmpty());
        assertTrue(products.search("_", null, false, 50).isEmpty());
    }

    @Test
    void searchCanFilterByCategory() {
        products.create(new ProductInput("Toor Dal", null, dalCategory, "kg", null, "120", null));
        products.create(input("Sugar", "kg", null, "44", null));

        List<Product> found = products.search("", dalCategory, false, 50);

        assertEquals(1, found.size());
        assertEquals("Toor Dal", found.getFirst().name());
    }

    // ------------------------------------------------------------------ permissions

    @Test
    void staffCanSearchButNotChangeProducts() {
        Product sugar = products.create(input("Sugar", "kg", null, "44", null));
        fixture.signInStaff();

        assertFalse(products.search("sugar", null, false, 50).isEmpty());
        assertThrows(PermissionDeniedException.class, () -> products.create(input("Salt", "pcs", null, "28", null)));
        assertThrows(PermissionDeniedException.class, () -> products.update(sugar.id(), input("Sugar", "kg", null, "1", null)));
        assertThrows(PermissionDeniedException.class, () -> products.setActive(sugar.id(), false));
        assertThrows(PermissionDeniedException.class, () -> products.delete(sugar.id()));
    }
}
