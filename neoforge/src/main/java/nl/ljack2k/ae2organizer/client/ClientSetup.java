package nl.ljack2k.ae2organizer.client;

import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import nl.ljack2k.ae2organizer.backend.BackendRegistry;
import nl.ljack2k.ae2organizer.backend.StorageBackend;

/**
 * Mod-bus client setup: register the backends whose mod is present, then load
 * each one's saved tabs once the client is ready.
 */
public final class ClientSetup {
    private ClientSetup() {}

    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            BackendRegistry.init();
            for (StorageBackend backend : BackendRegistry.all()) {
                TabManager.forBackend(backend.id()).load();
            }
        });
    }
}
