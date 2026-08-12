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

    /**
     * The base class of this backend's storage screens (what {@link #handles}
     * accepts), for integrations that register per-screen-class handlers — JEI's
     * ghost-ingredient drops onto the tab bars. Called only when this backend is
     * instantiated, so returning the mod's class literal is safe.
     */
    Class<? extends Screen> screenClass();

    /** A per-open adapter for a screen this backend {@link #handles}. */
    ScreenAdapter adapt(Screen screen);

    /**
     * Whether this screen is a companion the terminal <em>bounces through</em> and
     * returns from — a craft amount/confirm dialog, a crafting status view, a
     * terminal settings page. Both AE2 and RS serve those from a fresh server-side
     * menu, so the terminal that comes back is a brand-new screen instance; without
     * this, "reset filter when opening a grid" would fire on every autocraft
     * request. Returning is not opening, so the active tab is kept.
     */
    default boolean isCompanionScreen(Screen screen) {
        return false;
    }

    /**
     * This backend's visual look for the tab panel and this mod's client-only
     * screens. A singleton per backend. RS returns its bundled-sprite theme; AE2
     * returns a theme that renders through AE2's own GUI pipeline.
     */
    Theme theme();

    /**
     * Push the active tab's predicate into this backend's client-side filter
     * bridge (which its mixin reads). {@code null} clears the filter (the "All"
     * tab). Predicate operates on an {@link ItemStack}; non-item resources are
     * tested as {@link ItemStack#EMPTY}.
     */
    void setActiveFilter(@Nullable Predicate<ItemStack> predicate);
}
