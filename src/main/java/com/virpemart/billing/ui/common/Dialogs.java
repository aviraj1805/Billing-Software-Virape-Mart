package com.virpemart.billing.ui.common;

import java.util.Optional;

import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar.ButtonData;
import javafx.scene.control.ButtonType;
import javafx.scene.control.TextInputDialog;
import javafx.stage.Window;

/** Simple message boxes used by the screens. */
public final class Dialogs {

    private Dialogs() {
    }

    /**
     * Asks a yes/no question. "Cancel" is the default button, so pressing Enter by accident
     * never deletes, switches off or saves a mistake. The user must click the confirm button.
     *
     * @param confirmText text on the "yes" button, for example "Switch off"
     * @return true if the user clicked the confirm button
     */
    public static boolean confirm(Window owner, String title, String message, String confirmText) {
        ButtonType yes = new ButtonType(confirmText, ButtonData.OK_DONE);
        ButtonType no = new ButtonType("Cancel", ButtonData.CANCEL_CLOSE);
        Alert alert = new Alert(AlertType.CONFIRMATION, message, yes, no);
        alert.initOwner(owner);
        alert.setTitle(title);
        alert.setHeaderText(null);
        Button yesButton = (Button) alert.getDialogPane().lookupButton(yes);
        Button noButton = (Button) alert.getDialogPane().lookupButton(no);
        yesButton.setDefaultButton(false);
        noButton.setDefaultButton(true);
        alert.setOnShown(event -> noButton.requestFocus());
        return alert.showAndWait().filter(yes::equals).isPresent();
    }

    /**
     * Asks a yes/no question where "yes" is harmless, such as "Print the bill?". Here "yes" is the default
     * button, so Enter says yes and Esc says no. Use {@link #confirm} for anything that saves, deletes or
     * switches off.
     *
     * @return true if the user chose {@code yesText}
     */
    public static boolean question(Window owner, String title, String message, String yesText, String noText) {
        ButtonType yes = new ButtonType(yesText, ButtonData.OK_DONE);
        ButtonType no = new ButtonType(noText, ButtonData.CANCEL_CLOSE);
        Alert alert = new Alert(AlertType.CONFIRMATION, message, yes, no);
        alert.initOwner(owner);
        alert.setTitle(title);
        alert.setHeaderText(null);
        Button yesButton = (Button) alert.getDialogPane().lookupButton(yes);
        alert.setOnShown(event -> yesButton.requestFocus());
        return alert.showAndWait().filter(yes::equals).isPresent();
    }

    public static void info(Window owner, String title, String message) {
        show(owner, AlertType.INFORMATION, title, message);
    }

    public static void warning(Window owner, String title, String message) {
        show(owner, AlertType.WARNING, title, message);
    }

    /** Asks for one line of text. Returns empty if the user cancels. */
    public static Optional<String> askText(Window owner, String title, String prompt, String initialValue) {
        TextInputDialog dialog = new TextInputDialog(initialValue);
        dialog.initOwner(owner);
        dialog.setTitle(title);
        dialog.setHeaderText(null);
        dialog.setContentText(prompt);
        return dialog.showAndWait();
    }

    private static void show(Window owner, AlertType type, String title, String message) {
        Alert alert = new Alert(type, message, ButtonType.OK);
        alert.initOwner(owner);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.showAndWait();
    }
}
