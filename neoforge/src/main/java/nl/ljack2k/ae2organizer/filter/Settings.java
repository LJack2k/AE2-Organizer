package nl.ljack2k.ae2organizer.filter;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * Per-client general settings, persisted alongside the tabs.
 *
 * @param resetFilterOnOpen if true, opening a terminal clears the active tab
 *                          (always starts on "All"); if false, the last active
 *                          tab is remembered across opens.
 * @param showTabLabels     if true, the tab bar shows each tab's name as a wide
 *                          label button; if false, icon-only cells (name on hover).
 */
public record Settings(boolean resetFilterOnOpen, boolean showTabLabels) {

    public static final Settings DEFAULT = new Settings(false, false);

    public static final Codec<Settings> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.BOOL.optionalFieldOf("resetFilterOnOpen", false).forGetter(Settings::resetFilterOnOpen),
            Codec.BOOL.optionalFieldOf("showTabLabels", false).forGetter(Settings::showTabLabels)
    ).apply(i, Settings::new));
}
