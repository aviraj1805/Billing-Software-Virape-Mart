package com.virpemart.billing.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.virpemart.billing.model.Money;
import com.virpemart.billing.model.Product;
import com.virpemart.billing.model.Unit;
import com.virpemart.billing.service.ProductImportService.Preview;
import com.virpemart.billing.service.ProductImportService.Result;
import com.virpemart.billing.service.ProductImportService.Row;
import com.virpemart.billing.service.ProductImportService.RowStatus;

class ProductImportServiceTest {

    @TempDir
    Path temp;

    private TestFixture fixture;
    private ProductImportService importer;

    @BeforeEach
    void setUp() {
        fixture = new TestFixture(temp);
        importer = fixture.services.productImport();
    }

    /** Writes a CSV file the way Excel's "CSV UTF-8" does: with a byte-order mark and CRLF line endings. */
    private Path excelCsv(String... lines) throws IOException {
        Path file = temp.resolve("products.csv");
        Files.writeString(file, "﻿" + String.join("\r\n", lines) + "\r\n", StandardCharsets.UTF_8);
        return file;
    }

    @Test
    void importsTheBlankTemplateExamples() throws IOException {
        Path file = temp.resolve("template.csv");
        Files.writeString(file, ProductImportService.templateText(), StandardCharsets.UTF_8);

        Result result = importer.importFile(file);

        assertEquals(3, result.imported());
        Product sugar = fixture.services.products().search("sugar", null, false, 10).getFirst();
        assertEquals("साखर", sugar.nameMr());
        assertEquals("Sugar, Salt & Jaggery", sugar.categoryName());
        assertEquals(Unit.KG, sugar.unit());
    }

    @Test
    void previewShowsEveryRowAndSavesNothing() throws IOException {
        Path file = excelCsv(
                "Name,Marathi Name,Category,Unit,Pack Size,Rate,MRP",
                "Toor Dal,तूर डाळ,Dal & Pulses,kg,,120,",
                ",,,kg,,50,",
                "Sugar,साखर,,kilo,,abc,",
                "Toor Dal,,,KG,,125,",
                "Moong Dal,मूग डाळ,Lentils,kg,,110,");

        Preview preview = importer.preview(file);

        List<Row> rows = preview.rows();
        assertEquals(5, rows.size());
        assertEquals(RowStatus.READY, rows.get(0).status());
        assertEquals(2, rows.get(0).rowNumber(), "row numbers match Excel (header is row 1)");
        assertEquals(RowStatus.ERROR, rows.get(1).status());
        assertTrue(rows.get(1).message().contains("name"), rows.get(1).message());
        assertEquals(RowStatus.ERROR, rows.get(2).status());
        assertTrue(rows.get(2).message().contains("abc"), rows.get(2).message());
        assertEquals(RowStatus.SKIPPED, rows.get(3).status());
        assertEquals("Same product as row 2.", rows.get(3).message());
        assertEquals(RowStatus.READY, rows.get(4).status());
        assertEquals("New category: Lentils", rows.get(4).message());
        assertEquals(List.of("Lentils"), preview.newCategories());
        assertEquals(2, preview.count(RowStatus.READY));

        assertEquals(0, fixture.count("SELECT COUNT(*) FROM products"), "preview never saves");
    }

    @Test
    void importSavesGoodRowsCreatesCategoriesAndAudits() throws IOException {
        Path file = excelCsv(
                "Name,Marathi Name,Category,Unit,Pack Size,Rate,MRP",
                "Toor Dal,तूर डाळ,Dal & Pulses,kg,,120,",
                "Moong Dal,मूग डाळ,lentils,kg,,110,",
                "Masoor Dal,,LENTILS,kg,,95,",
                "Bad,,,kg,,-1,");

        Result result = importer.importFile(file);

        assertEquals(3, result.imported());
        assertEquals(1, result.errors());
        assertEquals(List.of("lentils"), result.newCategories(), "created once, with the first spelling");
        assertEquals(3, fixture.count("SELECT COUNT(*) FROM products"));
        assertEquals(2, fixture.count("SELECT COUNT(*) FROM products p JOIN categories c ON c.id = p.category_id"
                + " WHERE c.name = 'lentils'"));
        assertEquals(1, fixture.count("SELECT COUNT(*) FROM audit_log WHERE action = 'PRODUCTS_IMPORTED'"));
        List<String> codes = fixture.services.products().search("", null, false, 10).stream().map(Product::code).sorted().toList();
        assertEquals(List.of("P0001", "P0002", "P0003"), codes);
    }

    @Test
    void existingProductsAreSkippedNotChanged() throws IOException {
        fixture.services.products().create(new ProductInput("Sugar", null, null, "kg", null, "44", null));
        Path file = excelCsv("Name,Unit,Rate", "Sugar,kg,50", "Jaggery,kg,60");

        Result result = importer.importFile(file);

        assertEquals(1, result.imported());
        assertEquals(1, result.skipped());
        Product sugar = fixture.services.products().search("sugar", null, false, 10).getFirst();
        assertEquals(Money.ofRupees(44), sugar.rate(), "import never changes an existing product");
    }

    @Test
    void understandsOtherColumnNamesAndIgnoresUnknownOnes() throws IOException {
        Path file = excelCsv(
                "Sr No,Item Name,Price,M.R.P.,Sold By,Packing,Type,Supplier",
                "1,Parle-G,10,10,pkt,80 g,Biscuits & Bakery,ABC Traders");

        Result result = importer.importFile(file);

        assertEquals(1, result.imported());
        Product biscuit = fixture.services.products().search("parle", null, false, 10).getFirst();
        assertEquals(Unit.PCS, biscuit.unit());
        assertEquals("80 g", biscuit.packSize());
        assertEquals(Money.ofRupees(10), biscuit.mrp());
        assertEquals("Biscuits & Bakery", biscuit.categoryName());
    }

    @Test
    void acceptsRupeeSignsQuotedValuesSemicolonsAndBlankLines() throws IOException {
        Path file = excelCsv(
                "Name;Unit;Rate;MRP",
                "\"Oil; Sunflower\";ltr;\"₹ 1,450.00\";Rs 1500",
                ";;;",
                "Ghee;l;650;");

        Result result = importer.importFile(file);

        assertEquals(2, result.imported());
        Product oil = fixture.services.products().search("sunflower", null, false, 10).getFirst();
        assertEquals("Oil; Sunflower", oil.name());
        assertEquals(Money.ofRupees(1450), oil.rate());
        assertEquals(Money.ofRupees(1500), oil.mrp());
    }

    @Test
    void missingRequiredColumnsAreExplained() throws IOException {
        Path file = excelCsv("Name,Price", "Sugar,44");

        ValidationException error = assertThrows(ValidationException.class, () -> importer.preview(file));

        assertTrue(error.getMessage().contains("missing these columns: Unit"), error.getMessage());
    }

    @Test
    void fileNotSavedAsUtf8IsRejectedWithHelp() throws IOException {
        Path file = temp.resolve("ansi.csv");
        Files.writeString(file, "Name,Unit,Rate\r\nCafé Coffee,pcs,100\r\n", Charset.forName("windows-1252"));

        ValidationException error = assertThrows(ValidationException.class, () -> importer.preview(file));

        assertTrue(error.getMessage().contains("CSV UTF-8"), error.getMessage());
    }

    @Test
    void excelFileIsRejectedWithHelp() throws IOException {
        Path file = temp.resolve("products.xlsx");
        Files.writeString(file, "not really excel");

        ValidationException error = assertThrows(ValidationException.class, () -> importer.preview(file));

        assertTrue(error.getMessage().contains("Save As"), error.getMessage());
    }

    @Test
    void emptySheetIsRejected() throws IOException {
        assertThrows(ValidationException.class, () -> importer.preview(excelCsv("Name,Unit,Rate")));
    }

    @Test
    void staffCannotImport() throws IOException {
        Path file = excelCsv("Name,Unit,Rate", "Sugar,kg,44");
        fixture.signInStaff();

        assertThrows(PermissionDeniedException.class, () -> importer.preview(file));
        assertThrows(PermissionDeniedException.class, () -> importer.importFile(file));
    }

    @Test
    void headerKeysIgnorePunctuationAndCase() {
        assertEquals("mrp", ProductImportService.headerKey(" M.R.P. "));
        assertEquals("packsize", ProductImportService.headerKey("Pack_Size"));
    }
}
