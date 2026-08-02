package nl.ljack2k.ae2organizer.backend.rslegacy;

import com.refinedmods.refinedstorage.screen.grid.GridScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.Slot;
import nl.ljack2k.ae2organizer.TerminalOrganizer;
import nl.ljack2k.ae2organizer.backend.ScreenAdapter;
import nl.ljack2k.ae2organizer.backend.SearchClearable;

import java.util.List;

/**
 * {@link ScreenAdapter} over Refined Storage 1.12's {@code GridScreen}. All geometry
 * comes from the vanilla container-screen getters RS inherits; the tab filter is
 * re-applied by forcing the grid view to rebuild its filtered list.
 */
public final class RsLegacyScreenAdapter implements ScreenAdapter, SearchClearable {

    private final GridScreen screen;

    RsLegacyScreenAdapter(GridScreen screen) {
        this.screen = screen;
    }

    @Override
    public int guiLeft() {
        return screen.getGuiLeft();
    }

    @Override
    public int guiTop() {
        return screen.getGuiTop();
    }

    @Override
    public int xSize() {
        return screen.getXSize();
    }

    @Override
    public int ySize() {
        return screen.getYSize();
    }

    @Override
    public List<Slot> slots() {
        return screen.getMenu().slots;
    }

    @Override
    public String terminalKey() {
        try {
            ResourceLocation id = BuiltInRegistries.MENU.getKey(screen.getMenu().getType());
            if (id != null) {
                return id.toString();
            }
        } catch (Throwable ignored) {
            // some menus may not expose a type; fall through
        }
        return screen.getMenu().getClass().getSimpleName();
    }

    @Override
    public Component title() {
        return screen.getTitle();
    }

    /**
     * {@code forceSort()} rather than {@code sort()}: RS's {@code sort()} defers to
     * {@code GridScreen#canSort()} and does nothing while the player is, say, holding
     * a stack — but a tab change must always take effect.
     */
    @Override
    public void refilter() {
        try {
            screen.getView().forceSort();
        } catch (Throwable t) {
            TerminalOrganizer.LOGGER.debug("[TerminalOrganizer] grid re-filter skipped", t);
        }
    }

    /**
     * Clears RS's grid search box. {@code GridScreen#searchField} is private and its
     * type ({@code SearchWidget}) subclasses vanilla {@link net.minecraft.client.gui.components.EditBox},
     * so it is read reflectively and emptied via {@code setValue("")}, which fires RS's
     * own change listener. Best-effort: any failure is swallowed.
     */
    @Override
    public void clearSearch() {
        try {
            java.lang.reflect.Field field = GridScreen.class.getDeclaredField("searchField");
            field.setAccessible(true);
            Object value = field.get(screen);
            if (value instanceof net.minecraft.client.gui.components.EditBox box) {
                box.setValue("");
            }
        } catch (Throwable t) {
            TerminalOrganizer.LOGGER.debug("[TerminalOrganizer] could not clear grid search box", t);
        }
    }
}
