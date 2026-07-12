package nl.ljack2k.ae2organizer.filter;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * One on-screen filter panel. Tabs are assigned to a window by {@link Tab#window()}
 * (matching {@link #id()}); a window only controls presentation — position,
 * orientation, whether it shows labels, and its size multiplier. The active tab is
 * global across all windows (see {@code TabManager}), so windows are purely a way
 * to lay tabs out into separate movable groups.
 *
 * @param id          stable internal key; {@link #MAIN_ID} is the default window.
 * @param name        display name (shown in the editor tree and, with labels, as the title).
 * @param orientation vertical list or horizontal row.
 * @param showLabels  show tab names beside icons (vs icon-only).
 * @param scale       size multiplier for rows/icons/text.
 * @param position    how {@code x}/{@code y} are interpreted.
 * @param x           absolute screen x (only meaningful when {@code position == FREE}).
 * @param y           absolute screen y (only meaningful when {@code position == FREE}).
 */
public record FilterWindow(String id, String name, Orientation orientation, boolean showLabels,
                           double scale, PositionMode position, int x, int y,
                           boolean showGear, boolean showAll, Map<String, Placement> placements,
                           String baseTerminal, List<String> hiddenOn, boolean collapsed) {

    public static final String MAIN_ID = "main";

    public static final double MIN_SCALE = 0.7;
    public static final double MAX_SCALE = 1.8;
    public static final double DEFAULT_SCALE = 1.15;

    public static final Codec<FilterWindow> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.fieldOf("id").forGetter(FilterWindow::id),
            Codec.STRING.optionalFieldOf("name", "Filters").forGetter(FilterWindow::name),
            Orientation.CODEC.optionalFieldOf("orientation", Orientation.VERTICAL).forGetter(FilterWindow::orientation),
            Codec.BOOL.optionalFieldOf("showLabels", false).forGetter(FilterWindow::showLabels),
            Codec.DOUBLE.optionalFieldOf("scale", DEFAULT_SCALE).forGetter(FilterWindow::scale),
            PositionMode.CODEC.optionalFieldOf("position", PositionMode.DOCK).forGetter(FilterWindow::position),
            Codec.INT.optionalFieldOf("x", 0).forGetter(FilterWindow::x),
            Codec.INT.optionalFieldOf("y", 0).forGetter(FilterWindow::y),
            Codec.BOOL.optionalFieldOf("showGear", true).forGetter(FilterWindow::showGear),
            Codec.BOOL.optionalFieldOf("showAll", true).forGetter(FilterWindow::showAll),
            Codec.unboundedMap(Codec.STRING, Placement.CODEC).optionalFieldOf("placements", Map.of())
                    .forGetter(FilterWindow::placements),
            Codec.STRING.optionalFieldOf("baseTerminal", "").forGetter(FilterWindow::baseTerminal),
            Codec.STRING.listOf().optionalFieldOf("hiddenOn", List.of()).forGetter(FilterWindow::hiddenOn),
            Codec.BOOL.optionalFieldOf("collapsed", false).forGetter(FilterWindow::collapsed)
    ).apply(i, FilterWindow::new));

    /** The default window used on first run / migration from single-panel configs. */
    public static FilterWindow createDefault(boolean showLabels, double scale) {
        return new FilterWindow(MAIN_ID, "Filters", Orientation.VERTICAL, showLabels, scale,
                PositionMode.DOCK, 0, 0, true, true, Map.of(), "", List.of(), false);
    }

    /** Whether this window is shown on the given terminal type. */
    public boolean visibleOn(String terminalKey) {
        return !hiddenOn.contains(terminalKey);
    }

    /**
     * The placement to use for a given terminal, in priority order:
     * <ol>
     *   <li>this terminal's own override, if any;</li>
     *   <li>the <b>base</b> terminal's placement — the first terminal the user
     *       positioned — so a freshly-opened terminal inherits a sensible spot
     *       instead of the bare default;</li>
     *   <li>the window's global default position.</li>
     * </ol>
     */
    public Placement resolve(String terminalKey) {
        Placement own = placements.get(terminalKey);
        if (own != null) {
            return own;
        }
        if (!baseTerminal.isEmpty()) {
            Placement base = placements.get(baseTerminal);
            if (base != null) {
                return base;
            }
        }
        if (!placements.isEmpty()) {
            return placements.values().iterator().next();
        }
        return new Placement(position, x, y);
    }

    /**
     * A copy with the given terminal's placement set/overwritten. The first
     * terminal positioned becomes the {@code baseTerminal} that others inherit.
     */
    public FilterWindow withPlacement(String terminalKey, Placement placement) {
        Map<String, Placement> next = new HashMap<>(placements);
        next.put(terminalKey, placement);
        String base = baseTerminal.isEmpty() ? terminalKey : baseTerminal;
        return new FilterWindow(id, name, orientation, showLabels, scale, position, x, y,
                showGear, showAll, next, base, hiddenOn, collapsed);
    }

    public double clampedScale() {
        return Math.max(MIN_SCALE, Math.min(scale, MAX_SCALE));
    }

    /**
     * Whether tab labels are actually drawn. Horizontal windows are always
     * icon-only (labels would make each cell wide enough to push the gear —
     * and the whole panel — off-screen), regardless of the stored flag.
     */
    public boolean effectiveLabels() {
        return showLabels && orientation == Orientation.VERTICAL;
    }

    public FilterWindow withName(String newName) {
        return new FilterWindow(id, newName, orientation, showLabels, scale, position, x, y,
                showGear, showAll, placements, baseTerminal, hiddenOn, collapsed);
    }
}
