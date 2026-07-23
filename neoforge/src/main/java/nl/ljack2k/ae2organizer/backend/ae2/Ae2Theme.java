package nl.ljack2k.ae2organizer.backend.ae2;

import appeng.client.gui.style.BackgroundGenerator;
import appeng.client.gui.style.PaletteColor;
import appeng.client.gui.style.ScreenStyle;
import appeng.client.gui.style.StyleManager;
import net.minecraft.client.gui.GuiGraphics;
import nl.ljack2k.ae2organizer.backend.Theme;
import org.jetbrains.annotations.Nullable;

/**
 * Applied Energistics 2 look: renders the tab panel and this mod's client-only
 * screens through AE2's own GUI pipeline (background generator + palette) so they
 * match AE2 and inherit AE2 "dark mode" resource packs (which retheme by
 * overriding {@code textures/guis/background.png} and {@code screens/common/palette.json}).
 * <p>
 * This class references {@code appeng.*}, so it must only ever be reached via
 * {@link Ae2Backend#theme()} — {@code Ae2Backend} (and thus this class) is only
 * classloaded when AE2 is present. Never reference it from common/RS code paths.
 * A singleton (see {@link Ae2Backend#theme()}).
 */
public final class Ae2Theme implements Theme {

    public static final Ae2Theme INSTANCE = new Ae2Theme();

    private Ae2Theme() {}

    private static final int FALLBACK_TEXT_COLOR = 0xFF413F54;
    private static final int FALLBACK_SELECTION_COLOR = 0xFFACE9FF;

    private static boolean styleAttempted;
    @Nullable
    private static ScreenStyle cachedStyle;

    /** AE2's common screen style, loaded once and cached; {@code null} if unavailable. */
    @Nullable
    private static ScreenStyle style() {
        if (!styleAttempted) {
            styleAttempted = true;
            for (String path : new String[]{"/screens/common/common.json", "/screens/common/palette.json"}) {
                try {
                    cachedStyle = StyleManager.loadStyleDoc(path);
                    if (cachedStyle != null) {
                        break;
                    }
                } catch (Throwable ignored) {
                    // remain null
                }
            }
        }
        return cachedStyle;
    }

    @Override
    public void panel(GuiGraphics g, int x, int y, int w, int h) {
        BackgroundGenerator.draw(w, h, g, x, y);
    }

    @Override
    public int textColor() {
        ScreenStyle style = style();
        if (style != null) {
            try {
                return style.getColor(PaletteColor.DEFAULT_TEXT_COLOR).toARGB();
            } catch (Throwable ignored) {
                // fall through
            }
        }
        return FALLBACK_TEXT_COLOR;
    }

    @Override
    public int selectionColor() {
        ScreenStyle style = style();
        if (style != null) {
            try {
                return style.getColor(PaletteColor.SELECTION_COLOR).toARGB();
            } catch (Throwable ignored) {
                // fall through
            }
        }
        return FALLBACK_SELECTION_COLOR;
    }

    @Override
    public void settingsIcon(GuiGraphics g, int x, int y) {
        // Same shared white gear the RS theme uses, tinted to AE2's palette text
        // colour — so it inverts automatically under AE2 dark-mode resource packs.
        nl.ljack2k.ae2organizer.client.gui.RsStyle.settingsIcon(g, x, y, textColor());
    }
}
