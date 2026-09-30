package com.virpemart.billing.service;

import java.nio.file.Path;
import java.time.Clock;

import com.virpemart.billing.db.Database;
import com.virpemart.billing.print.ReceiptPrinter;
import com.virpemart.billing.print.SystemReceiptPrinter;
import com.virpemart.billing.repository.AuditRepository;
import com.virpemart.billing.repository.BillRepository;
import com.virpemart.billing.repository.CategoryRepository;
import com.virpemart.billing.repository.CustomerRepository;
import com.virpemart.billing.repository.LedgerRepository;
import com.virpemart.billing.repository.ProductRepository;
import com.virpemart.billing.repository.ReportRepository;
import com.virpemart.billing.repository.SettingsRepository;

/**
 * All services of the app, created once at startup and shared by the screens.
 * New services are added here as later phases need them.
 */
public record Services(
        CategoryService categories,
        ProductService products,
        ProductImportService productImport,
        CustomerService customers,
        LedgerService ledger,
        BillingService billing,
        SettingsService settings,
        PrintService printing,
        ReportService reports,
        BackupService backups,
        AuditService audit) {

    /** Wires every service for the running app: real Windows printers and the app's backups folder. */
    public static Services create(Database database, Session session, Clock clock, Path backupsDir) {
        return create(database, session, clock, new SystemReceiptPrinter(), backupsDir);
    }

    /** For tests: the given (fake) printer, and backups in a "backups" folder next to the database file. */
    public static Services create(Database database, Session session, Clock clock, ReceiptPrinter printer) {
        return create(database, session, clock, printer, database.file().resolveSibling("backups"));
    }

    /** Wires every service to the database, session, clock, printer and backups folder. */
    public static Services create(Database database, Session session, Clock clock, ReceiptPrinter printer,
                                  Path backupsDir) {
        AuditRepository audit = new AuditRepository();
        CategoryRepository categoryRepository = new CategoryRepository();
        ProductRepository productRepository = new ProductRepository();
        CustomerRepository customerRepository = new CustomerRepository();
        LedgerRepository ledgerRepository = new LedgerRepository();

        CategoryService categories = new CategoryService(database, categoryRepository, audit, session, clock);
        ProductService products = new ProductService(database, productRepository, categoryRepository, audit, session, clock);
        ProductImportService productImport = new ProductImportService(database, products, categories,
                categoryRepository, audit, session, clock);
        CustomerService customers = new CustomerService(database, customerRepository, ledgerRepository, audit,
                session, clock);
        LedgerService ledger = new LedgerService(database, customerRepository, ledgerRepository, audit, session, clock);
        BillingService billing = new BillingService(database, new BillRepository(), productRepository,
                customerRepository, ledgerRepository, audit, session, clock);
        SettingsService settings = new SettingsService(database, new SettingsRepository(), audit, session, clock);
        PrintService printing = new PrintService(database, billing, settings, audit, printer, session, clock);
        ReportService reports = new ReportService(database, new ReportRepository(), session);
        BackupService backups = new BackupService(database, backupsDir, audit, session, clock);
        AuditService auditLog = new AuditService(database, audit, session);
        return new Services(categories, products, productImport, customers, ledger, billing, settings, printing,
                reports, backups, auditLog);
    }
}
