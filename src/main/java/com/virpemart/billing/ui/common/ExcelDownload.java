package com.virpemart.billing.ui.common;

import java.io.File;
import java.nio.file.Path;
import java.util.function.Function;

import javafx.stage.FileChooser;
import javafx.stage.Window;

/**
 * The "Download to Excel…" buttons: asks where to save the file, saves it away from the screen thread, then says
 * how many rows were saved and where. The saving itself is done by {@link com.virpemart.billing.service.ExportService}.
 */
public final class ExcelDownload {

    private ExcelDownload() {
    }

    /**
     * @param title    window title, for example "Download the product list"
     * @param fileName suggested file name, ending in .csv
     * @param what     what a row is, in plural, for the message: "products" or "customers"
     * @param save     saves the file and returns the number of rows; may throw a message for the user
     */
    public static void start(Window owner, String title, String fileName, String what, Function<Path, Integer> save) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(title);
        chooser.setInitialFileName(fileName);
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV file (opens in Excel)", "*.csv"));
        File target = chooser.showSaveDialog(owner);
        if (target == null) {
            return;
        }
        Path file = target.toPath();
        Background.run("download-" + what, () -> save.apply(file),
                count -> Dialogs.info(owner, title, "Saved " + count + " " + what + " in " + file.getFileName()
                        + "\n(folder: " + file.getParent() + ").\n\nDouble-click the file to open it in Excel."),
                () -> { });
    }
}
