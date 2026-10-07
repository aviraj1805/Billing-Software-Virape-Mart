package com.virpemart.billing.ui.common;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

import javafx.geometry.Bounds;
import javafx.geometry.Rectangle2D;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.control.skin.VirtualFlow;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.text.Font;
import javafx.scene.text.Text;
import javafx.stage.Popup;
import javafx.stage.Screen;

/**
 * A drop-down list of matches under a text box, for choosing with the keyboard or the mouse:
 * type to search, Up/Down (or Page Up/Page Down) to move, Enter to choose, Esc to close. A click on a row
 * chooses it; the scroll bar and the mouse wheel only scroll.
 *
 * <p>Every row has the same fixed height, so whole rows always fit. The list is wide enough for its longest row (up to
 * the width of the screen; near the right edge it opens further left) and as tall as the space below the text box
 * allows. It never scrolls sideways: a row too long for the screen ends in "...".
 *
 * @param <T> the kind of item, for example a product
 */
public final class SearchPopup<T> {

    private static final int VISIBLE_ROWS = 12;
    private static final double ROW_HEIGHT = 36;
    /** Must match the font size of {@code .search-popup} in app.css; used to measure the longest row. */
    private static final double FONT_SIZE = 16;
    /** Cell padding, border and room for the vertical scroll bar, added to the measured text width. */
    private static final double EXTRA_WIDTH = 48;
    private static final double SCREEN_MARGIN = 8;
    /** The list's border and padding above and below the rows. */
    private static final double EXTRA_HEIGHT = 8;

    private final TextField field;
    private final Function<String, List<T>> search;
    private final Function<T, String> text;
    private final Consumer<T> onChoose;
    private final Popup popup = new Popup();
    private final ListView<T> list = new ListView<>();
    private final Text measure = new Text();
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
        this.text = text;
        this.onChoose = onChoose;

        measure.setFont(Font.font("Segoe UI", FONT_SIZE));
        list.getStyleClass().add("search-popup");
        list.setPlaceholder(new Label("No match. Check the spelling."));
        list.setFixedCellSize(ROW_HEIGHT);
        list.setCellFactory(listView -> {
            ListCell<T> cell = new ListCell<>() {
                @Override
                protected void updateItem(T item, boolean empty) {
                    super.updateItem(item, empty);
                    setText(empty || item == null ? null : text.apply(item));
                }
            };
            // A cell never asks for more width than the list has, so no sideways scroll bar hides the last row.
            cell.setPrefWidth(0);
            // Only a click on a row chooses; a click on the scroll bar must just scroll.
            cell.setOnMouseClicked(event -> {
                if (event.getButton() == MouseButton.PRIMARY && !cell.isEmpty() && cell.getItem() != null) {
                    choose(cell.getItem());
                }
            });
            return cell;
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
        String typed = field.getText();
        if (typed == null || typed.isBlank() || field.getScene() == null) {
            popup.hide();
            return;
        }
        List<T> found = search.apply(typed);
        list.getItems().setAll(found);
        if (!found.isEmpty()) {
            list.getSelectionModel().selectFirst();
            list.scrollTo(0);
        }

        Bounds bounds = field.localToScreen(field.getBoundsInLocal());
        Rectangle2D screen = screenOf(bounds);
        double width = Math.min(screen.getWidth() - 2 * SCREEN_MARGIN,
                Math.max(field.getWidth(), longestRow(found) + EXTRA_WIDTH));
        list.setPrefWidth(width);
        int fitBelow = (int) ((screen.getMaxY() - bounds.getMaxY() - SCREEN_MARGIN - EXTRA_HEIGHT) / ROW_HEIGHT);
        int rows = Math.max(1, Math.min(found.size(), Math.min(VISIBLE_ROWS, Math.max(3, fitBelow))));
        list.setPrefHeight(rows * ROW_HEIGHT + EXTRA_HEIGHT);
        // Under the text box; near the right edge of the screen the list opens further left so it is not cut off.
        double x = Math.max(screen.getMinX() + SCREEN_MARGIN,
                Math.min(bounds.getMinX(), screen.getMaxX() - SCREEN_MARGIN - width));
        if (popup.isShowing()) {
            popup.setX(x);
        } else {
            popup.show(field, x, bounds.getMaxY());
        }
    }

    /** Width of the widest row's text, so rows are not cut off. */
    private double longestRow(List<T> found) {
        double widest = 0;
        for (T item : found) {
            measure.setText(text.apply(item));
            widest = Math.max(widest, measure.getLayoutBounds().getWidth());
        }
        return widest;
    }

    private static Rectangle2D screenOf(Bounds bounds) {
        List<Screen> screens = Screen.getScreensForRectangle(bounds.getMinX(), bounds.getMinY(), 1, 1);
        return (screens.isEmpty() ? Screen.getPrimary() : screens.getFirst()).getVisualBounds();
    }

    private void onKey(KeyEvent event) {
        switch (event.getCode()) {
            case DOWN -> {
                if (!popup.isShowing()) {
                    refresh();
                } else {
                    moveBy(1);
                }
                event.consume();
            }
            case UP -> {
                if (popup.isShowing()) {
                    moveBy(-1);
                    event.consume();
                }
            }
            case PAGE_DOWN -> {
                if (popup.isShowing()) {
                    moveBy(VISIBLE_ROWS - 1);
                    event.consume();
                }
            }
            case PAGE_UP -> {
                if (popup.isShowing()) {
                    moveBy(-(VISIBLE_ROWS - 1));
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

    /** Moves the highlight up or down, stopping at the first and last row, and keeps it in view. */
    private void moveBy(int steps) {
        int count = list.getItems().size();
        if (count == 0) {
            return;
        }
        int current = Math.max(0, list.getSelectionModel().getSelectedIndex());
        int next = Math.clamp(current + steps, 0, count - 1);
        list.getSelectionModel().select(next);
        showRow(next);
    }

    /**
     * Scrolls only as far as needed to show the row whole. ({@code ListView.scrollTo} would move the row to the top
     * every time, so the list jumped on each key press.)
     */
    private void showRow(int index) {
        if (list.lookup(".virtual-flow") instanceof VirtualFlow<?> flow) {
            flow.scrollTo(index);
        } else {
            list.scrollTo(index);
        }
    }

    private void choose(T item) {
        popup.hide();
        onChoose.accept(item);
    }
}
