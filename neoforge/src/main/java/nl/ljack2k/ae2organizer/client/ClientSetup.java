package nl.ljack2k.ae2organizer.client;

import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;

/** Mod-bus client setup: load the saved tabs once the client is ready. */
public final class ClientSetup {
    private ClientSetup() {}

    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(TabManager::load);
    }
}
