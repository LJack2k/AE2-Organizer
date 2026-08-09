package nl.ljack2k.ae2organizer.client.gui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/**
 * Theme-neutral drawing helpers shared by both backends' screens and the tab
 * panel: bevelled buttons, insets, slots, dividers, scaled item/text, and vanilla
 * text-field widgets. All drawing uses vanilla {@link GuiGraphicsExtractor}, so nothing
 * here ties us to RS or AE2 internals.
 * <p>
 * The <em>backend-specific</em> look (the background panel, its text/selection
 * colours, the settings icon) lives on each backend's {@code Theme} instead
 * ({@code RsTheme} / {@code Ae2Theme}), so an AE2 terminal and an RS grid can
 * differ. These helpers are the parts that are the same on either theme.
 */
public final class RsStyle {
    private RsStyle() {}

    /** Translucent dim drawn behind our popup screens instead of vanilla's blur. */
    public static final int DIM = 0xB0101018;


    /**
     * Dark label text baked into the vanilla-style {@link #labelButton} and
     * {@link #checkbox} drop-ins. These widgets are deliberately theme-neutral
     * (they read on the light bevelled faces they draw), so they carry their own
     * fixed colour rather than a backend's {@code Theme.textColor()}.
     */
    private static final int LABEL_TEXT = 0xFF404040;

    /**
     * A plain (vanilla) text box, readable on the light panel.
     */
    public static EditBox textField(Font font, int x, int y, int width, int height, Component message) {
        return new ThemedField(font, x, y, width, height, message, false);
    }

    /**
     * Like {@link #textField} but selects the entire contents on every click, so a
     * click-then-type replaces the value instead of positioning the caret.
     */
    public static EditBox selectAllField(Font font, int x, int y, int width, int height, Component message) {
        return new ThemedField(font, x, y, width, height, message, true);
    }

    /**
     * Drops keyboard focus from a focused text field when a click lands outside it.
     * Call from a {@code Screen}'s {@code mouseClicked} before delegating to super.
     */
    public static void blurFieldOnOutsideClick(Screen screen, double mouseX, double mouseY) {
        if (screen.getFocused() instanceof EditBox box && !box.isMouseOver(mouseX, mouseY)) {
            screen.setFocused(null);
        }
    }

    /** A vanilla {@link EditBox} that clears its selection on blur; optional select-all-on-click. */
    private static final class ThemedField extends EditBox {
        private final boolean selectAllOnClick;

        ThemedField(Font font, int x, int y, int width, int height, Component message, boolean selectAllOnClick) {
            super(font, x, y, width, height, message);
            this.selectAllOnClick = selectAllOnClick;
        }

        @Override
        public void onClick(MouseButtonEvent event, boolean doubleClick) {
            if (selectAllOnClick) {
                moveCursorToEnd(false);
                setHighlightPos(0);
            } else {
                super.onClick(event, doubleClick);
            }
        }

        @Override
        public void setFocused(boolean focused) {
            super.setFocused(focused);
            if (!focused) {
                setHighlightPos(getCursorPosition());
            }
        }
    }

    /**
     * A bevelled button face: raised when inactive, sunken when active. Translucent
     * so it reads on both light and dark panels.
     */
    public static void bevelButton(GuiGraphicsExtractor graphics, int x, int y, int w, int h, boolean active, boolean hovered) {
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

    /**
     * A labelled push button (bevelled panel + centered text). A drop-in vanilla
     * replacement for AE2's {@code AE2Button}; pair with a hit test on the same rect.
     */
    public static void labelButton(GuiGraphicsExtractor graphics, Font font, Component label,
                                   int x, int y, int w, int h, boolean hovered, boolean active) {
        bevelButton(graphics, x, y, w, h, active, hovered);
        int off = active ? 1 : 0;
        int tw = font.width(label);
        graphics.text(font, label, x + (w - tw) / 2 + off, y + (h - 8) / 2 + off, LABEL_TEXT, false);
    }

    /**
     * A checkbox (14px box + label). A drop-in vanilla replacement for AE2's
     * {@code AECheckbox}; pair with a hit test on {@code [x, y, box+label]}.
     */
    public static void checkbox(GuiGraphicsExtractor graphics, Font font, Component label,
                                int x, int y, boolean checked, boolean hovered) {
        int box = 11;
        bevelButton(graphics, x, y, box, box, true, hovered);
        if (checked) {
            graphics.text(font, "✔", x + 2, y + 1, LABEL_TEXT, false);
        }
        graphics.text(font, label, x + box + 4, y + 2, LABEL_TEXT, false);
    }

    /** Renders an item icon scaled to {@code size} pixels (vanilla renderItem is fixed 16px). */
    public static void scaledItem(GuiGraphicsExtractor graphics, ItemStack stack, int x, int y, int size) {
        graphics.pose().pushMatrix();
        graphics.pose().translate(x, y);
        float s = size / 16f;
        graphics.pose().scale(s, s);
        graphics.item(stack, 0, 0);
        graphics.pose().popMatrix();
    }

    /** Draws text at a given scale (vanilla font is fixed 8px tall). */
    public static void scaledText(GuiGraphicsExtractor graphics, Font font, String text, int x, int y, int color, float scale) {
        graphics.pose().pushMatrix();
        graphics.pose().translate(x, y);
        graphics.pose().scale(scale, scale);
        graphics.text(font, text, 0, 0, color, false);
        graphics.pose().popMatrix();
    }

    /** A recessed content panel: dark fill with a sunken bevel. */
    public static void inset(GuiGraphicsExtractor graphics, int x, int y, int w, int h) {
        graphics.fill(x, y, x + w, y + h, 0x55000000);
        graphics.fill(x, y, x + w, y + 1, 0x66000000);
        graphics.fill(x, y, x + 1, y + h, 0x66000000);
        graphics.fill(x, y + h - 1, x + w, y + h, 0x2BFFFFFF);
        graphics.fill(x + w - 1, y, x + w, y + h, 0x2BFFFFFF);
    }

    /** A 1px engraved horizontal divider. */
    public static void divider(GuiGraphicsExtractor graphics, int x, int y, int w) {
        graphics.fill(x, y, x + w, y + 1, 0x55000000);
        graphics.fill(x, y + 1, x + w, y + 2, 0x1FFFFFFF);
    }

    /** A subtle recessed 18px slot. */
    public static void slot(GuiGraphicsExtractor graphics, int x, int y) {
        graphics.fill(x, y, x + 18, y + 18, 0x44000000);
        graphics.fill(x, y, x + 18, y + 1, 0x66000000);
        graphics.fill(x, y, x + 1, y + 18, 0x66000000);
        graphics.fill(x, y + 17, x + 18, y + 18, 0x33FFFFFF);
        graphics.fill(x + 17, y, x + 18, y + 18, 0x33FFFFFF);
    }
}
