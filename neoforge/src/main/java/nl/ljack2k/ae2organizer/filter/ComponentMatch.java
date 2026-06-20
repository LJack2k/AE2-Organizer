package nl.ljack2k.ae2organizer.filter;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;

/**
 * The per-stack data-component (NBT) checks supported in v1. These are
 * presence-based, not value-based (no "Sharpness &ge; 3"). The two {@code *_KEY}
 * / {@code *_TYPE} variants take an argument (see {@link ComponentCondition}).
 */
public enum ComponentMatch implements StringRepresentable {
    ENCHANTED("enchanted", false),
    HAS_CUSTOM_NAME("named", false),
    DAMAGED("damaged", false),
    HAS_CUSTOM_DATA_KEY("custom_data_key", true),
    HAS_COMPONENT_TYPE("component_type", true);

    public static final Codec<ComponentMatch> CODEC = StringRepresentable.fromEnum(ComponentMatch::values);

    private final String serializedName;
    private final boolean usesArg;

    ComponentMatch(String serializedName, boolean usesArg) {
        this.serializedName = serializedName;
        this.usesArg = usesArg;
    }

    /** Whether this match reads {@link ComponentCondition#arg()} (a data key or component-type id). */
    public boolean usesArg() {
        return usesArg;
    }

    @Override
    public String getSerializedName() {
        return serializedName;
    }
}
