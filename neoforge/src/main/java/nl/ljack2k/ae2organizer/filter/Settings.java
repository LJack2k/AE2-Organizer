package nl.ljack2k.ae2organizer.filter;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * Per-client general settings, persisted alongside the tabs.
 *
 * @param resetFilterOnOpen      clear the active tab when opening a terminal.
 * @param showTabLabels          show tab names as labels (vs icon-only).
 * @param tabScale               size multiplier for the tab-bar rows (icons + text).
 * @param clearSearchOnTabSelect clear AE2's search bar whenever a tab is selected.
 * @param syncJeiOnTabSelect     sync JEI's ingredient search bar whenever a tab is selected.
 */
public record Settings(boolean resetFilterOnOpen, boolean showTabLabels, double tabScale,
                       boolean clearSearchOnTabSelect, boolean syncJeiOnTabSelect) {

    public static final double MIN_SCALE = 0.7;
    public static final double MAX_SCALE = 1.8;

    public static final double DEFAULT_SCALE = 1.15;
    public static final Settings DEFAULT = new Settings(false, false, DEFAULT_SCALE, false, false);

    public static final Codec<Settings> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.BOOL.optionalFieldOf("resetFilterOnOpen", false).forGetter(Settings::resetFilterOnOpen),
            Codec.BOOL.optionalFieldOf("showTabLabels", false).forGetter(Settings::showTabLabels),
            Codec.DOUBLE.optionalFieldOf("tabScale", DEFAULT_SCALE).forGetter(Settings::tabScale),
            Codec.BOOL.optionalFieldOf("clearSearchOnTabSelect", false).forGetter(Settings::clearSearchOnTabSelect),
            Codec.BOOL.optionalFieldOf("syncJeiOnTabSelect", false).forGetter(Settings::syncJeiOnTabSelect)
    ).apply(i, Settings::new));

    /** Clamped scale safe for layout math. */
    public double clampedScale() {
        return Math.max(MIN_SCALE, Math.min(tabScale, MAX_SCALE));
    }
}
