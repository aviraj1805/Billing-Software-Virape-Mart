package com.virpemart.billing.ui.common;

import java.util.Map;

import javafx.scene.control.Control;
import javafx.scene.control.Label;
import javafx.stage.Stage;

/**
 * Shows a form's error message and highlights the input it is about.
 * Field names match {@link com.virpemart.billing.service.ValidationException#field()}.
 */
public final class FormErrors {

    private static final String ERROR_STYLE = "field-error";

    private final Label errorLabel;
    private final Map<String, Control> fields;

    /**
     * @param errorLabel label under the form where messages appear (starts hidden)
     * @param fields     input controls by field name
     */
    public FormErrors(Label errorLabel, Map<String, Control> fields) {
        this.errorLabel = errorLabel;
        this.fields = fields;
        errorLabel.setVisible(false);
        errorLabel.setManaged(false);
    }

    /** Shows the message; highlights and focuses the field if it is known. */
    public void show(String message, String field) {
        errorLabel.setText(message);
        errorLabel.setVisible(true);
        errorLabel.setManaged(true);
        Control control = field == null ? null : fields.get(field);
        if (control != null) {
            control.getStyleClass().add(ERROR_STYLE);
            control.requestFocus();
        }
        resizeWindow();
    }

    /** Hides the message and all highlights. */
    public void clear() {
        errorLabel.setVisible(false);
        errorLabel.setManaged(false);
        for (Control control : fields.values()) {
            control.getStyleClass().remove(ERROR_STYLE);
        }
        resizeWindow();
    }

    private void resizeWindow() {
        if (errorLabel.getScene() != null && errorLabel.getScene().getWindow() instanceof Stage stage) {
            stage.sizeToScene();
        }
    }
}
