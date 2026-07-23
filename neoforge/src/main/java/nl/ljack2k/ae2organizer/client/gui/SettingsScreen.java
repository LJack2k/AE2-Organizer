package nl.ljack2k.ae2organizer.client.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import nl.ljack2k.ae2organizer.backend.Theme;
import nl.ljack2k.ae2organizer.client.TabManager;
import nl.ljack2k.ae2organizer.filter.Settings;
import nl.ljack2k.ae2organizer.persist.TabShare;
import org.jetbrains.annotations.Nullable;

/**
 * Windowed, client-only global settings, themed via {@link RsStyle}.
 * Presentation settings (label mode, size, orientation, position) are per-window
 * and edited in the tab editor's window panel; only cross-cutting behaviour — plus
 * whole-config import/export — lives here.
 * <p>
 * The three toggles are drawn with {@link RsStyle#checkbox} and hit-tested
 * manually against their rows (no real widget), so the state lives entirely in
 * the boolean fields below.
 */
public final class SettingsScreen extends Screen {

    private static final int CHECK_H = 14;

    private final Screen parent;
    private final TabManager.Store store;
    private final Theme theme;
    private boolean resetFilterOnOpen;
    private boolean clearSearchOnTabSelect;
    private boolean syncViewerOnTabSelect;

    // Full config parsed from the clipboard, awaiting the import-replace confirmation.
    @Nullable
    private TabShare.AllData pendingImportAll;
    private String status = "";

    private int left;
    private int top;
    private int panelW;
    private int panelH;
    private int row1Y, row2Y, row3Y;

    public SettingsScreen(Screen parent, TabManager.Store store, Theme theme) {
        super(Component.translatable("ae2organizer.gui.settings.title"));
        this.parent = parent;
        this.store = store;
        this.theme = theme;
        Settings current = store.getSettings();
        this.resetFilterOnOpen = current.resetFilterOnOpen();
        this.clearSearchOnTabSelect = current.clearSearchOnTabSelect();
        this.syncViewerOnTabSelect = current.syncViewerOnTabSelect();
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
        row1Y = top + 28;
        row2Y = top + 52;
        row3Y = top + 76;

        if (pendingImportAll != null) {
            int by = top + 62;
            addRenderableWidget(new RsButton(left + 20, by, 96, 20, Component.literal("Replace all"), b -> {
                TabShare.AllData data = pendingImportAll;
                pendingImportAll = null;
                store.replaceAll(data.windows(), data.tabs());
                if (parent instanceof TabEditorScreen ed) {
                    ed.reloadDrafts();
                }
                onClose();
            }));
            addRenderableWidget(new RsButton(left + 124, by, 96, 20, Component.literal("Cancel"), b -> {
                pendingImportAll = null;
                rebuildWidgets();
            }));
            return;
        }

        // All-windows import/export (whole config, via the clipboard).
        int shareY = top + 138;
        int halfW = (panelW - 20 - 4) / 2;
        addRenderableWidget(new RsButton(left + 10, shareY, halfW, 20,
                Component.literal("Export all"), b -> exportAll()));
        addRenderableWidget(new RsButton(left + 10 + halfW + 4, shareY, panelW - 20 - halfW - 4, 20,
                Component.literal("Import all"), b -> importAll()));

        int actionY = top + panelH - 26;
        addRenderableWidget(new RsButton(left + panelW - 130, actionY, 58, 20,
                Component.literal("Save"), b -> {
            store.setSettings(new Settings(resetFilterOnOpen, clearSearchOnTabSelect, syncViewerOnTabSelect));
            onClose();
        }));
        addRenderableWidget(new RsButton(left + panelW - 68, actionY, 58, 20,
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
            status = "No TerminalOrganizer windows on the clipboard";
        } else {
            pendingImportAll = data;   // ask before replacing everything
        }
        rebuildWidgets();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && pendingImportAll == null) {
            if (inRow(mouseX, mouseY, row1Y)) {
                resetFilterOnOpen = !resetFilterOnOpen;
                return true;
            }
            if (inRow(mouseX, mouseY, row2Y)) {
                clearSearchOnTabSelect = !clearSearchOnTabSelect;
                return true;
            }
            if (inRow(mouseX, mouseY, row3Y)) {
                syncViewerOnTabSelect = !syncViewerOnTabSelect;
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private boolean inRow(double mx, double my, int rowY) {
        return mx >= left + 10 && mx < left + panelW - 10 && my >= rowY && my < rowY + CHECK_H;
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, this.width, this.height, RsStyle.DIM);
        theme.panel(graphics, left, top, panelW, panelH);
        int tc = theme.textColor();
        int noteColor = (tc & 0x00FFFFFF) | 0xBB000000;
        graphics.drawString(this.font, getTitle(), left + 10, top + 9, tc, false);

        if (pendingImportAll != null) {
            graphics.drawString(this.font, "Replace ALL windows and tabs with the",
                    left + 10, top + 30, tc, false);
            graphics.drawString(this.font, pendingImportAll.windows().size() + " imported window(s)? This cannot be undone.",
                    left + 10, top + 42, tc, false);
            return;
        }

        RsStyle.checkbox(graphics, this.font, Component.literal("Reset filter when opening a grid"),
                left + 10, row1Y, resetFilterOnOpen, inRow(mouseX, mouseY, row1Y));
        RsStyle.checkbox(graphics, this.font, Component.literal("Clear search bar when selecting a tab"),
                left + 10, row2Y, clearSearchOnTabSelect, inRow(mouseX, mouseY, row2Y));
        RsStyle.checkbox(graphics, this.font, Component.literal("Sync JEI search bar when selecting a tab"),
                left + 10, row3Y, syncViewerOnTabSelect, inRow(mouseX, mouseY, row3Y));

        RsStyle.divider(graphics, left + 10, top + 100, panelW - 20);
        RsStyle.scaledText(graphics, this.font,
                "Viewer sync supports: mod (@mod), tag (#tag), name; Not → exclude (-).",
                left + 10, top + 108, noteColor, 0.75f);
        RsStyle.scaledText(graphics, this.font,
                "Window size, labels, orientation & position: edit per window.",
                left + 10, top + 118, noteColor, 0.75f);
        graphics.drawString(this.font, "Import / export all windows + tabs (clipboard):",
                left + 10, top + 128, tc, false);
        if (!status.isEmpty()) {
            RsStyle.scaledText(graphics, this.font, status, left + 10, top + panelH - 38, noteColor, 0.85f);
        }
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(parent);
    }
}
