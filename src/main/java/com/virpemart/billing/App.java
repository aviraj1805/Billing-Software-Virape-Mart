package com.virpemart.billing;

import java.io.IOException;
import java.util.Optional;

import com.virpemart.billing.config.AppInfo;
import com.virpemart.billing.db.PendingRestore;
import com.virpemart.billing.model.BackupInfo;
import com.virpemart.billing.ui.MainWindowController;
import com.virpemart.billing.ui.common.ErrorHandler;
import com.virpemart.billing.ui.common.Format;
import com.virpemart.billing.ui.common.Views;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar.ButtonData;
import javafx.scene.control.ButtonType;
import javafx.stage.Stage;

/**
 * The JavaFX application.
 *
 * <p>{@link #init()} runs the startup steps (folders, logging, lock, database) before any window
 * opens. {@link #start(Stage)} shows the main window, or a clear message if startup failed.
 * {@link #stop()} releases resources when the app closes.
 */
public class App extends Application {

    private static final double INITIAL_WIDTH = 1200;
    private static final double INITIAL_HEIGHT = 750;
    private static final double MIN_WIDTH = 1000;
    private static final double MIN_HEIGHT = 600;

    private AppContext context;
    private StartupException startupError;

    @Override
    public void init() {
        try {
            context = Startup.start();
        } catch (StartupException e) {
            startupError = e;
        }
    }

    @Override
    public void start(Stage stage) {
        if (startupError instanceof DamagedDataException damaged) {
            offerNewestBackup(damaged);
            Platform.exit();
            return;
        }
        if (startupError != null) {
            ErrorHandler.show(AlertType.ERROR, "Virpe Mart Billing", startupError.getMessage());
            Platform.exit();
            return;
        }
        ErrorHandler.install(context.paths().logsDir());

        Views.Loaded<MainWindowController> main = Views.load("main-window.fxml", context);
        Parent root = main.root();
        Scene scene = Views.scene(root);
        stage.setOnCloseRequest(event -> {
            if (!main.controller().confirmClose()) {
                event.consume(); // stay open
            }
        });

        stage.setTitle(AppInfo.name() + " " + AppInfo.version());
        stage.setScene(scene);
        stage.setWidth(INITIAL_WIDTH);
        stage.setHeight(INITIAL_HEIGHT);
        stage.setMinWidth(MIN_WIDTH);
        stage.setMinHeight(MIN_HEIGHT);
        stage.setMaximized(true); // the shop laptop uses the app full screen
        stage.show();
    }

    /**
     * The data file is damaged: offer to put the newest good backup in place. The damaged file is kept in the backups
     * folder, never deleted.
     */
    private static void offerNewestBackup(DamagedDataException damaged) {
        Optional<BackupInfo> backup = damaged.newestGoodBackup();
        if (backup.isEmpty()) {
            ErrorHandler.show(AlertType.ERROR, "Virpe Mart Billing", damaged.getMessage()
                    + "\n\nNo good backup was found on this laptop. If you have a backup on a pendrive, please ask "
                    + "for help to restore it. Do not delete anything in the folder:\n" + damaged.databaseFile().getParent());
            return;
        }
        BackupInfo info = backup.get();
        String lastBill = info.lastBillAt() == null ? "no bills" : info.billCount() + " bills, the last one on "
                + Format.dateTime(info.lastBillAt());
        ButtonType restore = new ButtonType("Restore this backup", ButtonData.OK_DONE);
        ButtonType close = new ButtonType("Close", ButtonData.CANCEL_CLOSE);
        Alert alert = new Alert(AlertType.ERROR, damaged.getMessage()
                + "\n\nThe newest good backup was saved on " + Format.dateTime(info.savedAt()) + " (" + lastBill + ")."
                + "\nBills and payments made after that are not in it. Check them against the paper book."
                + "\n\nRestore this backup?", restore, close);
        alert.setTitle("Virpe Mart Billing");
        alert.setHeaderText(null);
        ((Button) alert.getDialogPane().lookupButton(restore)).setDefaultButton(false);
        ((Button) alert.getDialogPane().lookupButton(close)).setDefaultButton(true);
        if (alert.showAndWait().filter(restore::equals).isEmpty()) {
            return;
        }
        try {
            PendingRestore.stage(info.file(), damaged.databaseFile(),
                    "backup " + info.file().getFileName() + " (the data file was damaged)");
            ErrorHandler.show(AlertType.INFORMATION, "Virpe Mart Billing",
                    "The backup is ready. Open Virpe Mart Billing again to finish.");
        } catch (IOException e) {
            ErrorHandler.show(AlertType.ERROR, "Virpe Mart Billing",
                    "The backup could not be copied: " + e.getMessage());
        }
    }

    @Override
    public void stop() throws Exception {
        if (context != null) {
            context.services().backups().backupOnClose();
            context.close();
        }
    }
}
