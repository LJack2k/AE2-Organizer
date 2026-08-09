package nl.ljack2k.ae2organizer.backend;

import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.Slot;

import java.util.List;

/**
 * Per-open view of a terminal/grid screen, abstracting the few things the shared
 * tab UI needs from AE2's {@code MEStorageScreen} or RS's {@code AbstractGridScreen}.
 */
public interface ScreenAdapter {

    int guiLeft();

    int guiTop();

    /** GUI image width (AE2 {@code imageWidth} / vanilla {@code getXSize}). */
    int xSize();

    /** GUI image height (AE2 {@code imageHeight} / vanilla {@code getYSize}). */
    int ySize();

    /** The menu's slots — used to dock the tab bar past protruding card slots. */
    List<Slot> slots();

    /** Stable per-terminal key (menu-type id), used for per-terminal placement. */
    String terminalKey();

    /** The screen title (for the visibility list / friendly names). */
    Component title();

    /**
     * Re-run the storage view's filter+sort so a tab change is reflected now.
     * AE2: {@code repo.updateView()}; RS: {@code menu.getRepository().sort()}.
     */
    void refilter();
}
