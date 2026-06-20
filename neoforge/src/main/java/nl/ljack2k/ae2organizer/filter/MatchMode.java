package nl.ljack2k.ae2organizer.filter;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;

/** How a tab combines its conditions: match ANY (OR) or ALL (AND) of them. */
public enum MatchMode implements StringRepresentable {
    ANY("any"),
    ALL("all");

    public static final Codec<MatchMode> CODEC = StringRepresentable.fromEnum(MatchMode::values);

    private final String serializedName;

    MatchMode(String serializedName) {
        this.serializedName = serializedName;
    }

    @Override
    public String getSerializedName() {
        return serializedName;
    }
}
