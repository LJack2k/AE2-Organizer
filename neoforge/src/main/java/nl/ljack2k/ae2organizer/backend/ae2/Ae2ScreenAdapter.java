package nl.ljack2k.ae2organizer.backend.ae2;

import appeng.client.gui.me.common.MEStorageScreen;
import appeng.client.gui.me.common.Repo;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.Slot;
import nl.ljack2k.ae2organizer.StorageOrganizer;
import nl.ljack2k.ae2organizer.backend.ScreenAdapter;
import nl.ljack2k.ae2organizer.backend.SearchClearable;
import nl.ljack2k.ae2organizer.backend.ae2.mixin.AbstractContainerScreenAccessor;
import nl.ljack2k.ae2organizer.backend.ae2.mixin.MEStorageScreenAccessor;
import net.minecraft.resources.Identifier;

import java.util.List;

/**
 * {@link ScreenAdapter} over AE2's {@code MEStorageScreen}. Panel dimensions
 * come from the vanilla {@code imageWidth}/{@code imageHeight} via
 * {@link AbstractContainerScreenAccessor}; the tab filter is re-applied by
 * re-running the terminal {@code Repo}'s view.
 */
public final class Ae2ScreenAdapter implements ScreenAdapter, SearchClearable {

    private final MEStorageScreen<?> screen;

    Ae2ScreenAdapter(MEStorageScreen<?> screen) {
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
        return ((AbstractContainerScreenAccessor) screen).ae2organizer$getImageWidth();
    }

    @Override
    public int ySize() {
        return ((AbstractContainerScreenAccessor) screen).ae2organizer$getImageHeight();
    }

    @Override
    public List<Slot> slots() {
        return screen.getMenu().slots;
    }

    @Override
    public String terminalKey() {
        try {
            Identifier id = BuiltInRegistries.MENU.getKey(screen.getMenu().getType());
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
    public void refilter() {
        try {
            Repo repo = ((MEStorageScreenAccessor) screen).ae2organizer$getRepo();
            repo.updateView();
        } catch (Throwable t) {
            StorageOrganizer.LOGGER.debug("[StorageOrganizer] terminal re-filter skipped", t);
        }
    }

    @Override
    public void clearSearch() {
        try {
            ((MEStorageScreenAccessor) screen).ae2organizer$getSearchField().setValue("");
        } catch (Throwable t) {
            StorageOrganizer.LOGGER.debug("[StorageOrganizer] could not clear terminal search box", t);
        }
    }
}
