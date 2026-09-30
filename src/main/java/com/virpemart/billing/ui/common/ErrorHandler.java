package com.virpemart.billing.ui.common;

import java.nio.file.Path;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.virpemart.billing.service.UserFacingException;

import javafx.application.Platform;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.layout.Region;

/**
 * Catches any error that no screen handled and shows a calm message instead of a crash.
 *
 * <ul>
 *   <li>Expected problems ({@link UserFacingException}) show their own message as a warning.</li>
 *   <li>Unexpected problems are written to the log file with full details, and the user sees a
 *       short message saying where the details are.</li>
 * </ul>
 */
public final class ErrorHandler {

    private static final Logger LOG = LoggerFactory.getLogger(ErrorHandler.class);

    private static volatile Path logsDir;

    private ErrorHandler() {
    }

    /** Installs the handler for every thread, including the JavaFX screen thread. */
    public static void install(Path logsFolder) {
        logsDir = logsFolder;
        Thread.setDefaultUncaughtExceptionHandler((thread, error) -> handle(error));
    }

    /** Logs the error if needed and shows a message on screen. Safe to call from any thread. */
    public static void handle(Throwable error) {
        try {
            Optional<UserFacingException> expected = findUserFacing(error);
            if (expected.isPresent()) {
                LOG.info("User-facing problem: {}", expected.get().getMessage());
                show(AlertType.WARNING, "Please check", expected.get().getMessage());
            } else {
                LOG.error("Unexpected error", error);
                show(AlertType.ERROR, "Something went wrong",
                        "Something went wrong, but your saved bills are safe.\n\n"
                                + "Please try again. If it keeps happening, close and reopen the app.\n\n"
                                + "Details were saved in the log folder:\n" + logsDir);
            }
        } catch (RuntimeException secondary) {
            // Never let the error handler itself crash the app.
            secondary.addSuppressed(error);
            LOG.error("Error while handling an error", secondary);
        }
    }

    /** Shows a simple message box. Safe to call from any thread. */
    public static void show(AlertType type, String title, String message) {
        if (!Platform.isFxApplicationThread()) {
            Platform.runLater(() -> show(type, title, message));
            return;
        }
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.getDialogPane().setMinHeight(Region.USE_PREF_SIZE); // show long messages in full
        alert.showAndWait();
    }

    /** Looks through the chain of causes for a message written for the user. */
    static Optional<UserFacingException> findUserFacing(Throwable error) {
        for (Throwable t = error; t != null; t = t.getCause()) {
            if (t instanceof UserFacingException userFacing) {
                return Optional.of(userFacing);
            }
            if (t.getCause() == t) {
                break;
            }
        }
        return Optional.empty();
    }
}
