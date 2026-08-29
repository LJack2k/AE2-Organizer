package nl.ljack2k.ae2organizer.client;

import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import nl.ljack2k.ae2organizer.backend.BackendRegistry;
import nl.ljack2k.ae2organizer.backend.StorageBackend;

/**
 * Drops every registered backend theme's cached palette whenever the client
 * reloads its resource packs.
 * <p>
 * Without this, enabling an AE2 "dark mode" resource pack <em>mid-session</em>
 * left our screens half-themed: the panel texture turned black (textures are
 * re-read on reload) while the text colour stayed at whatever palette had been
 * cached on first draw — the light theme's near-black — so every label went
 * invisible. Enabling the same pack before launch looked perfectly fine, which
 * is what made this easy to miss.
 * <p>
 * Only invalidates; the next draw re-reads lazily. That keeps this independent
 * of whether AE2's own {@code StyleManager} has refreshed yet, since it runs
 * long before anything renders again.
 */
public final class ThemeReloadListener implements ResourceManagerReloadListener {

    public static final ThemeReloadListener INSTANCE = new ThemeReloadListener();

    private ThemeReloadListener() {
    }

    @Override
    public void onResourceManagerReload(ResourceManager resourceManager) {
        // Only touches backends whose mod is actually present, so a missing
        // storage mod's theme class is never loaded.
        for (StorageBackend backend : BackendRegistry.all()) {
            try {
                backend.theme().invalidate();
            } catch (Throwable ignored) {
                // a theme that can't invalidate must not break the reload
            }
        }
    }
}
