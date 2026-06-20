package nl.ljack2k.ae2organizer.client;

import appeng.api.stacks.AEKey;
import appeng.client.gui.me.common.MEStorageScreen;
import appeng.client.gui.me.common.Repo;
import nl.ljack2k.ae2organizer.client.gui.TabBarWidget;
import nl.ljack2k.ae2organizer.filter.TabFilterHolder;
import nl.ljack2k.ae2organizer.mixin.MEStorageScreenAccessor;

import net.minecraft.client.gui.screens.Screen;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import org.jetbrains.annotations.Nullable;

import java.util.function.Predicate;

/**
 * Game-bus screen hooks. Registered (client-only) from the mod constructor.
 * <p>
 * The tab bar is attached as a renderable on {@code ScreenEvent.Init.Post}, but
 * its <em>input</em> is driven through the cancelable {@code ScreenEvent} mouse
 * events: AE2's terminal screen overrides {@code mouseScrolled}/{@code mouseDragged}
 * and consumes them before added widgets see them, so routing through the
 * pre-events (which fire first) is the reliable way to get scrollbar drag and
 * wheel scrolling.
 */
public final class ClientEvents {
    private ClientEvents() {}

    @Nullable
    private static TabBarWidget activeBar;
    @Nullable
    private static Screen activeBarScreen;

    @SubscribeEvent
    public static void onScreenInit(ScreenEvent.Init.Post event) {
        if (!(event.getScreen() instanceof MEStorageScreen<?> terminal)) {
            return;
        }
        if (!TabManager.isLoaded()) {
            TabManager.load();
        }
        if (TabManager.getSettings().resetFilterOnOpen()) {
            TabManager.setActive(null);
        }
        TabBarWidget bar = new TabBarWidget(terminal);
        event.addListener(bar);
        activeBar = bar;
        activeBarScreen = terminal;
        applyFilter(terminal, TabManager.activePredicate());
    }

    private static boolean isActive(Screen screen) {
        return activeBar != null && screen == activeBarScreen;
    }

    @SubscribeEvent
    public static void onMousePressed(ScreenEvent.MouseButtonPressed.Pre event) {
        if (event.getButton() == 0 && isActive(event.getScreen())
                && activeBar.handleMouseDown(event.getMouseX(), event.getMouseY())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onMouseDragged(ScreenEvent.MouseDragged.Pre event) {
        if (isActive(event.getScreen())
                && activeBar.handleMouseDrag(event.getMouseX(), event.getMouseY())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onMouseReleased(ScreenEvent.MouseButtonReleased.Pre event) {
        if (activeBar != null) {
            activeBar.handleMouseUp();
        }
    }

    @SubscribeEvent
    public static void onMouseScrolled(ScreenEvent.MouseScrolled.Pre event) {
        if (isActive(event.getScreen())
                && activeBar.handleScroll(event.getMouseX(), event.getMouseY(), event.getScrollDeltaY())) {
            event.setCanceled(true);
        }
    }

    /**
     * Pushes a predicate into AE2's client {@code Repo} and refreshes the view.
     * Pass {@code null} to clear the filter (the "All" tab).
     */
    public static void applyFilter(MEStorageScreen<?> terminal, Predicate<AEKey> predicate) {
        Repo repo = ((MEStorageScreenAccessor) terminal).ae2organizer$getRepo();
        ((TabFilterHolder) repo).ae2organizer$setTabFilter(predicate);
        repo.updateView();
    }
}
