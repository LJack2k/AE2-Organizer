package nl.ljack2k.ae2organizer.filter;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
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
 * Matches item keys by a per-stack data component. {@link #arg()} is unused for
 * presence checks and carries the data key / component-type id for the
 * argument-taking matches. Always guards on {@link AEItemKey} so fluid/other
 * keys never match.
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
            case ENCHANTED -> key -> key instanceof AEItemKey ik && isEnchanted(ik.getReadOnlyStack());
            case HAS_CUSTOM_NAME -> key -> key instanceof AEItemKey ik
                    && ik.getReadOnlyStack().has(DataComponents.CUSTOM_NAME);
            case DAMAGED -> key -> key instanceof AEItemKey ik && ik.isDamaged();
            case HAS_CUSTOM_DATA_KEY -> {
                String dataKey = arg.trim();
                yield dataKey.isEmpty()
                        ? key -> false
                        : key -> key instanceof AEItemKey ik && hasCustomDataKey(ik.getReadOnlyStack(), dataKey);
            }
            case HAS_COMPONENT_TYPE -> {
                ResourceLocation rl = ResourceLocation.tryParse(arg.trim());
                DataComponentType<?> componentType = rl == null ? null : BuiltInRegistries.DATA_COMPONENT_TYPE.get(rl);
                yield componentType == null
                        ? key -> false
                        : key -> key instanceof AEItemKey ik && ik.getReadOnlyStack().has(componentType);
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
