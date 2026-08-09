package nl.ljack2k.ae2organizer.dev;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import nl.ljack2k.ae2organizer.StorageOrganizer;

/**
 * Dev-only server → client trigger: "select this tab" (empty id = the All tab).
 * Lets the RCON harness exercise tab selection (filter + viewer sync) headlessly.
 */
public record SelectTabPayload(String tabId) implements CustomPacketPayload {
    public static final Type<SelectTabPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(StorageOrganizer.MODID, "select_tab"));

    public static final StreamCodec<ByteBuf, SelectTabPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, SelectTabPayload::tabId,
            SelectTabPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
