package nl.ljack2k.ae2organizer.filter;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;

/**
 * How a {@link FilterWindow}'s on-screen position is resolved.
 * <ul>
 *   <li>{@link #DOCK} — anchored to the grid's right edge, following the GUI.</li>
 *   <li>{@link #CENTER} — centered on the screen using the window's own size.</li>
 *   <li>{@link #FREE} — absolute screen coordinates ({@code x}/{@code y}), set by
 *       dragging in move-mode.</li>
 * </ul>
 */
public enum PositionMode implements StringRepresentable {
    DOCK("dock"),
    CENTER("center"),
    FREE("free");

    public static final Codec<PositionMode> CODEC = StringRepresentable.fromEnum(PositionMode::values);

    private final String serializedName;

    PositionMode(String serializedName) {
        this.serializedName = serializedName;
    }

    @Override
    public String getSerializedName() {
        return serializedName;
    }
}
