package nl.ljack2k.ae2organizer.client.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

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
    private final IntConsumer onPick;

    private int left, top, panelW, panelH;

    public WindowPickerScreen(Screen parent, List<String> windowNames, int currentIndex, IntConsumer onPick) {
        super(Component.literal("Move tab to window"));
        this.parent = parent;
        this.windowNames = windowNames;
        this.currentIndex = currentIndex;
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
            Ae2Button btn = new Ae2Button(x, y, w, BTN_H, Component.literal(label), b -> {
                onPick.accept(index);
                onClose();
            });
            btn.active = !current;   // can't move into the tab's own window
            addRenderableWidget(btn);
            y += 22;
        }

        addRenderableWidget(new Ae2Button(left + panelW - 68, top + panelH - 26, 58, 20,
                Component.literal("Cancel"), b -> onClose()));
    }

    @Override
    public void renderBackground(GuiGraphics graphics) {
        graphics.fill(0, 0, this.width, this.height, Ae2Style.DIM);
        Ae2Style.panel(graphics, left, top, panelW, panelH);
        graphics.drawString(this.font, getTitle(), left + 10, top + 9, Ae2Style.textColor(), false);
    }

    
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(parent);
    }
}
