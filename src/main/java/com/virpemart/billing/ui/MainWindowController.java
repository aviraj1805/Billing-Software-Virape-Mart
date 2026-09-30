package com.virpemart.billing.ui;

import com.virpemart.billing.config.AppInfo;

import javafx.fxml.FXML;
import javafx.scene.control.Label;

/** Controller for {@code main-window.fxml}. For now it only shows the version. */
public class MainWindowController {

    @FXML
    private Label versionLabel;

    @FXML
    private void initialize() {
        versionLabel.setText("Version " + AppInfo.version());
    }
}
