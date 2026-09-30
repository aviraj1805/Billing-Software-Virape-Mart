package com.virpemart.billing.service;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.Clock;
import java.util.List;
import java.util.Optional;

import com.virpemart.billing.db.Database;
import com.virpemart.billing.db.DbTime;
import com.virpemart.billing.model.Category;
import com.virpemart.billing.model.User;
import com.virpemart.billing.repository.AuditRepository;
import com.virpemart.billing.repository.CategoryRepository;

/** Product categories: list, add, rename and switch off/on. Changes are for the owner only. */
public final class CategoryService {

    private static final int MAX_NAME_LENGTH = 50;

    private final Database database;
    private final CategoryRepository categories;
    private final AuditRepository audit;
    private final Session session;
    private final Clock clock;

    public CategoryService(Database database, CategoryRepository categories, AuditRepository audit,
                           Session session, Clock clock) {
        this.database = database;
        this.categories = categories;
        this.audit = audit;
        this.session = session;
        this.clock = clock;
    }

    /** Categories in name order. */
    public List<Category> list(boolean includeInactive) {
        session.requireSignedIn();
        return database.query(c -> categories.list(c, includeInactive));
    }

    public Category create(String name) {
        User user = session.requireOwner();
        String cleaned = checkName(name);
        return database.inTransaction(c -> createInTransaction(c, cleaned, user));
    }

    public Category rename(long id, String newName) {
        User user = session.requireOwner();
        String cleaned = checkName(newName);
        return database.inTransaction(c -> {
            Category existing = categories.findById(c, id)
                    .orElseThrow(() -> new BusinessRuleException("This category no longer exists."));
            Optional<Category> clash = categories.findByName(c, cleaned);
            if (clash.isPresent() && clash.get().id() != id) {
                throw new ValidationException("name", "The category \"" + clash.get().name() + "\" already exists.");
            }
            categories.rename(c, id, cleaned);
            audit.insert(c, user.id(), "CATEGORY_RENAMED", "categories", id,
                    existing.name() + " -> " + cleaned, DbTime.now(clock));
            return new Category(id, cleaned, existing.active());
        });
    }

    /** Switches a category off (hidden from choices for products) or back on. Products keep their category. */
    public void setActive(long id, boolean active) {
        User user = session.requireOwner();
        database.runInTransaction(c -> {
            Category existing = categories.findById(c, id)
                    .orElseThrow(() -> new BusinessRuleException("This category no longer exists."));
            categories.setActive(c, id, active);
            audit.insert(c, user.id(), active ? "CATEGORY_SWITCHED_ON" : "CATEGORY_SWITCHED_OFF", "categories", id,
                    existing.name(), DbTime.now(clock));
        });
    }

    /** Adds a category inside an existing transaction. Used by create and by the product import. */
    Category createInTransaction(Connection connection, String cleanedName, User user) throws SQLException {
        Optional<Category> clash = categories.findByName(connection, cleanedName);
        if (clash.isPresent()) {
            throw new ValidationException("name", "The category \"" + clash.get().name() + "\" already exists.");
        }
        Category created = categories.insert(connection, cleanedName);
        audit.insert(connection, user.id(), "CATEGORY_CREATED", "categories", created.id(), cleanedName,
                DbTime.now(clock));
        return created;
    }

    /** Cleans and checks a category name. */
    static String checkName(String name) {
        String cleaned = Texts.clean(name);
        if (cleaned == null) {
            throw new ValidationException("name", "Please enter a category name.");
        }
        if (cleaned.length() > MAX_NAME_LENGTH) {
            throw new ValidationException("name", "The category name is too long. Use at most "
                    + MAX_NAME_LENGTH + " letters.");
        }
        return cleaned;
    }
}
