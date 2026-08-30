package nl.ljack2k.ae2organizer.dev;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import nl.ljack2k.ae2organizer.StorageOrganizer;

/**
 * Dev-only server → client trigger: "select exactly this resource pack" (empty id
 * = none). Lets the harness reproduce a player enabling an AE2 dark-mode pack
 * mid-session, which is the only way the theme palette cache goes stale.
 */
public record SetPackPayload(String packId) implements CustomPacketPayload {
    public static final Type<SetPackPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(StorageOrganizer.MODID, "set_pack"));

    public static final StreamCodec<ByteBuf, SetPackPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, SetPackPayload::packId,
            SetPackPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
