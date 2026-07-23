package nl.ljack2k.ae2organizer.backend;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.function.Predicate;

/**
 * One storage system this mod can add filter tabs to (Applied Energistics 2 or
 * Refined Storage). Backends are the hard separation seam: each owns its own set
 * of tabs/windows/settings (a per-backend store keyed by {@link #id()}) and its
 * own filter hook, so filters never mix between systems.
 * <p>
 * A backend is only instantiated when its mod is actually present (see
 * {@link BackendRegistry#init()}), so implementations may freely reference that
 * mod's classes — they are never classloaded otherwise.
 */
public interface StorageBackend {

    /** Stable id, also the config filename and per-backend store key. AE2 uses
     *  {@code "ae2"} → keeps reading the legacy {@code tabs.json}; RS uses {@code "rs"}. */
    String id();

    /** Whether this backend's terminal/grid screen is the given screen. */
    boolean handles(Screen screen);

    /** A per-open adapter for a screen this backend {@link #handles}. */
    ScreenAdapter adapt(Screen screen);

    /**
     * Push the active tab's predicate into this backend's client-side filter
     * bridge (which its mixin reads). {@code null} clears the filter (the "All"
     * tab). Predicate operates on an {@link ItemStack}; non-item resources are
     * tested as {@link ItemStack#EMPTY}.
     */
    void setActiveFilter(@Nullable Predicate<ItemStack> predicate);
}
