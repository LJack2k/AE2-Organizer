package nl.ljack2k.ae2organizer;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Entry point for TerminalOrganizer — a client-side mod that adds user-defined filter
 * tabs to Applied Energistics 2 terminals <em>and</em> Refined Storage 2 grids,
 * with a hard separation between the two (each storage system has its own
 * independent tabs/windows/settings).
 * <p>
 * All behaviour is client-side, so wiring is gated on {@link Dist#isClient()}.
 * Both AE2 and RS are optional: {@code BackendRegistry.init()} (run at client
 * setup) only instantiates the backend whose mod is present, and the mixin
 * configs self-gate, so the jar loads cleanly with either, both, or neither.
 */
@Mod(TerminalOrganizer.MODID)
public final class TerminalOrganizer {
    public static final String MODID = "ae2organizer";
    public static final Logger LOGGER = LoggerFactory.getLogger(MODID);

    public TerminalOrganizer(IEventBus modBus, Dist dist) {
        if (dist.isClient()) {
            nl.ljack2k.ae2organizer.client.ClientBootstrap.init(modBus);
            LOGGER.info("[TerminalOrganizer] Client loaded — filter tabs enabled on AE2 terminals and RS grids.");
        }
        // Dev-only RCON/screenshot test harness; never active in a normal install.
        if (System.getProperty("ae2organizer.devHarness") != null) {
            nl.ljack2k.ae2organizer.dev.DevHarness.init(modBus, dist);
            LOGGER.info("[TerminalOrganizer] Dev harness enabled (ae2organizer.devHarness).");
        }
    }
}
