package nl.ljack2k.ae2organizer.filter;

import com.mojang.serialization.Codec;
import net.minecraft.world.item.ItemStack;

import java.util.function.Predicate;

/**
 * One filter rule within a {@link Tab}. Implementations are immutable records,
 * each tied to a {@link ConditionType}. Dispatched (de)serialization keys on
 * the {@code "type"} field, inlining each implementation's own fields, e.g.
 * {@code {"type":"mod","modId":"create"}}.
 * <p>
 * {@link #toPredicate()} is called once when a tab becomes active; it should
 * precompute anything expensive (resolved tags, lowercased text, registry
 * lookups) and return a cheap per-stack test, since the predicate runs over
 * every entry in the grid view on each refresh.
 * <p>
 * Predicates operate on an {@link ItemStack}. Non-item grid resources (fluids,
 * chemicals) are passed as {@link ItemStack#EMPTY}; this port filters items
 * only, so a positive condition never matches an empty stack (mirroring AE2's
 * {@code instanceof AEItemKey} guard). The abstraction is deliberately kept at
 * {@code ItemStack} so fluid/chemical conditions can be added later without
 * touching the model shape.
 */
public interface Condition {

    // DFU 6.0.8 (MC 1.20.1) dispatch expects the per-type function to return a
    // Codec (1.21+ DFU takes a MapCodec); ConditionType holds MapCodecs, so adapt.
    Codec<Condition> CODEC = ConditionType.CODEC.dispatch("type", Condition::type, t -> t.codec().codec());

    ConditionType type();

    /**
     * When {@code true}, this condition is an <em>exclusion</em>: a stack
     * matching {@link #toPredicate()} is hidden. Exclusions are always
     * AND-combined regardless of the tab's {@link MatchMode}, so a tab reads as
     * {@code (positives combined by mode) AND (none of the exclusions)}.
     */
    boolean negate();

    /**
     * The raw (non-negated) test against a stored stack. Must tolerate
     * {@link ItemStack#EMPTY} (a non-item resource). {@link Tab} applies
     * {@link #negate()} itself.
     */
    Predicate<ItemStack> toPredicate();
}
