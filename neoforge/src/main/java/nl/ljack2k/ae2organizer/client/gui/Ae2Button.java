package nl.ljack2k.ae2organizer.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

/**
 * A small text button themed to match AE2 panels via {@link Ae2Style#bevelButton}.
 * <p>
 * The 1.21.1 / 26.1 lines use AE2's own {@code appeng.client.gui.widgets.AE2Button},
 * but AE2 15.4 (1.20.1) ships no generic text button — only icon / setting / tab
 * buttons. This local widget keeps AE2Button's exact constructor shape so the
 * editor screens read identically across lines, and draws through our shared bevel
 * helper so it still respects AE2 light/dark resource packs.
 */
public class Ae2Button extends Button {

    public Ae2Button(int x, int y, int width, int height, Component message, OnPress onPress) {
        super(x, y, width, height, message, onPress, DEFAULT_NARRATION);
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        Ae2Style.bevelButton(graphics, getX(), getY(), getWidth(), getHeight(), false, isHoveredOrFocused());
        var font = Minecraft.getInstance().font;
        int color = this.active ? Ae2Style.textColor() : 0xFFA0A0A0;
        int tx = getX() + (getWidth() - font.width(getMessage())) / 2;
        int ty = getY() + (getHeight() - 8) / 2;
        graphics.drawString(font, getMessage(), tx, ty, color, false);
    }
}
