package nl.ljack2k.ae2organizer.backend;

import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * The per-backend visual look for this mod's client-only screens and the tab
 * panel: the background panel, its text/selection colours, and the settings
 * (gear/wrench) icon. Each {@link StorageBackend} supplies one via
 * {@link StorageBackend#theme()} so an AE2 terminal's tabs render through AE2's
 * own GUI pipeline (with dark-mode-resource-pack support) while an RS grid keeps
 * the bundled RS sprite look.
 * <p>
 * Everything else the screens draw (bevels, insets, slots, dividers, vanilla
 * widgets) is backend-agnostic and stays on the shared {@code RsStyle} helper.
 */
public interface Theme {

    /** The background panel at any size, drawn at {@code (x, y)}. */
    void panel(GuiGraphicsExtractor g, int x, int y, int w, int h);

    /** ARGB colour for label/body text on {@link #panel}. */
    int textColor();

    /** ARGB colour for a selection highlight on {@link #panel}. */
    int selectionColor();

    /** A 16x16 gear/wrench icon at {@code (x, y)} (the editor/settings button). */
    void settingsIcon(GuiGraphicsExtractor g, int x, int y);
}
