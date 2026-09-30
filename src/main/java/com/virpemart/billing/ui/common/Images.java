package com.virpemart.billing.ui.common;

import java.awt.image.BufferedImage;

import javafx.scene.image.Image;
import javafx.scene.image.PixelFormat;
import javafx.scene.image.WritableImage;

/** Turns pictures drawn with Java 2D (such as the bill preview) into pictures JavaFX can show. */
public final class Images {

    private Images() {
    }

    /** Copies the pixels of a Java 2D picture into a JavaFX picture. */
    public static Image toFx(BufferedImage picture) {
        int width = picture.getWidth();
        int height = picture.getHeight();
        int[] pixels = picture.getRGB(0, 0, width, height, null, 0, width);
        WritableImage image = new WritableImage(width, height);
        image.getPixelWriter().setPixels(0, 0, width, height, PixelFormat.getIntArgbInstance(), pixels, 0, width);
        return image;
    }
}
