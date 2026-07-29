package nl.ljack2k.ae2organizer.client;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

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

    public static void init() {
        MinecraftForge.EVENT_BUS.register(ClientEvents.class);
        FMLJavaModLoadingContext.get().getModEventBus().addListener(ClientSetup::onClientSetup);
    }
}
