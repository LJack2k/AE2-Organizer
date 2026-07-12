package nl.ljack2k.ae2organizer.client;

import appeng.api.stacks.AEKey;
import appeng.client.gui.me.common.MEStorageScreen;
import appeng.client.gui.me.common.Repo;
import nl.ljack2k.ae2organizer.client.gui.TabBarWidget;
import nl.ljack2k.ae2organizer.filter.FilterWindow;
import nl.ljack2k.ae2organizer.filter.TabFilterHolder;
import nl.ljack2k.ae2organizer.mixin.MEStorageScreenAccessor;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.client.event.ScreenEvent;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * Game-bus screen hooks. Registered (client-only) from the mod constructor.
 * <p>
 * One {@link TabBarWidget} per {@link FilterWindow} is held in our own list and
 * <em>rendered every frame</em> on {@code ScreenEvent.Render.Post} — deliberately
 * not added as screen widgets (a resize or settings-return clears the screen's
 * widgets without firing {@code Init.Post}, which would wipe them permanently).
 * The bar list is rebuilt when the terminal instance changes or the set of windows
 * changes (e.g. after editing).
 * <p>
 * Input is driven through the cancelable {@code ScreenEvent} mouse pre-events,
 * because AE2's terminal overrides {@code mouseScrolled}/{@code mouseDragged} and
 * consumes them before added widgets see them.
 */
public final class ClientEvents {
    private ClientEvents() {}

    private static final List<TabBarWidget> BARS = new ArrayList<>();
    @Nullable
    private static Screen activeBarScreen;
    private static String barsSignature = "";

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
        // Remember this terminal's friendly name for the per-terminal visibility list.
        TabManager.rememberTerminal(TabBarWidget.terminalKey(terminal), terminal.getTitle().getString());
        rebuild(terminal);
        applyFilter(terminal, TabManager.activePredicate());
    }

    /**
     * Signature of the windows shown on this terminal (ids in order); rebuilds the
     * bars when it changes — including when per-terminal visibility toggles.
     */
    private static String signature(MEStorageScreen<?> terminal) {
        String key = TabBarWidget.terminalKey(terminal);
        StringBuilder sb = new StringBuilder();
        for (FilterWindow w : TabManager.visibleWindows(key)) {
            sb.append(w.id()).append(';');
        }
        return sb.toString();
    }

    private static void rebuild(MEStorageScreen<?> terminal) {
        BARS.clear();
        String key = TabBarWidget.terminalKey(terminal);
        for (FilterWindow w : TabManager.visibleWindows(key)) {
            BARS.add(new TabBarWidget(terminal, w.id()));
        }
        activeBarScreen = terminal;
        barsSignature = signature(terminal);
    }

    private static void ensure(MEStorageScreen<?> terminal) {
        if (!TabManager.isLoaded()) {
            TabManager.load();
        }
        if (activeBarScreen != terminal || !barsSignature.equals(signature(terminal))) {
            rebuild(terminal);
            applyFilter(terminal, TabManager.activePredicate());
        }
    }

    @SubscribeEvent
    public static void onScreenRender(ScreenEvent.Render.Post event) {
        if (!(event.getScreen() instanceof MEStorageScreen<?> terminal)) {
            return;
        }
        ensure(terminal);
        for (TabBarWidget bar : BARS) {
            bar.render(event.getGuiGraphics(), event.getMouseX(), event.getMouseY(), event.getPartialTick());
        }
        if (TabManager.isMoveMode()) {
            renderMoveBanner(event.getGuiGraphics(), terminal, event.getMouseX(), event.getMouseY());
        }
    }

    /** A top-center banner shown during move-mode; clicking it finishes moving. */
    private static void renderMoveBanner(net.minecraft.client.gui.GuiGraphics g, Screen screen, int mouseX, int mouseY) {
        int[] r = bannerRect(screen);
        boolean hover = mouseX >= r[0] && mouseX < r[0] + r[2] && mouseY >= r[1] && mouseY < r[1] + r[3];
        nl.ljack2k.ae2organizer.client.gui.Ae2Style.panel(g, r[0], r[1], r[2], r[3]);
        if (hover) {
            g.fill(r[0] + 1, r[1] + 1, r[0] + r[2] - 1, r[1] + r[3] - 1, 0x2200B4FF);
        }
        var font = net.minecraft.client.Minecraft.getInstance().font;
        int tc = nl.ljack2k.ae2organizer.client.gui.Ae2Style.textColor();
        String msg = "Move mode: drag panels — click here when done";
        g.drawString(font, msg, r[0] + 6, r[1] + (r[3] - 8) / 2, tc, false);
    }

    private static int[] bannerRect(Screen screen) {
        var font = net.minecraft.client.Minecraft.getInstance().font;
        int w = font.width("Move mode: drag panels — click here when done") + 12;
        int x = (screen.width - w) / 2;
        return new int[]{x, 4, w, 16};
    }

    /**
     * Client-side command {@code /ae2organizer resetwindows} — a last-resort
     * recovery if a window ends up unreachable (off-screen, no gear, etc.).
     */
    @SubscribeEvent
    public static void onRegisterClientCommands(RegisterClientCommandsEvent event) {
        event.getDispatcher().register(
                Commands.literal("ae2organizer").then(Commands.literal("resetwindows").executes(ctx -> {
                    if (!TabManager.isLoaded()) {
                        TabManager.load();
                    }
                    TabManager.resetWindowLayout();
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
        if (TabManager.isMoveMode()) {
            int[] r = bannerRect(event.getScreen());
            if (event.getMouseX() >= r[0] && event.getMouseX() < r[0] + r[2]
                    && event.getMouseY() >= r[1] && event.getMouseY() < r[1] + r[3]) {
                TabManager.setMoveMode(false);
                event.setCanceled(true);
                return;
            }
        }
        // Iterate last-to-first so an overlapping later window takes priority.
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
            if (BARS.get(i).handleScroll(event.getMouseX(), event.getMouseY(), event.getScrollDelta())) {
                event.setCanceled(true);
                return;
            }
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
