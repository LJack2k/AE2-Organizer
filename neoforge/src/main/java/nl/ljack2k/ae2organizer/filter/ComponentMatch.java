package nl.ljack2k.ae2organizer.filter;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;

/**
 * The per-stack NBT checks supported on the 1.20.1 line. These are presence-based,
 * not value-based (no "Sharpness &ge; 3"). Only {@code HAS_CUSTOM_DATA_KEY} takes an
 * argument — a top-level NBT key (see {@link ComponentCondition}).
 */
public enum ComponentMatch implements StringRepresentable {
    ENCHANTED("enchanted", false),
    HAS_CUSTOM_NAME("named", false),
    DAMAGED("damaged", false),
    HAS_CUSTOM_DATA_KEY("custom_data_key", true);

    public static final Codec<ComponentMatch> CODEC = StringRepresentable.fromEnum(ComponentMatch::values);

    private final String serializedName;
    private final boolean usesArg;

    ComponentMatch(String serializedName, boolean usesArg) {
        this.serializedName = serializedName;
        this.usesArg = usesArg;
    }

    /** Whether this match reads {@link ComponentCondition#arg()} (a top-level NBT key). */
    public boolean usesArg() {
        return usesArg;
    }

    @Override
    public String getSerializedName() {
        return serializedName;
    }
}
