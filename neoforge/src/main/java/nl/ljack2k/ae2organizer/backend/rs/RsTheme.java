package nl.ljack2k.ae2organizer.backend.rs;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import nl.ljack2k.ae2organizer.backend.Theme;

/**
 * Refined Storage look: the bundled nine-slice panel sprite + RS's own wrench item
 * as the settings icon, with the classic light-gray RS grid palette (dark
 * {@code 0x404040} text, RS-blue selection). Only loaded when RS is present (see
 * {@link RsBackend#theme()}), so referencing RS's wrench item here is safe.
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
