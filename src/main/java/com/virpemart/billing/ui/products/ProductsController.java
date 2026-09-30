package com.virpemart.billing.ui.products;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import com.virpemart.billing.AppContext;
import com.virpemart.billing.model.Category;
import com.virpemart.billing.model.Product;
import com.virpemart.billing.model.User;
import com.virpemart.billing.service.CategoryService;
import com.virpemart.billing.service.ProductService;
import com.virpemart.billing.ui.common.Dialogs;
import com.virpemart.billing.ui.common.Format;

import javafx.application.Platform;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.css.PseudoClass;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.VBox;
import javafx.stage.Window;
import javafx.util.StringConverter;

/**
 * The Products screen. Everyone can search; only the owner sees the buttons that change products.
 *
 * <p>Keyboard: Ctrl+F search, Ctrl+N add, Enter edit, Delete delete, Esc clears the search.
 */
public class ProductsController {

    /** Most rows shown at once. Typing in the search box narrows the list. */
    private static final int LIMIT = 500;
    private static final Category ALL_CATEGORIES = new Category(0, "All categories", true);
    private static final PseudoClass INACTIVE = PseudoClass.getPseudoClass("inactive");

    private final AppContext context;
    private final ProductService products;
    private final CategoryService categories;

    @FXML
    private VBox root;
    @FXML
    private TextField searchField;
    @FXML
    private ComboBox<Category> categoryFilter;
    @FXML
    private CheckBox showInactive;
    @FXML
    private TableView<Product> table;
    @FXML
    private TableColumn<Product, String> codeColumn;
    @FXML
    private TableColumn<Product, String> nameColumn;
    @FXML
    private TableColumn<Product, String> nameMrColumn;
    @FXML
    private TableColumn<Product, String> categoryColumn;
    @FXML
    private TableColumn<Product, String> unitColumn;
    @FXML
    private TableColumn<Product, String> packColumn;
    @FXML
    private TableColumn<Product, String> rateColumn;
    @FXML
    private TableColumn<Product, String> mrpColumn;
    @FXML
    private TableColumn<Product, String> statusColumn;
    @FXML
    private Label countLabel;
    @FXML
    private Button addButton;
    @FXML
    private Button importButton;
    @FXML
    private Button categoriesButton;
    @FXML
    private Button editButton;
    @FXML
    private Button toggleActiveButton;
    @FXML
    private Button deleteButton;

    public ProductsController(AppContext context) {
        this.context = context;
        this.products = context.services().products();
        this.categories = context.services().categories();
    }

    @FXML
    private void initialize() {
        setUpColumns();
        setUpRows();

        categoryFilter.setConverter(new NameConverter());
        loadCategoryFilter();

        searchField.textProperty().addListener((obs, oldText, newText) -> refresh());
        categoryFilter.valueProperty().addListener((obs, oldValue, newValue) -> refresh());
        showInactive.selectedProperty().addListener((obs, oldValue, newValue) -> refresh());
        table.getSelectionModel().selectedItemProperty().addListener((obs, oldValue, newValue) -> updateButtons());

        boolean owner = isOwner();
        for (Button ownerOnly : List.of(addButton, importButton, categoriesButton, editButton, toggleActiveButton,
                deleteButton)) {
            ownerOnly.setVisible(owner);
            ownerOnly.setManaged(owner);
        }
        table.setPlaceholder(new Label());

        root.addEventFilter(KeyEvent.KEY_PRESSED, this::onKeyPressed);
        refresh();
        Platform.runLater(searchField::requestFocus);
    }

    // ------------------------------------------------------------------ table setup

    private void setUpColumns() {
        column(codeColumn, Product::code);
        column(nameColumn, Product::name);
        column(nameMrColumn, p -> p.nameMr() == null ? "" : p.nameMr());
        column(categoryColumn, p -> p.categoryName() == null ? "" : p.categoryName());
        column(unitColumn, p -> p.unit().description());
        column(packColumn, p -> p.packSize() == null ? "" : p.packSize());
        column(rateColumn, p -> Format.money(p.rate()) + " / " + p.unit().shortLabel());
        column(mrpColumn, p -> Format.money(p.mrp()));
        column(statusColumn, p -> p.active() ? "Active" : "Switched off");
    }

    private static void column(TableColumn<Product, String> column, Function<Product, String> value) {
        column.setCellValueFactory(cell -> new ReadOnlyStringWrapper(value.apply(cell.getValue())));
    }

    private void setUpRows() {
        table.setRowFactory(tableView -> {
            TableRow<Product> row = new TableRow<>() {
                @Override
                protected void updateItem(Product product, boolean empty) {
                    super.updateItem(product, empty);
                    pseudoClassStateChanged(INACTIVE, !empty && product != null && !product.active());
                }
            };
            row.setOnMouseClicked(event -> {
                if (event.getButton() == MouseButton.PRIMARY && event.getClickCount() == 2 && !row.isEmpty()
                        && isOwner()) {
                    edit(row.getItem());
                }
            });
            return row;
        });
    }

    // ------------------------------------------------------------------ loading

    private void refresh() {
        Product selected = table.getSelectionModel().getSelectedItem();
        Category category = categoryFilter.getValue();
        Long categoryId = (category == null || category == ALL_CATEGORIES) ? null : category.id();

        List<Product> found = products.search(searchField.getText(), categoryId, showInactive.isSelected(), LIMIT + 1);
        boolean more = found.size() > LIMIT;
        table.getItems().setAll(more ? found.subList(0, LIMIT) : found);

        boolean filtered = !searchField.getText().isBlank() || categoryId != null;
        ((Label) table.getPlaceholder()).setText(filtered ? "No products match your search."
                : isOwner() ? "No products yet. Click \"+ Add product\" or \"Import from Excel…\"." : "No products yet.");

        countLabel.setText(more
                ? "Showing the first " + LIMIT + " products. Type in the search box to narrow the list."
                : found.size() + (found.size() == 1 ? " product" : " products"));

        if (selected != null) {
            reselect(selected.id());
        }
        updateButtons();
    }

    private void loadCategoryFilter() {
        Category current = categoryFilter.getValue();
        List<Category> items = new ArrayList<>();
        items.add(ALL_CATEGORIES);
        items.addAll(categories.list(false));
        categoryFilter.getItems().setAll(items);
        categoryFilter.setValue(current == null ? ALL_CATEGORIES
                : items.stream().filter(c -> c.id() == current.id()).findFirst().orElse(ALL_CATEGORIES));
    }

    private void reselect(long productId) {
        for (Product product : table.getItems()) {
            if (product.id() == productId) {
                table.getSelectionModel().select(product);
                table.scrollTo(product);
                return;
            }
        }
    }

    private void updateButtons() {
        Product selected = table.getSelectionModel().getSelectedItem();
        editButton.setDisable(selected == null);
        deleteButton.setDisable(selected == null);
        toggleActiveButton.setDisable(selected == null);
        toggleActiveButton.setText(selected != null && !selected.active() ? "Switch on" : "Switch off");
    }

    // ------------------------------------------------------------------ actions

    @FXML
    private void addProduct() {
        ProductFormController.open(window(), context, null).ifPresent(this::showSaved);
    }

    @FXML
    private void editSelected() {
        Product selected = table.getSelectionModel().getSelectedItem();
        if (selected != null) {
            edit(selected);
        }
    }

    private void edit(Product product) {
        ProductFormController.open(window(), context, product).ifPresent(this::showSaved);
    }

    private void showSaved(Product saved) {
        refresh();
        reselect(saved.id());
    }

    @FXML
    private void toggleSelected() {
        Product selected = table.getSelectionModel().getSelectedItem();
        if (selected == null) {
            return;
        }
        if (selected.active()) {
            boolean sure = Dialogs.confirm(window(), "Switch off product",
                    "Switch off \"" + selected.displayName() + "\"?\n\n"
                            + "It will be hidden from billing. Old bills are not changed. You can switch it on again later.",
                    "Switch off");
            if (!sure) {
                return;
            }
        }
        products.setActive(selected.id(), !selected.active());
        refresh();
    }

    @FXML
    private void deleteSelected() {
        Product selected = table.getSelectionModel().getSelectedItem();
        if (selected == null) {
            return;
        }
        if (products.isOnAnyBill(selected.id())) {
            if (selected.active() && Dialogs.confirm(window(), "Cannot delete",
                    "\"" + selected.displayName() + "\" is on old bills, so it cannot be deleted.\n\n"
                            + "Switch it off instead? It will be hidden from billing, and old bills stay correct.",
                    "Switch off")) {
                products.setActive(selected.id(), false);
                refresh();
            }
            return;
        }
        if (Dialogs.confirm(window(), "Delete product",
                "Delete \"" + selected.displayName() + "\" (" + selected.code() + ") for ever?", "Delete")) {
            products.delete(selected.id());
            refresh();
        }
    }

    @FXML
    private void openCategories() {
        CategoriesController.open(window(), context);
        loadCategoryFilter();
        refresh();
    }

    @FXML
    private void openImport() {
        ProductImportController.open(window(), context);
        loadCategoryFilter();
        refresh();
    }

    // ------------------------------------------------------------------ keyboard

    private void onKeyPressed(KeyEvent event) {
        if (event.isShortcutDown() && event.getCode() == KeyCode.F) {
            searchField.requestFocus();
            searchField.selectAll();
            event.consume();
        } else if (event.isShortcutDown() && event.getCode() == KeyCode.N && isOwner()) {
            addProduct();
            event.consume();
        } else if (event.getCode() == KeyCode.ESCAPE && searchField.isFocused() && !searchField.getText().isEmpty()) {
            searchField.clear();
            event.consume();
        } else if (event.getCode() == KeyCode.DOWN && searchField.isFocused() && !table.getItems().isEmpty()) {
            table.requestFocus();
            if (table.getSelectionModel().isEmpty()) {
                table.getSelectionModel().selectFirst();
            }
            event.consume();
        } else if (table.isFocused() && isOwner() && event.getCode() == KeyCode.ENTER) {
            editSelected();
            event.consume();
        } else if (table.isFocused() && isOwner() && event.getCode() == KeyCode.DELETE) {
            deleteSelected();
            event.consume();
        }
    }

    private boolean isOwner() {
        return context.session().currentUser().map(User::isOwner).orElse(false);
    }

    private Window window() {
        return root.getScene().getWindow();
    }

    /** Shows a category by its name in drop-down lists. */
    static final class NameConverter extends StringConverter<Category> {
        @Override
        public String toString(Category category) {
            return category == null ? "" : category.name() + (category.active() ? "" : " (switched off)");
        }

        @Override
        public Category fromString(String text) {
            throw new UnsupportedOperationException("Categories are chosen from the list");
        }
    }
}
