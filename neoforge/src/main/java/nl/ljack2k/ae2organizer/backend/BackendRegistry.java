package nl.ljack2k.ae2organizer.backend;

import net.minecraft.client.gui.screens.Screen;
import net.neoforged.fml.ModList;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Registers the storage backends whose mod is actually present, and resolves the
 * backend for an open screen. The {@code ModList} gates in {@link #init()} are
 * what keep a missing mod's backend class (and its transitive AE2/RS references)
 * from ever being classloaded — so the jar runs cleanly with either, both, or
 * neither storage mod installed.
 */
public final class BackendRegistry {
    private BackendRegistry() {}

    private static final List<StorageBackend> BACKENDS = new ArrayList<>();

    public static void init() {
        BACKENDS.clear();
        if (ModList.get().isLoaded("ae2")) {
            register(new nl.ljack2k.ae2organizer.backend.ae2.Ae2Backend());
        }
        if (ModList.get().isLoaded("refinedstorage")) {
            register(new nl.ljack2k.ae2organizer.backend.rs.RsBackend());
        }
    }

    public static void register(StorageBackend backend) {
        BACKENDS.add(backend);
    }

    public static List<StorageBackend> all() {
        return BACKENDS;
    }

    /** The backend handling this screen, or {@code null} if it isn't a storage screen. */
    @Nullable
    public static StorageBackend forScreen(Screen screen) {
        for (StorageBackend backend : BACKENDS) {
            if (backend.handles(screen)) {
                return backend;
            }
        }
        return null;
    }

    @Nullable
    public static StorageBackend byId(String id) {
        for (StorageBackend backend : BACKENDS) {
            if (backend.id().equals(id)) {
                return backend;
            }
        }
        return null;
    }
}
