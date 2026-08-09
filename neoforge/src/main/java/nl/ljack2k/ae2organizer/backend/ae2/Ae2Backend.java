package nl.ljack2k.ae2organizer.backend.ae2;

import appeng.client.gui.AEBaseScreen;
import appeng.client.gui.me.common.MEStorageScreen;
import appeng.menu.ISubMenu;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.world.item.ItemStack;
import nl.ljack2k.ae2organizer.backend.ScreenAdapter;
import nl.ljack2k.ae2organizer.backend.StorageBackend;
import nl.ljack2k.ae2organizer.backend.Theme;
import org.jetbrains.annotations.Nullable;

import java.util.function.Predicate;

/**
 * Applied Energistics 2 backend: adds filter tabs to ME/Crafting terminal
 * screens. Only instantiated when {@code ae2} is present (see
 * {@code BackendRegistry#init()}), so referencing AE2 classes here is safe.
 * <p>
 * Its store keeps the id {@code "ae2"} and reads/writes the legacy
 * {@code tabs.json} filename, so tabs made in the AE2-only releases carry over.
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

    /**
     * AE2 models every terminal side-trip (craft amount, craft confirm, crafting
     * status, terminal settings, …) as a sub-menu that returns to its parent, so
     * one {@link ISubMenu} check covers them all — no per-screen list to maintain.
     */
    @Override
    public boolean isCompanionScreen(Screen screen) {
        return screen instanceof AEBaseScreen<?> ae && ae.getMenu() instanceof ISubMenu;
    }

    @Override
    public Theme theme() {
        return Ae2Theme.INSTANCE;
    }

    @Override
    public void setActiveFilter(@Nullable Predicate<ItemStack> predicate) {
        RepoFilterBridge.setActiveFilter(predicate);
    }
}
