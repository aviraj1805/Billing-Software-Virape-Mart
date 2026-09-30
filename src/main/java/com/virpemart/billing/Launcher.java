package com.virpemart.billing;

import javafx.application.Application;

/**
 * Program entry point used by the packaged application.
 *
 * <p>When JavaFX is loaded from the classpath (as in our packaged app), Java refuses to
 * start a main class that itself extends {@link Application}. This tiny class avoids that
 * restriction by starting {@link App} indirectly.
 */
public final class Launcher {

    private Launcher() {
    }

    public static void main(String[] args) {
        Application.launch(App.class, args);
    }
}
