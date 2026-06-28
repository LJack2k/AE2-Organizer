package nl.ljack2k.ae2organizer.filter;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;

import java.util.function.Predicate;

/**
 * Matches item keys by per-stack NBT. (1.20.1 has no data components, so this is
 * NBT-based — unlike the 1.21.1/26.1 lines, which use the data-component API.)
 * {@link #arg()} is unused for presence checks and carries the NBT key for the
 * argument-taking match. Always guards on {@link AEItemKey} so fluid/other keys
 * never match.
 */
public record ComponentCondition(ComponentMatch match, String arg) implements Condition {

    public static final MapCodec<ComponentCondition> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            ComponentMatch.CODEC.fieldOf("match").forGetter(ComponentCondition::match),
            Codec.STRING.optionalFieldOf("arg", "").forGetter(ComponentCondition::arg)
    ).apply(i, ComponentCondition::new));

    @Override
    public ConditionType type() {
        return ConditionType.COMPONENT;
    }

    @Override
    public Predicate<AEKey> toPredicate() {
        return switch (match) {
            case ENCHANTED -> key -> key instanceof AEItemKey ik && isEnchanted(ik);
            case HAS_CUSTOM_NAME -> key -> key instanceof AEItemKey ik && ik.getReadOnlyStack().hasCustomHoverName();
            case DAMAGED -> key -> key instanceof AEItemKey ik && ik.isDamaged();
            case HAS_CUSTOM_DATA_KEY -> {
                String dataKey = arg.trim();
                yield dataKey.isEmpty()
                        ? key -> false
                        : key -> key instanceof AEItemKey ik && hasNbtKey(ik, dataKey);
            }
        };
    }

    /** Active enchantments (tools/armour) or stored ones (enchanted books). */
    private static boolean isEnchanted(AEItemKey ik) {
        ItemStack stack = ik.getReadOnlyStack();
        if (stack.isEnchanted()) {
            return true;
        }
        CompoundTag tag = ik.getTag();
        return tag != null && !tag.getList("StoredEnchantments", Tag.TAG_COMPOUND).isEmpty();
    }

    /** True if the stack's NBT carries a top-level key (the 1.20.1 analogue of a custom-data key). */
    private static boolean hasNbtKey(AEItemKey ik, String dataKey) {
        CompoundTag tag = ik.getTag();
        return tag != null && tag.contains(dataKey);
    }
}
