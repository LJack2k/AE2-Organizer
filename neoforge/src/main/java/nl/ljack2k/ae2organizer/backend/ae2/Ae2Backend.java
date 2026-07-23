package nl.ljack2k.ae2organizer.backend.ae2;

import appeng.client.gui.me.common.MEStorageScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.world.item.ItemStack;
import nl.ljack2k.ae2organizer.backend.ScreenAdapter;
import nl.ljack2k.ae2organizer.backend.StorageBackend;
import org.jetbrains.annotations.Nullable;

import java.util.function.Predicate;

/**
 * Applied Energistics 2 backend: adds filter tabs to ME/Crafting terminal
 * screens. Only instantiated when {@code ae2} is present (see
 * {@code BackendRegistry#init()}), so referencing AE2 classes here is safe.
 * <p>
 * Its store keeps the id {@code "ae2"} and reads/writes the legacy
 * {@code tabs.json} filename, so existing AE2Organizer users keep their tabs.
 */
public final class Ae2Backend implements StorageBackend {

    @Override
    public String id() {
        return "ae2";
    }

    @Override
    public boolean handles(Screen screen) {
        return screen instanceof MEStorageScreen<?>;
    }

    @Override
    public ScreenAdapter adapt(Screen screen) {
        return new Ae2ScreenAdapter((MEStorageScreen<?>) screen);
    }

    @Override
    public void setActiveFilter(@Nullable Predicate<ItemStack> predicate) {
        RepoFilterBridge.setActiveFilter(predicate);
    }
}
