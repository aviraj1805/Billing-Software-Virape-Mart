package com.virpemart.billing.service;

import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.virpemart.billing.db.Database;
import com.virpemart.billing.db.DbTime;
import com.virpemart.billing.model.Customer;
import com.virpemart.billing.model.Product;
import com.virpemart.billing.model.Unit;
import com.virpemart.billing.model.User;
import com.virpemart.billing.repository.AuditRepository;
import com.virpemart.billing.repository.CustomerRepository;
import com.virpemart.billing.repository.CustomerRepository.ExportRow;
import com.virpemart.billing.repository.ProductRepository;

/**
 * Saves the product list and the customer list as CSV files that open in Excel (the user's choice: the same kind of
 * file the product import reads). It only reads the data. Owner only. Each download is written to the audit log.
 */
public final class ExportService {

    private static final Logger LOG = LoggerFactory.getLogger(ExportService.class);

    /** Product columns. Name, Marathi Name, Unit, Pack Size and Rate are the import's names, so the file imports back. */
    public static final List<String> PRODUCT_HEADERS =
            List.of("Code", "Name", "Marathi Name", "Unit", "Pack Size", "Rate", "Active");

    /** Customer columns. The balance is the khata balance: dues, or a minus amount for an advance. */
    public static final List<String> CUSTOMER_HEADERS = List.of("Customer No", "Name", "Phone", "Address", "Notes",
            "Balance (minus = advance)", "Active", "Added on");

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final Database database;
    private final ProductRepository products;
    private final CustomerRepository customers;
    private final AuditRepository audit;
    private final Session session;
    private final Clock clock;

    public ExportService(Database database, ProductRepository products, CustomerRepository customers,
                         AuditRepository audit, Session session, Clock clock) {
        this.database = database;
        this.products = products;
        this.customers = customers;
        this.audit = audit;
        this.session = session;
        this.clock = clock;
    }

    /**
     * Saves every product, switched-off ones too, in code order. Owner only.
     *
     * @return how many products were saved
     * @throws BusinessRuleException if the file cannot be written, for example because it is open in Excel
     */
    public int exportProducts(Path file) {
        User user = session.requireOwner();
        List<Product> all = database.query(products::listAll);
        List<List<String>> rows = new ArrayList<>();
        for (Product p : all) {
            rows.add(List.of(p.code(), p.name(), text(p.nameMr()), unitText(p.unit()), text(p.packSize()),
                    p.rate().toInputString(), p.active() ? "Yes" : "No"));
        }
        write(file, PRODUCT_HEADERS, rows);
        database.runInTransaction(c -> audit.insert(c, user.id(), "PRODUCTS_EXPORTED", "products", null,
                all.size() + " products saved to " + file.getFileName(), DbTime.now(clock)));
        return all.size();
    }

    /**
     * Saves every customer, switched-off ones too, in customer-number order, with the khata balance. Owner only.
     *
     * @return how many customers were saved
     * @throws BusinessRuleException if the file cannot be written, for example because it is open in Excel
     */
    public int exportCustomers(Path file) {
        User user = session.requireOwner();
        List<ExportRow> all = database.query(customers::listAllForExport);
        List<List<String>> rows = new ArrayList<>();
        for (ExportRow row : all) {
            Customer c = row.summary().customer();
            rows.add(List.of(c.customerNo(), c.name(), text(c.phone()), text(c.address()), text(c.notes()),
                    row.summary().balance().toInputString(), c.active() ? "Yes" : "No",
                    DAY.format(row.addedOn())));
        }
        write(file, CUSTOMER_HEADERS, rows);
        database.runInTransaction(c -> audit.insert(c, user.id(), "CUSTOMERS_EXPORTED", "customers", null,
                all.size() + " customers saved to " + file.getFileName(), DbTime.now(clock)));
        return all.size();
    }

    /** The unit as the import spells it: kg, litre or pcs. */
    private static String unitText(Unit unit) {
        return switch (unit) {
            case KG -> "kg";
            case L -> "litre";
            case PCS -> "pcs";
        };
    }

    private static String text(String value) {
        return value == null ? "" : value;
    }

    /** Writes a CSV file with the byte-order mark Excel needs to show Marathi letters, and Windows line endings. */
    private static void write(Path file, List<String> headers, List<List<String>> rows) {
        try (Writer out = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
            out.write('﻿');
            try (CSVPrinter csv = new CSVPrinter(out, CSVFormat.EXCEL)) {
                csv.printRecord(headers);
                for (List<String> row : rows) {
                    csv.printRecord(row);
                }
            }
        } catch (IOException e) {
            LOG.warn("Could not write {}", file, e);
            throw new BusinessRuleException("The file could not be saved there. If it is open in Excel, close it and"
                    + " try again, or choose another folder.");
        }
    }
}
