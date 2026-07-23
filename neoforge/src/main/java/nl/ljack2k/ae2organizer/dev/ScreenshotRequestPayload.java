package nl.ljack2k.ae2organizer.dev;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import nl.ljack2k.ae2organizer.TerminalOrganizer;

/**
 * Server → client trigger: "take a screenshot now". Empty payload; it's just a
 * signal so the RCON-driven dev harness can capture what the client sees (e.g.
 * an open grid with the tab bar). No client refs here, so it is safe to load on
 * a dedicated server. Dev-only — registered only when {@code -Dae2organizer.devHarness}
 * is set.
 */
public record ScreenshotRequestPayload() implements CustomPacketPayload {
    public static final Type<ScreenshotRequestPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(TerminalOrganizer.MODID, "screenshot"));

    public static final StreamCodec<ByteBuf, ScreenshotRequestPayload> STREAM_CODEC =
            StreamCodec.unit(new ScreenshotRequestPayload());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
