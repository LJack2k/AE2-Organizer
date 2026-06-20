package nl.ljack2k.ae2organizer.filter;

import appeng.api.stacks.AEKey;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.Locale;
import java.util.function.Predicate;

/** Matches keys whose mod id (namespace) equals the configured value. */
public record ModCondition(String modId) implements Condition {

    public static final MapCodec<ModCondition> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            com.mojang.serialization.Codec.STRING.fieldOf("modId").forGetter(ModCondition::modId)
    ).apply(i, ModCondition::new));

    @Override
    public ConditionType type() {
        return ConditionType.MOD;
    }

    @Override
    public Predicate<AEKey> toPredicate() {
        String id = modId.trim().toLowerCase(Locale.ROOT);
        if (id.isEmpty()) {
            return key -> false;
        }
        return key -> id.equals(key.getModId());
    }
}
