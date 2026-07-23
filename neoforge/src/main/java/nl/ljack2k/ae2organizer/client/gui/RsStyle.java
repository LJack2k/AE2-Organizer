package nl.ljack2k.ae2organizer.client.gui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/**
 * Renders our client-only screens and the tab panel in a look that matches
 * Refined Storage's grid GUI: the classic light-gray Minecraft container panel
 * with a raised bevel and dark ({@code 0x404040}) label text. RS's own grid uses
 * this vanilla-container styling, so approximating it here keeps the tab UI
 * visually native without depending on RS's internal (sprite-based) style API.
 * <p>
 * All drawing uses vanilla {@link GuiGraphics}, so nothing here ties us to RS or
 * AE2 internals — only the grid <em>filter</em> hook does.
 */
public final class RsStyle {
    private RsStyle() {}

    /** Translucent dim drawn behind our popup screens instead of vanilla's blur. */
    public static final int DIM = 0xB0101018;

    private static final int TEXT = 0xFF404040;
    private static final int SELECTION = 0xFF2A7FFF;

    /**
     * Nine-slice panel sprite ({@code assets/ae2organizer/textures/gui/sprites/panel.png}
     * + {@code .mcmeta}). Its pixels are RS's own grid-GUI border: a black rounded outer
     * border, a white top-left bevel, a {@code C6C6C6} body, and a {@code 555555}
     * bottom-right bevel — so a bundled sprite gives the native RS look at any size,
     * rather than hand-drawn fills.
     */
    private static final ResourceLocation PANEL_SPRITE =
            ResourceLocation.fromNamespaceAndPath("ae2organizer", "panel");

    public static int textColor() {
        return TEXT;
    }

    public static int selectionColor() {
        return SELECTION;
    }

    /** An RS-styled container panel at any size (bundled nine-slice sprite). */
    public static void panel(GuiGraphics graphics, int x, int y, int width, int height) {
        graphics.blitSprite(PANEL_SPRITE, x, y, width, height);
    }

    /**
     * The editor/settings button icon — a mod-bundled wrench sprite
     * ({@code assets/ae2organizer/textures/gui/sprites/settings.png}). Bundled
     * deliberately so it works on ANY install (AE2-only, RS-only, both, neither):
     * an earlier version rendered RS's own {@code refinedstorage:wrench} item, which
     * both looked wrong in an AE2 terminal and would have failed with RS absent.
     */
    private static final ResourceLocation SETTINGS_SPRITE =
            ResourceLocation.fromNamespaceAndPath("ae2organizer", "settings");

    public static void settingsIcon(GuiGraphics graphics, int x, int y) {
        graphics.blitSprite(SETTINGS_SPRITE, x, y, 16, 16);
    }

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
        public void onClick(double mouseX, double mouseY) {
            if (selectAllOnClick) {
                moveCursorToEnd(false);
                setHighlightPos(0);
            } else {
                super.onClick(mouseX, mouseY);
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

    /**
     * A labelled push button (bevelled panel + centered text). A drop-in vanilla
     * replacement for AE2's {@code AE2Button}; pair with a hit test on the same rect.
     */
    public static void labelButton(GuiGraphics graphics, Font font, Component label,
                                   int x, int y, int w, int h, boolean hovered, boolean active) {
        bevelButton(graphics, x, y, w, h, active, hovered);
        int off = active ? 1 : 0;
        int tw = font.width(label);
        graphics.drawString(font, label, x + (w - tw) / 2 + off, y + (h - 8) / 2 + off, TEXT, false);
    }

    /**
     * A checkbox (14px box + label). A drop-in vanilla replacement for AE2's
     * {@code AECheckbox}; pair with a hit test on {@code [x, y, box+label]}.
     */
    public static void checkbox(GuiGraphics graphics, Font font, Component label,
                                int x, int y, boolean checked, boolean hovered) {
        int box = 11;
        bevelButton(graphics, x, y, box, box, true, hovered);
        if (checked) {
            graphics.drawString(font, "✔", x + 2, y + 1, TEXT, false);
        }
        graphics.drawString(font, label, x + box + 4, y + 2, TEXT, false);
    }

    /** Renders an item icon scaled to {@code size} pixels (vanilla renderItem is fixed 16px). */
    public static void scaledItem(GuiGraphics graphics, ItemStack stack, int x, int y, int size) {
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

    /** A recessed content panel: dark fill with a sunken bevel. */
    public static void inset(GuiGraphics graphics, int x, int y, int w, int h) {
        graphics.fill(x, y, x + w, y + h, 0x55000000);
        graphics.fill(x, y, x + w, y + 1, 0x66000000);
        graphics.fill(x, y, x + 1, y + h, 0x66000000);
        graphics.fill(x, y + h - 1, x + w, y + h, 0x2BFFFFFF);
        graphics.fill(x + w - 1, y, x + w, y + h, 0x2BFFFFFF);
    }

    /** A 1px engraved horizontal divider. */
    public static void divider(GuiGraphics graphics, int x, int y, int w) {
        graphics.fill(x, y, x + w, y + 1, 0x55000000);
        graphics.fill(x, y + 1, x + w, y + 2, 0x1FFFFFFF);
    }

    /** A subtle recessed 18px slot. */
    public static void slot(GuiGraphics graphics, int x, int y) {
        graphics.fill(x, y, x + 18, y + 18, 0x44000000);
        graphics.fill(x, y, x + 18, y + 1, 0x66000000);
        graphics.fill(x, y, x + 1, y + 18, 0x66000000);
        graphics.fill(x, y + 17, x + 18, y + 18, 0x33FFFFFF);
        graphics.fill(x + 17, y, x + 18, y + 18, 0x33FFFFFF);
    }

    /** The cog/gear sprite location (generated asset), kept for any screen that blits it. */
    public static final ResourceLocation COG =
            ResourceLocation.fromNamespaceAndPath("ae2organizer", "textures/gui/cog.png");

}
