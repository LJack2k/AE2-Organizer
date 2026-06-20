package nl.ljack2k.ae2organizer.client.gui;

import appeng.client.gui.style.ScreenStyle;
import appeng.client.gui.widgets.AE2Button;
import appeng.client.gui.widgets.AECheckbox;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import nl.ljack2k.ae2organizer.client.TabManager;
import nl.ljack2k.ae2organizer.filter.Settings;
import org.jetbrains.annotations.Nullable;

/** Windowed, client-only general settings, themed via {@link Ae2Style}. */
public final class SettingsScreen extends Screen {

    private final Screen parent;
    private boolean resetFilterOnOpen;
    private boolean showTabLabels;

    @Nullable
    private AECheckbox resetBox;
    @Nullable
    private AECheckbox labelsBox;

    private int left;
    private int top;
    private int panelW;
    private int panelH;

    public SettingsScreen(Screen parent) {
        super(Component.literal("AE2Organizer Settings"));
        this.parent = parent;
        Settings current = TabManager.getSettings();
        this.resetFilterOnOpen = current.resetFilterOnOpen();
        this.showTabLabels = current.showTabLabels();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    protected void init() {
        panelW = Math.min(340, this.width - 20);
        panelH = Math.min(150, this.height - 20);
        left = (this.width - panelW) / 2;
        top = (this.height - panelH) / 2;

        ScreenStyle style = Ae2Style.style();
        if (style != null) {
            resetBox = new AECheckbox(left + 10, top + 28, panelW - 20, 20, style,
                    Component.literal("Reset filter when opening a terminal"));
            resetBox.setSelected(resetFilterOnOpen);
            addRenderableWidget(resetBox);

            labelsBox = new AECheckbox(left + 10, top + 54, panelW - 20, 20, style,
                    Component.literal("Show tab names as labels"));
            labelsBox.setSelected(showTabLabels);
            addRenderableWidget(labelsBox);
        } else {
            addRenderableWidget(CycleButton.onOffBuilder(resetFilterOnOpen)
                    .create(left + 10, top + 28, panelW - 20, 20,
                            Component.literal("Reset filter when opening a terminal"),
                            (btn, val) -> resetFilterOnOpen = val));
            addRenderableWidget(CycleButton.onOffBuilder(showTabLabels)
                    .create(left + 10, top + 54, panelW - 20, 20,
                            Component.literal("Show tab names as labels"),
                            (btn, val) -> showTabLabels = val));
        }

        int actionY = top + panelH - 26;
        addRenderableWidget(new AE2Button(left + panelW - 130, actionY, 58, 20,
                Component.literal("Done"), b -> {
            boolean reset = resetBox != null ? resetBox.isSelected() : resetFilterOnOpen;
            boolean labels = labelsBox != null ? labelsBox.isSelected() : showTabLabels;
            TabManager.setSettings(new Settings(reset, labels));
            onClose();
        }));
        addRenderableWidget(new AE2Button(left + panelW - 68, actionY, 58, 20,
                Component.literal("Cancel"), b -> onClose()));
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, this.width, this.height, Ae2Style.DIM);
        Ae2Style.panel(graphics, left, top, panelW, panelH);
        graphics.drawString(this.font, getTitle(), left + 10, top + 9, Ae2Style.textColor(), false);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(parent);
    }
}
