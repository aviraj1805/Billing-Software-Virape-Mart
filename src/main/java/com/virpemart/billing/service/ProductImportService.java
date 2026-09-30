package com.virpemart.billing.service;

import java.io.IOException;
import java.io.StringReader;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Clock;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

import com.virpemart.billing.db.Database;
import com.virpemart.billing.db.DbTime;
import com.virpemart.billing.model.Category;
import com.virpemart.billing.model.Product;
import com.virpemart.billing.model.ProductDetails;
import com.virpemart.billing.model.User;
import com.virpemart.billing.repository.AuditRepository;
import com.virpemart.billing.repository.CategoryRepository;

/**
 * Imports a product list that was saved from Excel or Google Sheets as a CSV file.
 *
 * <p>How it works:
 * <ol>
 *   <li>The first row must hold column names. Common names are understood, for example
 *       "Price" or "Rate" for the rate, and "Item" or "Product Name" for the name.</li>
 *   <li>{@link #preview(Path)} checks every row with the same rules as the product form and
 *       shows what would happen. Nothing is saved.</li>
 *   <li>{@link #importFile(Path)} saves all good rows in one transaction. Rows with problems and
 *       products that already exist are skipped. Unknown categories are created.</li>
 * </ol>
 * Only new products are added; existing products are never changed by an import.
 */
public final class ProductImportService {

    /** Column names in the blank template, in order. */
    public static final List<String> TEMPLATE_HEADERS =
            List.of("Name", "Marathi Name", "Category", "Unit", "Pack Size", "Rate", "MRP");

    private static final long MAX_FILE_BYTES = 5L * 1024 * 1024;
    private static final int MAX_ROWS = 20_000;

    /** The kinds of column the import understands. */
    enum Column {
        NAME("Name"), NAME_MR("Marathi Name"), CATEGORY("Category"), UNIT("Unit"),
        PACK_SIZE("Pack Size"), RATE("Rate"), MRP("MRP");

        private final String label;

        Column(String label) {
            this.label = label;
        }
    }

    /** Header names people commonly use, for each column. Compared after {@link #headerKey(String)}. */
    private static final Map<String, Column> HEADER_NAMES = buildHeaderNames();

    /** What will happen to one row. */
    public enum RowStatus {
        /** Will be imported. */
        READY,
        /** Will not be imported because it already exists. */
        SKIPPED,
        /** Has a problem that must be fixed in the sheet. */
        ERROR
    }

    /**
     * One row of the sheet and what will happen to it.
     *
     * @param rowNumber row number as shown in Excel (the header is row 1)
     * @param message   explanation for SKIPPED and ERROR rows, or a note for READY rows
     */
    public record Row(int rowNumber, String name, String nameMr, String category, String unit, String packSize,
                      String rate, String mrp, RowStatus status, String message) {
    }

    /** The result of checking a file. */
    public record Preview(List<Row> rows, List<String> newCategories) {

        public long count(RowStatus status) {
            return rows.stream().filter(r -> r.status() == status).count();
        }
    }

    /** The result of an import. */
    public record Result(int imported, int skipped, int errors, List<String> newCategories) {
    }

    /** A row as read from the file, before checking. */
    private record RawRow(int rowNumber, Map<Column, String> values) {

        String get(Column column) {
            return values.getOrDefault(column, "");
        }
    }

    /** A checked row, with its details if it is READY. */
    private record CheckedRow(Row row, ProductDetails details, String categoryName) {
    }

    private final Database database;
    private final ProductService productService;
    private final CategoryService categoryService;
    private final CategoryRepository categories;
    private final AuditRepository audit;
    private final Session session;
    private final Clock clock;

    public ProductImportService(Database database, ProductService productService, CategoryService categoryService,
                                CategoryRepository categories, AuditRepository audit, Session session, Clock clock) {
        this.database = database;
        this.productService = productService;
        this.categoryService = categoryService;
        this.categories = categories;
        this.audit = audit;
        this.session = session;
        this.clock = clock;
    }

    /** Checks the file and reports what would happen to each row. Saves nothing. Owner only. */
    public Preview preview(Path file) {
        session.requireOwner();
        List<RawRow> rawRows = readFile(file);
        return database.query(c -> {
            List<CheckedRow> checked = check(c, rawRows);
            return new Preview(checked.stream().map(CheckedRow::row).toList(), newCategories(checked));
        });
    }

    /** Imports all good rows in one transaction. Owner only. */
    public Result importFile(Path file) {
        User user = session.requireOwner();
        List<RawRow> rawRows = readFile(file);
        return database.inTransaction(c -> {
            List<CheckedRow> checked = check(c, rawRows);
            List<String> created = newCategories(checked);

            Map<String, Long> categoryIds = new HashMap<>();
            for (Category category : categories.list(c, true)) {
                categoryIds.put(category.name().toLowerCase(Locale.ROOT), category.id());
            }
            for (String name : created) {
                Category category = categoryService.createInTransaction(c, name, user);
                categoryIds.put(name.toLowerCase(Locale.ROOT), category.id());
            }

            String now = DbTime.now(clock);
            int imported = 0;
            for (CheckedRow row : checked) {
                if (row.row().status() != RowStatus.READY) {
                    continue;
                }
                ProductDetails details = row.details();
                if (row.categoryName() != null) {
                    Long categoryId = categoryIds.get(row.categoryName().toLowerCase(Locale.ROOT));
                    details = new ProductDetails(details.name(), details.nameMr(), categoryId, details.unit(),
                            details.packSize(), details.rate(), details.mrp());
                }
                productService.insertNew(c, details, now);
                imported++;
            }

            int skipped = (int) checked.stream().filter(r -> r.row().status() == RowStatus.SKIPPED).count();
            int errors = (int) checked.stream().filter(r -> r.row().status() == RowStatus.ERROR).count();
            audit.insert(c, user.id(), "PRODUCTS_IMPORTED", "products", null,
                    imported + " imported, " + skipped + " skipped, " + errors + " with errors, from "
                            + file.getFileName(), now);
            return new Result(imported, skipped, errors, created);
        });
    }

    /** A blank template with the column names and three example rows, ready to open in Excel. */
    public static String templateText() {
        List<List<String>> lines = List.of(
                TEMPLATE_HEADERS,
                List.of("Sugar", "साखर", "Sugar, Salt & Jaggery", "kg", "", "44", ""),
                List.of("Tata Salt", "टाटा मीठ", "Sugar, Salt & Jaggery", "pcs", "1 kg", "28", "30"),
                List.of("Groundnut Oil", "शेंगदाणा तेल", "Edible Oil & Ghee", "litre", "", "180", ""));
        StringBuilder text = new StringBuilder("﻿"); // byte-order mark: tells Excel the file is UTF-8
        for (List<String> line : lines) {
            text.append(String.join(",", line.stream().map(ProductImportService::csvValue).toList())).append("\r\n");
        }
        return text.toString();
    }

    // ------------------------------------------------------------------ checking rows

    private List<CheckedRow> check(Connection connection, List<RawRow> rawRows) throws SQLException {
        Map<String, Category> existingCategories = new HashMap<>();
        for (Category category : categories.list(connection, true)) {
            existingCategories.put(category.name().toLowerCase(Locale.ROOT), category);
        }

        Map<String, Integer> seen = new HashMap<>();
        List<CheckedRow> result = new ArrayList<>();
        for (RawRow raw : rawRows) {
            String categoryName = Texts.clean(raw.get(Column.CATEGORY));
            Category category = categoryName == null ? null : existingCategories.get(categoryName.toLowerCase(Locale.ROOT));
            ProductInput input = new ProductInput(raw.get(Column.NAME), raw.get(Column.NAME_MR),
                    category == null ? null : category.id(), raw.get(Column.UNIT), raw.get(Column.PACK_SIZE),
                    raw.get(Column.RATE), raw.get(Column.MRP));

            ProductDetails details;
            try {
                details = productService.check(input);
            } catch (ValidationException e) {
                result.add(new CheckedRow(row(raw, RowStatus.ERROR, e.getMessage()), null, null));
                continue;
            }

            String key = details.name().toLowerCase(Locale.ROOT) + "|"
                    + (details.packSize() == null ? "" : details.packSize().replace(" ", "").toLowerCase(Locale.ROOT))
                    + "|" + details.unit();
            Integer earlierRow = seen.putIfAbsent(key, raw.rowNumber());
            if (earlierRow != null) {
                result.add(new CheckedRow(row(raw, RowStatus.SKIPPED, "Same product as row " + earlierRow + "."),
                        null, null));
                continue;
            }

            Optional<Product> existing = productService.findSame(connection, details, 0);
            if (existing.isPresent()) {
                Product p = existing.get();
                result.add(new CheckedRow(row(raw, RowStatus.SKIPPED, "Already in your product list as " + p.code()
                        + (p.active() ? "." : " (switched off).")), null, null));
                continue;
            }

            String note = (categoryName != null && category == null) ? "New category: " + categoryName : "";
            String canonicalCategory = category == null ? categoryName : category.name();
            result.add(new CheckedRow(row(raw, RowStatus.READY, note), details, canonicalCategory));
        }
        return result;
    }

    private static Row row(RawRow raw, RowStatus status, String message) {
        return new Row(raw.rowNumber(), raw.get(Column.NAME), raw.get(Column.NAME_MR), raw.get(Column.CATEGORY),
                raw.get(Column.UNIT), raw.get(Column.PACK_SIZE), raw.get(Column.RATE), raw.get(Column.MRP),
                status, message);
    }

    /** Category names used by READY rows that do not exist yet, in first-seen order. */
    private static List<String> newCategories(List<CheckedRow> rows) {
        Map<String, String> names = new LinkedHashMap<>();
        for (CheckedRow row : rows) {
            if (row.row().status() == RowStatus.READY && row.categoryName() != null && row.details().categoryId() == null) {
                names.putIfAbsent(row.categoryName().toLowerCase(Locale.ROOT), row.categoryName());
            }
        }
        return List.copyOf(names.values());
    }

    // ------------------------------------------------------------------ reading the file

    private static List<RawRow> readFile(Path file) {
        String fileName = file.getFileName().toString().toLowerCase(Locale.ROOT);
        if (fileName.endsWith(".xlsx") || fileName.endsWith(".xls")) {
            throw new ValidationException("file", "This is an Excel file. In Excel, choose File, then Save As, "
                    + "and pick \"CSV UTF-8 (Comma delimited)\". Then choose that CSV file here.");
        }
        String text = readUtf8(file);
        char delimiter = guessDelimiter(text);
        CSVFormat format = CSVFormat.DEFAULT.builder()
                .setDelimiter(delimiter)
                .setIgnoreEmptyLines(false)
                .setTrim(true)
                .get();

        List<RawRow> rows = new ArrayList<>();
        Map<Integer, Column> columns = null;
        try (CSVParser parser = format.parse(new StringReader(text))) {
            for (CSVRecord record : parser) {
                if (isBlank(record)) {
                    continue;
                }
                if (columns == null) {
                    columns = mapHeader(record);
                    continue;
                }
                if (rows.size() >= MAX_ROWS) {
                    throw new ValidationException("file", "The file has more than " + MAX_ROWS
                            + " products. Please split it into smaller files.");
                }
                Map<Column, String> values = new EnumMap<>(Column.class);
                for (Map.Entry<Integer, Column> entry : columns.entrySet()) {
                    int index = entry.getKey();
                    values.put(entry.getValue(), index < record.size() ? record.get(index) : "");
                }
                rows.add(new RawRow((int) record.getRecordNumber(), values));
            }
        } catch (IOException | UncheckedIOException e) {
            throw new ValidationException("file", "The file could not be read as a CSV file. "
                    + "Please save it again from Excel as \"CSV UTF-8 (Comma delimited)\".");
        }
        if (columns == null) {
            throw new ValidationException("file", "The file is empty.");
        }
        if (rows.isEmpty()) {
            throw new ValidationException("file", "The file has column names but no products.");
        }
        return rows;
    }

    private static String readUtf8(Path file) {
        byte[] bytes;
        try {
            if (Files.size(file) > MAX_FILE_BYTES) {
                throw new ValidationException("file", "This file is too big. A product list should be under 5 MB.");
            }
            bytes = Files.readAllBytes(file);
        } catch (IOException e) {
            throw new ValidationException("file", "The file could not be opened. Close it in Excel and try again.");
        }
        int start = (bytes.length >= 3 && (bytes[0] & 0xFF) == 0xEF && (bytes[1] & 0xFF) == 0xBB
                && (bytes[2] & 0xFF) == 0xBF) ? 3 : 0;
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes, start, bytes.length - start))
                    .toString();
        } catch (CharacterCodingException e) {
            throw new ValidationException("file", "This file was not saved as \"CSV UTF-8\", so Marathi names would be "
                    + "lost. In Excel, choose File, then Save As, and pick \"CSV UTF-8 (Comma delimited)\".");
        }
    }

    /** Excel in some countries uses ";" instead of ",". Look at the first line to decide. */
    private static char guessDelimiter(String text) {
        int end = text.indexOf('\n');
        String firstLine = end < 0 ? text : text.substring(0, end);
        long commas = firstLine.chars().filter(ch -> ch == ',').count();
        long semicolons = firstLine.chars().filter(ch -> ch == ';').count();
        return semicolons > commas ? ';' : ',';
    }

    private static Map<Integer, Column> mapHeader(CSVRecord header) {
        Map<Integer, Column> columns = new LinkedHashMap<>();
        Map<Column, String> found = new EnumMap<>(Column.class);
        for (int i = 0; i < header.size(); i++) {
            Column column = HEADER_NAMES.get(headerKey(header.get(i)));
            if (column == null) {
                continue; // unknown columns are ignored
            }
            if (found.containsKey(column)) {
                throw new ValidationException("file", "Two columns both look like \"" + column.label + "\": \""
                        + found.get(column) + "\" and \"" + header.get(i) + "\". Please rename or remove one.");
            }
            found.put(column, header.get(i));
            columns.put(i, column);
        }
        List<String> missing = new ArrayList<>();
        for (Column required : List.of(Column.NAME, Column.UNIT, Column.RATE)) {
            if (!found.containsKey(required)) {
                missing.add(required.label);
            }
        }
        if (!missing.isEmpty()) {
            throw new ValidationException("file", "The sheet is missing these columns: " + String.join(", ", missing)
                    + ". The first row must have column names like: " + String.join(", ", TEMPLATE_HEADERS) + ".");
        }
        return columns;
    }

    /** Lower case, keeping only letters and digits, so "M.R.P." and "Rate (Rs)" match "mrp" and "raters". */
    static String headerKey(String header) {
        StringBuilder key = new StringBuilder();
        header.strip().toLowerCase(Locale.ROOT).codePoints()
                .filter(Character::isLetterOrDigit)
                .forEach(key::appendCodePoint);
        return key.toString();
    }

    private static Map<String, Column> buildHeaderNames() {
        Map<String, Column> names = new HashMap<>();
        addNames(names, Column.NAME, "name", "product name", "product", "item", "item name", "english name",
                "name (english)", "नाव");
        addNames(names, Column.NAME_MR, "marathi name", "name (marathi)", "name marathi", "marathi", "मराठी नाव",
                "मराठी");
        addNames(names, Column.CATEGORY, "category", "type", "group", "item type", "product type", "प्रकार");
        addNames(names, Column.UNIT, "unit", "sold by", "uom", "unit type", "एकक");
        addNames(names, Column.PACK_SIZE, "pack size", "pack", "size", "packing", "weight", "वजन");
        addNames(names, Column.RATE, "rate", "rate (rs)", "rate (₹)", "price", "selling price", "sale price",
                "selling rate", "our price", "दर", "किंमत");
        addNames(names, Column.MRP, "mrp", "m.r.p.", "mrp (rs)", "mrp (₹)", "max retail price", "एमआरपी");
        return Map.copyOf(names);
    }

    private static void addNames(Map<String, Column> names, Column column, String... headers) {
        for (String header : headers) {
            names.put(headerKey(header), column);
        }
    }

    private static boolean isBlank(CSVRecord record) {
        for (String value : record) {
            if (value != null && !value.isBlank()) {
                return false;
            }
        }
        return true;
    }

    private static String csvValue(String value) {
        return value.contains(",") || value.contains("\"") ? "\"" + value.replace("\"", "\"\"") + "\"" : value;
    }
}
