package nl.ljack2k.ae2organizer.client;

import net.minecraft.client.gui.screens.Screen;
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

    @SubscribeEvent
    public static void onScreenInit(ScreenEvent.Init.Post event) {
        StorageBackend backend = BackendRegistry.forScreen(event.getScreen());
        if (backend == null) {
            return;
        }
        Screen screen = event.getScreen();
        TabManager.Store store = TabManager.forBackend(backend.id());
        if (!store.isLoaded()) {
            store.load();
        }
        if (store.getSettings().resetFilterOnOpen()) {
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
            bar.render(event.getGuiGraphics(), event.getMouseX(), event.getMouseY(), event.getPartialTick());
        }
        if (activeStore != null && activeStore.isMoveMode()) {
            renderMoveBanner(backend.theme(), event.getGuiGraphics(), event.getScreen(), event.getMouseX(), event.getMouseY());
        }
    }

    private static final String MOVE_MSG = "Move mode: drag panels (or hold Alt anytime) — click here when done";

    private static void renderMoveBanner(nl.ljack2k.ae2organizer.backend.Theme theme,
                                         net.minecraft.client.gui.GuiGraphics g, Screen screen, int mouseX, int mouseY) {
        int[] r = bannerRect(screen);
        boolean hover = mouseX >= r[0] && mouseX < r[0] + r[2] && mouseY >= r[1] && mouseY < r[1] + r[3];
        theme.panel(g, r[0], r[1], r[2], r[3]);
        if (hover) {
            g.fill(r[0] + 1, r[1] + 1, r[0] + r[2] - 1, r[1] + r[3] - 1, 0x2200B4FF);
        }
        var font = net.minecraft.client.Minecraft.getInstance().font;
        g.drawString(font, MOVE_MSG, r[0] + 6, r[1] + (r[3] - 8) / 2, theme.textColor(), false);
    }

    private static int[] bannerRect(Screen screen) {
        var font = net.minecraft.client.Minecraft.getInstance().font;
        int w = font.width(MOVE_MSG) + 12;
        int x = (screen.width - w) / 2;
        return new int[]{x, 4, w, 16};
    }

    /**
     * Client-side command {@code /ae2organizer resetwindows} — a last-resort
     * recovery if a window ends up unreachable (off-screen, no gear, etc.). Resets
     * every backend's store so recovery works regardless of which screen is open.
     */
    @SubscribeEvent
    public static void onRegisterClientCommands(RegisterClientCommandsEvent event) {
        event.getDispatcher().register(
                Commands.literal("ae2organizer").then(Commands.literal("resetwindows").executes(ctx -> {
                    for (StorageBackend backend : BackendRegistry.all()) {
                        TabManager.Store store = TabManager.forBackend(backend.id());
                        if (!store.isLoaded()) {
                            store.load();
                        }
                        store.resetWindowLayout();
                    }
                    ctx.getSource().sendSuccess(() -> Component.literal(
                            "[AE2Organizer] Filter windows reset: first docked, rest centered, gears shown."), false);
                    return 1;
                })));
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
