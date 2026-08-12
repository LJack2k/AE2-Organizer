package nl.ljack2k.ae2organizer.backend.rs;

import com.refinedmods.refinedstorage.common.grid.screen.AbstractGridScreen;
import com.refinedmods.refinedstorage.common.support.amount.AbstractAmountScreen;
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
    public Class<? extends Screen> screenClass() {
        return AbstractGridScreen.class;
    }

    @Override
    public ScreenAdapter adapt(Screen screen) {
        return new RsScreenAdapter((AbstractGridScreen<?>) screen);
    }

    /**
     * RS serves the autocrafting preview (and every other amount prompt) from its
     * own menu, so requesting a craft closes the grid and reopens it afterwards as
     * a new screen. {@code AbstractAmountScreen} is the shared base of the preview
     * and every other amount prompt, so one check covers them all.
     */
    @Override
    public boolean isCompanionScreen(Screen screen) {
        return screen instanceof AbstractAmountScreen<?, ?>;
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
