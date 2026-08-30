package nl.ljack2k.ae2organizer.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import nl.ljack2k.ae2organizer.backend.BackendRegistry;
import nl.ljack2k.ae2organizer.backend.StorageBackend;
import nl.ljack2k.ae2organizer.client.gui.TabEditorScreen;
import org.jetbrains.annotations.Nullable;

/**
 * Client-only actions the dev harness triggers over the network (so the RCON test
 * loop can exercise UI paths that normally need a mouse: opening the editor and
 * selecting a tab). Referenced only from the CLIENT branch of the dev payload
 * registration, so a dedicated server never classloads it.
 * <p>
 * Backend-agnostic: it resolves the backend from the open screen (falling back to
 * RS, which the harness builds a grid for) and drives that backend's store.
 */
public final class DevClientActions {
    private DevClientActions() {}

    @Nullable
    private static StorageBackend resolve(@Nullable Screen screen) {
        StorageBackend backend = screen != null ? BackendRegistry.forScreen(screen) : null;
        return backend != null ? backend : BackendRegistry.byId("rs");
    }

    /** Open the tab editor, parented to the current storage screen if one is open. */
    public static void openEditor() {
        Minecraft mc = Minecraft.getInstance();
        Screen parent = mc.screen;
        StorageBackend backend = resolve(parent);
        if (backend == null) {
            return;
        }
        TabManager.Store store = TabManager.forBackend(backend.id());
        if (!store.isLoaded()) {
            store.load();
        }
        String key = (parent != null && backend.handles(parent))
                ? backend.adapt(parent).terminalKey()
                : "refinedstorage:grid";
        mc.setScreen(new TabEditorScreen(parent, key, store, backend.theme()));
    }

    /** Select a tab by id (empty/null = the All tab), applying filter + viewer sync. */
    public static void selectTab(@Nullable String id) {
        Minecraft mc = Minecraft.getInstance();
        Screen screen = mc.screen;
        StorageBackend backend = resolve(screen);
        if (backend == null) {
            return;
        }
        TabManager.Store store = TabManager.forBackend(backend.id());
        if (!store.isLoaded()) {
            store.load();
        }
        store.setActive((id == null || id.isEmpty()) ? null : id);
        ViewerSync.apply(store.activeTab());
        if (screen != null && backend.handles(screen)) {
            ClientEvents.applyFilter(backend.adapt(screen), store);
        }
    }

    /**
     * Dev-only: enable exactly the named resource pack (or none when {@code id} is
     * blank) and reload resources - the scripted equivalent of a player toggling a
     * pack in the options screen mid-session. Exists so the harness can reproduce
     * the "enable an AE2 dark-mode pack while the game is running" path, which is
     * the only way the theme-palette cache goes stale.
     */
    public static void setResourcePack(@Nullable String id) {
        Minecraft mc = Minecraft.getInstance();
        var repo = mc.getResourcePackRepository();
        repo.reload();
        java.util.List<String> selected = new java.util.ArrayList<>(repo.getSelectedIds());
        // Drop every file/ pack, then add back the requested one.
        selected.removeIf(s -> s.startsWith("file/"));
        if (id != null && !id.isBlank() && repo.getAvailableIds().contains(id)) {
            selected.add(id);
        }
        repo.setSelected(selected);
        mc.options.updateResourcePacks(repo);
        mc.reloadResourcePacks();
    }

    /**
     * Dev-only: change the GUI scale at runtime, the scripted equivalent of editing
     * it in Video Settings. Vanilla has no keybind for this, and restarting the
     * client between scales only exercises the load path - an in-game change instead
     * re-inits the open screen through {@code resize()}, which is the path a player
     * actually hits. {@code 0} means auto.
     * <p>
     * The value is not clamped here: the resize call runs it through
     * {@code Window#calculateScale}, which already bounds it to what the window can take.
     */
    public static void setGuiScale(@Nullable String arg) {
        if (arg == null || arg.isBlank()) {
            return;
        }
        int scale;
        try {
            scale = Integer.parseInt(arg.trim());
        } catch (NumberFormatException e) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        mc.options.guiScale().set(scale);
        mc.resizeDisplay();
        mc.options.save();
    }
}
