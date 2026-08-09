package nl.ljack2k.ae2organizer.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import nl.ljack2k.ae2organizer.backend.Theme;
import nl.ljack2k.ae2organizer.client.TabManager;
import nl.ljack2k.ae2organizer.filter.ComponentCondition;
import nl.ljack2k.ae2organizer.filter.ComponentMatch;
import nl.ljack2k.ae2organizer.filter.Condition;
import nl.ljack2k.ae2organizer.filter.ConditionType;
import nl.ljack2k.ae2organizer.filter.FilterWindow;
import nl.ljack2k.ae2organizer.filter.MatchMode;
import nl.ljack2k.ae2organizer.filter.ModCondition;
import nl.ljack2k.ae2organizer.filter.Orientation;
import nl.ljack2k.ae2organizer.filter.Placement;
import nl.ljack2k.ae2organizer.filter.PositionMode;
import nl.ljack2k.ae2organizer.filter.Tab;
import nl.ljack2k.ae2organizer.filter.TagCondition;
import nl.ljack2k.ae2organizer.filter.TextCondition;
import nl.ljack2k.ae2organizer.persist.TabShare;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Windowed, client-only editor for filter windows and their tabs, themed via
 * {@link RsStyle}.
 * <p>
 * The left panel is a two-level <b>tree</b>: each {@link WindowDraft} is a node
 * that expands to its {@link TabDraft}s. Selecting a window node edits window
 * properties (name, orientation, labels, size, position); selecting a tab node
 * edits that tab (name, icon, match mode, conditions) exactly as before.
 */
public final class TabEditorScreen extends Screen {

    public record GhostTarget(Rect2i area, Consumer<ItemStack> accept) {}

    private static final int PAD = 8;
    private static final int GAP = 5;
    private static final int HEADER_H = 11;
    private static final int BTN_H = 18;
    private static final int ROW_HE = 18;
    private static final int COND_ROW_H = 20;
    private static final int SBW = 8;
    private static final int TYPE_W = 86;
    private static final int NOT_W = 30;
    private static final int TAB_INDENT = 12;    // tab rows are inset under their window
    private static final int CARET_W = 12;       // width reserved for the window chevron

    private final Screen parent;
    private final String terminalKey;
    private final TabManager.Store store;
    private final Theme theme;
    private final List<WindowDraft> windows = new ArrayList<>();
    private final List<GhostTarget> ghostTargets = new ArrayList<>();
    // Tree selection: selWindow indexes windows; selTab indexes that window's tabs
    // (-1 means the window node itself is selected).
    private int selWindow = 0;
    private int selTab = -1;
    // Flattened visible tree rows: each is {windowIndex, tabIndex(-1 for window)}.
    private final List<int[]> treeRows = new ArrayList<>();
    private int pendingDeleteWindow = -1;
    // Tabs parsed from the clipboard, awaiting the import-replace confirmation.
    @Nullable
    private List<Tab> pendingImport;
    private String status = "";

    @Nullable
    private ItemStack draggingStack;
    private int listScroll = 0;
    private int condScroll = 0;
    private boolean draggingListScrollbar = false;
    private boolean draggingCondScrollbar = false;

    // Window frame
    private int left, top, panelW, panelH, innerX, innerR, contentTop, dividerY, footerY;
    // Inventory tray (tab mode)
    private int invPanelX, invPanelY, invPanelW, invPanelH, invX, invY;
    // Tree panel (list + attached toolbar)
    private int tabsX, tabsW, tabsHeaderY, tabsInsetY, tabsInsetH;
    private int listX, listY, listW, listRowW, listVisible, listSbX, toolbarY, toolbar2Y, toolbarDivY;
    private boolean listNeedScroll;
    private int listMaxScroll;
    private int mainTop, mainBottom;
    // Right column
    private int rightX, rightW, propsRight, labelX, fieldX;
    private int propsHeaderY, propsInsetY, propsInsetH, nameRowY, iconRowY, iconX, iconY;
    private int condHeaderY, condInsetY, condInsetH, condRowsTop, condVisible, condSbX, condContentR;
    private int ctrlRowY, ctrlDivY, modeBtnX, modeBtnW, addBtnX, addBtnW;
    private boolean condNeedScroll;
    private int condMaxScroll;

    public TabEditorScreen(Screen parent, String terminalKey, TabManager.Store store, Theme theme) {
        super(Component.translatable("ae2organizer.gui.editor.title"));
        this.parent = parent;
        this.terminalKey = terminalKey;
        this.store = store;
        this.theme = theme;
        for (FilterWindow w : store.windows()) {
            windows.add(WindowDraft.from(w, store));
        }
        if (windows.isEmpty()) {
            windows.add(WindowDraft.fresh("Filters"));
        }
        // Open on the window/tab holding the active tab; fall back to the first window.
        String activeId = store.activeTabId();
        selWindow = 0;
        selTab = -1;
        if (activeId != null) {
            outer:
            for (int wi = 0; wi < windows.size(); wi++) {
                List<TabDraft> t = windows.get(wi).tabs;
                for (int ti = 0; ti < t.size(); ti++) {
                    if (t.get(ti).id.equals(activeId)) {
                        selWindow = wi;
                        selTab = ti;
                        break outer;
                    }
                }
            }
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    public List<GhostTarget> ghostTargets() {
        return ghostTargets;
    }

    public int panelWidth() {
        return Math.max(1, Math.min(480, frameWidth() - 20));
    }

    public int panelHeight() {
        return Math.max(1, Math.min(400, frameHeight() - 20));
    }

    public int panelLeft() {
        return (frameWidth() - panelWidth()) / 2;
    }

    public int panelTop() {
        return (frameHeight() - panelHeight()) / 2;
    }

    static int frameWidth() {
        return Minecraft.getInstance().getWindow().getGuiScaledWidth();
    }

    static int frameHeight() {
        return Minecraft.getInstance().getWindow().getGuiScaledHeight();
    }

    private boolean editingWindow() {
        return selTab < 0;
    }

    @Nullable
    private WindowDraft selWindowDraft() {
        return (selWindow >= 0 && selWindow < windows.size()) ? windows.get(selWindow) : null;
    }

    @Nullable
    private TabDraft selTabDraft() {
        WindowDraft w = selWindowDraft();
        if (w == null || selTab < 0 || selTab >= w.tabs.size()) {
            return null;
        }
        return w.tabs.get(selTab);
    }

    private void rebuildTreeRows() {
        treeRows.clear();
        for (int wi = 0; wi < windows.size(); wi++) {
            treeRows.add(new int[]{wi, -1});
            WindowDraft w = windows.get(wi);
            if (!w.collapsed) {
                for (int ti = 0; ti < w.tabs.size(); ti++) {
                    treeRows.add(new int[]{wi, ti});
                }
            }
        }
    }

    @Override
    protected void init() {
        ghostTargets.clear();
        rebuildTreeRows();
        panelW = panelWidth();
        panelH = panelHeight();
        left = panelLeft();
        top = panelTop();
        innerX = left + PAD;
        innerR = left + panelW - PAD;
        contentTop = top + 22;

        footerY = top + panelH - PAD - BTN_H;
        dividerY = footerY - 6;
        mainTop = contentTop;
        mainBottom = dividerY - 6;

        // ---- Left: tree panel (list + two-row toolbar in one inset) ----
        tabsX = innerX;
        tabsW = 128;
        tabsHeaderY = mainTop;
        tabsInsetY = mainTop + HEADER_H;
        tabsInsetH = Math.max(64, mainBottom - tabsInsetY);
        int toolbarH = 2 * BTN_H + 2;
        toolbarY = tabsInsetY + tabsInsetH - 3 - toolbarH;
        toolbar2Y = toolbarY + BTN_H + 2;
        toolbarDivY = toolbarY - 4;
        listX = tabsX + 3;
        listY = tabsInsetY + 3;
        int listAreaH = Math.max(ROW_HE, (toolbarDivY - 3) - listY);
        listVisible = Math.max(1, listAreaH / ROW_HE);
        listNeedScroll = treeRows.size() > listVisible;
        listMaxScroll = Math.max(0, treeRows.size() - listVisible);
        listScroll = Math.max(0, Math.min(listScroll, listMaxScroll));
        listW = tabsW - 6;
        listRowW = listW - (listNeedScroll ? SBW + 2 : 0);
        listSbX = listX + listW - SBW;

        int tb = tabsX + 4;
        addRenderableWidget(new RsButton(tb, toolbarY, 38, BTN_H, Component.literal("+Win"), b -> addWindow()));
        addRenderableWidget(new RsButton(tb + 40, toolbarY, 38, BTN_H, Component.literal("+Tab"), b -> addTab()));
        addRenderableWidget(new RsButton(tb + 80, toolbarY, 38, BTN_H, Component.literal("Copy"), b -> copySelected()));
        addRenderableWidget(new RsButton(tb, toolbar2Y, 38, BTN_H, Component.literal("Del"), b -> deleteSelected()));
        addRenderableWidget(new RsButton(tb + 40, toolbar2Y, 38, BTN_H, Component.literal("▲"), b -> moveSelected(-1)));
        addRenderableWidget(new RsButton(tb + 80, toolbar2Y, 38, BTN_H, Component.literal("▼"), b -> moveSelected(1)));

        // ---- Right column ----
        rightX = tabsX + tabsW + 8;
        rightW = innerR - rightX;
        propsRight = rightX + rightW - 4;
        labelX = rightX + 4;
        fieldX = labelX + 34;

        if (pendingImport != null) {
            buildConfirmImport();
        } else if (pendingDeleteWindow >= 0) {
            buildConfirmDelete();
        } else if (editingWindow()) {
            buildWindowPanel();
        } else {
            buildTabPanel(selTabDraft());
        }

        addRenderableWidget(new RsButton(innerX, footerY, 62, BTN_H,
                Component.literal("Settings…"), b -> this.minecraft.setScreen(new SettingsScreen(this, store, theme))));
        addRenderableWidget(new RsButton(innerX + 66, footerY, 66, BTN_H,
                Component.literal("Move…"), b -> commitThenMove()));
        addRenderableWidget(new RsButton(innerR - 120, footerY, 58, BTN_H,
                Component.literal("Cancel"), b -> onClose()));
        addRenderableWidget(new RsButton(innerR - 58, footerY, 58, BTN_H,
                Component.literal("Save"), b -> commitAndClose()));
    }

    // ---- Right panel: window properties ------------------------------------

    private static final int WIN_ROWS = 9;

    private void buildWindowPanel() {
        WindowDraft w = selWindowDraft();
        propsHeaderY = mainTop;
        propsInsetY = mainTop + HEADER_H;
        propsInsetH = 4 + WIN_ROWS * 22 + 4;
        if (w == null) {
            return;
        }
        int rowY = propsInsetY + 4;
        int fX = rightX + 74;
        int fW = propsRight - fX;

        EditBox name = RsStyle.selectAllField(this.font, fX, rowY + 1, fW, 16, Component.literal("Name"));
        name.setMaxLength(48);
        name.setValue(w.name);
        name.setResponder(s -> w.name = s);
        addRenderableWidget(name);
        rowY += 22;

        addRenderableWidget(new RsButton(fX, rowY, fW, BTN_H,
                Component.literal(w.orientation == Orientation.HORIZONTAL ? "Horizontal" : "Vertical"),
                b -> { w.orientation = cycle(w.orientation); rebuildWidgets(); }));
        rowY += 22;

        if (w.orientation == Orientation.HORIZONTAL) {
            // Horizontal windows are always icon-only (labels push the gear off-screen).
            RsButton disp = new RsButton(fX, rowY, fW, BTN_H, Component.literal("Icons only"), b -> {});
            disp.active = false;
            addRenderableWidget(disp);
        } else {
            addRenderableWidget(new RsButton(fX, rowY, fW, BTN_H,
                    Component.literal(w.showLabels ? "Labels" : "Icons only"),
                    b -> { w.showLabels = !w.showLabels; rebuildWidgets(); }));
        }
        rowY += 22;

        addRenderableWidget(new WindowSizeSlider(fX, rowY, fW, BTN_H, w));
        rowY += 22;

        addRenderableWidget(new RsButton(fX, rowY, fW, BTN_H,
                Component.literal(w.showGear ? "Gear: shown" : "Gear: hidden"),
                b -> { w.showGear = !w.showGear; rebuildWidgets(); }));
        rowY += 22;

        addRenderableWidget(new RsButton(fX, rowY, fW, BTN_H,
                Component.literal(w.showAll ? "All entry: shown" : "All entry: hidden"),
                b -> { w.showAll = !w.showAll; rebuildWidgets(); }));
        rowY += 22;

        // Per-grid visibility: opens a small list of known grid types.
        int hidden = w.hiddenOn.size();
        addRenderableWidget(new RsButton(fX, rowY, fW, BTN_H,
                Component.literal(hidden == 0 ? "Grids… (all shown)" : "Grids… (" + hidden + " hidden)"),
                b -> this.minecraft.setScreen(
                        new WindowVisibilityScreen(this, knownTerminals(), w.hiddenOn, terminalKey, store, theme))));
        rowY += 22;

        // Positions are per-grid; this recenters the window for THIS grid.
        addRenderableWidget(new RsButton(fX, rowY, fW, BTN_H,
                Component.literal("Center here"),
                b -> {
                    w.placements.put(terminalKey, new Placement(PositionMode.CENTER, 0, 0));
                    if (w.baseTerminal.isEmpty()) {
                        w.baseTerminal = terminalKey;
                    }
                    rebuildWidgets();
                }));
        rowY += 22;

        // Share this window's tabs (conditions only) via the clipboard.
        int halfW = (fW - 4) / 2;
        addRenderableWidget(new RsButton(fX, rowY, halfW, BTN_H,
                Component.literal("Export"), b -> exportWindowTabs(w)));
        addRenderableWidget(new RsButton(fX + halfW + 4, rowY, fW - halfW - 4, BTN_H,
                Component.literal("Import"), b -> importWindowTabs()));
    }

    private void exportWindowTabs(WindowDraft w) {
        List<Tab> tabs = new ArrayList<>(w.tabs.size());
        for (TabDraft t : w.tabs) {
            tabs.add(t.toTab(w.id));
        }
        Minecraft.getInstance().keyboardHandler.setClipboard(TabShare.export(tabs));
        status = "Copied " + tabs.size() + " tab(s) to clipboard";
        rebuildWidgets();
    }

    private void importWindowTabs() {
        String clip = Minecraft.getInstance().keyboardHandler.getClipboard();
        List<Tab> parsed = TabShare.parse(clip).orElse(null);
        if (parsed == null) {
            status = "No Storage Organizer tabs on the clipboard";
        } else {
            pendingImport = parsed;   // ask before replacing
        }
        rebuildWidgets();
    }

    private void buildConfirmImport() {
        int by = mainTop + 40;
        addRenderableWidget(new RsButton(rightX + 20, by, 80, BTN_H, Component.literal("Replace"), b -> {
            WindowDraft w = selWindowDraft();
            List<Tab> imported = pendingImport;
            pendingImport = null;
            if (w != null && imported != null) {
                w.tabs.clear();
                for (Tab t : imported) {
                    TabDraft d = TabDraft.from(t);
                    d.id = "tab-" + UUID.randomUUID().toString().substring(0, 8);   // fresh id
                    w.tabs.add(d);
                }
                w.collapsed = false;
                selTab = -1;
                status = "Imported " + imported.size() + " tab(s)";
            }
            rebuildWidgets();
        }));
        addRenderableWidget(new RsButton(rightX + 110, by, 80, BTN_H, Component.literal("Cancel"), b -> {
            pendingImport = null;
            rebuildWidgets();
        }));
    }

    // ---- Right panel: confirm window delete --------------------------------

    private void buildConfirmDelete() {
        int by = mainTop + 40;
        addRenderableWidget(new RsButton(rightX + 20, by, 80, BTN_H, Component.literal("Delete"), b -> {
            int idx = pendingDeleteWindow;
            pendingDeleteWindow = -1;
            doDeleteWindow(idx);
        }));
        addRenderableWidget(new RsButton(rightX + 110, by, 80, BTN_H, Component.literal("Cancel"), b -> {
            pendingDeleteWindow = -1;
            rebuildWidgets();
        }));
    }

    // ---- Right panel: tab properties (name / icon / conditions / inv) ------

    private void buildTabPanel(@Nullable TabDraft draft) {
        propsHeaderY = mainTop;
        propsInsetY = mainTop + HEADER_H;
        propsInsetH = 4 + 2 * BTN_H + 4 + 4;
        nameRowY = propsInsetY + 4;
        iconRowY = nameRowY + 22;
        iconX = fieldX;
        iconY = iconRowY;

        int invGridW = 9 * 18;
        int invBlockH = 3 * 18 + 4 + 18;
        invPanelX = rightX;
        invPanelW = rightW;
        invPanelH = 3 + invBlockH + 3;

        condHeaderY = propsInsetY + propsInsetH + GAP;
        condInsetY = condHeaderY + HEADER_H;
        ctrlRowY = condInsetY + 4;
        ctrlDivY = ctrlRowY + BTN_H + 2;
        condRowsTop = ctrlRowY + BTN_H + 6;
        int ctrlW = rightW - 8;
        int half = (ctrlW - 4) / 2;
        modeBtnX = rightX + 4;
        modeBtnW = half;
        addBtnX = rightX + 4 + half + 4;
        addBtnW = ctrlW - half - 4;
        condContentR = rightX + rightW - 4;
        condSbX = rightX + rightW - 3 - SBW;

        int condCount = draft == null ? 0 : draft.conditions.size();
        int rowsRoom = mainBottom - condRowsTop - 4 - GAP - HEADER_H - invPanelH;
        int capacity = Math.max(1, rowsRoom / COND_ROW_H);
        condVisible = Math.min(capacity, Math.max(1, condCount));
        condInsetH = (condRowsTop - condInsetY) + condVisible * COND_ROW_H + 4;
        condNeedScroll = condCount > condVisible;
        condMaxScroll = Math.max(0, condCount - condVisible);
        condScroll = Math.max(0, Math.min(condScroll, condMaxScroll));

        invPanelY = condInsetY + condInsetH + GAP;
        invX = invPanelX + (invPanelW - invGridW) / 2;
        invY = invPanelY + HEADER_H + 3;

        if (draft == null) {
            return;
        }

        // Name (leaves room on the right for the window-reassign button).
        EditBox name = RsStyle.selectAllField(this.font, fieldX, nameRowY + 1, propsRight - 68 - fieldX, 16, Component.literal("Name"));
        name.setMaxLength(64);
        name.setValue(draft.name);
        name.setResponder(s -> draft.name = s);
        addRenderableWidget(name);

        // Window-reassign button (top-right of the Name row) — opens a picker.
        addRenderableWidget(new RsButton(propsRight - 66, nameRowY, 66, BTN_H,
                Component.literal("Window…"), b -> openWindowPicker()));

        // Icon: recessed slot + Pick button; slot is a ghost target.
        addRenderableWidget(new RsButton(fieldX + 22, iconRowY, propsRight - (fieldX + 22), BTN_H,
                Component.literal("Pick…"), b -> openIconPicker(draft)));
        // TODO(task6): JEI ghost-drag + viewer sync
        ghostTargets.add(new GhostTarget(new Rect2i(iconX, iconY, 18, 18), stack -> draft.icon = idOf(stack)));

        // Conditions control row.
        addRenderableWidget(new RsButton(modeBtnX, ctrlRowY, modeBtnW, BTN_H,
                Component.literal(draft.mode == MatchMode.ALL ? "Match ALL" : "Match ANY"),
                b -> { draft.mode = draft.mode == MatchMode.ALL ? MatchMode.ANY : MatchMode.ALL; rebuildWidgets(); }));
        addRenderableWidget(new RsButton(addBtnX, ctrlRowY, addBtnW, BTN_H,
                Component.literal("+ Add condition"), b -> {
            draft.conditions.add(CondDraft.fresh());
            condScroll = Integer.MAX_VALUE / 2;
            rebuildWidgets();
        }));

        int rowsShown = Math.min(condVisible, draft.conditions.size() - condScroll);
        for (int k = 0; k < rowsShown; k++) {
            final int condIndex = condScroll + k;
            final CondDraft cond = draft.conditions.get(condIndex);
            int rowY = condRowsTop + k * COND_ROW_H;
            int contentR = condNeedScroll ? condSbX - 2 : condContentR;
            int removeX = contentR - 18;
            int notX = rightX + 4 + TYPE_W + 2;
            int fieldStart = notX + NOT_W + 2;

            addRenderableWidget(new RsButton(rightX + 4, rowY, TYPE_W, BTN_H,
                    Component.literal("Type: " + cond.type.getSerializedName()),
                    b -> { cond.type = cycle(cond.type); rebuildWidgets(); }));

            addRenderableWidget(new RsButton(notX, rowY, NOT_W, BTN_H,
                    Component.literal(cond.negate ? "Not" : "Is"),
                    b -> { cond.negate = !cond.negate; rebuildWidgets(); }));

            if (cond.type == ConditionType.COMPONENT) {
                if (cond.componentMatch.usesArg()) {
                    int cycleW = 80;
                    addRenderableWidget(matchButton(cond, fieldStart, rowY, cycleW));
                    EditBox arg = RsStyle.selectAllField(this.font, fieldStart + cycleW + 2, rowY + 1,
                            Math.max(20, removeX - (fieldStart + cycleW + 2) - 2), 16, Component.literal("Arg"));
                    arg.setMaxLength(128);
                    arg.setValue(cond.value);
                    arg.setResponder(s -> cond.value = s);
                    addRenderableWidget(arg);
                } else {
                    addRenderableWidget(matchButton(cond, fieldStart, rowY, Math.max(20, removeX - fieldStart - 2)));
                }
            } else {
                int pickX = removeX - 20;
                int boxW = Math.max(20, pickX - fieldStart - 2);
                EditBox value = RsStyle.selectAllField(this.font, fieldStart, rowY + 1, boxW, 16, Component.literal("Value"));
                value.setMaxLength(128);
                value.setValue(cond.value);
                value.setResponder(s -> cond.value = s);
                addRenderableWidget(value);
                addRenderableWidget(new RsButton(pickX, rowY, 18, BTN_H,
                        Component.literal("…"), b -> openConditionPicker(cond)));
                // TODO(task6): JEI ghost-drag + viewer sync
                ghostTargets.add(new GhostTarget(new Rect2i(fieldStart, rowY, boxW, 18),
                        stack -> applyDroppedToCondition(cond, stack)));
            }

            addRenderableWidget(new RsButton(removeX, rowY, 18, BTN_H, Component.literal("✖"), b -> {
                draft.conditions.remove(condIndex);
                rebuildWidgets();
            }));
        }
    }

    private RsButton matchButton(CondDraft cond, int x, int y, int width) {
        return new RsButton(x, y, width, BTN_H, Component.literal(cond.componentMatch.getSerializedName()), b -> {
            cond.componentMatch = cycleMatch(cond.componentMatch);
            rebuildWidgets();
        });
    }

    private static <E extends Enum<E>> E cycle(E value) {
        E[] values = value.getDeclaringClass().getEnumConstants();
        return values[(value.ordinal() + 1) % values.length];
    }

    /**
     * Like {@link #cycle} but skips matches this Minecraft line can't honour — on
     * 1.20.1 {@code component_type} needs a registry that doesn't exist, so it must
     * not be selectable even though it still parses (see {@link ComponentMatch#supported()}).
     */
    private static ComponentMatch cycleMatch(ComponentMatch value) {
        ComponentMatch next = cycle(value);
        while (!next.supported() && next != value) {
            next = cycle(next);
        }
        return next;
    }


    // ---- Rendering ---------------------------------------------------------

    @Override
    public void renderBackground(GuiGraphics graphics) {
        graphics.fill(0, 0, this.width, this.height, RsStyle.DIM);
        theme.panel(graphics, left, top, panelW, panelH);
        int tc = theme.textColor();
        graphics.drawString(this.font, getTitle(), left + PAD, top + 8, tc, false);

        graphics.drawString(this.font, "Windows & Tabs", tabsX, tabsHeaderY, tc, false);
        RsStyle.inset(graphics, tabsX, tabsInsetY, tabsW, tabsInsetH);
        RsStyle.divider(graphics, tabsX + 3, toolbarDivY, tabsW - 6);

        if (pendingImport != null) {
            graphics.drawString(this.font, "Confirm import", rightX, mainTop, tc, false);
            RsStyle.inset(graphics, rightX, mainTop + HEADER_H, rightW, 60);
            WindowDraft w = selWindowDraft();
            int have = w == null ? 0 : w.tabs.size();
            graphics.drawString(this.font, "Replace this window's " + have + " tab(s)",
                    rightX + 6, mainTop + HEADER_H + 8, tc, false);
            graphics.drawString(this.font, "with " + pendingImport.size() + " imported tab(s)?",
                    rightX + 6, mainTop + HEADER_H + 20, tc, false);
        } else if (pendingDeleteWindow >= 0) {
            graphics.drawString(this.font, "Confirm", rightX, mainTop, tc, false);
            RsStyle.inset(graphics, rightX, mainTop + HEADER_H, rightW, 60);
            WindowDraft w = (pendingDeleteWindow < windows.size()) ? windows.get(pendingDeleteWindow) : null;
            int n = w == null ? 0 : w.tabs.size();
            graphics.drawString(this.font, "Delete window \"" + (w == null ? "" : w.name) + "\"",
                    rightX + 6, mainTop + HEADER_H + 8, tc, false);
            graphics.drawString(this.font, "and its " + n + " tab(s)?", rightX + 6, mainTop + HEADER_H + 20, tc, false);
        } else if (editingWindow()) {
            graphics.drawString(this.font, "Window", rightX, mainTop, tc, false);
            RsStyle.inset(graphics, rightX, propsInsetY, rightW, propsInsetH);
            int ly = propsInsetY + 4;
            graphics.drawString(this.font, "Name", rightX + 4, ly + 5, tc, false); ly += 22;
            graphics.drawString(this.font, "Layout", rightX + 4, ly + 5, tc, false); ly += 22;
            graphics.drawString(this.font, "Display", rightX + 4, ly + 5, tc, false); ly += 22;
            graphics.drawString(this.font, "Size", rightX + 4, ly + 5, tc, false); ly += 22;
            graphics.drawString(this.font, "Gear", rightX + 4, ly + 5, tc, false); ly += 22;
            graphics.drawString(this.font, "All", rightX + 4, ly + 5, tc, false); ly += 22;
            graphics.drawString(this.font, "Visible", rightX + 4, ly + 5, tc, false); ly += 22;
            graphics.drawString(this.font, "Position", rightX + 4, ly + 5, tc, false); ly += 22;
            graphics.drawString(this.font, "Tabs", rightX + 4, ly + 5, tc, false);
        } else {
            graphics.drawString(this.font, "Properties", rightX, propsHeaderY, tc, false);
            RsStyle.inset(graphics, rightX, propsInsetY, rightW, propsInsetH);
            graphics.drawString(this.font, "Conditions", rightX, condHeaderY, tc, false);
            RsStyle.inset(graphics, rightX, condInsetY, rightW, condInsetH);
            RsStyle.divider(graphics, rightX + 4, ctrlDivY, rightW - 8);
            graphics.drawString(this.font, "Inventory — drag onto the icon or a condition", invPanelX, invPanelY, tc, false);
            RsStyle.inset(graphics, invPanelX, invPanelY + HEADER_H, invPanelW, invPanelH);
            graphics.drawString(this.font, "Name", labelX, nameRowY + 5, tc, false);
            graphics.drawString(this.font, "Icon", labelX, iconRowY + 5, tc, false);
        }

        RsStyle.divider(graphics, innerX, dividerY, innerR - innerX);
        if (!status.isEmpty()) {
            int note = (tc & 0x00FFFFFF) | 0xBB000000;
            RsStyle.scaledText(graphics, this.font, status, innerX + 138, footerY + 6, note, 0.85f);
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // 1.20.1's Screen.render does NOT call renderBackground for us.
        this.renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
        drawTree(graphics, mouseX, mouseY);

        if (!editingWindow() && pendingDeleteWindow < 0) {
            TabDraft draft = selTabDraft();
            if (draft != null) {
                RsStyle.slot(graphics, iconX, iconY);
                ItemStack icon = iconStack(draft.icon);
                if (!icon.isEmpty()) {
                    graphics.renderItem(icon, iconX + 1, iconY + 1);
                    if (inRect(mouseX, mouseY, iconX, iconY, 18, 18) && draggingStack == null) {
                        graphics.renderTooltip(this.font, icon, mouseX, mouseY);
                    }
                }
                drawCondScrollbar(graphics);
            }
            drawInventory(graphics, mouseX, mouseY);
        }

        if (draggingStack != null && !draggingStack.isEmpty()) {
            // Highlight valid drop zones green while dragging (brighter under the cursor).
            for (GhostTarget target : ghostTargets) {
                Rect2i a = target.area();
                boolean over = inRect(mouseX, mouseY, a.getX(), a.getY(), a.getWidth(), a.getHeight());
                graphics.fill(a.getX(), a.getY(), a.getX() + a.getWidth(), a.getY() + a.getHeight(),
                        over ? 0x9040FF40 : 0x5040FF40);
                graphics.renderOutline(a.getX(), a.getY(), a.getWidth(), a.getHeight(), 0xFF40C040);
            }
            graphics.renderItem(draggingStack, mouseX - 8, mouseY - 8);
        }
    }

    private void drawTree(GuiGraphics graphics, int mouseX, int mouseY) {
        int rows = Math.min(listVisible, treeRows.size() - listScroll);
        for (int i = 0; i < rows; i++) {
            int idx = listScroll + i;
            int[] node = treeRows.get(idx);
            int wi = node[0], ti = node[1];
            int y = listY + i * ROW_HE;
            boolean isWindow = ti < 0;
            boolean active = isWindow ? (editingWindow() && selWindow == wi)
                    : (!editingWindow() && selWindow == wi && selTab == ti);
            // Tab rows are inset (their whole button starts further right) so they
            // read as nested under the window row.
            int rowX = isWindow ? listX : listX + TAB_INDENT;
            int rowW = isWindow ? listRowW : listRowW - TAB_INDENT;
            boolean hovered = inRect(mouseX, mouseY, rowX, y, rowW, ROW_HE - 1);
            RsStyle.bevelButton(graphics, rowX, y, rowW, ROW_HE - 1, active, hovered);
            int off = active ? 1 : 0;
            WindowDraft w = windows.get(wi);
            if (isWindow) {
                // Filled chevron at normal height, aligned with the window name.
                String caret = w.collapsed ? "▶" : "▼";
                graphics.drawString(this.font, caret, rowX + 3 + off, y + 5 + off, theme.textColor(), false);
                String label = (w.name.isBlank() ? "Window" : w.name);
                String text = this.font.plainSubstrByWidth(label, rowW - CARET_W - 2);
                graphics.drawString(this.font, text, rowX + CARET_W + off, y + 5 + off, theme.textColor(), false);
            } else {
                TabDraft t = w.tabs.get(ti);
                RsStyle.scaledItem(graphics, iconStack(t.icon), rowX + 2 + off, y + 1 + off, 14);
                String label = t.name.isBlank() ? t.id : t.name;
                String text = this.font.plainSubstrByWidth(label, rowW - 20);
                graphics.drawString(this.font, text, rowX + 18 + off, y + 5 + off, theme.textColor(), false);
            }
        }
        if (listNeedScroll) {
            int sbH = listVisible * ROW_HE;
            graphics.fill(listSbX, listY, listSbX + SBW, listY + sbH, 0x66000000);
            int thumbH = Math.max(12, sbH * listVisible / treeRows.size());
            int travel = sbH - thumbH;
            int thumbY = listY + (listMaxScroll == 0 ? 0 : travel * listScroll / listMaxScroll);
            RsStyle.bevelButton(graphics, listSbX, thumbY, SBW, thumbH, false, draggingListScrollbar);
        }
    }

    private void drawCondScrollbar(GuiGraphics graphics) {
        if (!condNeedScroll) {
            return;
        }
        TabDraft draft = selTabDraft();
        if (draft == null) {
            return;
        }
        int size = draft.conditions.size();
        int sbH = condVisible * COND_ROW_H;
        graphics.fill(condSbX, condRowsTop, condSbX + SBW, condRowsTop + sbH, 0x66000000);
        int thumbH = Math.max(12, sbH * condVisible / size);
        int travel = sbH - thumbH;
        int thumbY = condRowsTop + (condMaxScroll == 0 ? 0 : travel * condScroll / condMaxScroll);
        RsStyle.bevelButton(graphics, condSbX, thumbY, SBW, thumbH, false, draggingCondScrollbar);
    }

    private void drawInventory(GuiGraphics graphics, int mouseX, int mouseY) {
        if (this.minecraft == null || this.minecraft.player == null) {
            return;
        }
        ItemStack hovered = ItemStack.EMPTY;
        for (int i = 0; i < 36; i++) {
            int[] p = invSlotPos(i);
            RsStyle.slot(graphics, p[0], p[1]);
            ItemStack stack = this.minecraft.player.getInventory().getItem(i);
            if (!stack.isEmpty()) {
                graphics.renderItem(stack, p[0] + 1, p[1] + 1);
                graphics.renderItemDecorations(this.font, stack, p[0] + 1, p[1] + 1);
                if (inRect(mouseX, mouseY, p[0], p[1], 18, 18)) {
                    hovered = stack;
                }
            }
        }
        if (!hovered.isEmpty() && draggingStack == null) {
            graphics.renderTooltip(this.font, hovered, mouseX, mouseY);
        }
    }

    // ---- Item-picking ------------------------------------------------------

    private void openIconPicker(TabDraft draft) {
        this.minecraft.setScreen(new ItemPickerScreen(this, Component.literal("Pick tab icon"), theme, item -> {
            draft.icon = BuiltInRegistries.ITEM.getKey(item).toString();
            this.minecraft.setScreen(this);
        }));
    }

    private void openConditionPicker(CondDraft cond) {
        switch (cond.type) {
            case MOD -> this.minecraft.setScreen(new ItemPickerScreen(this,
                    Component.literal("Pick item — uses its mod"), theme, item -> {
                cond.value = BuiltInRegistries.ITEM.getKey(item).getNamespace();
                this.minecraft.setScreen(this);
            }));
            case TEXT -> this.minecraft.setScreen(new ItemPickerScreen(this,
                    Component.literal("Pick item — uses its name"), theme, item -> {
                cond.value = new ItemStack(item).getHoverName().getString();
                this.minecraft.setScreen(this);
            }));
            case TAG -> this.minecraft.setScreen(new ItemPickerScreen(this,
                    Component.literal("Pick item — choose its tag"), theme, item ->
                    this.minecraft.setScreen(new TagChooserScreen(this, item, theme, tag -> {
                        cond.value = tag;
                        this.minecraft.setScreen(this);
                    }))));
            case COMPONENT -> { /* no item picker */ }
        }
    }

    private void applyDroppedToCondition(CondDraft cond, ItemStack stack) {
        switch (cond.type) {
            case MOD -> {
                cond.value = BuiltInRegistries.ITEM.getKey(stack.getItem()).getNamespace();
                rebuildWidgets();
            }
            case TEXT -> {
                cond.value = stack.getHoverName().getString();
                rebuildWidgets();
            }
            case TAG -> this.minecraft.setScreen(new TagChooserScreen(this, stack.getItem(), theme, tag -> {
                cond.value = tag;
                this.minecraft.setScreen(this);
            }));
            case COMPONENT -> { /* none */ }
        }
    }

    private static String idOf(ItemStack stack) {
        return BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
    }

    private static ItemStack iconStack(String id) {
        ResourceLocation rl = ResourceLocation.tryParse(id.trim());
        Item item = rl == null ? Items.CHEST : BuiltInRegistries.ITEM.getOptional(rl).orElse(Items.CHEST);
        return new ItemStack(item);
    }

    // ---- Inventory + list geometry ----------------------------------------

    private int[] invSlotPos(int index) {
        if (index < 9) {
            return new int[]{invX + index * 18, invY + 3 * 18 + 4};
        }
        int main = index - 9;
        return new int[]{invX + (main % 9) * 18, invY + (main / 9) * 18};
    }

    private int invSlotAt(double mouseX, double mouseY) {
        for (int i = 0; i < 36; i++) {
            int[] p = invSlotPos(i);
            if (inRect(mouseX, mouseY, p[0], p[1], 18, 18)) {
                return i;
            }
        }
        return -1;
    }

    private static boolean inRect(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    private void listScrollTo(double mouseY) {
        if (listMaxScroll == 0) {
            listScroll = 0;
            return;
        }
        double fraction = (mouseY - listY) / Math.max(1, listVisible * ROW_HE);
        listScroll = Math.max(0, Math.min((int) Math.round(fraction * listMaxScroll), listMaxScroll));
    }

    private void condScrollTo(double mouseY) {
        if (condMaxScroll == 0) {
            return;
        }
        double fraction = (mouseY - condRowsTop) / Math.max(1, condVisible * COND_ROW_H);
        int next = Math.max(0, Math.min((int) Math.round(fraction * condMaxScroll), condMaxScroll));
        if (next != condScroll) {
            condScroll = next;
            rebuildWidgets();
        }
    }

    // ---- Tree selection ----------------------------------------------------

    private void selectNode(int wi, int ti) {
        selWindow = wi;
        selTab = ti;
        condScroll = 0;
        rebuildWidgets();
    }

    // ---- Toolbar actions ---------------------------------------------------

    private void addWindow() {
        WindowDraft w = WindowDraft.fresh("Window " + (windows.size() + 1));
        windows.add(w);
        selWindow = windows.size() - 1;
        selTab = -1;
        rebuildWidgets();
    }

    private void addTab() {
        if (windows.isEmpty()) {
            return;
        }
        int wi = Math.max(0, Math.min(selWindow, windows.size() - 1));
        WindowDraft w = windows.get(wi);
        w.collapsed = false;
        w.tabs.add(new TabDraft("tab-" + UUID.randomUUID().toString().substring(0, 8),
                "New Tab", "minecraft:chest", MatchMode.ANY));
        selWindow = wi;
        selTab = w.tabs.size() - 1;
        condScroll = 0;
        rebuildWidgets();
    }

    private void copySelected() {
        WindowDraft w = selWindowDraft();
        if (w == null) {
            return;
        }
        if (editingWindow()) {
            WindowDraft copy = WindowDraft.fresh(w.name + " (copy)");
            copy.orientation = w.orientation;
            copy.showLabels = w.showLabels;
            copy.scale = w.scale;
            for (TabDraft t : w.tabs) {
                copy.tabs.add(t.copyWithNewId());
            }
            windows.add(selWindow + 1, copy);
            selWindow = selWindow + 1;
            selTab = -1;
        } else {
            TabDraft src = w.tabs.get(selTab);
            TabDraft copy = src.copyWithNewId();
            copy.name = src.name + " (copy)";
            w.tabs.add(selTab + 1, copy);
            selTab = selTab + 1;
        }
        condScroll = 0;
        rebuildWidgets();
    }

    private void deleteSelected() {
        WindowDraft w = selWindowDraft();
        if (w == null) {
            return;
        }
        if (editingWindow()) {
            if (windows.size() <= 1) {
                return;   // never remove the last window
            }
            if (!w.tabs.isEmpty()) {
                pendingDeleteWindow = selWindow;   // ask first
                rebuildWidgets();
            } else {
                doDeleteWindow(selWindow);
            }
        } else {
            w.tabs.remove(selTab);
            selTab = w.tabs.isEmpty() ? -1 : Math.min(selTab, w.tabs.size() - 1);
            condScroll = 0;
            rebuildWidgets();
        }
    }

    private void doDeleteWindow(int index) {
        if (index < 0 || index >= windows.size() || windows.size() <= 1) {
            rebuildWidgets();
            return;
        }
        windows.remove(index);
        selWindow = Math.min(index, windows.size() - 1);
        selTab = -1;
        rebuildWidgets();
    }

    private void moveSelected(int delta) {
        WindowDraft w = selWindowDraft();
        if (w == null) {
            return;
        }
        if (editingWindow()) {
            int target = selWindow + delta;
            if (target < 0 || target >= windows.size()) {
                return;
            }
            Collections.swap(windows, selWindow, target);
            selWindow = target;
        } else {
            int target = selTab + delta;
            if (target < 0 || target >= w.tabs.size()) {
                return;
            }
            Collections.swap(w.tabs, selTab, target);
            selTab = target;
        }
        rebuildWidgets();
    }

    /** Opens the window picker for the selected tab. */
    private void openWindowPicker() {
        if (editingWindow() || selWindowDraft() == null) {
            return;
        }
        List<String> names = new ArrayList<>(windows.size());
        for (WindowDraft w : windows) {
            names.add(w.name.isBlank() ? w.id : w.name);
        }
        this.minecraft.setScreen(new WindowPickerScreen(this, names, selWindow, theme, this::moveSelectedTabToWindow));
    }

    /** Move the selected tab into the given window (by index). */
    private void moveSelectedTabToWindow(int target) {
        WindowDraft w = selWindowDraft();
        if (w == null || editingWindow() || target < 0 || target >= windows.size() || target == selWindow) {
            return;
        }
        TabDraft t = w.tabs.remove(selTab);
        WindowDraft dest = windows.get(target);
        dest.collapsed = false;
        dest.tabs.add(t);
        selWindow = target;
        selTab = dest.tabs.size() - 1;
        condScroll = 0;
        rebuildWidgets();
    }

    // ---- Commit / close ----------------------------------------------------

    /**
     * All grid types to show in the visibility list: every grid the user
     * has opened (stable — survives {@code resetwindows}), plus any that still
     * carry coords, plus the current one.
     */
    private List<String> knownTerminals() {
        TreeSet<String> set = new TreeSet<>(store.knownTerminalKeys());
        for (WindowDraft w : windows) {
            set.addAll(w.placements.keySet());
            if (!w.baseTerminal.isEmpty()) {
                set.add(w.baseTerminal);
            }
        }
        set.add(terminalKey);
        return new ArrayList<>(set);
    }

    private List<Tab> flattenTabs() {
        List<Tab> tabs = new ArrayList<>();
        for (WindowDraft w : windows) {
            for (TabDraft t : w.tabs) {
                tabs.add(t.toTab(w.id));
            }
        }
        return tabs;
    }

    private List<FilterWindow> collectWindows() {
        List<FilterWindow> out = new ArrayList<>(windows.size());
        for (WindowDraft w : windows) {
            out.add(w.toWindow());
        }
        return out;
    }

    // ---- Used by SettingsScreen's all-windows import/export ----------------

    /** Current (unsaved) windows as they'd be saved. */
    public List<FilterWindow> draftWindows() {
        return collectWindows();
    }

    /** Current (unsaved) tabs as they'd be saved. */
    public List<Tab> draftTabs() {
        return flattenTabs();
    }

    /** Reload the editor's drafts from {@link TabManager} (after an all-windows import). */
    public void reloadDrafts() {
        windows.clear();
        for (FilterWindow w : store.windows()) {
            windows.add(WindowDraft.from(w, store));
        }
        if (windows.isEmpty()) {
            windows.add(WindowDraft.fresh("Filters"));
        }
        selWindow = 0;
        selTab = -1;
        pendingImport = null;
        pendingDeleteWindow = -1;
        condScroll = 0;
        listScroll = 0;
    }

    private void commitAndClose() {
        store.replaceAll(collectWindows(), flattenTabs());
        onClose();
    }

    private void commitThenMove() {
        store.replaceAll(collectWindows(), flattenTabs());
        store.setMoveMode(true);
        onClose();
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(parent);
    }

    // ---- Input -------------------------------------------------------------

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            RsStyle.blurFieldOnOutsideClick(this, mouseX, mouseY);
            if (!editingWindow() && pendingDeleteWindow < 0) {
                int invIndex = invSlotAt(mouseX, mouseY);
                if (invIndex >= 0 && this.minecraft != null && this.minecraft.player != null) {
                    ItemStack stack = this.minecraft.player.getInventory().getItem(invIndex);
                    if (!stack.isEmpty()) {
                        draggingStack = stack.copy();
                        return true;
                    }
                }
                if (inRect(mouseX, mouseY, iconX, iconY, 18, 18)) {
                    TabDraft d = selTabDraft();
                    if (d != null) {
                        openIconPicker(d);
                        return true;
                    }
                }
                if (condNeedScroll && inRect(mouseX, mouseY, condSbX, condRowsTop, SBW, condVisible * COND_ROW_H)) {
                    draggingCondScrollbar = true;
                    condScrollTo(mouseY);
                    return true;
                }
            }
            if (listNeedScroll && inRect(mouseX, mouseY, listSbX, listY, SBW, listVisible * ROW_HE)) {
                draggingListScrollbar = true;
                listScrollTo(mouseY);
                return true;
            }
            int rows = Math.min(listVisible, treeRows.size() - listScroll);
            if (inRect(mouseX, mouseY, listX, listY, listRowW, rows * ROW_HE)) {
                int row = (int) ((mouseY - listY) / ROW_HE);
                if (row >= 0 && row < rows) {
                    int[] node = treeRows.get(listScroll + row);
                    int wi = node[0], ti = node[1];
                    if (ti < 0 && mouseX < listX + CARET_W) {
                        windows.get(wi).collapsed = !windows.get(wi).collapsed;
                        rebuildWidgets();
                    } else {
                        selectNode(wi, ti);
                    }
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dx, double dy) {
        if (draggingListScrollbar) {
            listScrollTo(mouseY);
            return true;
        }
        if (draggingCondScrollbar) {
            condScrollTo(mouseY);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        draggingListScrollbar = false;
        draggingCondScrollbar = false;
        if (draggingStack != null) {
            ItemStack dropped = draggingStack;
            draggingStack = null;
            for (GhostTarget target : ghostTargets) {
                Rect2i area = target.area();
                if (inRect(mouseX, mouseY, area.getX(), area.getY(), area.getWidth(), area.getHeight())) {
                    target.accept().accept(dropped);
                    break;
                }
            }
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollY) {
        if (listNeedScroll && inRect(mouseX, mouseY, listX, listY, listW, listVisible * ROW_HE)) {
            listScroll = Math.max(0, Math.min(listScroll + (scrollY < 0 ? 1 : -1), listMaxScroll));
            return true;
        }
        if (!editingWindow() && condNeedScroll
                && inRect(mouseX, mouseY, rightX, condRowsTop, rightW, condVisible * COND_ROW_H)) {
            int next = Math.max(0, Math.min(condScroll + (scrollY < 0 ? 1 : -1), condMaxScroll));
            if (next != condScroll) {
                condScroll = next;
                rebuildWidgets();
            }
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollY);
    }

    // ---- Size slider -------------------------------------------------------

    private final class WindowSizeSlider extends AbstractSliderButton {
        private final WindowDraft draft;

        WindowSizeSlider(int x, int y, int w, int h, WindowDraft draft) {
            super(x, y, w, h, Component.empty(),
                    (draft.scale - FilterWindow.MIN_SCALE) / (FilterWindow.MAX_SCALE - FilterWindow.MIN_SCALE));
            this.draft = draft;
            updateMessage();
        }

        @Override
        protected void updateMessage() {
            setMessage(Component.literal("Size: " + Math.round(draft.scale * 100) + "%"));
        }

        @Override
        protected void applyValue() {
            draft.scale = FilterWindow.MIN_SCALE + this.value * (FilterWindow.MAX_SCALE - FilterWindow.MIN_SCALE);
        }
    }

    // ---- Mutable working copies -------------------------------------------

    private static final class WindowDraft {
        String id;
        String name;
        Orientation orientation;
        boolean showLabels;
        double scale;
        PositionMode position;
        int x, y;
        boolean showGear;
        boolean showAll;
        boolean collapsed;
        final Map<String, Placement> placements = new HashMap<>();
        String baseTerminal = "";
        final Set<String> hiddenOn = new HashSet<>();
        final List<TabDraft> tabs = new ArrayList<>();

        WindowDraft(String id, String name, Orientation orientation, boolean showLabels, double scale,
                    PositionMode position, int x, int y, boolean showGear, boolean showAll) {
            this.id = id;
            this.name = name;
            this.orientation = orientation;
            this.showLabels = showLabels;
            this.scale = scale;
            this.position = position;
            this.x = x;
            this.y = y;
            this.showGear = showGear;
            this.showAll = showAll;
        }

        static WindowDraft fresh(String name) {
            return new WindowDraft("win-" + UUID.randomUUID().toString().substring(0, 8), name,
                    Orientation.VERTICAL, false, FilterWindow.DEFAULT_SCALE, PositionMode.CENTER, 0, 0, true, true);
        }

        static WindowDraft from(FilterWindow w, TabManager.Store store) {
            WindowDraft d = new WindowDraft(w.id(), w.name(), w.orientation(), w.showLabels(),
                    w.clampedScale(), w.position(), w.x(), w.y(), w.showGear(), w.showAll());
            d.placements.putAll(w.placements());
            d.baseTerminal = w.baseTerminal();
            d.hiddenOn.addAll(w.hiddenOn());
            d.collapsed = w.collapsed();
            for (Tab t : store.tabsForWindow(w.id())) {
                d.tabs.add(TabDraft.from(t));
            }
            return d;
        }

        FilterWindow toWindow() {
            String finalName = name.isBlank() ? id : name;
            return new FilterWindow(id, finalName, orientation, showLabels, scale, position, x, y,
                    showGear, showAll, new HashMap<>(placements), baseTerminal, new ArrayList<>(hiddenOn), collapsed);
        }
    }

    private static final class TabDraft {
        String id;
        String name;
        String icon;
        MatchMode mode;
        final List<CondDraft> conditions = new ArrayList<>();

        TabDraft(String id, String name, String icon, MatchMode mode) {
            this.id = id;
            this.name = name;
            this.icon = icon;
            this.mode = mode;
        }

        static TabDraft from(Tab tab) {
            TabDraft draft = new TabDraft(tab.id(), tab.name(), tab.icon().toString(), tab.mode());
            for (Condition condition : tab.conditions()) {
                draft.conditions.add(CondDraft.from(condition));
            }
            return draft;
        }

        TabDraft copyWithNewId() {
            TabDraft copy = new TabDraft("tab-" + UUID.randomUUID().toString().substring(0, 8),
                    name, icon, mode);
            for (CondDraft cond : conditions) {
                copy.conditions.add(new CondDraft(cond.type, cond.value, cond.componentMatch, cond.negate));
            }
            return copy;
        }

        Tab toTab(String windowId) {
            List<Condition> built = new ArrayList<>();
            for (CondDraft cond : conditions) {
                Condition condition = cond.build();
                if (condition != null && !built.contains(condition)) {
                    built.add(condition);
                }
            }
            ResourceLocation iconId = ResourceLocation.tryParse(icon.trim());
            String finalName = name.isBlank() ? id : name;
            return new Tab(id, finalName, iconId != null ? iconId : Tab.DEFAULT_ICON, mode, built, windowId);
        }
    }

    private static final class CondDraft {
        ConditionType type;
        String value;
        ComponentMatch componentMatch;
        boolean negate;

        CondDraft(ConditionType type, String value, ComponentMatch componentMatch, boolean negate) {
            this.type = type;
            this.value = value;
            this.componentMatch = componentMatch;
            this.negate = negate;
        }

        static CondDraft fresh() {
            return new CondDraft(ConditionType.MOD, "", ComponentMatch.ENCHANTED, false);
        }

        static CondDraft from(Condition condition) {
            if (condition instanceof ModCondition c) {
                return new CondDraft(ConditionType.MOD, c.modId(), ComponentMatch.ENCHANTED, c.negate());
            }
            if (condition instanceof TagCondition c) {
                return new CondDraft(ConditionType.TAG, c.tagId().toString(), ComponentMatch.ENCHANTED, c.negate());
            }
            if (condition instanceof TextCondition c) {
                return new CondDraft(ConditionType.TEXT, c.text(), ComponentMatch.ENCHANTED, c.negate());
            }
            if (condition instanceof ComponentCondition c) {
                return new CondDraft(ConditionType.COMPONENT, c.arg(), c.match(), c.negate());
            }
            return fresh();
        }

        @Nullable
        Condition build() {
            return switch (type) {
                case MOD -> value.isBlank() ? null : new ModCondition(value.trim(), negate);
                case TAG -> {
                    ResourceLocation rl = ResourceLocation.tryParse(value.trim());
                    yield rl == null ? null : new TagCondition(rl, negate);
                }
                case TEXT -> value.isBlank() ? null : new TextCondition(value.trim(), negate);
                case COMPONENT -> componentMatch.usesArg()
                        ? (value.isBlank() ? null : new ComponentCondition(componentMatch, value.trim(), negate))
                        : new ComponentCondition(componentMatch, "", negate);
            };
        }
    }
}
