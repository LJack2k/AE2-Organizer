package nl.ljack2k.ae2organizer;

import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLEnvironment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Entry point for StorageOrganizer — a client-side mod that adds user-defined
 * filter tabs to Applied Energistics 2 terminals <em>and</em> Refined Storage
 * grids, with a hard separation between the two (each storage system has its own
 * independent tabs/windows/settings).
 * <p>
 * This is the <strong>Forge / 1.20.1</strong> line. It carries the same unified
 * backend architecture as the newer lines, but its RS backend is a different
 * implementation: 1.20.1 never got RS2, so {@code backend.rslegacy} hooks RS
 * <em>1.12</em>'s {@code GridScreen} / {@code IGridView} instead. It keeps the
 * backend id {@code "rs"}, so stores and filter exports line up across versions.
 * <p>
 * Both storage mods are optional: {@code BackendRegistry.init()} only instantiates
 * the backend whose mod is present and the mixin configs self-gate, so the jar
 * loads cleanly with either, both, or neither.
 * <p>
 * All behaviour is client-side, so wiring is gated on {@link FMLEnvironment#dist}:
 * nothing is registered on a dedicated server, and AE2's client classes are never
 * touched there.
 */
@Mod(StorageOrganizer.MODID)
public final class StorageOrganizer {
    public static final String MODID = "ae2organizer";
    public static final Logger LOGGER = LoggerFactory.getLogger(MODID);

    public StorageOrganizer() {
        if (FMLEnvironment.dist.isClient()) {
            nl.ljack2k.ae2organizer.client.ClientBootstrap.init();
            LOGGER.info("[StorageOrganizer] Client loaded — filter tabs enabled on AE2 terminals and RS grids.");
        }
        // Dev-only RCON/screenshot test harness; never active in a normal install.
        if (System.getProperty("ae2organizer.devHarness") != null) {
            nl.ljack2k.ae2organizer.dev.DevHarness.init();
            LOGGER.info("[StorageOrganizer] Dev harness enabled (ae2organizer.devHarness).");
        }
    }
}
