package com.virpemart.billing.ui;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

import com.virpemart.billing.AppContext;
import com.virpemart.billing.config.AppInfo;
import com.virpemart.billing.model.User;
import com.virpemart.billing.ui.common.Format;
import com.virpemart.billing.ui.common.Views;

import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.Toggle;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

/**
 * The main window: top bar, menu on the left and the chosen screen in the middle.
 * Each screen is loaded the first time it is opened and then kept, so switching is instant.
 */
public class MainWindowController {

    private final AppContext context;
    private final Map<Toggle, Node> screens = new HashMap<>();

    @FXML
    private ToggleGroup navGroup;
    @FXML
    private ToggleButton billingNav;
    @FXML
    private ToggleButton productsNav;
    @FXML
    private ToggleButton customersNav;
    @FXML
    private ToggleButton historyNav;
    @FXML
    private ToggleButton settingsNav;
    @FXML
    private StackPane content;
    @FXML
    private Label userLabel;
    @FXML
    private Label dateLabel;
    @FXML
    private Label versionLabel;
    @FXML
    private Label devLabel;

    public MainWindowController(AppContext context) {
        this.context = context;
    }

    @FXML
    private void initialize() {
        userLabel.setText(context.session().currentUser()
                .map(User::displayName)
                .orElse("Nobody signed in"));
        dateLabel.setText(Format.date(LocalDate.now(context.clock())));
        versionLabel.setText("Version " + AppInfo.version());
        devLabel.setVisible(context.paths().isOverridden());
        devLabel.setManaged(context.paths().isOverridden());

        navGroup.selectedToggleProperty().addListener((obs, oldToggle, newToggle) -> {
            if (newToggle == null) {
                oldToggle.setSelected(true); // one screen is always selected
            } else {
                content.getChildren().setAll(screens.computeIfAbsent(newToggle, this::createScreen));
            }
        });
        // Products is the first finished screen; Billing becomes the start screen in Phase 4.
        productsNav.setSelected(true);
    }

    private Node createScreen(Toggle toggle) {
        if (toggle == productsNav) {
            return Views.load("products.fxml", context).root();
        }
        if (toggle == billingNav) {
            return comingSoon("Billing", "The billing screen arrives in Phase 4.");
        }
        if (toggle == customersNav) {
            return comingSoon("Customers", "Customer accounts arrive in Phase 3.");
        }
        if (toggle == historyNav) {
            return comingSoon("History & Reports", "Bill history and reports arrive in Phase 6.");
        }
        return comingSoon("Settings", "Shop details and printer settings arrive in Phase 5.");
    }

    private static Node comingSoon(String title, String message) {
        Label heading = new Label(title);
        heading.getStyleClass().add("page-title");
        Label text = new Label(message);
        text.getStyleClass().add("muted");
        VBox box = new VBox(8, heading, text);
        box.setAlignment(Pos.CENTER);
        return box;
    }
}
