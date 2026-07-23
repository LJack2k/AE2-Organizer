package nl.ljack2k.ae2organizer.backend;

/**
 * Optional capability a {@link ScreenAdapter} may also implement: clearing the
 * terminal/grid's own search box (the "clear search bar when selecting a tab"
 * setting). Kept separate from the fixed {@link ScreenAdapter} SPI so the seam
 * stays minimal; the tab UI probes for it with an {@code instanceof} check.
 */
public interface SearchClearable {

    /** Empty the storage screen's search field (best-effort; never throws). */
    void clearSearch();
}
