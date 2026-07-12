package nl.ljack2k.ae2organizer.client;

import appeng.api.stacks.AEKey;
import nl.ljack2k.ae2organizer.filter.FilterWindow;
import nl.ljack2k.ae2organizer.filter.Placement;
import nl.ljack2k.ae2organizer.filter.PositionMode;
import nl.ljack2k.ae2organizer.filter.Settings;
import nl.ljack2k.ae2organizer.filter.Tab;
import nl.ljack2k.ae2organizer.persist.TabStorage;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * Client-side singleton holding the loaded windows + tabs and the active selection.
 * The active tab is tracked by id so it survives editor edits; a {@code null} id
 * means the "All" pseudo-tab (no filter). The active selection is global across all
 * windows — windows are only a way to lay tabs out into separate movable groups.
 */
public final class TabManager {
    private TabManager() {}

    private static final List<FilterWindow> WINDOWS = new ArrayList<>();
    private static final List<Tab> TABS = new ArrayList<>();
    /** Remembered display names for terminal types (menu-type id → screen title). */
    private static final Map<String, String> TERMINAL_NAMES = new HashMap<>();
    @Nullable
    private static String activeTabId = null;
    private static Settings settings = Settings.DEFAULT;
    private static boolean loaded = false;

    /** Transient (not persisted): when true, windows can be dragged in the terminal. */
    private static boolean moveMode = false;

    public static void load() {
        TabStorage.StoredData data = TabStorage.load();
        WINDOWS.clear();
        WINDOWS.addAll(data.windows());
        TABS.clear();
        TABS.addAll(data.tabs());
        TERMINAL_NAMES.clear();
        TERMINAL_NAMES.putAll(data.terminalNames());
        settings = data.settings();
        loaded = true;
    }

    /**
     * Records that a terminal type has been opened, so it appears in the
     * visibility list. The key is always registered (AE2 terminals often have a
     * blank vanilla screen title — the friendly name is derived from the id in
     * the UI); a non-blank title, if any, is kept as an override. Persists on change.
     */
    public static void rememberTerminal(String terminalKey, String displayName) {
        String name = displayName == null ? "" : displayName.trim();
        boolean known = TERMINAL_NAMES.containsKey(terminalKey);
        boolean better = !name.isEmpty() && !name.equals(TERMINAL_NAMES.get(terminalKey));
        if (!known || better) {
            TERMINAL_NAMES.put(terminalKey, name);
            persist();
        }
    }

    /** The remembered display name for a terminal type, or {@code null} if unseen. */
    @Nullable
    public static String terminalName(String terminalKey) {
        return TERMINAL_NAMES.get(terminalKey);
    }

    /** All terminal types the user has opened (and so we have names for). */
    public static java.util.Set<String> knownTerminalKeys() {
        return new java.util.HashSet<>(TERMINAL_NAMES.keySet());
    }

    public static Settings getSettings() {
        return settings;
    }

    public static void setSettings(Settings newSettings) {
        settings = newSettings;
        persist();
    }

    private static void persist() {
        TabStorage.save(settings, WINDOWS, TABS, TERMINAL_NAMES);
    }

    public static boolean isLoaded() {
        return loaded;
    }

    // ---- Windows -----------------------------------------------------------

    /** Unmodifiable view of the current windows (in display order). */
    public static List<FilterWindow> windows() {
        return Collections.unmodifiableList(WINDOWS);
    }

    /** Whether any window shows its gear (used to keep the editor reachable). */
    public static boolean anyGear() {
        for (FilterWindow w : WINDOWS) {
            if (w.showGear()) {
                return true;
            }
        }
        return false;
    }

    @Nullable
    public static FilterWindow window(String id) {
        for (FilterWindow w : WINDOWS) {
            if (w.id().equals(id)) {
                return w;
            }
        }
        return null;
    }

    /**
     * Windows shown on the given terminal type, in order. If every window is
     * hidden there, the first window is returned anyway so the editor gear stays
     * reachable (lockout safeguard).
     */
    public static List<FilterWindow> visibleWindows(String terminalKey) {
        List<FilterWindow> vis = new ArrayList<>();
        for (FilterWindow w : WINDOWS) {
            if (w.visibleOn(terminalKey)) {
                vis.add(w);
            }
        }
        if (vis.isEmpty() && !WINDOWS.isEmpty()) {
            vis.add(WINDOWS.get(0));
        }
        return vis;
    }

    /** Tabs assigned to the given window, in display order. */
    public static List<Tab> tabsForWindow(String windowId) {
        List<Tab> out = new ArrayList<>();
        for (Tab tab : TABS) {
            if (tab.window().equals(windowId)) {
                out.add(tab);
            }
        }
        return out;
    }

    /** Replaces a single window in place (by id) and persists. Used by move-mode drag. */
    public static void updateWindow(FilterWindow updated) {
        for (int i = 0; i < WINDOWS.size(); i++) {
            if (WINDOWS.get(i).id().equals(updated.id())) {
                WINDOWS.set(i, updated);
                persist();
                return;
            }
        }
    }

    /** Replaces the entire windows + tabs lists (used by the editor on save) and persists. */
    public static void replaceAll(List<FilterWindow> newWindows, List<Tab> newTabs) {
        WINDOWS.clear();
        WINDOWS.addAll(newWindows);
        TABS.clear();
        TABS.addAll(newTabs);
        if (WINDOWS.isEmpty()) {
            WINDOWS.add(FilterWindow.createDefault(false, FilterWindow.DEFAULT_SCALE));
        }
        // Drop the active selection if its tab no longer exists.
        if (activeTabId != null && activeTab() == null) {
            activeTabId = null;
        }
        persist();
    }

    /**
     * Last-resort recovery: put every window back into a reachable state — the
     * first window docked, the rest centered, all gears shown — and turn off
     * move-mode. Tabs and windows are otherwise untouched. Persists.
     */
    public static void resetWindowLayout() {
        for (int i = 0; i < WINDOWS.size(); i++) {
            FilterWindow w = WINDOWS.get(i);
            PositionMode mode = (i == 0) ? PositionMode.DOCK : PositionMode.CENTER;
            // Reset the global default AND drop every per-terminal override.
            WINDOWS.set(i, new FilterWindow(w.id(), w.name(), w.orientation(), w.showLabels(),
                    w.clampedScale(), mode, 0, 0, true, w.showAll(), java.util.Map.of(), "", java.util.List.of(),
                    w.collapsed()));
        }
        moveMode = false;
        persist();
    }

    /** Sets/overwrites a window's placement for one terminal type and persists. */
    public static void updateWindowPlacement(String windowId, String terminalKey,
                                             PositionMode mode, int x, int y) {
        for (int i = 0; i < WINDOWS.size(); i++) {
            FilterWindow w = WINDOWS.get(i);
            if (w.id().equals(windowId)) {
                WINDOWS.set(i, w.withPlacement(terminalKey, new Placement(mode, x, y)));
                persist();
                return;
            }
        }
    }

    public static boolean isMoveMode() {
        return moveMode;
    }

    public static void setMoveMode(boolean on) {
        moveMode = on;
    }

    // ---- Tabs / active selection ------------------------------------------

    /** Unmodifiable view of all tabs (in display order, across every window). */
    public static List<Tab> tabs() {
        return Collections.unmodifiableList(TABS);
    }

    @Nullable
    public static String activeTabId() {
        return activeTabId;
    }

    public static void setActive(@Nullable String id) {
        activeTabId = id;
    }

    @Nullable
    public static Tab activeTab() {
        if (activeTabId == null) {
            return null;
        }
        for (Tab tab : TABS) {
            if (tab.id().equals(activeTabId)) {
                return tab;
            }
        }
        return null;
    }

    /** The predicate for the active tab, or {@code null} to show everything. */
    @Nullable
    public static Predicate<AEKey> activePredicate() {
        Tab tab = activeTab();
        return tab == null ? null : tab.toPredicate();
    }
}
