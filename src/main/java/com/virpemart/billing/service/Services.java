package com.virpemart.billing.service;

import java.time.Clock;

import com.virpemart.billing.db.Database;
import com.virpemart.billing.repository.AuditRepository;
import com.virpemart.billing.repository.CategoryRepository;
import com.virpemart.billing.repository.ProductRepository;

/**
 * All services of the app, created once at startup and shared by the screens.
 * New services are added here as later phases need them.
 */
public record Services(
        CategoryService categories,
        ProductService products,
        ProductImportService productImport) {

    /** Wires every service to the database, session and clock. */
    public static Services create(Database database, Session session, Clock clock) {
        AuditRepository audit = new AuditRepository();
        CategoryRepository categoryRepository = new CategoryRepository();
        ProductRepository productRepository = new ProductRepository();

        CategoryService categories = new CategoryService(database, categoryRepository, audit, session, clock);
        ProductService products = new ProductService(database, productRepository, categoryRepository, audit, session, clock);
        ProductImportService productImport = new ProductImportService(database, products, categories,
                categoryRepository, audit, session, clock);
        return new Services(categories, products, productImport);
    }
}
