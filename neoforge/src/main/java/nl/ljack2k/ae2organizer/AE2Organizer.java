package nl.ljack2k.ae2organizer;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import nl.ljack2k.ae2organizer.client.ClientEvents;
import nl.ljack2k.ae2organizer.client.ClientSetup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Entry point for AE2Organizer — a client-side mod that adds user-defined
 * filter tabs to Applied Energistics 2 terminals.
 * <p>
 * All behaviour is client-side, so wiring is gated on {@link Dist#isClient()}:
 * the game-bus screen hooks ({@link ClientEvents}) draw the tab bar, and the
 * mod-bus {@link ClientSetup} loads the saved tabs. Nothing is registered on a
 * dedicated server, so AE2's client classes are never touched there.
 */
@Mod(AE2Organizer.MODID)
public final class AE2Organizer {
    public static final String MODID = "ae2organizer";
    public static final Logger LOGGER = LoggerFactory.getLogger(MODID);

    public AE2Organizer(IEventBus modBus, Dist dist) {
        if (dist.isClient()) {
            NeoForge.EVENT_BUS.register(ClientEvents.class);
            modBus.addListener(ClientSetup::onClientSetup);
            LOGGER.info("[AE2Organizer] Client loaded — filter tabs enabled on AE2 terminals.");
        }
    }
}
