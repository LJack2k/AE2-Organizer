package nl.ljack2k.ae2organizer.filter;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.enchantment.ItemEnchantments;

import java.util.function.Predicate;

/**
 * Matches item stacks by a per-stack data component. {@link #arg()} is unused
 * for presence checks and carries the data key / component-type id for the
 * argument-taking matches. Empty stacks (non-item resources) never match.
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
            case HAS_CUSTOM_NAME -> stack -> !stack.isEmpty() && stack.has(DataComponents.CUSTOM_NAME);
            case DAMAGED -> stack -> !stack.isEmpty() && stack.isDamaged();
            case HAS_CUSTOM_DATA_KEY -> {
                String dataKey = arg.trim();
                yield dataKey.isEmpty()
                        ? stack -> false
                        : stack -> !stack.isEmpty() && hasCustomDataKey(stack, dataKey);
            }
            case HAS_COMPONENT_TYPE -> {
                ResourceLocation rl = ResourceLocation.tryParse(arg.trim());
                DataComponentType<?> componentType = rl == null ? null : BuiltInRegistries.DATA_COMPONENT_TYPE.get(rl);
                yield componentType == null
                        ? stack -> false
                        : stack -> !stack.isEmpty() && stack.has(componentType);
            }
        };
    }

    private static boolean isEnchanted(ItemStack stack) {
        ItemEnchantments enchantments = stack.get(DataComponents.ENCHANTMENTS);
        if (enchantments != null && !enchantments.isEmpty()) {
            return true;
        }
        ItemEnchantments stored = stack.get(DataComponents.STORED_ENCHANTMENTS);
        return stored != null && !stored.isEmpty();
    }

    private static boolean hasCustomDataKey(ItemStack stack, String dataKey) {
        CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
        return customData != null && customData.copyTag().contains(dataKey);
    }
}
