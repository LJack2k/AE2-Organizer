package nl.ljack2k.ae2organizer.dev;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import nl.ljack2k.ae2organizer.client.DevClientActions;
import org.lwjgl.glfw.GLFW;

/**
 * Dev-only hotkeys, active only under {@code -Dae2organizer.devHarness}.
 * <p>
 * <b>F6 cycles the GUI scale</b> (auto → 1 → 2 → 3 → 4). Vanilla has no keybind
 * for this, and the interesting case is changing scale <em>while a storage screen
 * is open</em>, so the open screen is re-inited through {@code resize()} rather
 * than constructed fresh.
 * <p>
 * Uses {@link InputEvent.Key} rather than a {@code KeyMapping}: a KeyMapping's
 * {@code consumeClick()} only ticks while no screen is open, which would miss the
 * whole point. It also keeps this out of the Controls screen and out of the lang
 * file, so nothing dev-only leaks into a shipped resource.
 * <p>
 * A raw key code, not a rebindable mapping, on purpose - this class is excluded
 * from the published jar, so there is nothing for a player to rebind.
 */
public final class DevKeybinds {
    private DevKeybinds() {}

    /** auto, then the usable scales; the resize call clamps anything the window can't take. */
    private static final int[] CYCLE = {0, 1, 2, 3, 4};

    @SubscribeEvent
    public static void onKey(InputEvent.Key event) {
        if (event.getAction() != GLFW.GLFW_PRESS || event.getKey() != GLFW.GLFW_KEY_F6) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        int current = mc.options.guiScale().get();
        int next = CYCLE[0];
        for (int i = 0; i < CYCLE.length; i++) {
            if (CYCLE[i] == current) {
                next = CYCLE[(i + 1) % CYCLE.length];
                break;
            }
        }
        DevClientActions.setGuiScale(String.valueOf(next));
        if (mc.gui != null) {
            mc.gui.setOverlayMessage(Component.literal(
                    "GUI scale: " + (next == 0 ? "auto" : next)
                            + " (effective " + mc.getWindow().getGuiScale() + ")"), false);
        }
    }
}
