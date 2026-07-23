package nl.ljack2k.ae2organizer.filter;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.function.Predicate;

/**
 * Matches item stacks carrying the given item tag, e.g. {@code c:ingots}.
 * <p>
 * Note (1.21 / NeoForge): common tags use the {@code c:} namespace
 * (e.g. {@code c:ingots}), not the old {@code forge:} namespace.
 */
public record TagCondition(ResourceLocation tagId, boolean negate) implements Condition {

    public static final MapCodec<TagCondition> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            ResourceLocation.CODEC.fieldOf("tag").forGetter(TagCondition::tagId),
            com.mojang.serialization.Codec.BOOL.optionalFieldOf("negate", false).forGetter(TagCondition::negate)
    ).apply(i, TagCondition::new));

    @Override
    public ConditionType type() {
        return ConditionType.TAG;
    }

    @Override
    public Predicate<ItemStack> toPredicate() {
        TagKey<Item> tagKey = TagKey.create(Registries.ITEM, tagId);
        return stack -> !stack.isEmpty() && stack.is(tagKey);
    }
}
