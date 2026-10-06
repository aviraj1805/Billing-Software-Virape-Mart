package com.virpemart.billing.ui.common;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.awt.Color;
import java.awt.image.BufferedImage;

import org.junit.jupiter.api.Test;

/** Only the picture preparation is tested: the clipboard and WhatsApp are checked by hand. */
class WhatsAppShareTest {

    @Test
    void transparentPartsBecomeWhiteAndTheRestIsKept() {
        BufferedImage picture = new BufferedImage(2, 1, BufferedImage.TYPE_INT_ARGB);
        picture.setRGB(0, 0, 0x00000000);              // fully transparent
        picture.setRGB(1, 0, Color.BLACK.getRGB());   // black text

        BufferedImage solid = WhatsAppShare.withoutTransparency(picture);

        assertEquals(BufferedImage.TYPE_INT_RGB, solid.getType());
        assertEquals(Color.WHITE.getRGB(), solid.getRGB(0, 0));
        assertEquals(Color.BLACK.getRGB(), solid.getRGB(1, 0));
    }
}
