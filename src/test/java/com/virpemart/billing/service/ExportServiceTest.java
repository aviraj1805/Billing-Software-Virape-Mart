package com.virpemart.billing.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.virpemart.billing.model.Product;

class ExportServiceTest {

    @TempDir
    Path temp;

    private TestFixture fixture;
    private ExportService exports;

    @BeforeEach
    void setUp() {
        fixture = new TestFixture(temp);
        exports = fixture.services.exports();
    }

    /** The rows of a downloaded file, after checking it starts with the byte-order mark Excel needs. */
    private static List<List<String>> read(Path file) throws IOException {
        String text = Files.readString(file, StandardCharsets.UTF_8);
        assertTrue(text.startsWith("﻿"), "byte-order mark, so Excel shows Marathi letters");
        List<List<String>> rows = new ArrayList<>();
        try (CSVParser parser = CSVParser.parse(new StringReader(text.substring(1)), CSVFormat.EXCEL)) {
            for (CSVRecord record : parser) {
                rows.add(record.toList());
            }
        }
        return rows;
    }

    @Test
    void productListHasEveryProductAsSavedAndNoHiddenValues() throws IOException {
        ProductService products = fixture.services.products();
        products.create(new ProductInput("Toor Dal", "तूर डाळ", "kg", null, "120.50"));
        Product salt = products.create(new ProductInput("Tata Salt", null, "pcs", "1 kg", "28"));
        Product oil = products.create(new ProductInput("Oil", null, "litre", null, "180"));
        fixture.setStoredMrp(salt.id(), 3000);
        products.setActive(oil.id(), false);
        Path file = temp.resolve("products.csv");

        int count = exports.exportProducts(file);

        assertEquals(3, count);
        assertEquals(List.of(
                ExportService.PRODUCT_HEADERS,
                List.of("P0001", "Toor Dal", "तूर डाळ", "kg", "", "120.5", "Yes"),
                List.of("P0002", "Tata Salt", "", "pcs", "1 kg", "28", "Yes"),
                List.of("P0003", "Oil", "", "litre", "", "180", "No")), read(file));
        assertEquals(1, fixture.count("SELECT COUNT(*) FROM audit_log WHERE action = 'PRODUCTS_EXPORTED'"
                + " AND details = '3 products saved to products.csv'"));
    }

    @Test
    void downloadedProductListImportsBackWithoutChangingAnything() throws IOException {
        ProductService products = fixture.services.products();
        products.create(new ProductInput("Sugar", "साखर", "kg", null, "44"));
        products.create(new ProductInput("Parle-G", null, "pcs", "80 g", "10"));
        Path file = temp.resolve("products.csv");
        exports.exportProducts(file);

        ProductImportService.Result result = fixture.services.productImport().importFile(file);

        assertEquals(0, result.imported(), "the same products are already in the list");
        assertEquals(2, result.skipped());
        assertEquals(0, result.errors(), "the downloaded columns are understood by the import");
        assertEquals(2, fixture.count("SELECT COUNT(*) FROM products"));
    }

    @Test
    void customerListHasEveryCustomerWithTheKhataBalance() throws IOException {
        CustomerService customers = fixture.services.customers();
        long ramesh = customers.create(new CustomerInput("रमेश पाटील", "98765 43210", "Main Road, Pune\nNear temple",
                "Pays on Sunday", "1,250.50")).customer().id();
        long anita = customers.create(new CustomerInput("Anita", null, null, null, null)).customer().id();
        customers.setActive(anita, false);
        Path file = temp.resolve("customers.csv");

        int count = exports.exportCustomers(file);

        assertEquals(2, count);
        assertEquals(List.of(
                ExportService.CUSTOMER_HEADERS,
                List.of("C0001", "रमेश पाटील", "9876543210", "Main Road, Pune Near temple", "Pays on Sunday",
                        "1250.5", "Yes", "30/09/2026"),
                List.of("C0002", "Anita", "", "", "", "0", "No", "30/09/2026")), read(file));
        assertEquals("1250.5", customers.find(ramesh).orElseThrow().balance().toInputString(),
                "the same balance as the khata");
        assertEquals(1, fixture.count("SELECT COUNT(*) FROM audit_log WHERE action = 'CUSTOMERS_EXPORTED'"));
    }

    @Test
    void downloadsAreForTheOwnerOnly() {
        fixture.signInStaff();

        assertThrows(PermissionDeniedException.class, () -> exports.exportProducts(temp.resolve("p.csv")));
        assertThrows(PermissionDeniedException.class, () -> exports.exportCustomers(temp.resolve("c.csv")));
    }

    @Test
    void aFileThatCannotBeWrittenGivesAClearMessage() throws IOException {
        Path folder = Files.createDirectory(temp.resolve("a-folder"));

        BusinessRuleException error = assertThrows(BusinessRuleException.class, () -> exports.exportProducts(folder));

        assertTrue(error.getMessage().contains("close it and try again"), error.getMessage());
        assertEquals(0, fixture.count("SELECT COUNT(*) FROM audit_log WHERE action = 'PRODUCTS_EXPORTED'"));
    }
}
