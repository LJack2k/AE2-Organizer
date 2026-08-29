package nl.ljack2k.ae2organizer.dev;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * How the dev harness asks a client to do something, without adding a network
 * channel: the server sends the player a system chat message carrying a magic
 * prefix, and the client-side listener consumes (and swallows) it.
 * <p>
 * This deliberately avoids a custom {@code SimpleChannel}. Registering one on the
 * client made it stall during login against the dev dedicated server — the client
 * classifies the userdev server as a vanilla connection, and the extra channel then
 * hangs the handshake until the server's 30s login timeout. A chat marker has zero
 * handshake surface, which also keeps faith with the mod's client-only, any-server
 * design: nothing about the wire protocol changes.
 * <p>
 * Dev-only; only ever used when {@code -Dae2organizer.devHarness} is set.
 */
public final class DevSignal {

    /** Prefix that marks a chat line as a harness command rather than real chat. */
    public static final String PREFIX = "[TO-DEV]";

    public static final String ACTION_SCREENSHOT = "screenshot";
    public static final String ACTION_EDITOR = "editor";
    public static final String ACTION_SELECT_TAB = "select_tab";
    /** Dev-only: enable/disable a resource pack at runtime (arg = "<packId>" or "" to clear). */
    public static final String ACTION_SET_PACK = "set_pack";

    private DevSignal() {
    }

    /** Ask this player's client to perform an action. */
    public static void send(ServerPlayer player, String action, String arg) {
        player.sendSystemMessage(Component.literal(PREFIX + " " + action + " " + arg));
    }
}
