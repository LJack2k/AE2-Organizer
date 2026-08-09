package nl.ljack2k.ae2organizer.filter;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.item.ItemStack;

import java.util.Locale;
import java.util.function.Predicate;

/** Matches item stacks whose display name contains the given text (case-insensitive). */
public record TextCondition(String text, boolean negate) implements Condition {

    public static final MapCodec<TextCondition> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.STRING.fieldOf("text").forGetter(TextCondition::text),
            Codec.BOOL.optionalFieldOf("negate", false).forGetter(TextCondition::negate)
    ).apply(i, TextCondition::new));

    @Override
    public ConditionType type() {
        return ConditionType.TEXT;
    }

    @Override
    public Predicate<ItemStack> toPredicate() {
        String needle = text.trim().toLowerCase(Locale.ROOT);
        return stack -> {
            if (stack.isEmpty()) {
                return false;
            }
            if (needle.isEmpty()) {
                return true;
            }
            return stack.getHoverName().getString().toLowerCase(Locale.ROOT).contains(needle);
        };
    }
}
