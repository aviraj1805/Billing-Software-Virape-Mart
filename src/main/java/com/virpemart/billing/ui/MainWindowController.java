package com.virpemart.billing.ui;

import com.virpemart.billing.AppContext;
import com.virpemart.billing.config.AppInfo;
import com.virpemart.billing.model.User;

import javafx.fxml.FXML;
import javafx.scene.control.Label;

/**
 * Controller for {@code main-window.fxml}. Until the real screens arrive, it shows whether the
 * database and session started correctly.
 */
public class MainWindowController {

    private final AppContext context;

    @FXML
    private Label versionLabel;
    @FXML
    private Label databaseLabel;
    @FXML
    private Label dataFolderLabel;
    @FXML
    private Label userLabel;

    public MainWindowController(AppContext context) {
        this.context = context;
    }

    @FXML
    private void initialize() {
        versionLabel.setText("Version " + AppInfo.version());
        databaseLabel.setText("Database ready (schema version " + context.schemaVersion() + ")");
        dataFolderLabel.setText("Data folder: " + context.paths().baseDir()
                + (context.paths().isOverridden() ? "  [development]" : ""));
        userLabel.setText(context.session().currentUser()
                .map(User::displayName)
                .map(name -> "Signed in: " + name)
                .orElse("Nobody signed in yet (the login screen arrives in Phase 7)"));
    }
}
