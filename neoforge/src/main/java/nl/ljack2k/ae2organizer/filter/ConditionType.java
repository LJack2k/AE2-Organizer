package nl.ljack2k.ae2organizer.filter;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import net.minecraft.util.StringRepresentable;

/**
 * The kinds of filter condition. Drives both Codec dispatch (each type knows
 * its own {@link MapCodec}) and the editor's type cycle button.
 */
public enum ConditionType implements StringRepresentable {
    MOD("mod", ModCondition.CODEC),
    TAG("tag", TagCondition.CODEC),
    TEXT("text", TextCondition.CODEC),
    COMPONENT("component", ComponentCondition.CODEC);

    public static final Codec<ConditionType> CODEC = StringRepresentable.fromEnum(ConditionType::values);

    private final String serializedName;
    private final MapCodec<? extends Condition> codec;

    ConditionType(String serializedName, MapCodec<? extends Condition> codec) {
        this.serializedName = serializedName;
        this.codec = codec;
    }

    public MapCodec<? extends Condition> codec() {
        return codec;
    }

    @Override
    public String getSerializedName() {
        return serializedName;
    }
}
