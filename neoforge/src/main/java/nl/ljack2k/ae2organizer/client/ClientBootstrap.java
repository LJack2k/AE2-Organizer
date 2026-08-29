package nl.ljack2k.ae2organizer.client;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.client.event.AddClientReloadListenersEvent;
import nl.ljack2k.ae2organizer.StorageOrganizer;

/**
 * Central client-side wiring, called once from the {@code @Mod} constructor on
 * the client dist. Registers the game-bus screen hooks (tab bar rendering /
 * input) and the mod-bus setup listener (registers the present backends and
 * loads their saved tabs). Kept in its own client-package class so the entry
 * point never references client-only types from a common context.
 */
public final class ClientBootstrap {

    private ClientBootstrap() {
    }

    public static void init(IEventBus modBus) {
        NeoForge.EVENT_BUS.register(ClientEvents.class);
        modBus.addListener(ClientSetup::onClientSetup);
        // Re-read backend palettes when resource packs change, so toggling an AE2
        // dark-mode pack mid-session re-themes our screens instead of leaving them
        // with a black panel and near-black text. 26.1 renamed the event to
        // AddClientReloadListenersEvent and requires a listener id.
        modBus.addListener((AddClientReloadListenersEvent e) -> e.addListener(
                Identifier.fromNamespaceAndPath(StorageOrganizer.MODID, "theme_reload"),
                ThemeReloadListener.INSTANCE));
    }
}
