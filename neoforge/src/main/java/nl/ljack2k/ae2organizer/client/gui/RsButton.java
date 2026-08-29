package nl.ljack2k.ae2organizer.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

/**
 * A push button that renders with {@link RsStyle} (bevelled panel + centered
 * text) instead of vanilla widget sprites. A drop-in replacement for AE2's
 * {@code AE2Button}: same constructor shape ({@code x, y, w, h, label, onPress}),
 * and vanilla {@link Button} still handles hit-testing, {@code active} state and
 * {@code onPress}, so screens keep the {@code addRenderableWidget(new RsButton(…))}
 * pattern unchanged.
 */
public final class RsButton extends Button {

    public RsButton(int x, int y, int width, int height, Component message, OnPress onPress) {
        super(x, y, width, height, message, onPress, DEFAULT_NARRATION);
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        boolean hovered = this.active && isHoveredOrFocused();
        RsStyle.labelButton(graphics, Minecraft.getInstance().font, getMessage(),
                getX(), getY(), getWidth(), getHeight(), hovered, false);
        if (!this.active) {
            // Dim disabled buttons so they read as non-interactive. The wash has to go
            // *towards* the panel, so it flips with the theme: a black wash on a dark
            // (blackout-pack) panel buried the label instead of merely greying it.
            graphics.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(),
                    RsStyle.darkTheme() ? 0x40000000 : 0x66303030);
        }
    }
}
