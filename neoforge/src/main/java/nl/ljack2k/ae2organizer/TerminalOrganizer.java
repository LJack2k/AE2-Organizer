package nl.ljack2k.ae2organizer;

import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLEnvironment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Entry point for TerminalOrganizer — a client-side mod that adds user-defined
 * filter tabs to Applied Energistics 2 terminals.
 * <p>
 * This is the <strong>Forge / 1.20.1</strong> line. It carries the same unified
 * backend architecture as the newer lines but ships only the AE2 backend:
 * Refined Storage for 1.20.1 is RS <em>1.12</em>, a pre-rewrite codebase whose
 * grid API has nothing in common with the RS2 one the other lines hook, so that
 * backend can't be reused here. The SPI in {@code backend/} is untouched, so a
 * legacy-RS backend can be added later without disturbing the core.
 * <p>
 * All behaviour is client-side, so wiring is gated on {@link FMLEnvironment#dist}:
 * nothing is registered on a dedicated server, and AE2's client classes are never
 * touched there.
 */
@Mod(TerminalOrganizer.MODID)
public final class TerminalOrganizer {
    public static final String MODID = "ae2organizer";
    public static final Logger LOGGER = LoggerFactory.getLogger(MODID);

    public TerminalOrganizer() {
        if (FMLEnvironment.dist.isClient()) {
            nl.ljack2k.ae2organizer.client.ClientBootstrap.init();
            LOGGER.info("[TerminalOrganizer] Client loaded — filter tabs enabled on AE2 terminals.");
        }
    }
}
