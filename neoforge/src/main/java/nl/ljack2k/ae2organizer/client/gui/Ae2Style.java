package nl.ljack2k.ae2organizer.client.gui;

import appeng.client.gui.style.BackgroundGenerator;
import appeng.client.gui.style.PaletteColor;
import appeng.client.gui.style.ScreenStyle;
import appeng.client.gui.style.StyleManager;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * Renders our client-only screens through AE2's own GUI pipeline (background +
 * palette) so they match AE2 and inherit AE2 "dark mode" resource packs, which
 * retheme by overriding {@code textures/guis/background.png} and
 * {@code screens/common/palette.json}.
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
                    // remain null
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

    /**
     * A plain (vanilla) text box. AE2's AETextField rendered stray border
     * artifacts when used outside an AE2 container screen, so we use a clean
     * EditBox; it's readable on the themed panel.
     */
    public static EditBox textField(Font font, int x, int y, int width, int height, Component message) {
        return new ThemedField(font, x, y, width, height, message, false);
    }

    /**
     * Like {@link #textField} but selects the entire contents on every click (single
     * or double), so a click-then-type replaces the value instead of positioning the
     * caret. Overriding {@code onClick} is enough: it is the hook EditBox uses to place
     * the caret.
     */
    public static EditBox selectAllField(Font font, int x, int y, int width, int height, Component message) {
        return new ThemedField(font, x, y, width, height, message, true);
    }

    /**
     * Drops keyboard focus from a focused text field when a click lands outside it.
     * Vanilla only moves focus when a widget is actually clicked, so a click on empty
     * panel space would otherwise leave a field focused and still drawing its selection
     * highlight. Pair with {@link ThemedField}, which clears the selection on blur. Call
     * from a {@code Screen}'s {@code mouseClicked} before delegating to {@code super}.
     */
    public static void blurFieldOnOutsideClick(Screen screen, double mouseX, double mouseY) {
        if (screen.getFocused() instanceof EditBox box && !box.isMouseOver(mouseX, mouseY)) {
            screen.setFocused(null);
        }
    }

    /**
     * A vanilla {@link EditBox} that clears its text selection when it loses focus —
     * EditBox keeps drawing the highlight even while unfocused, so dropping focus alone
     * isn't enough. Optionally selects everything on click (see {@link #selectAllField}).
     */
    private static final class ThemedField extends EditBox {
        private final boolean selectAllOnClick;

        ThemedField(Font font, int x, int y, int width, int height, Component message, boolean selectAllOnClick) {
            super(font, x, y, width, height, message);
            this.selectAllOnClick = selectAllOnClick;
        }

        @Override
        public void onClick(double mouseX, double mouseY) {
            if (selectAllOnClick) {
                moveCursorToEnd(false); // caret to end, clearing any prior highlight
                setHighlightPos(0);     // extend selection back to the start
            } else {
                super.onClick(mouseX, mouseY);
            }
        }

        @Override
        public void setFocused(boolean focused) {
            super.setFocused(focused);
            if (!focused) {
                setHighlightPos(getCursorPosition()); // collapse selection on blur
            }
        }
    }

    /**
     * A bevelled button face: raised when inactive, sunken when active. Translucent
     * so it reads on both light and dark panels.
     */
    public static void bevelButton(GuiGraphics graphics, int x, int y, int w, int h, boolean active, boolean hovered) {
        int hi = 0x70FFFFFF;
        int lo = 0x70000000;
        int face = active ? 0x33000000 : hovered ? 0x33FFFFFF : 0x14FFFFFF;
        graphics.fill(x, y, x + w, y + h, face);
        if (active) { // sunken
            graphics.fill(x, y, x + w, y + 1, lo);
            graphics.fill(x, y, x + 1, y + h, lo);
            graphics.fill(x, y + h - 1, x + w, y + h, hi);
            graphics.fill(x + w - 1, y, x + w, y + h, hi);
        } else { // raised
            graphics.fill(x, y, x + w, y + 1, hi);
            graphics.fill(x, y, x + 1, y + h, hi);
            graphics.fill(x, y + h - 1, x + w, y + h, lo);
            graphics.fill(x + w - 1, y, x + w, y + h, lo);
        }
    }

    /** Renders an item icon scaled to {@code size} pixels (vanilla renderItem is fixed 16px). */
    public static void scaledItem(GuiGraphics graphics, net.minecraft.world.item.ItemStack stack, int x, int y, int size) {
        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 0);
        float s = size / 16f;
        graphics.pose().scale(s, s, 1f);
        graphics.renderItem(stack, 0, 0);
        graphics.pose().popPose();
    }

    /** Draws text at a given scale (vanilla font is fixed 8px tall). */
    public static void scaledText(GuiGraphics graphics, Font font, String text, int x, int y, int color, float scale) {
        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 0);
        graphics.pose().scale(scale, scale, 1f);
        graphics.drawString(font, text, 0, 0, color, false);
        graphics.pose().popPose();
    }

    /**
     * A recessed content panel: dark fill with a sunken bevel (dark top/left,
     * light bottom/right). The grouping primitive for the editor — generalises
     * {@link #slot}. Translucent so it reads on both light and dark themes.
     */
    public static void inset(GuiGraphics graphics, int x, int y, int w, int h) {
        graphics.fill(x, y, x + w, y + h, 0x55000000);
        graphics.fill(x, y, x + w, y + 1, 0x66000000);          // top (dark)
        graphics.fill(x, y, x + 1, y + h, 0x66000000);          // left (dark)
        graphics.fill(x, y + h - 1, x + w, y + h, 0x2BFFFFFF);   // bottom (light)
        graphics.fill(x + w - 1, y, x + w, y + h, 0x2BFFFFFF);   // right (light)
    }

    /** A 1px engraved horizontal divider (dark line above a faint highlight). */
    public static void divider(GuiGraphics graphics, int x, int y, int w) {
        graphics.fill(x, y, x + w, y + 1, 0x55000000);
        graphics.fill(x, y + 1, x + w, y + 2, 0x1FFFFFFF);
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
