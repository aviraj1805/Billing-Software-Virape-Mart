package com.virpemart.billing.ui.products;

import com.virpemart.billing.AppContext;
import com.virpemart.billing.model.Category;
import com.virpemart.billing.service.CategoryService;
import com.virpemart.billing.service.UserFacingException;
import com.virpemart.billing.ui.common.Dialogs;
import com.virpemart.billing.ui.common.Views;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.stage.Stage;
import javafx.stage.Window;

/** The "Categories" window: add, rename and switch categories off or on. Owner only. */
public class CategoriesController {

    private final CategoryService categories;

    @FXML
    private ListView<Category> list;
    @FXML
    private Button renameButton;
    @FXML
    private Button toggleButton;

    private Stage stage;

    public CategoriesController(AppContext context) {
        this.categories = context.services().categories();
    }

    /** Opens the window and waits until it is closed. */
    public static void open(Window owner, AppContext context) {
        Views.Loaded<CategoriesController> view = Views.load("categories.fxml", context);
        view.controller().stage = Views.dialog(owner, "Categories", view.root());
        view.controller().stage.showAndWait();
    }

    @FXML
    private void initialize() {
        list.setCellFactory(listView -> new ListCell<>() {
            @Override
            protected void updateItem(Category category, boolean empty) {
                super.updateItem(category, empty);
                setText(empty || category == null ? null
                        : category.name() + (category.active() ? "" : "   (switched off)"));
                setStyle(empty || category == null || category.active() ? "" : "-fx-opacity: 0.55;");
            }
        });
        list.getSelectionModel().selectedItemProperty().addListener((obs, oldValue, newValue) -> updateButtons());
        reload(null);
    }

    private void reload(Long selectId) {
        list.getItems().setAll(categories.list(true));
        if (selectId != null) {
            list.getItems().stream().filter(c -> c.id() == selectId).findFirst().ifPresent(c -> {
                list.getSelectionModel().select(c);
                list.scrollTo(c);
            });
        }
        updateButtons();
    }

    private void updateButtons() {
        Category selected = list.getSelectionModel().getSelectedItem();
        renameButton.setDisable(selected == null);
        toggleButton.setDisable(selected == null);
        toggleButton.setText(selected != null && !selected.active() ? "Switch on" : "Switch off");
    }

    @FXML
    private void add() {
        Dialogs.askText(stage, "Add category", "Category name:", "").ifPresent(name -> {
            try {
                reload(categories.create(name).id());
            } catch (UserFacingException e) {
                Dialogs.warning(stage, "Add category", e.getMessage());
            }
        });
    }

    @FXML
    private void rename() {
        Category selected = list.getSelectionModel().getSelectedItem();
        if (selected == null) {
            return;
        }
        Dialogs.askText(stage, "Rename category", "New name:", selected.name()).ifPresent(name -> {
            try {
                reload(categories.rename(selected.id(), name).id());
            } catch (UserFacingException e) {
                Dialogs.warning(stage, "Rename category", e.getMessage());
            }
        });
    }

    @FXML
    private void toggle() {
        Category selected = list.getSelectionModel().getSelectedItem();
        if (selected != null) {
            categories.setActive(selected.id(), !selected.active());
            reload(selected.id());
        }
    }

    @FXML
    private void close() {
        stage.close();
    }
}
