package com.virpemart.billing.service;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import com.virpemart.billing.db.Database;
import com.virpemart.billing.db.DbTime;
import com.virpemart.billing.model.Category;
import com.virpemart.billing.model.Money;
import com.virpemart.billing.model.Product;
import com.virpemart.billing.model.ProductDetails;
import com.virpemart.billing.model.Unit;
import com.virpemart.billing.model.User;
import com.virpemart.billing.repository.AuditRepository;
import com.virpemart.billing.repository.CategoryRepository;
import com.virpemart.billing.repository.ProductRepository;
import com.virpemart.billing.repository.ProductSearch;

/**
 * Everything about the product list: search, add, edit, switch off/on and delete.
 *
 * <p>Rules:
 * <ul>
 *   <li>Anyone signed in can search. Only the owner can change products.</li>
 *   <li>Codes are created automatically: P0001, P0002, ...</li>
 *   <li>Two products may not have the same name, pack size and unit.</li>
 *   <li>A product that appears on any bill cannot be deleted, only switched off.</li>
 *   <li>Every change is written to the audit log.</li>
 * </ul>
 */
public final class ProductService {

    private static final int MAX_NAME_LENGTH = 100;
    private static final int MAX_PACK_SIZE_LENGTH = 30;

    private final Database database;
    private final ProductRepository products;
    private final CategoryRepository categories;
    private final AuditRepository audit;
    private final Session session;
    private final Clock clock;

    public ProductService(Database database, ProductRepository products, CategoryRepository categories,
                          AuditRepository audit, Session session, Clock clock) {
        this.database = database;
        this.products = products;
        this.categories = categories;
        this.audit = audit;
        this.session = session;
        this.clock = clock;
    }

    // ------------------------------------------------------------------ reading

    /** Searches products; see {@link ProductSearch} for how matching works. */
    public List<Product> search(String text, Long categoryId, boolean includeInactive, int limit) {
        session.requireSignedIn();
        return database.query(c -> products.search(c, new ProductSearch(text, categoryId, includeInactive, limit)));
    }

    public Optional<Product> find(long id) {
        session.requireSignedIn();
        return database.query(c -> products.findById(c, id));
    }

    /** True if the product appears on any saved bill (then it can only be switched off, not deleted). */
    public boolean isOnAnyBill(long id) {
        session.requireSignedIn();
        return database.query(c -> products.isOnAnyBill(c, id));
    }

    // ------------------------------------------------------------------ checking input

    /**
     * Checks and cleans typed product details without saving anything.
     * Screens call this first to warn about things like a rate above MRP.
     *
     * @throws ValidationException naming the first field that has a problem
     */
    public ProductDetails check(ProductInput input) {
        String name = Texts.clean(input.name());
        if (name == null) {
            throw new ValidationException("name", "Please enter the product name.");
        }
        if (name.length() > MAX_NAME_LENGTH) {
            throw new ValidationException("name", "The name is too long. Use at most " + MAX_NAME_LENGTH + " letters.");
        }

        String nameMr = Texts.clean(input.nameMr());
        if (nameMr != null && nameMr.length() > MAX_NAME_LENGTH) {
            throw new ValidationException("nameMr",
                    "The Marathi name is too long. Use at most " + MAX_NAME_LENGTH + " letters.");
        }

        Unit unit = Unit.parse(input.unit()).orElseThrow(() -> new ValidationException("unit",
                (Texts.clean(input.unit()) == null ? "Please choose how it is sold" : "\"" + input.unit().strip()
                        + "\" is not a known unit. Use") + ": kg, litre or pcs."));

        String packSize = Texts.clean(input.packSize());
        if (packSize != null && packSize.length() > MAX_PACK_SIZE_LENGTH) {
            throw new ValidationException("packSize",
                    "The pack size is too long. Use at most " + MAX_PACK_SIZE_LENGTH + " letters, like 500 g.");
        }

        Money rate = Amounts.parsePositive("rate", input.rate(), "rate", true);
        Money mrp = Amounts.parsePositive("mrp", input.mrp(), "MRP", false);

        return new ProductDetails(name, nameMr, input.categoryId(), unit, packSize, rate, mrp);
    }

    // ------------------------------------------------------------------ changing

    /** Adds a new product with the next automatic code. Owner only. */
    public Product create(ProductInput input) {
        User user = session.requireOwner();
        ProductDetails details = check(input);
        return database.inTransaction(c -> {
            checkCategory(c, details.categoryId());
            checkNotDuplicate(c, details, 0);
            String now = DbTime.now(clock);
            Product created = insertNew(c, details, now);
            audit.insert(c, user.id(), "PRODUCT_CREATED", "products", created.id(),
                    created.code() + " " + created.displayName() + ", rate " + created.rate().toPlainString()
                            + " per " + created.unit().shortLabel(), now);
            return created;
        });
    }

    /** Saves changes to a product. Owner only. Old bills are not affected. */
    public Product update(long id, ProductInput input) {
        User user = session.requireOwner();
        ProductDetails details = check(input);
        return database.inTransaction(c -> {
            Product before = products.findById(c, id)
                    .orElseThrow(() -> new BusinessRuleException("This product no longer exists."));
            checkCategory(c, details.categoryId());
            checkNotDuplicate(c, details, id);

            String changes = describeChanges(before, details);
            if (changes.isEmpty()) {
                return before;
            }
            String now = DbTime.now(clock);
            products.update(c, id, details, now);
            audit.insert(c, user.id(), "PRODUCT_UPDATED", "products", id, before.code() + ": " + changes, now);
            return products.findById(c, id).orElseThrow();
        });
    }

    /** Switches a product off (hidden from billing, kept for old bills) or back on. Owner only. */
    public Product setActive(long id, boolean active) {
        User user = session.requireOwner();
        return database.inTransaction(c -> {
            Product product = products.findById(c, id)
                    .orElseThrow(() -> new BusinessRuleException("This product no longer exists."));
            if (product.active() == active) {
                return product;
            }
            String now = DbTime.now(clock);
            products.setActive(c, id, active, now);
            audit.insert(c, user.id(), active ? "PRODUCT_SWITCHED_ON" : "PRODUCT_SWITCHED_OFF", "products", id,
                    product.code() + " " + product.displayName(), now);
            return products.findById(c, id).orElseThrow();
        });
    }

    /**
     * Deletes a product that has never been billed. Owner only.
     *
     * @throws BusinessRuleException if the product appears on any bill
     */
    public void delete(long id) {
        User user = session.requireOwner();
        database.runInTransaction(c -> {
            Product product = products.findById(c, id)
                    .orElseThrow(() -> new BusinessRuleException("This product no longer exists."));
            if (products.isOnAnyBill(c, id)) {
                throw new BusinessRuleException("\"" + product.displayName() + "\" is on old bills, so it cannot be "
                        + "deleted. Switch it off instead: it will be hidden from billing, and old bills stay correct.");
            }
            products.delete(c, id);
            audit.insert(c, user.id(), "PRODUCT_DELETED", "products", id,
                    product.code() + " " + product.displayName(), DbTime.now(clock));
        });
    }

    // ------------------------------------------------------------------ shared with the import

    /** Inserts checked details with the next automatic code. No permission check and no audit. */
    Product insertNew(Connection connection, ProductDetails details, String now) throws SQLException {
        String code = String.format("P%04d", products.maxCodeNumber(connection) + 1);
        long id = products.insert(connection, code, details, now);
        return products.findById(connection, id).orElseThrow();
    }

    /** The existing product with the same name, pack size and unit, if any. */
    Optional<Product> findSame(Connection connection, ProductDetails details, long excludeId) throws SQLException {
        return products.findSame(connection, details.name(), details.packSize(), details.unit(), excludeId);
    }

    private void checkNotDuplicate(Connection connection, ProductDetails details, long excludeId) throws SQLException {
        Optional<Product> same = findSame(connection, details, excludeId);
        if (same.isPresent()) {
            Product existing = same.get();
            throw new ValidationException("name", existing.active()
                    ? "This product already exists as " + existing.code() + " \"" + existing.displayName()
                            + "\". Change the name or pack size, or edit the existing product."
                    : "A switched-off product with this name already exists: " + existing.code() + " \""
                            + existing.displayName() + "\". Switch it back on instead of adding it again.");
        }
    }

    private void checkCategory(Connection connection, Long categoryId) throws SQLException {
        if (categoryId == null) {
            return;
        }
        Optional<Category> category = categories.findById(connection, categoryId);
        if (category.isEmpty()) {
            throw new ValidationException("category", "Please choose a category from the list.");
        }
    }

    /** Lists what changed, for example "rate 44.00 -> 46.00; pack size 1 kg -> 2 kg". */
    private static String describeChanges(Product before, ProductDetails after) {
        List<String> changes = new ArrayList<>();
        addChange(changes, "name", before.name(), after.name());
        addChange(changes, "Marathi name", before.nameMr(), after.nameMr());
        addChange(changes, "category id", before.categoryId(), after.categoryId());
        addChange(changes, "unit", before.unit(), after.unit());
        addChange(changes, "pack size", before.packSize(), after.packSize());
        addChange(changes, "rate", before.rate().toPlainString(), after.rate().toPlainString());
        addChange(changes, "MRP", before.mrp() == null ? null : before.mrp().toPlainString(),
                after.mrp() == null ? null : after.mrp().toPlainString());
        return String.join("; ", changes);
    }

    private static void addChange(List<String> changes, String label, Object before, Object after) {
        if (!Objects.equals(before, after)) {
            changes.add(label + " " + (before == null ? "(none)" : before) + " -> " + (after == null ? "(none)" : after));
        }
    }
}
