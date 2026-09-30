package com.virpemart.billing;

import java.io.IOException;
import java.net.URL;
import java.util.Objects;

import com.virpemart.billing.config.AppInfo;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

/**
 * The JavaFX application: loads the main window layout and shows it.
 *
 * <p>Startup steps such as logging, database checks and login will be added here in later phases.
 */
public class App extends Application {

    private static final double INITIAL_WIDTH = 1024;
    private static final double INITIAL_HEIGHT = 700;

    @Override
    public void start(Stage stage) throws IOException {
        Parent root = FXMLLoader.load(resource("/fxml/main-window.fxml"));

        Scene scene = new Scene(root, INITIAL_WIDTH, INITIAL_HEIGHT);
        scene.getStylesheets().add(resource("/css/app.css").toExternalForm());

        stage.setTitle(AppInfo.name() + " " + AppInfo.version());
        stage.setScene(scene);
        stage.show();
    }

    private static URL resource(String path) {
        return Objects.requireNonNull(App.class.getResource(path), () -> "Missing resource: " + path);
    }
}
