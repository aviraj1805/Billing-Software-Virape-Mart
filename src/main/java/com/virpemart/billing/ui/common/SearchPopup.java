package com.virpemart.billing.ui.common;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

import javafx.geometry.Bounds;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.stage.Popup;

/**
 * A drop-down list of matches under a text box, for choosing with the keyboard:
 * type to search, Up/Down to move, Enter to choose, Esc to close.
 *
 * @param <T> the kind of item, for example a product
 */
public final class SearchPopup<T> {

    private static final int VISIBLE_ROWS = 9;
    private static final double ROW_HEIGHT = 34;

    private final TextField field;
    private final Function<String, List<T>> search;
    private final Consumer<T> onChoose;
    private final Popup popup = new Popup();
    private final ListView<T> list = new ListView<>();
    private boolean quiet;

    /**
     * @param field    the text box to attach to
     * @param search   finds items for the typed text (runs on every key press, so it must be fast)
     * @param text     how one item is shown in the list
     * @param onChoose called when the user chooses an item
     */
    public SearchPopup(TextField field, Function<String, List<T>> search, Function<T, String> text, Consumer<T> onChoose) {
        this.field = field;
        this.search = search;
        this.onChoose = onChoose;

        list.getStyleClass().add("search-popup");
        list.setPlaceholder(new Label("No match. Check the spelling."));
        list.setCellFactory(listView -> new ListCell<>() {
            @Override
            protected void updateItem(T item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : text.apply(item));
            }
        });
        list.setOnMouseClicked(event -> {
            if (event.getButton() == MouseButton.PRIMARY && list.getSelectionModel().getSelectedItem() != null) {
                choose(list.getSelectionModel().getSelectedItem());
            }
        });
        list.setFocusTraversable(false);
        popup.getContent().add(list);
        popup.setAutoHide(true);

        field.textProperty().addListener((obs, oldText, newText) -> {
            if (!quiet) {
                refresh();
            }
        });
        field.addEventFilter(KeyEvent.KEY_PRESSED, this::onKey);
        field.focusedProperty().addListener((obs, was, is) -> {
            if (!is) {
                popup.hide();
            }
        });
    }

    /** Sets the text without searching, for example to clear the box after choosing. */
    public void setTextQuietly(String text) {
        quiet = true;
        try {
            field.setText(text);
        } finally {
            quiet = false;
        }
        popup.hide();
    }

    public boolean isShowing() {
        return popup.isShowing();
    }

    public void hide() {
        popup.hide();
    }

    private void refresh() {
        String text = field.getText();
        if (text == null || text.isBlank()) {
            popup.hide();
            return;
        }
        List<T> found = search.apply(text);
        list.getItems().setAll(found);
        if (!found.isEmpty()) {
            list.getSelectionModel().selectFirst();
            list.scrollTo(0);
        }
        list.setPrefWidth(field.getWidth());
        list.setPrefHeight(Math.max(1, Math.min(found.size(), VISIBLE_ROWS)) * ROW_HEIGHT + 4);
        if (!popup.isShowing() && field.getScene() != null) {
            Bounds bounds = field.localToScreen(field.getBoundsInLocal());
            popup.show(field, bounds.getMinX(), bounds.getMaxY());
        }
    }

    private void onKey(KeyEvent event) {
        switch (event.getCode()) {
            case DOWN -> {
                if (!popup.isShowing()) {
                    refresh();
                } else {
                    list.getSelectionModel().selectNext();
                    list.scrollTo(list.getSelectionModel().getSelectedIndex());
                }
                event.consume();
            }
            case UP -> {
                if (popup.isShowing()) {
                    list.getSelectionModel().selectPrevious();
                    list.scrollTo(list.getSelectionModel().getSelectedIndex());
                    event.consume();
                }
            }
            case ENTER -> {
                if (popup.isShowing() && list.getSelectionModel().getSelectedItem() != null) {
                    choose(list.getSelectionModel().getSelectedItem());
                    event.consume();
                }
            }
            case ESCAPE -> {
                if (popup.isShowing()) {
                    popup.hide();
                    event.consume();
                }
            }
            default -> {
                // other keys type into the box as usual
            }
        }
    }

    private void choose(T item) {
        popup.hide();
        onChoose.accept(item);
    }
}
