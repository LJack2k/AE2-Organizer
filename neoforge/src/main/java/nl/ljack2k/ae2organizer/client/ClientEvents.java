package nl.ljack2k.ae2organizer.client;

import com.mojang.brigadier.context.CommandContext;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import nl.ljack2k.ae2organizer.backend.BackendRegistry;
import nl.ljack2k.ae2organizer.backend.ScreenAdapter;
import nl.ljack2k.ae2organizer.backend.StorageBackend;
import nl.ljack2k.ae2organizer.client.gui.TabBarWidget;
import nl.ljack2k.ae2organizer.filter.FilterWindow;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Game-bus screen hooks, backend-dispatching. Registered (client-only) from
 * {@link ClientBootstrap}.
 * <p>
 * On each storage screen the open backend is resolved via {@link BackendRegistry}
 * ({@code null} → not a storage screen, ignored). One {@link TabBarWidget} per
 * visible {@link FilterWindow} of that backend's store is held here and
 * <em>rendered every frame</em> on {@code ScreenEvent.Render.Post} — deliberately
 * not added as screen widgets (a resize or settings-return clears the screen's
 * widgets without firing {@code Init.Post}). The bar list is rebuilt when the
 * screen instance changes or the visible-window set changes.
 * <p>
 * Input is driven through the cancelable {@code ScreenEvent} mouse pre-events,
 * because both AE2's and RS's screens override {@code mouseScrolled}/
 * {@code mouseDragged} and can consume them before added widgets see them.
 */
public final class ClientEvents {
    private ClientEvents() {}

    private static final List<TabBarWidget> BARS = new ArrayList<>();
    @Nullable
    private static Screen activeBarScreen;
    private static String barsSignature = "";
    @Nullable
    private static StorageBackend activeBackend;
    @Nullable
    private static ScreenAdapter activeAdapter;
    @Nullable
    private static TabManager.Store activeStore;

    /**
     * Ticks left during which reopening a terminal counts as a round trip rather
     * than a fresh open. Refreshed every tick a companion screen is on top and
     * counted down afterwards, so a preview that is escaped out of stops
     * suppressing the reset after ~half a second.
     */
    private static final int ROUND_TRIP_GRACE_TICKS = 10;
    private static int roundTripTicks;
    /** Weak so a closed terminal (and its menu contents) isn't pinned in memory. */
    @Nullable
    private static java.lang.ref.WeakReference<Screen> lastInitScreen;

    /**
     * Keeps {@link #roundTripTicks} alive while a backend companion screen (craft
     * preview/amount/status) is open. Cheap: one screen check per client tick.
     */
    @SubscribeEvent
    public static void onClientTick(net.neoforged.neoforge.client.event.ClientTickEvent.Post event) {
        Screen screen = net.minecraft.client.Minecraft.getInstance().screen;
        if (screen != null && BackendRegistry.forScreen(screen) == null && BackendRegistry.isCompanionScreen(screen)) {
            roundTripTicks = ROUND_TRIP_GRACE_TICKS;
        } else if (roundTripTicks > 0) {
            roundTripTicks--;
        }
    }

    @SubscribeEvent
    public static void onScreenInit(ScreenEvent.Init.Post event) {
        StorageBackend backend = BackendRegistry.forScreen(event.getScreen());
        if (backend == null) {
            if (BackendRegistry.isCompanionScreen(event.getScreen())) {
                roundTripTicks = ROUND_TRIP_GRACE_TICKS;
            }
            return;
        }
        Screen screen = event.getScreen();
        TabManager.Store store = TabManager.forBackend(backend.id());
        if (!store.isLoaded()) {
            store.load();
        }
        // Init.Post also fires on a window resize (same screen instance) and on the
        // terminal that comes back from a craft preview / settings page — neither is
        // the player opening a terminal, so the active tab survives both.
        boolean reinit = lastInitScreen != null && lastInitScreen.get() == screen;
        boolean freshOpen = !reinit && roundTripTicks == 0;
        lastInitScreen = new java.lang.ref.WeakReference<>(screen);
        roundTripTicks = 0;
        if (freshOpen && store.getSettings().resetFilterOnOpen()) {
            store.setActive(null);
        }
        ScreenAdapter adapter = backend.adapt(screen);
        store.rememberTerminal(adapter.terminalKey(), adapter.title().getString());
        rebuild(backend, screen, adapter, store);
        applyFilter(adapter, store);
    }

    private static String signature(ScreenAdapter adapter, TabManager.Store store) {
        StringBuilder sb = new StringBuilder();
        for (FilterWindow w : store.visibleWindows(adapter.terminalKey())) {
            sb.append(w.id()).append(';');
        }
        return sb.toString();
    }

    private static void rebuild(StorageBackend backend, Screen screen, ScreenAdapter adapter, TabManager.Store store) {
        BARS.clear();
        for (FilterWindow w : store.visibleWindows(adapter.terminalKey())) {
            BARS.add(new TabBarWidget(screen, adapter, store, backend.theme(), w.id()));
        }
        activeBackend = backend;
        activeBarScreen = screen;
        activeAdapter = adapter;
        activeStore = store;
        barsSignature = signature(adapter, store);
    }

    private static void ensure(StorageBackend backend, Screen screen) {
        TabManager.Store store = TabManager.forBackend(backend.id());
        if (!store.isLoaded()) {
            store.load();
        }
        if (activeBarScreen != screen || activeStore != store) {
            rebuild(backend, screen, backend.adapt(screen), store);
            applyFilter(activeAdapter, store);
            return;
        }
        if (activeAdapter != null && !barsSignature.equals(signature(activeAdapter, store))) {
            rebuild(backend, screen, activeAdapter, store);
            applyFilter(activeAdapter, store);
        }
    }

    @SubscribeEvent
    public static void onScreenRender(ScreenEvent.Render.Post event) {
        StorageBackend backend = BackendRegistry.forScreen(event.getScreen());
        if (backend == null) {
            return;
        }
        ensure(backend, event.getScreen());
        for (TabBarWidget bar : BARS) {
            bar.extractRenderState(event.getGuiGraphics(), event.getMouseX(), event.getMouseY(), event.getPartialTick());
        }
        if (activeStore != null && activeStore.isMoveMode()) {
            renderMoveBanner(backend.theme(), event.getGuiGraphics(), event.getScreen(), event.getMouseX(), event.getMouseY());
        }
    }

    private static final String MOVE_MSG = "Move mode: drag panels (or hold Alt anytime) — click here when done";

    private static void renderMoveBanner(nl.ljack2k.ae2organizer.backend.Theme theme,
                                         net.minecraft.client.gui.GuiGraphicsExtractor g, Screen screen, int mouseX, int mouseY) {
        int[] r = bannerRect(screen);
        boolean hover = mouseX >= r[0] && mouseX < r[0] + r[2] && mouseY >= r[1] && mouseY < r[1] + r[3];
        theme.panel(g, r[0], r[1], r[2], r[3]);
        if (hover) {
            g.fill(r[0] + 1, r[1] + 1, r[0] + r[2] - 1, r[1] + r[3] - 1, 0x2200B4FF);
        }
        var font = net.minecraft.client.Minecraft.getInstance().font;
        g.text(font, MOVE_MSG, r[0] + 6, r[1] + (r[3] - 8) / 2, theme.textColor(), false);
    }

    private static int[] bannerRect(Screen screen) {
        var font = net.minecraft.client.Minecraft.getInstance().font;
        int w = font.width(MOVE_MSG) + 12;
        int x = (screen.width - w) / 2;
        return new int[]{x, 4, w, 16};
    }

    /**
     * Client-side command {@code /storageorganizer resetwindows} — a last-resort
     * recovery if a window ends up unreachable (off-screen, no gear, etc.). Resets
     * every backend's store so recovery works regardless of which screen is open.
     */
    @SubscribeEvent
    public static void onRegisterClientCommands(RegisterClientCommandsEvent event) {
        event.getDispatcher().register(
                Commands.literal("storageorganizer")
                        .then(Commands.literal("resetwindows").executes(ClientEvents::resetWindows)));
    }

    private static int resetWindows(CommandContext<CommandSourceStack> ctx) {
        for (StorageBackend backend : BackendRegistry.all()) {
            TabManager.Store store = TabManager.forBackend(backend.id());
            if (!store.isLoaded()) {
                store.load();
            }
            store.resetWindowLayout();
        }
        ctx.getSource().sendSuccess(() -> Component.literal(
                "[StorageOrganizer] Filter windows reset: first docked, rest centered, gears shown."), false);
        return 1;
    }

    /**
     * The panel rectangles of the filter windows currently on screen, for item-list
     * mods to keep clear (JEI reads these through its global GUI handler and drops
     * the grid slots that intersect them, so its list wraps around our panels).
     * Empty unless one of our bars is really being drawn on the open screen — the
     * bar list outlives a closed terminal, and stale rects would blank out JEI slots
     * on unrelated screens. Recomputed per call, so dragging a panel reflows JEI live.
     */
    public static List<Rect2i> activeBarBounds() {
        Screen screen = net.minecraft.client.Minecraft.getInstance().screen;
        if (screen == null || screen != activeBarScreen || BARS.isEmpty()) {
            return List.of();
        }
        List<Rect2i> areas = new ArrayList<>(BARS.size());
        for (TabBarWidget bar : BARS) {
            Rect2i r = bar.bounds();
            if (r != null) {
                areas.add(r);
            }
        }
        return areas;
    }

    private static boolean isActive(Screen screen) {
        return !BARS.isEmpty() && screen == activeBarScreen;
    }

    @SubscribeEvent
    public static void onMousePressed(ScreenEvent.MouseButtonPressed.Pre event) {
        if (event.getButton() != 0 || !isActive(event.getScreen())) {
            return;
        }
        if (activeStore != null && activeStore.isMoveMode()) {
            int[] r = bannerRect(event.getScreen());
            if (event.getMouseX() >= r[0] && event.getMouseX() < r[0] + r[2]
                    && event.getMouseY() >= r[1] && event.getMouseY() < r[1] + r[3]) {
                activeStore.setMoveMode(false);
                event.setCanceled(true);
                return;
            }
        }
        for (int i = BARS.size() - 1; i >= 0; i--) {
            if (BARS.get(i).handleMouseDown(event.getMouseX(), event.getMouseY())) {
                event.setCanceled(true);
                return;
            }
        }
    }

    @SubscribeEvent
    public static void onMouseDragged(ScreenEvent.MouseDragged.Pre event) {
        if (!isActive(event.getScreen())) {
            return;
        }
        for (TabBarWidget bar : BARS) {
            if (bar.handleMouseDrag(event.getMouseX(), event.getMouseY())) {
                event.setCanceled(true);
                return;
            }
        }
    }

    @SubscribeEvent
    public static void onMouseReleased(ScreenEvent.MouseButtonReleased.Pre event) {
        for (TabBarWidget bar : BARS) {
            bar.handleMouseUp();
        }
    }

    @SubscribeEvent
    public static void onMouseScrolled(ScreenEvent.MouseScrolled.Pre event) {
        if (!isActive(event.getScreen())) {
            return;
        }
        for (int i = BARS.size() - 1; i >= 0; i--) {
            if (BARS.get(i).handleScroll(event.getMouseX(), event.getMouseY(), event.getScrollDeltaY())) {
                event.setCanceled(true);
                return;
            }
        }
    }

    /**
     * Re-applies the active tab's filter to the open storage view. The predicate
     * itself lives in the backend's filter bridge (pushed by the store); this
     * re-runs the backend's filter+sort so the change is reflected immediately.
     */
    public static void applyFilter(@Nullable ScreenAdapter adapter, TabManager.Store store) {
        store.pushFilter();
        if (adapter != null) {
            adapter.refilter();
        }
    }
}
