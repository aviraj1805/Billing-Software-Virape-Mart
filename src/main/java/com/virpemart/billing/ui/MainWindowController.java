package com.virpemart.billing.ui;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

import com.virpemart.billing.AppContext;
import com.virpemart.billing.config.AppInfo;
import com.virpemart.billing.model.User;
import com.virpemart.billing.ui.billing.BillingController;
import com.virpemart.billing.ui.common.Dialogs;
import com.virpemart.billing.ui.common.Format;
import com.virpemart.billing.ui.common.Views;
import com.virpemart.billing.ui.history.BillHistoryController;
import com.virpemart.billing.ui.history.ReportsController;

import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.Toggle;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.StackPane;

/**
 * The main window: top bar, menu on the left and the chosen screen in the middle.
 * Each screen is loaded the first time it is opened and then kept, so switching is instant.
 */
public class MainWindowController {

    private final AppContext context;
    private final Map<Toggle, Node> screens = new HashMap<>();
    private BillingController billingController;
    private BillHistoryController billHistoryController;
    private ReportsController reportsController;
    private TabPane historyTabs;

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
                if (newToggle == historyNav) {
                    refreshHistoryTab();
                }
            }
        });
        billingNav.setSelected(true); // billing is what the shop uses most
    }

    /**
     * Asks before closing if bills would be lost. Called when the window's close button is clicked.
     *
     * @return true if the app may close
     */
    public boolean confirmClose() {
        if (billingController == null) {
            return true;
        }
        return billingController.unsavedWork()
                .map(work -> Dialogs.confirm(content.getScene().getWindow(), "Close Virpe Mart",
                        "Not saved yet: " + work + ".\nThese are lost if the app closes now.\n\n"
                                + "Close anyway?", "Close and lose them"))
                .orElse(true);
    }

    private Node createScreen(Toggle toggle) {
        if (toggle == productsNav) {
            return Views.load("products.fxml", context).root();
        }
        if (toggle == billingNav) {
            Views.Loaded<BillingController> billing = Views.load("billing.fxml", context);
            billingController = billing.controller();
            return billing.root();
        }
        if (toggle == customersNav) {
            return Views.load("customers.fxml", context).root();
        }
        if (toggle == historyNav) {
            return createHistoryScreen();
        }
        return Views.load("settings.fxml", context).root();
    }

    /** Bills tab for everyone; Reports tab for the owner only (the report service checks this too). */
    private Node createHistoryScreen() {
        Views.Loaded<BillHistoryController> bills = Views.load("bill-history.fxml", context);
        billHistoryController = bills.controller();
        historyTabs = new TabPane(new Tab("Bills", bills.root()));
        historyTabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        boolean owner = context.session().currentUser().map(User::isOwner).orElse(false);
        if (owner) {
            Views.Loaded<ReportsController> reports = Views.load("reports.fxml", context);
            reportsController = reports.controller();
            historyTabs.getTabs().add(new Tab("Reports", reports.root()));
        }
        historyTabs.getSelectionModel().selectedIndexProperty().addListener((obs, oldTab, newTab) ->
                refreshHistoryTab());
        return historyTabs;
    }

    /** Bills and figures change while billing, so the open tab loads again each time it is shown. */
    private void refreshHistoryTab() {
        if (historyTabs.getSelectionModel().getSelectedIndex() == 1 && reportsController != null) {
            reportsController.refresh();
        } else {
            billHistoryController.refresh();
        }
    }
}
