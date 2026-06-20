package nl.ljack2k.ae2organizer.client;

import appeng.api.stacks.AEKey;
import appeng.client.gui.me.common.MEStorageScreen;
import appeng.client.gui.me.common.Repo;
import nl.ljack2k.ae2organizer.client.gui.TabBarWidget;
import nl.ljack2k.ae2organizer.filter.TabFilterHolder;
import nl.ljack2k.ae2organizer.mixin.MEStorageScreenAccessor;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;

import java.util.function.Predicate;

/**
 * Game-bus screen hooks. Registered (client-only) from the mod constructor.
 * When an AE2 terminal screen finishes (re)initialising, we attach the tab bar
 * and re-apply the active tab so the view is consistent after resizes and after
 * returning from the tab editor.
 */
public final class ClientEvents {
    private ClientEvents() {}

    @SubscribeEvent
    public static void onScreenInit(ScreenEvent.Init.Post event) {
        if (!(event.getScreen() instanceof MEStorageScreen<?> terminal)) {
            return;
        }
        if (!TabManager.isLoaded()) {
            // Safety net in case a terminal opens before client setup ran.
            TabManager.load();
        }
        if (TabManager.getSettings().resetFilterOnOpen()) {
            TabManager.setActive(null);
        }
        event.addListener(new TabBarWidget(terminal));
        applyFilter(terminal, TabManager.activePredicate());
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
