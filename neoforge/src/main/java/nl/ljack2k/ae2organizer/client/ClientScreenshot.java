package nl.ljack2k.ae2organizer.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraftforge.fml.loading.FMLPaths;
import nl.ljack2k.ae2organizer.StorageOrganizer;

/**
 * Client-only: grab the current frame to {@code <rundir>/screenshots/rsorgshot.png}
 * (fixed name so it's overwritten and easy to read back). Driven by the dev
 * harness's {@code /rsorgshot} command so the agent can capture what the joined
 * client sees (e.g. an open grid with the tab bar). Dev-only.
 */
public final class ClientScreenshot {
    private ClientScreenshot() {}

    public static void take() {
        Minecraft mc = Minecraft.getInstance();
        try {
            Screenshot.grab(
                    FMLPaths.GAMEDIR.get().toFile(),
                    "rsorgshot.png",
                    mc.getMainRenderTarget(),
                    // Vanilla reports success/failure through this callback only; log it,
                    // otherwise a failed grab looks exactly like a lost signal.
                    component -> StorageOrganizer.LOGGER.info("[StorageOrganizer] screenshot: {}",
                            component.getString()));
        } catch (Throwable t) {
            StorageOrganizer.LOGGER.warn("[StorageOrganizer] screenshot failed", t);
        }
    }
}
