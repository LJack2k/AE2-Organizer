package nl.ljack2k.ae2organizer.dev;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import nl.ljack2k.ae2organizer.StorageOrganizer;

/**
 * Dev-only server → client trigger: "set the GUI scale" ({@code 0} = auto).
 * Changing it while a storage screen is open re-inits that screen through
 * {@code resize()}, which is the path a player hits and the one a restart-based
 * test never exercises.
 */
public record SetGuiScalePayload(int scale) implements CustomPacketPayload {
    public static final Type<SetGuiScalePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(StorageOrganizer.MODID, "set_gui_scale"));

    public static final StreamCodec<ByteBuf, SetGuiScalePayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, SetGuiScalePayload::scale,
            SetGuiScalePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
