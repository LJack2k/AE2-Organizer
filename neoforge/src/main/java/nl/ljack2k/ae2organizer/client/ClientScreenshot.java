package nl.ljack2k.ae2organizer.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.neoforged.fml.loading.FMLPaths;

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
        Screenshot.grab(
                FMLPaths.GAMEDIR.get().toFile(),
                mc.getMainRenderTarget(),
                component -> {});
    }
}
