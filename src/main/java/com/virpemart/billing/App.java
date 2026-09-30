package com.virpemart.billing;

import com.virpemart.billing.config.AppInfo;
import com.virpemart.billing.ui.common.ErrorHandler;
import com.virpemart.billing.ui.common.Views;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert.AlertType;
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
        if (startupError != null) {
            ErrorHandler.show(AlertType.ERROR, "Virpe Mart Billing", startupError.getMessage());
            Platform.exit();
            return;
        }
        ErrorHandler.install(context.paths().logsDir());

        Parent root = Views.load("main-window.fxml", context).root();
        Scene scene = Views.scene(root);

        stage.setTitle(AppInfo.name() + " " + AppInfo.version());
        stage.setScene(scene);
        stage.setWidth(INITIAL_WIDTH);
        stage.setHeight(INITIAL_HEIGHT);
        stage.setMinWidth(MIN_WIDTH);
        stage.setMinHeight(MIN_HEIGHT);
        stage.show();
    }

    @Override
    public void stop() throws Exception {
        if (context != null) {
            context.close();
        }
    }
}
