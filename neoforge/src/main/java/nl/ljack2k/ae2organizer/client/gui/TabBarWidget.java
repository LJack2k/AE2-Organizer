package nl.ljack2k.ae2organizer.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import nl.ljack2k.ae2organizer.backend.ScreenAdapter;
import nl.ljack2k.ae2organizer.backend.SearchClearable;
import nl.ljack2k.ae2organizer.backend.Theme;
import nl.ljack2k.ae2organizer.client.ClientEvents;
import nl.ljack2k.ae2organizer.client.TabManager;
import nl.ljack2k.ae2organizer.client.ViewerSync;
import nl.ljack2k.ae2organizer.filter.FilterWindow;
import nl.ljack2k.ae2organizer.filter.Orientation;
import nl.ljack2k.ae2organizer.filter.PositionMode;
import nl.ljack2k.ae2organizer.filter.Tab;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * One filter window rendered as a themed panel beside a storage screen. Bound to
 * a {@link FilterWindow} by id (looked up fresh each frame so editor changes apply
 * live) within a specific backend's {@link TabManager.Store}. Reads all screen
 * geometry through a {@link ScreenAdapter}, so it is agnostic of AE2 vs RS.
 * <p>Rendering is here; input is driven by {@code ClientEvents} via cancelable
 * {@code ScreenEvent}s (the storage screens consume scroll/drag before added
 * widgets see them).
 */
public final class TabBarWidget extends AbstractWidget {
    private static final int BASE_ROW_H = 13;
    private static final int BASE_ICON = 11;
    private static final int BASE_ICON_CELL = 15;
    private static final float BASE_TEXT_SCALE = 0.85f;
    private static final int BASE_LABEL_W = 82;
    private static final int ICON = 16;     // vanilla slot base size, for barX math
    private static final int PAD = 4;
    private static final int TITLE_H = 16;
    private static final int GEAR_SZ = 16;
    private static final int GAP = 2;
    private static final int SLOT_FRAME = 18;   // a slot's drawn frame is 18px (16px item + 1px border)
    private static final int SB_W = 8;
    private static final int SB_GAP = 2;

    private final Screen screen;
    private final ScreenAdapter adapter;
    private final TabManager.Store store;
    private final Theme theme;
    private final String windowId;
    private final String terminalKey;
    private int scroll = 0;
    private boolean draggingScrollbar = false;

    // Move-mode drag state.
    private boolean draggingPanel = false;
    private int dragGrabX, dragGrabY;
    private int dragX, dragY;

    public TabBarWidget(Screen screen, ScreenAdapter adapter, TabManager.Store store, Theme theme, String windowId) {
        super(0, 0, 1, 1, Component.literal("Storage Filter Tabs"));
        this.screen = screen;
        this.adapter = adapter;
        this.store = store;
        this.theme = theme;
        this.windowId = windowId;
        this.terminalKey = adapter.terminalKey();
        Layout l = layout();
        if (l != null) {
            setX(l.panelX);
            setY(l.panelY);
            setWidth(l.panelW);
            setHeight(l.panelH);
        }
    }

    public String windowId() {
        return windowId;
    }

    /**
     * This window's current panel rectangle, or {@code null} if it isn't drawing.
     * Recomputed from the live layout (position, scale and tab count can all change
     * between frames), which is what lets JEI's item list reflow around a panel as
     * it is dragged. {@link Rect2i} is vanilla, so nothing here depends on JEI.
     */
    @Nullable
    public Rect2i bounds() {
        Layout l = layout();
        return l == null ? null : new Rect2i(l.panelX, l.panelY, l.panelW, l.panelH);
    }

    @Nullable
    private FilterWindow window() {
        return store.window(windowId);
    }

    private List<Tab> windowTabs() {
        return store.tabsForWindow(windowId);
    }

    /**
     * Move-mode is active via the explicit toggle or while Alt is held. (Alt, not
     * Shift — Shift collides with viewer cheat-grab and vanilla shift-click transfer.)
     */
    private boolean moveActive() {
        return store.isMoveMode() || Screen.hasAltDown();
    }

    /**
     * Item drag-and-drop is active while the player carries a stack on the cursor
     * (picked up from the terminal or inventory) or drags an ingredient out of
     * JEI (see {@code BarGhostHandler}). The bar then grows a trailing "+" cell
     * (new tab from the item) and tabs become drop targets.
     */
    private boolean dragActive() {
        return !moveActive() && (!adapter.carried().isEmpty() || ClientEvents.hasExternalDrag());
    }

    /** One droppable spot on this bar, exposed to JEI's ghost-ingredient drags. */
    public record DropTarget(Rect2i area, Runnable action) {}

    /**
     * The current drop targets for an externally dragged stack (JEI): every tab
     * plus the trailing "+" cell. Call with {@code ClientEvents}' external-drag
     * stack already set, so {@link #layout()} includes the "+" cell.
     */
    public List<DropTarget> dropTargets(ItemStack stack) {
        Layout l = layout();
        if (l == null) {
            return List.of();
        }
        List<DropTarget> out = new java.util.ArrayList<>();
        List<Tab> tabs = windowTabs();
        for (int i = 0; i < l.entryRects.length; i++) {
            Tab tab = tabForEntry(l.entryIndices[i], tabs, l.hasAll);
            if (tab != null) {
                int[] r = l.entryRects[i];
                out.add(new DropTarget(new Rect2i(r[0], r[1], r[2], r[3]),
                        () -> ClientEvents.openAddToTabDialog(adapter, store, theme, tab, stack.copy())));
            }
        }
        if (l.plusRect != null) {
            int[] r = l.plusRect;
            out.add(new DropTarget(new Rect2i(r[0], r[1], r[2], r[3]),
                    () -> Minecraft.getInstance().setScreen(
                            new TabEditorScreen(screen, terminalKey, store, theme, windowId, stack.copy()))));
        }
        return out;
    }

    /**
     * Whether this window draws a gear. Forced on for the first window <em>visible
     * on this screen</em> if none of the visible windows show one, so the editor is
     * always reachable.
     */
    private boolean effectiveGear(FilterWindow w) {
        if (w.showGear()) {
            return true;
        }
        List<FilterWindow> vis = store.visibleWindows(terminalKey);
        for (FilterWindow v : vis) {
            if (v.showGear()) {
                return false;
            }
        }
        return !vis.isEmpty() && vis.get(0).id().equals(windowId);
    }

    private int imageWidth() {
        return adapter.xSize();
    }

    private int imageHeight() {
        return adapter.ySize();
    }

    private static int screenW() {
        return Minecraft.getInstance().getWindow().getGuiScaledWidth();
    }

    private static int screenH() {
        return Minecraft.getInstance().getWindow().getGuiScaledHeight();
    }

    private int dockX() {
        int guiLeft = adapter.guiLeft();
        int guiTop = adapter.guiTop();
        int bottom = guiTop + imageHeight();
        int right = guiLeft + imageWidth();
        for (Slot slot : adapter.slots()) {
            int slotRight = guiLeft + slot.x - 1 + SLOT_FRAME;
            int slotY = guiTop + slot.y;
            if (slotRight > right && slotY < bottom && slotY + ICON > guiTop) {
                right = slotRight;
            }
        }
        return right + GAP;
    }

    // ---- Layout ------------------------------------------------------------

    @Nullable
    private Layout layout() {
        FilterWindow w = window();
        if (w == null) {
            return null;
        }
        double scale = w.clampedScale();
        boolean labels = w.effectiveLabels();
        boolean gear = effectiveGear(w);
        boolean hasAll = w.showAll();
        int rowH = Math.max(9, (int) Math.round(BASE_ROW_H * scale));
        int iconDraw = Math.max(8, (int) Math.round(BASE_ICON * scale));
        int iconCell = Math.max(10, (int) Math.round(BASE_ICON_CELL * scale));
        float textScale = (float) (BASE_TEXT_SCALE * scale);
        int labelW = (int) Math.round(BASE_LABEL_W * scale);
        int cellMain = labels ? iconCell + 2 + labelW : iconCell;
        int entryCount = (hasAll ? 1 : 0) + windowTabs().size();
        boolean plus = dragActive();

        return (w.orientation() == Orientation.HORIZONTAL)
                ? layoutHorizontal(w, rowH, iconCell, iconDraw, textScale, cellMain, labels, gear, hasAll, entryCount, plus)
                : layoutVertical(w, rowH, iconCell, iconDraw, textScale, cellMain, labels, gear, hasAll, entryCount, plus);
    }

    private Layout layoutVertical(FilterWindow w, int rowH, int iconCell, int iconDraw, float textScale,
                                  int listW, boolean labels, boolean gear, boolean hasAll, int entryCount,
                                  boolean plus) {
        int titleH = (gear || labels) ? TITLE_H + 1 : 0;
        // While an item is carried, one extra fixed row at the bottom holds the
        // "+" drop cell; it never scrolls with the entries.
        int plusH = plus ? rowH : 0;
        int desiredPanelH = PAD * 2 + titleH + entryCount * rowH + plusH;
        int minPanelH = PAD * 2 + titleH + rowH + plusH;
        int cap = (w.position() == PositionMode.DOCK) ? imageHeight() : screenH() - 4;
        int panelH = Math.min(desiredPanelH, Math.max(minPanelH, cap));

        int listH = Math.max(rowH, panelH - PAD * 2 - titleH - plusH);
        int visibleRows = Math.max(1, listH / rowH);
        boolean needScroll = entryCount > visibleRows;
        int maxScroll = Math.max(0, entryCount - visibleRows);
        scroll = Math.max(0, Math.min(scroll, maxScroll));

        int listAreaW = listW + (needScroll ? SB_GAP + SB_W : 0);
        int contentW = Math.max(listAreaW, gear ? GEAR_SZ : 1);
        int panelW = PAD * 2 + contentW;

        int[] origin = resolveOrigin(w, panelW, panelH);
        int panelX = origin[0], panelY = origin[1];
        int contentX = panelX + PAD;
        int contentY = panelY + PAD;
        int listTop = contentY + titleH;
        int sbX = contentX + listW + SB_GAP;
        int gearX = contentX + contentW - GEAR_SZ;

        int rows = Math.min(visibleRows, entryCount - scroll);
        int[][] rects = new int[Math.max(0, rows)][4];
        int[] indices = new int[Math.max(0, rows)];
        for (int i = 0; i < rows; i++) {
            indices[i] = scroll + i;
            rects[i] = new int[]{contentX, listTop + i * rowH, listW, rowH};
        }
        int[] plusRect = plus ? new int[]{contentX, listTop + Math.max(0, rows) * rowH, listW, rowH} : null;
        return new Layout(false, panelX, panelY, panelW, panelH, contentX, contentY, gearX, contentY,
                rowH, iconCell, iconDraw, textScale, labels, gear, hasAll, listW, titleH > 0,
                needScroll, maxScroll, sbX, listTop, visibleRows, rects, indices, plusRect);
    }

    private Layout layoutHorizontal(FilterWindow w, int rowH, int iconCell, int iconDraw, float textScale,
                                    int cellW, boolean labels, boolean gear, boolean hasAll, int entryCount,
                                    boolean plus) {
        int panelH = PAD * 2 + rowH;
        int gearReserve = gear ? GEAR_SZ + GAP : 0;
        // While an item is carried, one extra fixed cell after the entries holds
        // the "+" drop cell; it never scrolls with the entries.
        int plusW = plus ? cellW : 0;
        int maxCellsWidth = screenW() - 8 - PAD * 2 - gearReserve - plusW;
        int maxVisible = Math.max(1, maxCellsWidth / cellW);
        int visibleCells = Math.min(Math.max(1, entryCount), maxVisible);
        boolean needScroll = entryCount > visibleCells;
        int maxScroll = Math.max(0, entryCount - visibleCells);
        scroll = Math.max(0, Math.min(scroll, maxScroll));

        int panelW = PAD * 2 + gearReserve + visibleCells * cellW + plusW;
        int[] origin = resolveOrigin(w, panelW, panelH);
        int panelX = origin[0], panelY = origin[1];
        int contentX = panelX + PAD;
        int contentY = panelY + PAD;
        // Gear on the LEFT for horizontal windows; cells follow it.
        int gearX = contentX;
        int gearY = contentY + (rowH - GEAR_SZ) / 2;
        int cellsX = contentX + gearReserve;

        int cells = Math.min(visibleCells, entryCount - scroll);
        int[][] rects = new int[Math.max(0, cells)][4];
        int[] indices = new int[Math.max(0, cells)];
        for (int i = 0; i < cells; i++) {
            indices[i] = scroll + i;
            rects[i] = new int[]{cellsX + i * cellW, contentY, cellW, rowH};
        }
        int[] plusRect = plus ? new int[]{cellsX + Math.max(0, cells) * cellW, contentY, cellW, rowH} : null;
        return new Layout(true, panelX, panelY, panelW, panelH, contentX, contentY, gearX, gearY,
                rowH, iconCell, iconDraw, textScale, labels, gear, hasAll, cellW, false,
                needScroll, maxScroll, 0, contentY, visibleCells, rects, indices, plusRect);
    }

    private int[] resolveOrigin(FilterWindow w, int panelW, int panelH) {
        if (draggingPanel) {
            return new int[]{dragX, dragY};
        }
        // Position is per-screen: this screen's override, else the global default.
        nl.ljack2k.ae2organizer.filter.Placement p = w.resolve(terminalKey);
        return switch (p.mode()) {
            case DOCK -> new int[]{dockX(), adapter.guiTop()};
            case CENTER -> new int[]{(screenW() - panelW) / 2, (screenH() - panelH) / 2};
            case FREE -> {
                // Recovery: a window dragged entirely off-screen snaps to center so
                // it (and its gear) is always reachable.
                if (fullyOffScreen(p.x(), p.y(), panelW, panelH)) {
                    yield new int[]{(screenW() - panelW) / 2, (screenH() - panelH) / 2};
                }
                yield new int[]{p.x(), p.y()};
            }
        };
    }

    private static boolean fullyOffScreen(int x, int y, int panelW, int panelH) {
        int sw = screenW(), sh = screenH();
        return x + panelW <= 0 || x >= sw || y + panelH <= 0 || y >= sh;
    }

    private record Layout(boolean horizontal, int panelX, int panelY, int panelW, int panelH,
                          int contentX, int contentY, int gearX, int gearY,
                          int rowH, int iconCell, int iconDraw, float textScale, boolean labels,
                          boolean gear, boolean hasAll, int cellMain, boolean hasTitle,
                          boolean needScroll, int maxScroll, int sbX, int listTop, int visible,
                          int[][] entryRects, int[] entryIndices,
                          @Nullable int[] plusRect) {}

    /** Maps a visible entry ordinal to a tab, or {@code null} for the "All" entry. */
    @Nullable
    private static Tab tabForEntry(int entry, List<Tab> tabs, boolean hasAll) {
        if (hasAll && entry == 0) {
            return null;
        }
        int idx = hasAll ? entry - 1 : entry;
        return (idx >= 0 && idx < tabs.size()) ? tabs.get(idx) : null;
    }

    private static boolean isAllEntry(int entry, boolean hasAll) {
        return hasAll && entry == 0;
    }

    private static boolean inRect(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    // ---- Rendering ---------------------------------------------------------

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Publish this backend's palette to the theme-neutral widget helpers.
        RsStyle.useTheme(theme);
        Layout l = layout();
        if (l == null) {
            return;
        }
        List<Tab> tabs = windowTabs();
        FilterWindow w = window();
        String activeId = store.activeTabId();
        var font = Minecraft.getInstance().font;
        boolean move = moveActive();

        theme.panel(graphics, l.panelX, l.panelY, l.panelW, l.panelH);

        if (!l.horizontal && l.labels && w != null) {
            RsStyle.scaledText(graphics, font, w.name(),
                    l.contentX, l.contentY + 4, theme.textColor(), l.textScale);
        }
        if (l.gear) {
            boolean gearHover = !move && inRect(mouseX, mouseY, l.gearX, l.gearY, GEAR_SZ, GEAR_SZ);
            if (gearHover) {
                graphics.fill(l.gearX, l.gearY, l.gearX + GEAR_SZ, l.gearY + GEAR_SZ, 0x33FFFFFF);
            }
            theme.settingsIcon(graphics, l.gearX, l.gearY);
        }
        if (!l.horizontal && l.hasTitle) {
            graphics.fill(l.contentX, l.contentY + TITLE_H, l.contentX + l.cellMain, l.contentY + TITLE_H + 1, 0x40000000);
        }

        Component hoverTip = null;
        if (l.gear && !move && inRect(mouseX, mouseY, l.gearX, l.gearY, GEAR_SZ, GEAR_SZ)) {
            hoverTip = Component.translatable("ae2organizer.editor.title");
        }
        for (int i = 0; i < l.entryRects.length; i++) {
            int entry = l.entryIndices[i];
            int[] r = l.entryRects[i];
            Tab tab = tabForEntry(entry, tabs, l.hasAll);
            boolean all = isAllEntry(entry, l.hasAll);
            boolean active = all ? activeId == null : (tab != null && tab.id().equals(activeId));
            ItemStack icon = all ? new ItemStack(Items.COMPASS) : (tab == null ? ItemStack.EMPTY : iconStack(tab.icon()));
            Component label = all ? Component.translatable("ae2organizer.tab.all")
                    : (tab == null ? Component.empty() : Component.literal(tab.name()));
            boolean hovered = !move && inRect(mouseX, mouseY, r[0], r[1], r[2], r[3]);

            RsStyle.bevelButton(graphics, r[0], r[1], r[2], r[3], active, hovered);
            // While an item is carried, tabs are drop targets: tint the hovered one.
            if (l.plusRect != null && hovered && !all) {
                graphics.fill(r[0], r[1], r[0] + r[2], r[1] + r[3], 0x5040FF40);
                if (tab != null) {
                    hoverTip = Component.literal("Add item to \"" + tab.name() + "\"…");
                }
            }
            int off = active ? 1 : 0;
            if (!icon.isEmpty()) {
                RsStyle.scaledItem(graphics, icon, r[0] + 2 + off,
                        r[1] + (r[3] - l.iconDraw) / 2 + off, l.iconDraw);
            }
            if (l.labels) {
                int textW = r[2] - l.iconCell - 4;
                String text = font.plainSubstrByWidth(label.getString(), (int) (textW / l.textScale));
                RsStyle.scaledText(graphics, font, text, r[0] + l.iconCell + 2 + off,
                        r[1] + (r[3] - Math.round(8 * l.textScale)) / 2 + off, theme.textColor(), l.textScale);
            } else if (hovered && hoverTip == null) {
                hoverTip = label;
            }
        }

        if (l.plusRect != null) {
            int[] r = l.plusRect;
            boolean hovered = inRect(mouseX, mouseY, r[0], r[1], r[2], r[3]);
            RsStyle.bevelButton(graphics, r[0], r[1], r[2], r[3], false, hovered);
            if (hovered) {
                graphics.fill(r[0], r[1], r[0] + r[2], r[1] + r[3], 0x5040FF40);
                hoverTip = Component.literal("New tab from this item");
            }
            int pw = font.width("+");
            graphics.drawString(font, "+", r[0] + (r[2] - pw) / 2,
                    r[1] + (r[3] - 8) / 2 + 1, theme.textColor(), false);
        }

        if (!l.horizontal && l.needScroll) {
            drawScrollbar(graphics, l);
        }
        if (move) {
            graphics.renderOutline(l.panelX - 1, l.panelY - 1, l.panelW + 2, l.panelH + 2, 0xFF00B4FF);
        }
        if (hoverTip != null) {
            graphics.renderTooltip(font, hoverTip, mouseX, mouseY);
        }
    }

    private void drawScrollbar(GuiGraphics graphics, Layout l) {
        int sbTop = l.listTop;
        int sbH = l.visible * l.rowH;
        graphics.fill(l.sbX, sbTop, l.sbX + SB_W, sbTop + sbH, 0x66000000);
        int entryCount = (l.hasAll ? 1 : 0) + windowTabs().size();
        int thumbH = Math.max(12, sbH * l.visible / Math.max(1, entryCount));
        int travel = sbH - thumbH;
        int thumbY = sbTop + (l.maxScroll == 0 ? 0 : travel * scroll / l.maxScroll);
        RsStyle.bevelButton(graphics, l.sbX, thumbY, SB_W, thumbH, false, draggingScrollbar);
    }

    private static ItemStack iconStack(ResourceLocation id) {
        return new ItemStack(BuiltInRegistries.ITEM.getOptional(id).orElse(Items.CHEST));
    }

    // ---- Input (called from ClientEvents) ----------------------------------

    public boolean handleMouseDown(double mouseX, double mouseY) {
        Layout l = layout();
        if (l == null || !inRect(mouseX, mouseY, l.panelX, l.panelY, l.panelW, l.panelH)) {
            return false;
        }
        if (moveActive()) {
            draggingPanel = true;
            dragGrabX = (int) mouseX - l.panelX;
            dragGrabY = (int) mouseY - l.panelY;
            dragX = l.panelX;
            dragY = l.panelY;
            return true;
        }
        if (l.gear && inRect(mouseX, mouseY, l.gearX, l.gearY, GEAR_SZ, GEAR_SZ)) {
            playClick();
            Minecraft.getInstance().setScreen(new TabEditorScreen(screen, terminalKey, store, theme));
            return true;
        }
        if (!l.horizontal && l.needScroll
                && inRect(mouseX, mouseY, l.sbX, l.listTop, SB_W, l.visible * l.rowH)) {
            draggingScrollbar = true;
            scrollTo(mouseY, l);
            return true;
        }
        // Drop mode: an item is carried on the cursor. The "+" cell seeds a new
        // tab in the editor; an existing tab asks how to add the item. Any other
        // click on the panel is swallowed so the carried stack can't be thrown.
        if (l.plusRect != null) {
            return handleDrop(l, mouseX, mouseY);
        }
        List<Tab> tabs = windowTabs();
        for (int i = 0; i < l.entryRects.length; i++) {
            int[] r = l.entryRects[i];
            if (inRect(mouseX, mouseY, r[0], r[1], r[2], r[3])) {
                Tab tab = tabForEntry(l.entryIndices[i], tabs, l.hasAll);
                String clickedId = tab == null ? null : tab.id();
                // Clicking the already-active filter clears back to "All".
                String newId = (clickedId != null && clickedId.equals(store.activeTabId())) ? null : clickedId;
                store.setActive(newId);
                if (store.getSettings().clearSearchOnTabSelect() && adapter instanceof SearchClearable sc) {
                    sc.clearSearch();
                }
                if (store.getSettings().syncViewerOnTabSelect()) {
                    ViewerSync.apply(store.activeTab());
                }
                ClientEvents.applyFilter(adapter, store);
                playClick();
                break;
            }
        }
        return true;
    }

    /**
     * Right-click input. Right-clicking a tab opens the editor with that tab
     * selected; every other right-click inside the panel is just consumed so the
     * screen underneath doesn't act on it.
     */
    public boolean handleRightClick(double mouseX, double mouseY) {
        Layout l = layout();
        if (l == null || !inRect(mouseX, mouseY, l.panelX, l.panelY, l.panelW, l.panelH)) {
            return false;
        }
        if (moveActive()) {
            return true;
        }
        List<Tab> tabs = windowTabs();
        for (int i = 0; i < l.entryRects.length; i++) {
            int[] r = l.entryRects[i];
            if (inRect(mouseX, mouseY, r[0], r[1], r[2], r[3])) {
                Tab tab = tabForEntry(l.entryIndices[i], tabs, l.hasAll);
                if (tab != null) {
                    playClick();
                    Minecraft.getInstance().setScreen(
                            new TabEditorScreen(screen, terminalKey, store, theme, tab.id()));
                }
                return true;
            }
        }
        return true;
    }

    public boolean handleMouseDrag(double mouseX, double mouseY) {
        if (draggingPanel) {
            dragX = (int) mouseX - dragGrabX;
            dragY = (int) mouseY - dragGrabY;
            return true;
        }
        if (draggingScrollbar) {
            Layout l = layout();
            if (l != null) {
                scrollTo(mouseY, l);
            }
            return true;
        }
        return false;
    }

    /**
     * Shared drop handling for a press or release while an item is carried:
     * the "+" cell opens the editor seeded from the item; an existing tab opens
     * the add-to-tab dialog; any other spot on the panel is consumed so the
     * container screen can't treat the input as a click-outside throw. A drop
     * that lands on a target also banks the carried stack back into the player
     * inventory — the drop is the end of the gesture, not a reason to keep
     * holding the item.
     */
    private boolean handleDrop(Layout l, double mouseX, double mouseY) {
        ItemStack carried = adapter.carried();
        if (carried.isEmpty()) {
            // Drop mode without a cursor stack (a JEI drag): JEI delivers the
            // drop through its own targets; just consume the panel input.
            return true;
        }
        if (l.plusRect != null && inRect(mouseX, mouseY, l.plusRect[0], l.plusRect[1], l.plusRect[2], l.plusRect[3])) {
            ItemStack dropped = carried.copy();
            playClick();
            adapter.returnCarriedToInventory();
            Minecraft.getInstance().setScreen(
                    new TabEditorScreen(screen, terminalKey, store, theme, windowId, dropped));
            return true;
        }
        List<Tab> tabs = windowTabs();
        for (int i = 0; i < l.entryRects.length; i++) {
            int[] r = l.entryRects[i];
            if (inRect(mouseX, mouseY, r[0], r[1], r[2], r[3])) {
                Tab tab = tabForEntry(l.entryIndices[i], tabs, l.hasAll);
                if (tab != null) {
                    ItemStack dropped = carried.copy();
                    playClick();
                    adapter.returnCarriedToInventory();
                    ClientEvents.openAddToTabDialog(adapter, store, theme, tab, dropped);
                }
                return true;
            }
        }
        return true;
    }

    /**
     * Release-side input, called from {@code ClientEvents} on cancelable
     * {@code MouseButtonReleased.Pre}. Completes a drag gesture: releasing a
     * carried item over a tab or the "+" cell triggers the same drop actions as
     * a press. Every release inside the panel is consumed (returns {@code true})
     * — the container screen would otherwise treat a release outside its own
     * bounds as "throw the carried stack on the ground".
     */
    public boolean handleMouseRelease(double mouseX, double mouseY) {
        if (ClientEvents.hasExternalDrag()) {
            return false;   // JEI owns this release: its drag manager delivers the drop
        }
        Layout l = layout();
        if (l == null || !inRect(mouseX, mouseY, l.panelX, l.panelY, l.panelW, l.panelH)) {
            return false;
        }
        if (l.plusRect != null) {
            return handleDrop(l, mouseX, mouseY);
        }
        return true;
    }

    public void handleMouseUp() {
        if (draggingPanel) {
            draggingPanel = false;
            // Save the position for THIS screen only.
            store.updateWindowPlacement(windowId, terminalKey, PositionMode.FREE, dragX, dragY);
        }
        draggingScrollbar = false;
    }

    public boolean handleScroll(double mouseX, double mouseY, double deltaY) {
        Layout l = layout();
        if (l == null || !inRect(mouseX, mouseY, l.panelX, l.panelY, l.panelW, l.panelH)) {
            return false;
        }
        if (l.needScroll) {
            scroll = Math.max(0, Math.min(scroll + (deltaY < 0 ? 1 : -1), l.maxScroll));
        }
        return true;
    }

    private void scrollTo(double mouseY, Layout l) {
        if (l.maxScroll == 0) {
            scroll = 0;
            return;
        }
        double fraction = (mouseY - l.listTop) / Math.max(1, l.visible * l.rowH);
        scroll = Math.max(0, Math.min((int) Math.round(fraction * l.maxScroll), l.maxScroll));
    }

    private void playClick() {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
    }

    @Override
    public boolean isMouseOver(double mouseX, double mouseY) {
        Layout l = layout();
        return l != null && inRect(mouseX, mouseY, l.panelX, l.panelY, l.panelW, l.panelH);
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        output.add(NarratedElementType.TITLE, getMessage());
    }
}
