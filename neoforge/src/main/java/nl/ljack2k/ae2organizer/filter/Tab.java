package nl.ljack2k.ae2organizer.filter;

import appeng.api.stacks.AEKey;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;

import java.util.List;
import java.util.function.Predicate;

/**
 * A user-defined filter tab: a name, an icon item, a {@link MatchMode}, and a
 * list of {@link Condition}s. {@code id} is a stable internal key (used for
 * active-tab tracking and as a fallback display value).
 */
public record Tab(String id, String name, Identifier icon, MatchMode mode, List<Condition> conditions) {

    public static final Identifier DEFAULT_ICON = Identifier.withDefaultNamespace("chest");

    public static final Codec<Tab> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.fieldOf("id").forGetter(Tab::id),
            Codec.STRING.fieldOf("name").forGetter(Tab::name),
            Identifier.CODEC.optionalFieldOf("icon", DEFAULT_ICON).forGetter(Tab::icon),
            MatchMode.CODEC.optionalFieldOf("mode", MatchMode.ANY).forGetter(Tab::mode),
            Condition.CODEC.listOf().optionalFieldOf("conditions", List.of()).forGetter(Tab::conditions)
    ).apply(i, Tab::new));

    /**
     * Builds the combined predicate for this tab. An empty condition list
     * matches everything.
     * <p>
     * Positive conditions are combined with AND ({@link MatchMode#ALL}) or OR
     * ({@link MatchMode#ANY}). Negated conditions (see {@link Condition#negate()})
     * are exclusions: they are always AND-combined and applied on top, so the tab
     * reads as {@code (positives combined by mode) AND (none of the exclusions)}.
     * A tab with only exclusions shows everything except those.
     */
    public Predicate<AEKey> toPredicate() {
        Predicate<AEKey> positives = null;
        Predicate<AEKey> exclusions = null;
        for (Condition condition : conditions) {
            Predicate<AEKey> part = condition.toPredicate();
            if (condition.negate()) {
                Predicate<AEKey> without = part.negate();
                exclusions = (exclusions == null) ? without : exclusions.and(without);
            } else {
                positives = (positives == null) ? part
                        : (mode == MatchMode.ALL) ? positives.and(part) : positives.or(part);
            }
        }
        if (positives == null && exclusions == null) {
            return key -> true;
        }
        if (positives == null) {
            return exclusions;
        }
        if (exclusions == null) {
            return positives;
        }
        return positives.and(exclusions);
    }
}
