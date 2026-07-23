package nl.ljack2k.ae2organizer.backend.rs;

import com.refinedmods.refinedstorage.common.grid.screen.AbstractGridScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.world.item.ItemStack;
import nl.ljack2k.ae2organizer.backend.ScreenAdapter;
import nl.ljack2k.ae2organizer.backend.StorageBackend;
import nl.ljack2k.ae2organizer.backend.Theme;
import org.jetbrains.annotations.Nullable;

import java.util.function.Predicate;

/**
 * Refined Storage 2 backend: adds filter tabs to RS grid screens. Only
 * instantiated when {@code refinedstorage} is present (see
 * {@code BackendRegistry#init()}), so referencing RS classes here is safe.
 */
public final class RsBackend implements StorageBackend {

    @Override
    public String id() {
        return "rs";
    }

    @Override
    public boolean handles(Screen screen) {
        return screen instanceof AbstractGridScreen<?>;
    }

    @Override
    public ScreenAdapter adapt(Screen screen) {
        return new RsScreenAdapter((AbstractGridScreen<?>) screen);
    }

    @Override
    public Theme theme() {
        return RsTheme.INSTANCE;
    }

    @Override
    public void setActiveFilter(@Nullable Predicate<ItemStack> predicate) {
        GridFilterBridge.setActiveFilter(predicate);
    }
}
