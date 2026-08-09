package nl.ljack2k.ae2organizer.backend;

import net.minecraft.client.gui.screens.Screen;
import net.minecraftforge.fml.ModList;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Registers the storage backends whose mod is actually present, and resolves the
 * backend for an open screen. The {@code ModList} gate in {@link #init()} is what
 * keeps a missing mod's backend class (and its transitive AE2 references) from
 * ever being classloaded — so the jar runs cleanly without the storage mod.
 * <p>
 * On the Forge/1.20.1 line the RS backend is {@code backend.rslegacy}, written against
 * RS <strong>1.12</strong> (1.20.1 never got RS2, whose grid API the 1.21.1/26.1 lines
 * hook). It keeps the backend id {@code "rs"}, so the store file and cross-line filter
 * exports match the other lines.
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
            register(new nl.ljack2k.ae2organizer.backend.rslegacy.RsLegacyBackend());
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

    /** Whether any backend treats this screen as a terminal round-trip companion
     *  (craft amount/confirm, crafting status, terminal settings — see
     *  {@link StorageBackend#isCompanionScreen}). */
    public static boolean isCompanionScreen(Screen screen) {
        for (StorageBackend backend : BACKENDS) {
            if (backend.isCompanionScreen(screen)) {
                return true;
            }
        }
        return false;
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
