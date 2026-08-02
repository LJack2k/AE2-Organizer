package nl.ljack2k.ae2organizer.backend.rslegacy;

import com.refinedmods.refinedstorage.screen.grid.GridScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.world.item.ItemStack;
import nl.ljack2k.ae2organizer.backend.ScreenAdapter;
import nl.ljack2k.ae2organizer.backend.StorageBackend;
import nl.ljack2k.ae2organizer.backend.Theme;
import org.jetbrains.annotations.Nullable;

import java.util.function.Predicate;

/**
 * Refined Storage backend for the 1.20.1 line, targeting <strong>RS 1.12</strong> —
 * the pre-rewrite codebase, whose client grid API ({@code GridScreen} + {@code IGridView})
 * is unrelated to the RS2 one the 1.21.1/26.1 lines hook. Only instantiated when
 * {@code refinedstorage} is present (see {@code BackendRegistry#init()}), so referencing
 * RS classes here is safe.
 * <p>
 * It keeps the backend id {@code "rs"} — the same as the RS2 backend on the other lines —
 * so its store is the familiar {@code config/ae2organizer/rs.json} and a filter set
 * exported on another line imports here unchanged.
 */
public final class RsLegacyBackend implements StorageBackend {

    @Override
    public String id() {
        return "rs";
    }

    @Override
    public boolean handles(Screen screen) {
        return screen instanceof GridScreen;
    }

    @Override
    public ScreenAdapter adapt(Screen screen) {
        return new RsLegacyScreenAdapter((GridScreen) screen);
    }

    /**
     * RS 1.12 serves the crafting-amount and crafting-preview dialogs from their own
     * screens and reopens the grid afterwards, so a craft request would otherwise look
     * like the player opening a grid and reset the active tab.
     */
    @Override
    public boolean isCompanionScreen(Screen screen) {
        return screen instanceof com.refinedmods.refinedstorage.screen.grid.CraftingSettingsScreen
                || screen instanceof com.refinedmods.refinedstorage.screen.grid.CraftingPreviewScreen
                || screen instanceof com.refinedmods.refinedstorage.screen.grid.AlternativesScreen;
    }

    @Override
    public Theme theme() {
        return RsLegacyTheme.INSTANCE;
    }

    @Override
    public void setActiveFilter(@Nullable Predicate<ItemStack> predicate) {
        GridFilterBridge.setActiveFilter(predicate);
    }
}
