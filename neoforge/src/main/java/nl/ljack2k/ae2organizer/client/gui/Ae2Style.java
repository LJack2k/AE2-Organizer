package nl.ljack2k.ae2organizer.client.gui;

import appeng.client.gui.style.BackgroundGenerator;
import appeng.client.gui.style.PaletteColor;
import appeng.client.gui.style.ScreenStyle;
import appeng.client.gui.style.StyleManager;
import appeng.client.gui.widgets.AETextField;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * Renders our client-only screens through AE2's own GUI pipeline (background,
 * palette, widgets) so they match AE2 and inherit AE2 "dark mode" resource packs
 * — which retheme by overriding {@code textures/guis/*.png} and
 * {@code screens/common/palette.json}. Doing this without an AE2 container screen
 * keeps the editor purely client-side (works on vanilla-AE2 servers).
 */
public final class Ae2Style {
    private Ae2Style() {}

    /** Translucent dim drawn behind our panels instead of vanilla's blurred background. */
    public static final int DIM = 0xB0101018;

    private static final int FALLBACK_TEXT_COLOR = 0xFF413F54;
    private static final int FALLBACK_SELECTION_COLOR = 0xFFACE9FF;

    private static boolean styleAttempted;
    @Nullable
    private static ScreenStyle cachedStyle;

    /** AE2's common screen style (for AE2 widgets that need one), or null if unavailable. */
    @Nullable
    public static ScreenStyle style() {
        if (!styleAttempted) {
            styleAttempted = true;
            for (String path : new String[]{"/screens/common/common.json", "/screens/common/palette.json"}) {
                try {
                    cachedStyle = StyleManager.loadStyleDoc(path);
                    if (cachedStyle != null) {
                        break;
                    }
                } catch (Throwable ignored) {
                    // Try next candidate; remain null otherwise.
                }
            }
        }
        return cachedStyle;
    }

    /** AE2's nine-sliced background panel at any size (themed via background.png). */
    public static void panel(GuiGraphics graphics, int x, int y, int width, int height) {
        BackgroundGenerator.draw(width, height, graphics, x, y);
    }

    public static int textColor() {
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

    public static int selectionColor() {
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

    /** An AE2-styled text field when the style is available, else a vanilla EditBox. */
    public static EditBox textField(Font font, int x, int y, int width, int height, Component message) {
        ScreenStyle style = style();
        if (style != null) {
            try {
                return new AETextField(style, font, x, y, width, height);
            } catch (Throwable ignored) {
                // fall back to vanilla
            }
        }
        return new EditBox(font, x, y, width, height, message);
    }

    /** A subtle recessed slot. Translucent so it reads on both light and dark panels. */
    public static void slot(GuiGraphics graphics, int x, int y) {
        graphics.fill(x, y, x + 18, y + 18, 0x44000000);
        graphics.fill(x, y, x + 18, y + 1, 0x66000000);
        graphics.fill(x, y, x + 1, y + 18, 0x66000000);
        graphics.fill(x, y + 17, x + 18, y + 18, 0x33FFFFFF);
        graphics.fill(x + 17, y, x + 18, y + 18, 0x33FFFFFF);
    }
}
