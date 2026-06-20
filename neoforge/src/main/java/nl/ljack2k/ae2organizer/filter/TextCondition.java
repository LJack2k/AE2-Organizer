package nl.ljack2k.ae2organizer.filter;

import appeng.api.stacks.AEKey;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.Locale;
import java.util.function.Predicate;

/** Matches keys whose display name contains the given text (case-insensitive). */
public record TextCondition(String text) implements Condition {

    public static final MapCodec<TextCondition> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.STRING.fieldOf("text").forGetter(TextCondition::text)
    ).apply(i, TextCondition::new));

    @Override
    public ConditionType type() {
        return ConditionType.TEXT;
    }

    @Override
    public Predicate<AEKey> toPredicate() {
        String needle = text.trim().toLowerCase(Locale.ROOT);
        if (needle.isEmpty()) {
            return key -> true;
        }
        return key -> key.getDisplayName().getString().toLowerCase(Locale.ROOT).contains(needle);
    }
}
