package com.virpemart.billing.ui.settings;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import com.virpemart.billing.AppContext;
import com.virpemart.billing.model.PaperSize;
import com.virpemart.billing.model.PrintAfterSave;
import com.virpemart.billing.model.PrinterSetup;
import com.virpemart.billing.model.ShopDetails;
import com.virpemart.billing.model.User;
import com.virpemart.billing.print.ReceiptRenderer;
import com.virpemart.billing.service.PrintService;
import com.virpemart.billing.service.SettingsService;
import com.virpemart.billing.service.UserFacingException;
import com.virpemart.billing.service.ValidationException;
import com.virpemart.billing.ui.common.Background;
import com.virpemart.billing.ui.common.ErrorHandler;
import com.virpemart.billing.ui.common.FormErrors;
import com.virpemart.billing.ui.common.Images;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Control;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputControl;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.util.StringConverter;

/**
 * The Settings screen: shop details printed on the bill, and which printer prints bills.
 * The preview on the right shows a made-up bill with the details as typed, before they are saved.
 * Only the owner can change anything; the helper sees the settings but cannot edit them.
 */
public class SettingsController {

    /** Shown in the printer list for "use the Windows default printer". */
    private static final String DEFAULT_PRINTER = "Windows default printer";
    private static final double DRAW_SCALE = 3;
    private static final double SHOW_SCALE = 1.5;
    /** An A4 bill is shrunk to fit the preview column. */
    private static final double MAX_PREVIEW_WIDTH = 360;

    private final SettingsService settings;
    private final PrintService printing;
    private final boolean owner;

    @FXML
    private Label ownerOnlyLabel;
    @FXML
    private TextField nameField;
    @FXML
    private TextField secondLineField;
    @FXML
    private TextArea addressArea;
    @FXML
    private TextField phoneField;
    @FXML
    private TextArea footerArea;
    @FXML
    private Label shopErrorLabel;
    @FXML
    private Label shopSavedLabel;
    @FXML
    private Button saveShopButton;
    @FXML
    private ComboBox<String> printerBox;
    @FXML
    private Button refreshButton;
    @FXML
    private ComboBox<PaperSize> paperBox;
    @FXML
    private ComboBox<PrintAfterSave> afterSaveBox;
    @FXML
    private Label printerErrorLabel;
    @FXML
    private Label printerSavedLabel;
    @FXML
    private Button testPrintButton;
    @FXML
    private Button savePrinterButton;
    @FXML
    private ImageView previewView;

    private FormErrors shopErrors;
    private FormErrors printerErrors;
    private ShopDetails previewShop;

    public SettingsController(AppContext context) {
        this.settings = context.services().settings();
        this.printing = context.services().printing();
        this.owner = context.session().currentUser().map(User::isOwner).orElse(false);
    }

    @FXML
    private void initialize() {
        shopErrors = new FormErrors(shopErrorLabel, Map.<String, Control>of("name", nameField,
                "secondLine", secondLineField, "address", addressArea, "phone", phoneField, "footer", footerArea));
        printerErrors = new FormErrors(printerErrorLabel, Map.<String, Control>of("paper", paperBox,
                "afterSave", afterSaveBox));

        paperBox.getItems().setAll(PaperSize.values());
        paperBox.setConverter(labels(PaperSize::label));
        afterSaveBox.getItems().setAll(PrintAfterSave.values());
        afterSaveBox.setConverter(labels(PrintAfterSave::label));

        fillShop(settings.shopDetails());
        fillPrinter(settings.printerSetup());
        loadPrinters();

        for (TextInputControl field : List.of(nameField, secondLineField, addressArea, phoneField, footerArea)) {
            field.textProperty().addListener((obs, oldText, newText) -> {
                shopSavedLabel.setText("Not saved yet");
                updatePreview();
            });
        }
        paperBox.valueProperty().addListener((obs, oldPaper, newPaper) -> {
            printerSavedLabel.setText("Not saved yet");
            updatePreview();
        });
        printerBox.valueProperty().addListener((obs, oldName, newName) -> printerSavedLabel.setText("Not saved yet"));
        afterSaveBox.valueProperty().addListener((obs, oldValue, newValue) -> printerSavedLabel.setText("Not saved yet"));
        shopSavedLabel.setText("");
        printerSavedLabel.setText("");

        if (!owner) {
            ownerOnlyLabel.setVisible(true);
            ownerOnlyLabel.setManaged(true);
            for (Control control : List.of(nameField, secondLineField, addressArea, phoneField, footerArea,
                    saveShopButton, printerBox, refreshButton, paperBox, afterSaveBox, testPrintButton,
                    savePrinterButton)) {
                control.setDisable(true);
            }
        }
        updatePreview();
    }

    // ------------------------------------------------------------------ shop details

    private void fillShop(ShopDetails shop) {
        nameField.setText(shop.name());
        secondLineField.setText(orEmpty(shop.secondLine()));
        addressArea.setText(String.join("\n", shop.addressLines()));
        phoneField.setText(orEmpty(shop.phone()));
        footerArea.setText(String.join("\n", shop.footerLines()));
    }

    @FXML
    private void saveShop() {
        shopErrors.clear();
        try {
            ShopDetails saved = settings.saveShopDetails(nameField.getText(), secondLineField.getText(),
                    addressArea.getText(), phoneField.getText(), footerArea.getText());
            fillShop(saved);
            shopSavedLabel.setText("Saved. New bills show these details.");
        } catch (ValidationException e) {
            shopErrors.show(e.getMessage(), e.field());
        } catch (UserFacingException e) {
            shopErrors.show(e.getMessage(), null);
        }
    }

    /**
     * Redraws the made-up bill with the details as typed. While a field is not valid (for example the shop name
     * is empty), the last good preview stays.
     */
    private void updatePreview() {
        try {
            previewShop = SettingsService.check(nameField.getText(), secondLineField.getText(),
                    addressArea.getText(), phoneField.getText(), footerArea.getText());
        } catch (ValidationException e) {
            if (previewShop == null) {
                return;
            }
        }
        PaperSize paper = paperBox.getValue() == null ? PrinterSetup.DEFAULT.paper() : paperBox.getValue();
        Image image = Images.toFx(new ReceiptRenderer(paper).toImage(printing.sampleReceipt(previewShop), DRAW_SCALE));
        previewView.setImage(image);
        previewView.setFitWidth(Math.min(image.getWidth() / DRAW_SCALE * SHOW_SCALE, MAX_PREVIEW_WIDTH));
    }

    // ------------------------------------------------------------------ printer

    private void fillPrinter(PrinterSetup setup) {
        printerBox.getItems().setAll(DEFAULT_PRINTER);
        if (setup.printerName() != null) {
            printerBox.getItems().add(setup.printerName());
        }
        printerBox.setValue(setup.printerName() == null ? DEFAULT_PRINTER : setup.printerName());
        paperBox.setValue(setup.paper());
        afterSaveBox.setValue(setup.afterSave());
    }

    /** Asks Windows for its printers. This can take a moment, so it runs in the background. */
    @FXML
    private void loadPrinters() {
        refreshButton.setDisable(true);
        Background.run("list-printers", printing::printerNames, names -> {
            String chosen = printerBox.getValue();
            String savedText = printerSavedLabel.getText(); // refreshing the list is not a change
            List<String> items = new ArrayList<>();
            items.add(DEFAULT_PRINTER);
            items.addAll(names);
            if (chosen != null && !items.contains(chosen)) {
                items.add(chosen); // saved printer that Windows no longer has: keep it visible
            }
            printerBox.getItems().setAll(items);
            printerBox.setValue(chosen);
            printerSavedLabel.setText(savedText);
        }, () -> refreshButton.setDisable(!owner));
    }

    private PrinterSetup typedSetup() {
        String name = printerBox.getValue();
        return new PrinterSetup(DEFAULT_PRINTER.equals(name) ? null : name, paperBox.getValue(),
                afterSaveBox.getValue());
    }

    @FXML
    private void savePrinter() {
        printerErrors.clear();
        try {
            fillPrinter(settings.savePrinterSetup(typedSetup()));
            loadPrinters();
            printerSavedLabel.setText("Saved.");
        } catch (ValidationException e) {
            printerErrors.show(e.getMessage(), e.field());
        } catch (UserFacingException e) {
            printerErrors.show(e.getMessage(), null);
        }
    }

    /** Prints the made-up bill on the printer chosen above, even before the choice is saved. */
    @FXML
    private void testPrint() {
        printerErrors.clear();
        PrinterSetup setup = typedSetup();
        if (setup.paper() == null) {
            printerErrors.show("Please choose the paper in the printer.", "paper");
            return;
        }
        testPrintButton.setDisable(true);
        printerSavedLabel.setText("Printing a test bill...");
        Background.run("test-print", () -> {
            printing.printTest(setup);
            return null;
        }, done -> {
            testPrintButton.setDisable(false);
            printerSavedLabel.setText("Test bill sent to the printer. Check it, then save these settings.");
        }, error -> {
            testPrintButton.setDisable(false);
            printerSavedLabel.setText("");
            ErrorHandler.handle(error);
        });
    }

    // ------------------------------------------------------------------ helpers

    private static String orEmpty(String text) {
        return text == null ? "" : text;
    }

    private static <T> StringConverter<T> labels(Function<T, String> label) {
        return new StringConverter<>() {
            @Override
            public String toString(T value) {
                return value == null ? "" : label.apply(value);
            }

            @Override
            public T fromString(String text) {
                throw new UnsupportedOperationException("Choose from the list.");
            }
        };
    }
}
