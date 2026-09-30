package com.virpemart.billing;

import java.io.IOException;
import java.net.URL;
import java.util.Objects;

import com.virpemart.billing.config.AppInfo;
import com.virpemart.billing.ui.common.ControllerFactory;
import com.virpemart.billing.ui.common.ErrorHandler;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
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

    private static final double INITIAL_WIDTH = 1024;
    private static final double INITIAL_HEIGHT = 700;

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
    public void start(Stage stage) throws IOException {
        if (startupError != null) {
            ErrorHandler.show(AlertType.ERROR, "Virpe Mart Billing", startupError.getMessage());
            Platform.exit();
            return;
        }
        ErrorHandler.install(context.paths().logsDir());

        FXMLLoader loader = new FXMLLoader(resource("/fxml/main-window.fxml"));
        loader.setControllerFactory(new ControllerFactory(context));
        Parent root = loader.load();

        Scene scene = new Scene(root, INITIAL_WIDTH, INITIAL_HEIGHT);
        scene.getStylesheets().add(resource("/css/app.css").toExternalForm());

        stage.setTitle(AppInfo.name() + " " + AppInfo.version());
        stage.setScene(scene);
        stage.show();
    }

    @Override
    public void stop() throws IOException {
        if (context != null) {
            context.close();
        }
    }

    private static URL resource(String path) {
        return Objects.requireNonNull(App.class.getResource(path), () -> "Missing resource: " + path);
    }
}
