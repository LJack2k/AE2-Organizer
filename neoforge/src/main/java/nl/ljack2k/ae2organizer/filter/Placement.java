package nl.ljack2k.ae2organizer.filter;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.Optional;

/**
 * A resolved on-screen placement: a {@link PositionMode} plus coordinates (only
 * meaningful when {@code mode == FREE}). Used both as a window's global default
 * and as a per-grid override in {@link FilterWindow#placements()}.
 * <p>
 * A FREE placement is stored as {@code dx}/{@code dy}: an offset from the
 * <em>terminal's</em> top-left, not from the screen's. Both the terminal and our
 * panel have a fixed size in scaled GUI pixels, so that offset means the same
 * thing at every GUI scale. The older {@code x}/{@code y} were absolute screen
 * coordinates, which did not survive a scale change at all: {@code getGuiScaledWidth()}
 * changes with the scale, so the terminal moves while the stored point does not.
 * Going from scale 3 to 1 on a 1920-wide window roughly triples the GUI width and
 * the panel ends up on the opposite side of the terminal.
 * <p>
 * {@code x}/{@code y} are kept only to read configs written before that change;
 * {@link #hasAnchor()} tells the two apart, and the tab bar rewrites a legacy
 * placement into {@code dx}/{@code dy} the first time it resolves one.
 */
public record Placement(PositionMode mode, int x, int y, Optional<Integer> dx, Optional<Integer> dy) {

    public static final Codec<Placement> CODEC = RecordCodecBuilder.create(i -> i.group(
            PositionMode.CODEC.optionalFieldOf("mode", PositionMode.FREE).forGetter(Placement::mode),
            Codec.INT.optionalFieldOf("x", 0).forGetter(Placement::x),
            Codec.INT.optionalFieldOf("y", 0).forGetter(Placement::y),
            Codec.INT.optionalFieldOf("dx").forGetter(Placement::dx),
            Codec.INT.optionalFieldOf("dy").forGetter(Placement::dy)
    ).apply(i, Placement::new));

    /** A legacy placement: absolute screen coordinates, no terminal anchor yet. */
    public static Placement absolute(PositionMode mode, int x, int y) {
        return new Placement(mode, x, y, Optional.empty(), Optional.empty());
    }

    /** A placement anchored to the terminal's top-left corner. */
    public static Placement anchored(PositionMode mode, int dx, int dy) {
        return new Placement(mode, 0, 0, Optional.of(dx), Optional.of(dy));
    }

    /** Whether this carries a terminal-relative anchor rather than legacy absolute coords. */
    public boolean hasAnchor() {
        return dx.isPresent() && dy.isPresent();
    }

    public int anchorX() {
        return dx.orElse(0);
    }

    public int anchorY() {
        return dy.orElse(0);
    }
}
