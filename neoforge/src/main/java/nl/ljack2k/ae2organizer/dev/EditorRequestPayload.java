package nl.ljack2k.ae2organizer.dev;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import nl.ljack2k.ae2organizer.AE2Organizer;

/**
 * Dev-only server → client trigger: "open the tab editor". Lets the RCON harness
 * screenshot the editor (with JEI beside it) without a mouse click on the gear.
 */
public record EditorRequestPayload() implements CustomPacketPayload {
    public static final Type<EditorRequestPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(AE2Organizer.MODID, "open_editor"));

    public static final StreamCodec<ByteBuf, EditorRequestPayload> STREAM_CODEC =
            StreamCodec.unit(new EditorRequestPayload());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
