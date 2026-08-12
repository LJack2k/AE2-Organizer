package nl.ljack2k.ae2organizer.backend.rs;

import com.refinedmods.refinedstorage.common.grid.screen.AbstractGridScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.Slot;
import nl.ljack2k.ae2organizer.StorageOrganizer;
import nl.ljack2k.ae2organizer.backend.ScreenAdapter;
import nl.ljack2k.ae2organizer.backend.SearchClearable;

import java.util.List;

/**
 * {@link ScreenAdapter} over Refined Storage's {@code AbstractGridScreen}. All
 * geometry comes from the vanilla container-screen getters RS inherits; the tab
 * filter is re-applied by re-running the grid repository's filter+sort.
 */
public final class RsScreenAdapter implements ScreenAdapter, SearchClearable {

    private final AbstractGridScreen<?> screen;

    RsScreenAdapter(AbstractGridScreen<?> screen) {
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

    @Override
    public net.minecraft.world.item.ItemStack carried() {
        return screen.getMenu().getCarried();
    }

    @Override
    public void refilter() {
        try {
            screen.getMenu().getRepository().sort();
        } catch (Throwable t) {
            StorageOrganizer.LOGGER.debug("[StorageOrganizer] grid re-filter skipped", t);
        }
    }

    /**
     * Clears RS's grid search box. RS's {@code searchField} is package-private and
     * its concrete type isn't public, so we read it reflectively; it subclasses
     * vanilla {@link net.minecraft.client.gui.components.EditBox}, and
     * {@code setValue("")} empties the box and fires RS's re-filter listener.
     * Best-effort: any failure is swallowed.
     */
    @Override
    public void clearSearch() {
        try {
            java.lang.reflect.Field field = AbstractGridScreen.class.getDeclaredField("searchField");
            field.setAccessible(true);
            Object value = field.get(screen);
            if (value instanceof net.minecraft.client.gui.components.EditBox box) {
                box.setValue("");
            }
        } catch (Throwable t) {
            StorageOrganizer.LOGGER.debug("[StorageOrganizer] could not clear grid search box", t);
        }
    }
}
