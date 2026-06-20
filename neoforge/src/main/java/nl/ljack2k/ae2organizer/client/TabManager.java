package nl.ljack2k.ae2organizer.client;

import appeng.api.stacks.AEKey;
import nl.ljack2k.ae2organizer.filter.Settings;
import nl.ljack2k.ae2organizer.filter.Tab;
import nl.ljack2k.ae2organizer.persist.TabStorage;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;

/**
 * Client-side singleton holding the loaded tabs and the active selection.
 * The active tab is tracked by id so it survives editor edits; a {@code null}
 * id means the "All" pseudo-tab (no filter).
 */
public final class TabManager {
    private TabManager() {}

    private static final List<Tab> TABS = new ArrayList<>();
    @Nullable
    private static String activeTabId = null;
    private static Settings settings = Settings.DEFAULT;
    private static boolean loaded = false;

    public static void load() {
        TabStorage.StoredData data = TabStorage.load();
        TABS.clear();
        TABS.addAll(data.tabs());
        settings = data.settings();
        loaded = true;
    }

    public static Settings getSettings() {
        return settings;
    }

    public static void setSettings(Settings newSettings) {
        settings = newSettings;
        persist();
    }

    private static void persist() {
        TabStorage.save(settings, TABS);
    }

    public static boolean isLoaded() {
        return loaded;
    }

    /** Unmodifiable view of the current tabs (in display order). */
    public static List<Tab> tabs() {
        return Collections.unmodifiableList(TABS);
    }

    /** Replaces the entire tab list (used by the editor on save) and persists it. */
    public static void replaceAll(List<Tab> newTabs) {
        TABS.clear();
        TABS.addAll(newTabs);
        // Drop the active selection if its tab no longer exists.
        if (activeTabId != null && activeTab() == null) {
            activeTabId = null;
        }
        persist();
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
