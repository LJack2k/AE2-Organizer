package nl.ljack2k.ae2organizer.client.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import nl.ljack2k.ae2organizer.backend.Theme;

import java.util.List;
import java.util.function.IntConsumer;

/**
 * A small popup that lists the windows and moves the current tab into the one
 * you pick. Picking the tab's current window is a no-op.
 */
public final class WindowPickerScreen extends Screen {

    private static final int BTN_H = 18;

    private final Screen parent;
    private final List<String> windowNames;
    private final int currentIndex;
    private final Theme theme;
    private final IntConsumer onPick;

    private int left, top, panelW, panelH;

    public WindowPickerScreen(Screen parent, List<String> windowNames, int currentIndex, Theme theme, IntConsumer onPick) {
        super(Component.literal("Move tab to window"));
        this.parent = parent;
        this.windowNames = windowNames;
        this.currentIndex = currentIndex;
        this.theme = theme;
        this.onPick = onPick;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    protected void init() {
        int rows = Math.max(1, windowNames.size());
        panelW = Math.min(320, this.width - 20);
        panelH = Math.min(this.height - 20, 44 + rows * 22 + 30);
        left = (this.width - panelW) / 2;
        top = (this.height - panelH) / 2;

        int x = left + 10;
        int w = panelW - 20;
        int y = top + 28;
        for (int i = 0; i < windowNames.size(); i++) {
            final int index = i;
            boolean current = i == currentIndex;
            String label = windowNames.get(i) + (current ? "  (current)" : "");
            RsButton btn = new RsButton(x, y, w, BTN_H, Component.literal(label), b -> {
                onPick.accept(index);
                onClose();
            });
            btn.active = !current;   // can't move into the tab's own window
            addRenderableWidget(btn);
            y += 22;
        }

        addRenderableWidget(new RsButton(left + panelW - 68, top + panelH - 26, 58, 20,
                Component.literal("Cancel"), b -> onClose()));
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, this.width, this.height, RsStyle.DIM);
        theme.panel(graphics, left, top, panelW, panelH);
        graphics.drawString(this.font, getTitle(), left + 10, top + 9, theme.textColor(), false);
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(parent);
    }
}
