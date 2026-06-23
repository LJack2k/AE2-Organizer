package nl.ljack2k.ae2organizer.client;

import nl.ljack2k.ae2organizer.filter.Tab;
import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;

/**
 * Thin bridge between tab-selection code and the optional JEI integration.
 * Holds no JEI imports — the handler is wired by {@link nl.ljack2k.ae2organizer.jei.AE2OrganizerJeiPlugin}
 * when JEI is present, and cleared when it is not.
 */
public final class JeiSync {
    private JeiSync() {}

    @Nullable
    private static Consumer<Tab> handler;

    public static void setHandler(@Nullable Consumer<Tab> h) {
        handler = h;
    }

    public static void apply(@Nullable Tab tab) {
        if (handler != null) handler.accept(tab);
    }
}
