package nl.ljack2k.ae2organizer.backend.rs;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import nl.ljack2k.ae2organizer.backend.Theme;

/**
 * Refined Storage look: the classic light-gray RS grid panel (drawn directly as a
 * bevel — see {@link #panel}) + RS's own wrench item as the settings icon, with the
 * RS palette (dark {@code 0x404040} text, RS-blue selection). Only loaded when RS is
 * present (see {@link RsBackend#theme()}), so referencing RS's wrench item here is safe.
 */
public final class RsTheme implements Theme {

    public static final RsTheme INSTANCE = new RsTheme();

    private RsTheme() {}

    private static final int TEXT = 0xFF404040;
    private static final int SELECTION = 0xFF2A7FFF;

    // RS/vanilla grid-panel bevel colours.
    private static final int PANEL_BORDER = 0xFF000000;
    private static final int PANEL_HILIGHT = 0xFFFFFFFF;
    private static final int PANEL_SHADOW = 0xFF555555;
    private static final int PANEL_BODY = 0xFFC6C6C6;

    /**
     * Draws the RS grid panel directly with four {@code fill}s (1px black outline,
     * 2px white top-left bevel, 2px {@code #555555} bottom-right bevel, {@code #C6C6C6}
     * body) instead of a nine-slice sprite.
     * <p>
     * A tiled nine-slice sprite (the previous approach) stamped the small centre as
     * thousands of quads per panel per frame ({@code blitTiledSprite}) — a client
     * profile put it at ~52% of the render thread. 1.21.1's nine-slice has no
     * {@code stretch_inner} option (added in 1.21.2), so the flat bevel is drawn by
     * hand: ~4 quads, pixel-identical to the old sprite.
     */
    @Override
    public void panel(GuiGraphics g, int x, int y, int w, int h) {
        int right = x + w;
        int bottom = y + h;
        g.fill(x, y, right, bottom, PANEL_BORDER);              // 1px black outline
        g.fill(x + 1, y + 1, right - 1, bottom - 1, PANEL_SHADOW);   // bottom-right bevel base
        g.fill(x + 1, y + 1, right - 3, bottom - 3, PANEL_HILIGHT);  // top-left bevel over it
        g.fill(x + 3, y + 3, right - 3, bottom - 3, PANEL_BODY);     // body
    }

    @Override
    public int textColor() {
        return TEXT;
    }

    @Override
    public int selectionColor() {
        return SELECTION;
    }

    private static ItemStack wrench;

    /** Refined Storage's own wrench item (RsTheme only loads when RS is present). */
    private static ItemStack wrench() {
        if (wrench == null) {
            var item = BuiltInRegistries.ITEM
                    .getOptional(ResourceLocation.fromNamespaceAndPath("refinedstorage", "wrench"))
                    .orElse(Items.COMPARATOR);
            wrench = new ItemStack(item);
        }
        return wrench;
    }

    @Override
    public void settingsIcon(GuiGraphics g, int x, int y) {
        // RS's own wrench item icon (matches RS; native art, no bundled sprite).
        g.renderItem(wrench(), x, y);
    }
}
