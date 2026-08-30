package nl.ljack2k.ae2organizer.dev;

import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import nl.ljack2k.ae2organizer.client.ClientScreenshot;
import nl.ljack2k.ae2organizer.client.DevClientActions;

/**
 * Client half of {@link DevSignal}: watches incoming chat for the harness prefix,
 * performs the requested action, and cancels the message so it never reaches the
 * chat HUD (screenshots would otherwise capture the command that triggered them).
 * <p>
 * Registered only on the client dist and only when the harness is enabled, so a
 * dedicated server never classloads it — nor the {@code net.minecraft.client.*}
 * types it pulls in.
 */
public final class DevClientSignalListener {

    private DevClientSignalListener() {
    }

    @SubscribeEvent
    public static void onChat(ClientChatReceivedEvent event) {
        String text = event.getMessage().getString();
        if (!text.startsWith(DevSignal.PREFIX)) {
            return;
        }
        event.setCanceled(true);
        String[] parts = text.substring(DevSignal.PREFIX.length()).trim().split(" ", 2);
        String action = parts[0];
        String arg = parts.length > 1 ? parts[1].trim() : "";
        nl.ljack2k.ae2organizer.StorageOrganizer.LOGGER.info(
                "[StorageOrganizer] dev signal: action='{}' arg='{}'", action, arg);
        switch (action) {
            case DevSignal.ACTION_SCREENSHOT -> ClientScreenshot.take();
            case DevSignal.ACTION_EDITOR -> DevClientActions.openEditor();
            case DevSignal.ACTION_SELECT_TAB -> DevClientActions.selectTab(arg);
            case DevSignal.ACTION_SET_PACK -> DevClientActions.setResourcePack(arg);
            case DevSignal.ACTION_SET_GUI_SCALE -> DevClientActions.setGuiScale(arg);
            default -> {
                // unknown action — swallowed, nothing to do
            }
        }
    }
}
