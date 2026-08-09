package nl.ljack2k.ae2organizer.filter;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * Per-client global behaviour settings, persisted alongside the windows and tabs.
 * Presentation settings (label mode, size) live per-window on {@link FilterWindow}.
 *
 * @param resetFilterOnOpen      clear the active tab when opening a grid.
 * @param clearSearchOnTabSelect clear RS's search bar whenever a tab is selected.
 * @param syncViewerOnTabSelect  sync the ingredient-viewer (JEI/REI) search bar whenever a tab is selected.
 */
public record Settings(boolean resetFilterOnOpen, boolean clearSearchOnTabSelect, boolean syncViewerOnTabSelect) {

    public static final Settings DEFAULT = new Settings(false, false, false);

    public static final Codec<Settings> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.BOOL.optionalFieldOf("resetFilterOnOpen", false).forGetter(Settings::resetFilterOnOpen),
            Codec.BOOL.optionalFieldOf("clearSearchOnTabSelect", false).forGetter(Settings::clearSearchOnTabSelect),
            Codec.BOOL.optionalFieldOf("syncViewerOnTabSelect", false).forGetter(Settings::syncViewerOnTabSelect)
    ).apply(i, Settings::new));
}
