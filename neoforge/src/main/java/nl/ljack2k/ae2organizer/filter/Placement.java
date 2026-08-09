package nl.ljack2k.ae2organizer.filter;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * A resolved on-screen placement: a {@link PositionMode} plus absolute coords
 * (only meaningful when {@code mode == FREE}). Used both as a window's global
 * default and as a per-grid override in {@link FilterWindow#placements()}.
 */
public record Placement(PositionMode mode, int x, int y) {

    public static final Codec<Placement> CODEC = RecordCodecBuilder.create(i -> i.group(
            PositionMode.CODEC.optionalFieldOf("mode", PositionMode.FREE).forGetter(Placement::mode),
            Codec.INT.optionalFieldOf("x", 0).forGetter(Placement::x),
            Codec.INT.optionalFieldOf("y", 0).forGetter(Placement::y)
    ).apply(i, Placement::new));
}
