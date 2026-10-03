package com.virpemart.billing.ui.common;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URL;
import java.util.List;
import java.util.Objects;

import com.virpemart.billing.AppContext;

import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;

/** Loads screens from FXML files and opens dialog windows with the app's styles. */
public final class Views {

    private static final String STYLESHEET = "/css/app.css";
    private static final List<String> ICON_SIZES = List.of("16", "32", "48", "256");
    private static List<Image> icons;

    /** A loaded screen and its controller. */
    public record Loaded<C>(Parent root, C controller) {
    }

    private Views() {
    }

    /** Loads {@code /fxml/<fileName>}, creating its controller with the app context. */
    public static <C> Loaded<C> load(String fileName, AppContext context) {
        FXMLLoader loader = new FXMLLoader(resource("/fxml/" + fileName));
        loader.setControllerFactory(new ControllerFactory(context));
        try {
            Parent root = loader.load();
            return new Loaded<>(root, loader.getController());
        } catch (IOException e) {
            throw new UncheckedIOException("Could not load screen " + fileName, e);
        }
    }

    /** Creates a window that stays on top of {@code owner} and blocks it until closed. */
    public static Stage dialog(Window owner, String title, Parent root) {
        Stage stage = new Stage();
        stage.initOwner(owner);
        stage.initModality(Modality.WINDOW_MODAL);
        stage.setTitle(title);
        stage.getIcons().setAll(appIcons());
        stage.setScene(scene(root));
        return stage;
    }

    /** The app's logo in several sizes, for window title bars and the taskbar. Loaded once. */
    public static List<Image> appIcons() {
        if (icons == null) {
            icons = ICON_SIZES.stream().map(size -> new Image(resource("/icons/app-" + size + ".png").toExternalForm()))
                    .toList();
        }
        return icons;
    }

    /** A scene with the app's stylesheet. */
    public static Scene scene(Parent root) {
        Scene scene = new Scene(root);
        scene.getStylesheets().add(resource(STYLESHEET).toExternalForm());
        return scene;
    }

    private static URL resource(String path) {
        return Objects.requireNonNull(Views.class.getResource(path), () -> "Missing resource: " + path);
    }
}
