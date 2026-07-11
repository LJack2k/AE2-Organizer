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

/**
 * Windowed, client-only global settings, themed via {@link Ae2Style}.
 * Presentation settings (label mode, size, orientation, position) are per-window
 * and edited in the tab editor's window panel; only cross-cutting behaviour lives
 * here.
 */
public final class SettingsScreen extends Screen {

    private final Screen parent;
    private boolean resetFilterOnOpen;
    private boolean clearSearchOnTabSelect;
    private boolean syncJeiOnTabSelect;

    @Nullable
    private AECheckbox resetBox;
    @Nullable
    private AECheckbox clearSearchBox;
    @Nullable
    private AECheckbox syncJeiBox;

    private int left;
    private int top;
    private int panelW;
    private int panelH;

    public SettingsScreen(Screen parent) {
        super(Component.literal("AE2 Organizer Settings"));
        this.parent = parent;
        Settings current = TabManager.getSettings();
        this.resetFilterOnOpen = current.resetFilterOnOpen();
        this.clearSearchOnTabSelect = current.clearSearchOnTabSelect();
        this.syncJeiOnTabSelect = current.syncJeiOnTabSelect();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    protected void init() {
        panelW = Math.min(360, this.width - 20);
        panelH = Math.min(190, this.height - 20);
        left = (this.width - panelW) / 2;
        top = (this.height - panelH) / 2;

        ScreenStyle style = Ae2Style.style();
        if (style != null) {
            resetBox = new AECheckbox(left + 10, top + 28, panelW - 20, 18, style,
                    Component.literal("Reset filter when opening a terminal"));
            resetBox.setSelected(resetFilterOnOpen);
            addRenderableWidget(resetBox);

            clearSearchBox = new AECheckbox(left + 10, top + 52, panelW - 20, 18, style,
                    Component.literal("Clear search bar when selecting a tab"));
            clearSearchBox.setSelected(clearSearchOnTabSelect);
            addRenderableWidget(clearSearchBox);

            syncJeiBox = new AECheckbox(left + 10, top + 76, panelW - 20, 18, style,
                    Component.literal("Sync JEI search bar when selecting a tab"));
            syncJeiBox.setSelected(syncJeiOnTabSelect);
            addRenderableWidget(syncJeiBox);
        } else {
            addRenderableWidget(CycleButton.onOffBuilder(resetFilterOnOpen)
                    .create(left + 10, top + 28, panelW - 20, 18,
                            Component.literal("Reset filter when opening a terminal"),
                            (btn, val) -> resetFilterOnOpen = val));
            addRenderableWidget(CycleButton.onOffBuilder(clearSearchOnTabSelect)
                    .create(left + 10, top + 52, panelW - 20, 18,
                            Component.literal("Clear search bar when selecting a tab"),
                            (btn, val) -> clearSearchOnTabSelect = val));
            addRenderableWidget(CycleButton.onOffBuilder(syncJeiOnTabSelect)
                    .create(left + 10, top + 76, panelW - 20, 18,
                            Component.literal("Sync JEI search bar when selecting a tab"),
                            (btn, val) -> syncJeiOnTabSelect = val));
        }

        int actionY = top + panelH - 26;
        addRenderableWidget(new AE2Button(left + panelW - 130, actionY, 58, 20,
                Component.literal("Save"), b -> {
            boolean reset = resetBox != null ? resetBox.isSelected() : resetFilterOnOpen;
            boolean clearSearch = clearSearchBox != null ? clearSearchBox.isSelected() : clearSearchOnTabSelect;
            boolean syncJei = syncJeiBox != null ? syncJeiBox.isSelected() : syncJeiOnTabSelect;
            TabManager.setSettings(new Settings(reset, clearSearch, syncJei));
            onClose();
        }));
        addRenderableWidget(new AE2Button(left + panelW - 68, actionY, 58, 20,
                Component.literal("Cancel"), b -> onClose()));
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, this.width, this.height, Ae2Style.DIM);
        Ae2Style.panel(graphics, left, top, panelW, panelH);
        int tc = Ae2Style.textColor();
        int noteColor = (tc & 0x00FFFFFF) | 0xBB000000;
        graphics.drawString(this.font, getTitle(), left + 10, top + 9, tc, false);
        Ae2Style.divider(graphics, left + 10, top + 100, panelW - 20);
        Ae2Style.scaledText(graphics, this.font,
                "JEI sync supports: mod (@mod), tag (#tag), name; Not → exclude (-).",
                left + 10, top + 110, noteColor, 0.75f);
        Ae2Style.scaledText(graphics, this.font,
                "Window size, labels, orientation & position: edit per window.",
                left + 10, top + 120, noteColor, 0.75f);
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(parent);
    }
}
