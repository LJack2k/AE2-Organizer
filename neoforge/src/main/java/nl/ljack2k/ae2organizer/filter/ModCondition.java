package nl.ljack2k.ae2organizer.filter;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

import java.util.Locale;
import java.util.function.Predicate;

/** Matches item stacks whose mod id (item namespace) equals the configured value. */
public record ModCondition(String modId, boolean negate) implements Condition {

    public static final MapCodec<ModCondition> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            com.mojang.serialization.Codec.STRING.fieldOf("modId").forGetter(ModCondition::modId),
            com.mojang.serialization.Codec.BOOL.optionalFieldOf("negate", false).forGetter(ModCondition::negate)
    ).apply(i, ModCondition::new));

    @Override
    public ConditionType type() {
        return ConditionType.MOD;
    }

    @Override
    public Predicate<ItemStack> toPredicate() {
        String id = modId.trim().toLowerCase(Locale.ROOT);
        if (id.isEmpty()) {
            return stack -> false;
        }
        return stack -> {
            if (stack.isEmpty()) {
                return false;
            }
            Identifier key = BuiltInRegistries.ITEM.getKey(stack.getItem());
            return id.equals(key.getNamespace());
        };
    }
}
