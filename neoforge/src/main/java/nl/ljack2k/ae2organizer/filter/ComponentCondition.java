package nl.ljack2k.ae2organizer.filter;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;

import java.util.function.Predicate;

/**
 * Matches item stacks by per-stack NBT. 1.20.1 predates data components, so this
 * is the NBT analogue of the newer lines' component checks — {@code custom_data_key}
 * looks for a top-level NBT key rather than a key inside {@code minecraft:custom_data}.
 * <p>
 * {@link ComponentMatch#HAS_COMPONENT_TYPE} has no meaning without the component
 * registry and never matches here; it is kept only so a config or clipboard export
 * from a 1.21+ line still parses (see {@link ComponentMatch#supported()}).
 * <p>
 * {@link #arg()} is unused for presence checks and carries the NBT key for the
 * argument-taking match. Empty stacks (non-item resources) never match.
 */
public record ComponentCondition(ComponentMatch match, String arg, boolean negate) implements Condition {

    public static final MapCodec<ComponentCondition> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            ComponentMatch.CODEC.fieldOf("match").forGetter(ComponentCondition::match),
            Codec.STRING.optionalFieldOf("arg", "").forGetter(ComponentCondition::arg),
            Codec.BOOL.optionalFieldOf("negate", false).forGetter(ComponentCondition::negate)
    ).apply(i, ComponentCondition::new));

    @Override
    public ConditionType type() {
        return ConditionType.COMPONENT;
    }

    @Override
    public Predicate<ItemStack> toPredicate() {
        return switch (match) {
            case ENCHANTED -> stack -> !stack.isEmpty() && isEnchanted(stack);
            case HAS_CUSTOM_NAME -> stack -> !stack.isEmpty() && stack.hasCustomHoverName();
            case DAMAGED -> stack -> !stack.isEmpty() && stack.isDamaged();
            case HAS_CUSTOM_DATA_KEY -> {
                String dataKey = arg.trim();
                yield dataKey.isEmpty()
                        ? stack -> false
                        : stack -> !stack.isEmpty() && hasNbtKey(stack, dataKey);
            }
            // No data-component registry on 1.20.1 — parses, never matches.
            case HAS_COMPONENT_TYPE -> stack -> false;
        };
    }

    /** Active enchantments (tools/armour) or stored ones (enchanted books). */
    private static boolean isEnchanted(ItemStack stack) {
        if (stack.isEnchanted()) {
            return true;
        }
        CompoundTag tag = stack.getTag();
        return tag != null && !tag.getList("StoredEnchantments", Tag.TAG_COMPOUND).isEmpty();
    }

    /** True if the stack's NBT carries a top-level key. */
    private static boolean hasNbtKey(ItemStack stack, String dataKey) {
        CompoundTag tag = stack.getTag();
        return tag != null && tag.contains(dataKey);
    }
}
