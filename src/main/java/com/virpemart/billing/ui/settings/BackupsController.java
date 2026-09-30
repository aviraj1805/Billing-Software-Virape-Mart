package com.virpemart.billing.ui.settings;

import java.io.File;
import java.nio.file.Files;

import com.virpemart.billing.AppContext;
import com.virpemart.billing.model.BackupInfo;
import com.virpemart.billing.service.BackupService;
import com.virpemart.billing.service.UserFacingException;
import com.virpemart.billing.ui.common.Background;
import com.virpemart.billing.ui.common.Dialogs;
import com.virpemart.billing.ui.common.ErrorHandler;
import com.virpemart.billing.ui.common.Format;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Window;

/**
 * The Backups card in Settings: when the last automatic backup was made, "Back up now" to any folder (for example a
 * pendrive) and "Restore a backup". A restore is finished when the app is opened again.
 */
public class BackupsController {

    private final BackupService backups;

    @FXML
    private VBox root;
    @FXML
    private Label lastBackupLabel;
    @FXML
    private Label folderLabel;
    @FXML
    private Label statusLabel;
    @FXML
    private Button backupButton;
    @FXML
    private Button restoreButton;

    public BackupsController(AppContext context) {
        this.backups = context.services().backups();
    }

    @FXML
    private void initialize() {
        folderLabel.setText("Backups folder on this laptop: " + backups.backupsFolder());
        showLastBackup();
        // Refresh the time whenever Settings is opened again (the card is put back into the window).
        root.sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (newScene != null) {
                showLastBackup();
            }
        });
    }

    private void showLastBackup() {
        lastBackupLabel.setText(backups.lastAutomaticBackup()
                .map(time -> "Last automatic backup: " + Format.dateTime(time))
                .orElse("No automatic backup yet. One is made each time the app opens."));
    }

    /** Copies the data to a folder the owner chooses. Runs in the background. */
    @FXML
    private void backupNow() {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Choose the pendrive or folder for the backup");
        File folder = chooser.showDialog(window());
        if (folder == null) {
            return;
        }
        backupButton.setDisable(true);
        statusLabel.setText("Making the backup...");
        Background.run("backup-now", () -> backups.backupNow(folder.toPath()), file -> {
            backupButton.setDisable(false);
            statusLabel.setText("Backup saved: " + file);
        }, error -> {
            backupButton.setDisable(false);
            statusLabel.setText("");
            ErrorHandler.handle(error);
        });
    }

    /**
     * Restores a backup the owner chooses: the file is checked, the owner confirms with its date and bill count,
     * then the app closes. The backup is put in place when the app is opened again; the current data is kept.
     */
    @FXML
    private void restore() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Choose the backup to restore");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Virpe Mart backups (*.db)", "*.db"));
        if (Files.isDirectory(backups.backupsFolder())) {
            chooser.setInitialDirectory(backups.backupsFolder().toFile());
        }
        File file = chooser.showOpenDialog(window());
        if (file == null) {
            return;
        }
        try {
            BackupInfo info = backups.checkBackup(file.toPath());
            String bills = info.lastBillAt() == null ? "It has no bills."
                    : "It has " + info.billCount() + " bills; the last one is from "
                    + Format.dateTime(info.lastBillAt()) + ".";
            if (!Dialogs.confirm(window(), "Restore a backup", "Restore the backup \"" + file.getName()
                    + "\"?\n\nIt was saved on " + Format.dateTime(info.savedAt()) + ". " + bills
                    + "\n\nEverything done after that (bills, payments, products, customers) will not be in the app."
                    + " Today's data is kept in the backups folder, so this can be undone."
                    + "\n\nThe app will close, so finish or clear any open or held bill first."
                    + " Open the app again to finish.", "Restore and close")) {
                return;
            }
            backups.restore(file.toPath());
        } catch (UserFacingException e) {
            Dialogs.warning(window(), "Restore a backup", e.getMessage());
            return;
        }
        Dialogs.info(window(), "Restore a backup", "The backup is ready. The app will close now.\n\n"
                + "Open Virpe Mart Billing again to finish the restore.");
        Platform.exit();
    }

    private Window window() {
        return root.getScene().getWindow();
    }
}
