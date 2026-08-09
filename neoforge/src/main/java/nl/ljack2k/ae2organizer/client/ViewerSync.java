package nl.ljack2k.ae2organizer.client;

import nl.ljack2k.ae2organizer.filter.Tab;
import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;

/**
 * Thin bridge between tab-selection code and the optional ingredient-viewer
 * integration (JEI today; REI could register here too). Holds no viewer imports —
 * the handler is wired by the optional JEI plugin when JEI is present, and cleared
 * when it is not, so the core stays viewer-agnostic.
 */
public final class ViewerSync {
    private ViewerSync() {}

    @Nullable
    private static Consumer<Tab> handler;

    public static void setHandler(@Nullable Consumer<Tab> h) {
        handler = h;
    }

    public static void apply(@Nullable Tab tab) {
        if (handler != null) {
            handler.accept(tab);
        }
    }
}
