package nl.ljack2k.ae2organizer;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;
import nl.ljack2k.ae2organizer.client.ClientEvents;
import nl.ljack2k.ae2organizer.client.ClientSetup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Entry point for AE2Organizer — a client-side mod that adds user-defined
 * filter tabs to Applied Energistics 2 terminals.
 * <p>
 * All behaviour is client-side, so wiring is gated on {@link FMLEnvironment#dist}:
 * the game-bus screen hooks ({@link ClientEvents}) draw the tab bar, and the
 * mod-bus {@link ClientSetup} loads the saved tabs. Nothing is registered on a
 * dedicated server, so AE2's client classes are never touched there.
 */
@Mod(AE2Organizer.MODID)
public final class AE2Organizer {
    public static final String MODID = "ae2organizer";
    public static final Logger LOGGER = LoggerFactory.getLogger(MODID);

    public AE2Organizer() {
        if (FMLEnvironment.dist.isClient()) {
            MinecraftForge.EVENT_BUS.register(ClientEvents.class);
            FMLJavaModLoadingContext.get().getModEventBus().addListener(ClientSetup::onClientSetup);
            LOGGER.info("[AE2Organizer] Client loaded — filter tabs enabled on AE2 terminals.");
        }
    }
}
