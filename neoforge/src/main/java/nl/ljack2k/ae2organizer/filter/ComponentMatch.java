package nl.ljack2k.ae2organizer.filter;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;

/**
 * The per-stack data-component (NBT) checks supported in v1. These are
 * presence-based, not value-based (no "Sharpness &ge; 3"). The two {@code *_KEY}
 * / {@code *_TYPE} variants take an argument (see {@link ComponentCondition}).
 */
public enum ComponentMatch implements StringRepresentable {
    ENCHANTED("enchanted", false, true),
    HAS_CUSTOM_NAME("named", false, true),
    DAMAGED("damaged", false, true),
    HAS_CUSTOM_DATA_KEY("custom_data_key", true, true),
    /**
     * Needs the data-component registry, which 1.20.1 doesn't have. Kept so a
     * config or clipboard export from a 1.21+ line still parses, but it is
     * unselectable in the editor and never matches on this line.
     */
    HAS_COMPONENT_TYPE("component_type", true, false);

    public static final Codec<ComponentMatch> CODEC = StringRepresentable.fromEnum(ComponentMatch::values);

    private final String serializedName;
    private final boolean usesArg;
    private final boolean supported;

    ComponentMatch(String serializedName, boolean usesArg, boolean supported) {
        this.serializedName = serializedName;
        this.usesArg = usesArg;
        this.supported = supported;
    }

    /** Whether this match reads {@link ComponentCondition#arg()} (a data key or component-type id). */
    public boolean usesArg() {
        return usesArg;
    }

    /** Whether this match does anything on this Minecraft line (see the enum constants). */
    public boolean supported() {
        return supported;
    }

    @Override
    public String getSerializedName() {
        return serializedName;
    }
}
