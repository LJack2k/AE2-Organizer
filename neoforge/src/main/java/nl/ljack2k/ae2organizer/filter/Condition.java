package nl.ljack2k.ae2organizer.filter;

import appeng.api.stacks.AEKey;
import com.mojang.serialization.Codec;

import java.util.function.Predicate;

/**
 * One filter rule within a {@link Tab}. Implementations are immutable records,
 * each tied to a {@link ConditionType}. Dispatched (de)serialization keys on
 * the {@code "type"} field, inlining each implementation's own fields, e.g.
 * {@code {"type":"mod","modId":"create"}}.
 * <p>
 * {@link #toPredicate()} is called once when a tab becomes active; it should
 * precompute anything expensive (resolved tags, lowercased text, registry
 * lookups) and return a cheap per-key test, since the predicate runs over every
 * entry in the network on each terminal view refresh.
 */
public interface Condition {

    // DFU 6.0.8 (MC 1.20.1) dispatch expects the per-type function to return a
    // Codec (1.21.1's DFU takes a MapCodec); ConditionType holds MapCodecs, so
    // adapt with .codec(). The only divergence here from the 1.21.1 source.
    Codec<Condition> CODEC = ConditionType.CODEC.dispatch("type", Condition::type, t -> t.codec().codec());

    ConditionType type();

    /** A test against a stored key. Must tolerate non-item keys (fluids, etc.). */
    Predicate<AEKey> toPredicate();
}
