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
import nl.ljack2k.ae2organizer.persist.TabShare;
import org.jetbrains.annotations.Nullable;

/**
 * Windowed, client-only global settings, themed via {@link Ae2Style}.
 * Presentation settings (label mode, size, orientation, position) are per-window
 * and edited in the tab editor's window panel; only cross-cutting behaviour — plus
 * whole-config import/export — lives here.
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

    // Full config parsed from the clipboard, awaiting the import-replace confirmation.
    @Nullable
    private TabShare.AllData pendingImportAll;
    private String status = "";

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
        panelH = Math.min(pendingImportAll != null ? 110 : 224, this.height - 20);
        left = (this.width - panelW) / 2;
        top = (this.height - panelH) / 2;

        if (pendingImportAll != null) {
            int by = top + 62;
            addRenderableWidget(new AE2Button(left + 20, by, 96, 20, Component.literal("Replace all"), b -> {
                TabShare.AllData data = pendingImportAll;
                pendingImportAll = null;
                TabManager.replaceAll(data.windows(), data.tabs());
                if (parent instanceof TabEditorScreen ed) {
                    ed.reloadDrafts();
                }
                onClose();
            }));
            addRenderableWidget(new AE2Button(left + 124, by, 96, 20, Component.literal("Cancel"), b -> {
                pendingImportAll = null;
                rebuildWidgets();
            }));
            return;
        }

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

        // All-windows import/export (whole config, via the clipboard).
        int shareY = top + 138;
        int halfW = (panelW - 20 - 4) / 2;
        addRenderableWidget(new AE2Button(left + 10, shareY, halfW, 20,
                Component.literal("Export all"), b -> exportAll()));
        addRenderableWidget(new AE2Button(left + 10 + halfW + 4, shareY, panelW - 20 - halfW - 4, 20,
                Component.literal("Import all"), b -> importAll()));

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

    private void exportAll() {
        if (parent instanceof TabEditorScreen ed) {
            this.minecraft.keyboardHandler.setClipboard(TabShare.exportAll(ed.draftWindows(), ed.draftTabs()));
            status = "Copied " + ed.draftWindows().size() + " window(s) to clipboard";
        } else {
            status = "Open Settings from the editor to export";
        }
        rebuildWidgets();
    }

    private void importAll() {
        String clip = this.minecraft.keyboardHandler.getClipboard();
        TabShare.AllData data = TabShare.parseAll(clip).orElse(null);
        if (data == null) {
            status = "No AE2Organizer windows on the clipboard";
        } else {
            pendingImportAll = data;   // ask before replacing everything
        }
        rebuildWidgets();
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, this.width, this.height, Ae2Style.DIM);
        Ae2Style.panel(graphics, left, top, panelW, panelH);
        int tc = Ae2Style.textColor();
        int noteColor = (tc & 0x00FFFFFF) | 0xBB000000;
        graphics.drawString(this.font, getTitle(), left + 10, top + 9, tc, false);

        if (pendingImportAll != null) {
            graphics.drawString(this.font, "Replace ALL windows and tabs with the",
                    left + 10, top + 30, tc, false);
            graphics.drawString(this.font, pendingImportAll.windows().size() + " imported window(s)? This cannot be undone.",
                    left + 10, top + 42, tc, false);
            return;
        }

        Ae2Style.divider(graphics, left + 10, top + 100, panelW - 20);
        Ae2Style.scaledText(graphics, this.font,
                "JEI sync supports: mod (@mod), tag (#tag), name; Not → exclude (-).",
                left + 10, top + 108, noteColor, 0.75f);
        Ae2Style.scaledText(graphics, this.font,
                "Window size, labels, orientation & position: edit per window.",
                left + 10, top + 118, noteColor, 0.75f);
        graphics.drawString(this.font, "Import / export all windows + tabs (clipboard):",
                left + 10, top + 128, tc, false);
        if (!status.isEmpty()) {
            Ae2Style.scaledText(graphics, this.font, status, left + 10, top + panelH - 38, noteColor, 0.85f);
        }
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(parent);
    }
}
