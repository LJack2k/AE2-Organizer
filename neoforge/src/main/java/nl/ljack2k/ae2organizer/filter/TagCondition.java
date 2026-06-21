package nl.ljack2k.ae2organizer.filter;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;

import java.util.function.Predicate;

/**
 * Matches item keys carrying the given item tag, e.g. {@code c:ingots}.
 * <p>
 * Note (1.21 / NeoForge): common tags use the {@code c:} namespace
 * (e.g. {@code c:ingots}), not the old {@code forge:} namespace.
 */
public record TagCondition(Identifier tagId) implements Condition {

    public static final MapCodec<TagCondition> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Identifier.CODEC.fieldOf("tag").forGetter(TagCondition::tagId)
    ).apply(i, TagCondition::new));

    @Override
    public ConditionType type() {
        return ConditionType.TAG;
    }

    @Override
    public Predicate<AEKey> toPredicate() {
        TagKey<Item> tagKey = TagKey.create(Registries.ITEM, tagId);
        return key -> key instanceof AEItemKey && key.isTagged(tagKey);
    }
}
