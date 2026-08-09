package nl.ljack2k.ae2organizer.client;

import net.minecraft.world.item.ItemStack;
import nl.ljack2k.ae2organizer.backend.BackendRegistry;
import nl.ljack2k.ae2organizer.backend.StorageBackend;
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
 * Client-side registry of per-backend {@link Store}s — the hard-separation seam
 * on the data side. Each storage backend ({@code "ae2"}, {@code "rs"}) gets its
 * own independent {@code Store} (windows + tabs + active selection + settings +
 * terminal names), persisted to its own {@code config/ae2organizer/<file>.json}.
 * Filters never mix: switching a tab pushes that store's predicate only into that
 * backend's client-side filter bridge.
 * <p>
 * {@code ClientEvents} resolves the backend for an open screen and drives only
 * that backend's store; the GUI is handed the specific store it belongs to.
 */
public final class TabManager {
    private TabManager() {}

    private static final Map<String, Store> STORES = new HashMap<>();

    /** The persisted filename for a backend id. AE2 keeps the legacy {@code tabs.json}. */
    private static String fileNameFor(String backendId) {
        return backendId.equals("ae2") ? "tabs.json" : backendId + ".json";
    }

    /** The store for a backend id, created (empty, not yet loaded) on first use. */
    public static Store forBackend(String backendId) {
        return STORES.computeIfAbsent(backendId, id -> new Store(id, fileNameFor(id)));
    }

    /** All stores instantiated so far (for cross-cutting recovery commands). */
    public static Iterable<Store> allStores() {
        return new ArrayList<>(STORES.values());
    }

    /**
     * One storage backend's independent set of windows + tabs + active selection +
     * settings. Mirrors the old flat TabManager API, but scoped to a single backend
     * and pushing its predicate into that backend's filter bridge on change.
     */
    public static final class Store {
        private final String backendId;
        private final String fileName;

        private final List<FilterWindow> windows = new ArrayList<>();
        private final List<Tab> tabs = new ArrayList<>();
        /** Remembered display names for terminal types (menu-type id → screen title). */
        private final Map<String, String> terminalNames = new HashMap<>();
        @Nullable
        private String activeTabId = null;
        private Settings settings = Settings.DEFAULT;
        private boolean loaded = false;

        /** Transient (not persisted): when true, windows can be dragged. */
        private boolean moveMode = false;

        Store(String backendId, String fileName) {
            this.backendId = backendId;
            this.fileName = fileName;
        }

        public String backendId() {
            return backendId;
        }

        public void load() {
            TabStorage.StoredData data = TabStorage.load(fileName);
            windows.clear();
            windows.addAll(data.windows());
            tabs.clear();
            tabs.addAll(data.tabs());
            terminalNames.clear();
            terminalNames.putAll(data.terminalNames());
            settings = data.settings();
            loaded = true;
            pushFilter();
        }

        private void persist() {
            TabStorage.save(fileName, settings, windows, tabs, terminalNames);
        }

        public boolean isLoaded() {
            return loaded;
        }

        // ---- Terminal names -----------------------------------------------

        public void rememberTerminal(String terminalKey, String displayName) {
            String name = displayName == null ? "" : displayName.trim();
            boolean known = terminalNames.containsKey(terminalKey);
            boolean better = !name.isEmpty() && !name.equals(terminalNames.get(terminalKey));
            if (!known || better) {
                terminalNames.put(terminalKey, name);
                persist();
            }
        }

        @Nullable
        public String terminalName(String terminalKey) {
            return terminalNames.get(terminalKey);
        }

        public java.util.Set<String> knownTerminalKeys() {
            return new java.util.HashSet<>(terminalNames.keySet());
        }

        // ---- Settings ------------------------------------------------------

        public Settings getSettings() {
            return settings;
        }

        public void setSettings(Settings newSettings) {
            settings = newSettings;
            persist();
        }

        // ---- Windows -------------------------------------------------------

        public List<FilterWindow> windows() {
            return Collections.unmodifiableList(windows);
        }

        public boolean anyGear() {
            for (FilterWindow w : windows) {
                if (w.showGear()) {
                    return true;
                }
            }
            return false;
        }

        @Nullable
        public FilterWindow window(String id) {
            for (FilterWindow w : windows) {
                if (w.id().equals(id)) {
                    return w;
                }
            }
            return null;
        }

        public List<FilterWindow> visibleWindows(String terminalKey) {
            List<FilterWindow> vis = new ArrayList<>();
            for (FilterWindow w : windows) {
                if (w.visibleOn(terminalKey)) {
                    vis.add(w);
                }
            }
            if (vis.isEmpty() && !windows.isEmpty()) {
                vis.add(windows.get(0));
            }
            return vis;
        }

        public List<Tab> tabsForWindow(String windowId) {
            List<Tab> out = new ArrayList<>();
            for (Tab tab : tabs) {
                if (tab.window().equals(windowId)) {
                    out.add(tab);
                }
            }
            return out;
        }

        public void updateWindow(FilterWindow updated) {
            for (int i = 0; i < windows.size(); i++) {
                if (windows.get(i).id().equals(updated.id())) {
                    windows.set(i, updated);
                    persist();
                    return;
                }
            }
        }

        public void replaceAll(List<FilterWindow> newWindows, List<Tab> newTabs) {
            windows.clear();
            windows.addAll(newWindows);
            tabs.clear();
            tabs.addAll(newTabs);
            if (windows.isEmpty()) {
                windows.add(FilterWindow.createDefault(false, FilterWindow.DEFAULT_SCALE));
            }
            if (activeTabId != null && activeTab() == null) {
                activeTabId = null;
            }
            persist();
            pushFilter();
        }

        public void resetWindowLayout() {
            for (int i = 0; i < windows.size(); i++) {
                FilterWindow w = windows.get(i);
                PositionMode mode = (i == 0) ? PositionMode.DOCK : PositionMode.CENTER;
                windows.set(i, new FilterWindow(w.id(), w.name(), w.orientation(), w.showLabels(),
                        w.clampedScale(), mode, 0, 0, true, w.showAll(), java.util.Map.of(), "", java.util.List.of(),
                        w.collapsed()));
            }
            moveMode = false;
            persist();
        }

        public void updateWindowPlacement(String windowId, String terminalKey,
                                          PositionMode mode, int x, int y) {
            for (int i = 0; i < windows.size(); i++) {
                FilterWindow w = windows.get(i);
                if (w.id().equals(windowId)) {
                    windows.set(i, w.withPlacement(terminalKey, new Placement(mode, x, y)));
                    persist();
                    return;
                }
            }
        }

        public boolean isMoveMode() {
            return moveMode;
        }

        public void setMoveMode(boolean on) {
            moveMode = on;
        }

        // ---- Tabs / active selection --------------------------------------

        public List<Tab> tabs() {
            return Collections.unmodifiableList(tabs);
        }

        @Nullable
        public String activeTabId() {
            return activeTabId;
        }

        public void setActive(@Nullable String id) {
            activeTabId = id;
            pushFilter();
        }

        @Nullable
        public Tab activeTab() {
            if (activeTabId == null) {
                return null;
            }
            for (Tab tab : tabs) {
                if (tab.id().equals(activeTabId)) {
                    return tab;
                }
            }
            return null;
        }

        @Nullable
        public Predicate<ItemStack> activePredicate() {
            Tab tab = activeTab();
            return tab == null ? null : tab.toPredicate();
        }

        /** Push the current active predicate into this backend's filter bridge. */
        public void pushFilter() {
            StorageBackend backend = BackendRegistry.byId(backendId);
            if (backend != null) {
                backend.setActiveFilter(activePredicate());
            }
        }
    }
}
