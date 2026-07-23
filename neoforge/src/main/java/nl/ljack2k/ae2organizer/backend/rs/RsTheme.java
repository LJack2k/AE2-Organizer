package nl.ljack2k.ae2organizer.backend.rs;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import nl.ljack2k.ae2organizer.backend.Theme;

/**
 * Refined Storage look: the bundled nine-slice panel sprite and wrench icon, with
 * the classic light-gray RS grid palette (dark {@code 0x404040} text, RS-blue
 * selection). All drawing uses bundled sprites + vanilla {@link GuiGraphics}, so
 * nothing here ties us to RS internals. A singleton (see {@link RsBackend#theme()}).
 */
public final class RsTheme implements Theme {

    public static final RsTheme INSTANCE = new RsTheme();

    private RsTheme() {}

    private static final int TEXT = 0xFF404040;
    private static final int SELECTION = 0xFF2A7FFF;

    /**
     * Nine-slice panel sprite ({@code assets/ae2organizer/textures/gui/sprites/panel.png}
     * + {@code .mcmeta}): RS's own grid-GUI border (black rounded outer border, white
     * top-left bevel, {@code C6C6C6} body, {@code 555555} bottom-right bevel).
     */
    private static final ResourceLocation PANEL_SPRITE =
            ResourceLocation.fromNamespaceAndPath("ae2organizer", "panel");

    @Override
    public void panel(GuiGraphics g, int x, int y, int w, int h) {
        g.blitSprite(PANEL_SPRITE, x, y, w, h);
    }

    @Override
    public int textColor() {
        return TEXT;
    }

    @Override
    public int selectionColor() {
        return SELECTION;
    }

    @Override
    public void settingsIcon(GuiGraphics g, int x, int y) {
        // Shared white gear sprite tinted to the RS panel's dark text colour.
        nl.ljack2k.ae2organizer.client.gui.RsStyle.settingsIcon(g, x, y, TEXT);
    }
}
