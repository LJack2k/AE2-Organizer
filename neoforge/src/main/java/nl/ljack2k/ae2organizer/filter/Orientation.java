package nl.ljack2k.ae2organizer.filter;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;

/** How a {@link FilterWindow} lays out its tabs: a vertical list or a horizontal row. */
public enum Orientation implements StringRepresentable {
    VERTICAL("vertical"),
    HORIZONTAL("horizontal");

    public static final Codec<Orientation> CODEC = StringRepresentable.fromEnum(Orientation::values);

    private final String serializedName;

    Orientation(String serializedName) {
        this.serializedName = serializedName;
    }

    @Override
    public String getSerializedName() {
        return serializedName;
    }
}
