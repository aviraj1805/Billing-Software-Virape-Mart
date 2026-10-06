package com.virpemart.billing.ui.common;

import java.awt.Desktop;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.Toolkit;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;

import javax.imageio.ImageIO;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Hands a bill picture to WhatsApp Desktop.
 *
 * <p>WhatsApp has no way for another program to attach a photo, so the picture is copied to the Windows clipboard
 * and WhatsApp is opened. The user opens the customer's chat and presses Ctrl+V, then Enter. A copy is also saved
 * as a PNG file, in case pasting does not work. This is slow work: call it away from the screen thread.
 */
public final class WhatsAppShare {

    private static final Logger LOG = LoggerFactory.getLogger(WhatsAppShare.class);

    /** The link WhatsApp Desktop registers in Windows. It opens WhatsApp without choosing a chat. */
    private static final URI OPEN_WHATSAPP = URI.create("whatsapp://send");

    /**
     * What was done.
     *
     * @param pictureFile    the saved copy of the picture
     * @param whatsAppOpened false if WhatsApp could not be opened (for example it is not installed)
     */
    public record Shared(Path pictureFile, boolean whatsAppOpened) {
    }

    private WhatsAppShare() {
    }

    /**
     * Saves the picture as {@code folder/fileName}, copies it to the clipboard and opens WhatsApp.
     *
     * @throws IOException if the picture file cannot be written
     */
    public static Shared share(BufferedImage picture, Path folder, String fileName) throws IOException {
        BufferedImage solid = withoutTransparency(picture);
        Files.createDirectories(folder);
        Path file = folder.resolve(fileName);
        ImageIO.write(solid, "png", file.toFile());
        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new PictureSelection(solid), null);
        LOG.info("Bill picture copied for WhatsApp: {}", file);
        return new Shared(file, openWhatsApp());
    }

    /** Opens WhatsApp Desktop. Returns false (and logs why) if Windows could not open it. */
    private static boolean openWhatsApp() {
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(OPEN_WHATSAPP);
                return true;
            }
            LOG.warn("Cannot open WhatsApp: opening links is not supported here");
        } catch (IOException | RuntimeException e) {
            LOG.warn("Cannot open WhatsApp", e);
        }
        return false;
    }

    /** A copy on a white, non-transparent background, because some programs paste transparent pictures as black. */
    static BufferedImage withoutTransparency(BufferedImage picture) {
        BufferedImage solid = new BufferedImage(picture.getWidth(), picture.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = solid.createGraphics();
        try {
            g.setColor(java.awt.Color.WHITE);
            g.fillRect(0, 0, picture.getWidth(), picture.getHeight());
            g.drawImage(picture, 0, 0, null);
        } finally {
            g.dispose();
        }
        return solid;
    }

    /** A picture on the clipboard, in the standard image format that Windows programs paste. */
    private record PictureSelection(Image picture) implements Transferable {

        @Override
        public DataFlavor[] getTransferDataFlavors() {
            return new DataFlavor[] {DataFlavor.imageFlavor};
        }

        @Override
        public boolean isDataFlavorSupported(DataFlavor flavor) {
            return DataFlavor.imageFlavor.equals(flavor);
        }

        @Override
        public Object getTransferData(DataFlavor flavor) throws UnsupportedFlavorException {
            if (!isDataFlavorSupported(flavor)) {
                throw new UnsupportedFlavorException(flavor);
            }
            return picture;
        }
    }
}
