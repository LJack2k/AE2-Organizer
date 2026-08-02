package nl.ljack2k.ae2organizer.dev;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.refinedmods.refinedstorage.apiimpl.API;
import com.refinedmods.refinedstorage.apiimpl.network.grid.factory.GridBlockGridFactory;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.loading.FMLEnvironment;
import nl.ljack2k.ae2organizer.TerminalOrganizer;
import org.jetbrains.annotations.Nullable;

/**
 * Dev-only test harness — enabled only when {@code -Dae2organizer.devHarness} is set
 * (the {@code runServer}/{@code runClientJoin} Gradle runs set it; a normal install
 * never does, so none of this ships as active behaviour).
 * <p>
 * It exists to drive the mod over RCON on a dedicated server + auto-joining client,
 * so the tab bar can be screenshotted on a real grid:
 * <ul>
 *   <li>{@code /rsorgtest build} — places a {@code creative_controller} + {@code grid}
 *       above the player (adjacent → one powered network).</li>
 *   <li>{@code /rsorgtest open} — opens that grid for the player through RS's own
 *       {@code IGridManager}, exactly as right-clicking the block does.</li>
 *   <li>{@code /rsorgtest editor} / {@code /rsorgtest tab <id>} — drive UI paths that
 *       would otherwise need a mouse.</li>
 *   <li>{@code /rsorgshot} — asks the client to grab a frame.</li>
 * </ul>
 * This is the RS 1.12 port of the harness the newer lines carry, with two differences:
 * RS 1.12 opens grids via {@code API.instance().getGridManager().openGrid(GridBlockGridFactory.ID,
 * player, pos)} rather than RS2's {@code Platform} menu opener, and server→client
 * signalling goes through {@link DevSignal}'s chat marker rather than a network
 * channel (a {@code SimpleChannel} here stalls the dev client's login — see DevSignal).
 */
public final class DevHarness {
    private DevHarness() {}

    private static final ResourceLocation CREATIVE_CONTROLLER =
            new ResourceLocation("refinedstorage", "creative_controller");
    private static final ResourceLocation GRID =
            new ResourceLocation("refinedstorage", "grid");

    @Nullable
    private static BlockPos lastGridPos;

    public static void init() {
        if (FMLEnvironment.dist.isClient()) {
            // Client only needs the signal listener; the commands are server-side.
            MinecraftForge.EVENT_BUS.register(DevClientSignalListener.class);
            return;
        }
        MinecraftForge.EVENT_BUS.addListener(DevHarness::registerCommands);
    }

    private static void registerCommands(RegisterCommandsEvent event) {
        LiteralArgumentBuilder<CommandSourceStack> test = Commands.literal("rsorgtest")
                .then(Commands.literal("build").executes(ctx -> build(ctx.getSource())))
                .then(Commands.literal("open").executes(ctx -> open(ctx.getSource())))
                .then(Commands.literal("editor")
                        .executes(ctx -> send(ctx.getSource(), DevSignal.ACTION_EDITOR, "")))
                .then(Commands.literal("tab")
                        .executes(ctx -> send(ctx.getSource(), DevSignal.ACTION_SELECT_TAB, ""))
                        .then(Commands.argument("id", StringArgumentType.string())
                                .executes(ctx -> send(ctx.getSource(), DevSignal.ACTION_SELECT_TAB,
                                        StringArgumentType.getString(ctx, "id")))));
        event.getDispatcher().register(test);
        event.getDispatcher().register(Commands.literal("rsorgshot")
                .executes(ctx -> send(ctx.getSource(), DevSignal.ACTION_SCREENSHOT, "")));
    }

    private static int build(CommandSourceStack src) {
        ServerPlayer player = src.getPlayer();
        if (player == null) {
            src.sendFailure(Component.literal("[TerminalOrganizer] build needs a player context."));
            return 0;
        }
        if (!ModList.get().isLoaded("refinedstorage")) {
            src.sendFailure(Component.literal("[TerminalOrganizer] Refined Storage is not loaded."));
            return 0;
        }
        ServerLevel level = player.serverLevel();
        Block controller = BuiltInRegistries.BLOCK.getOptional(CREATIVE_CONTROLLER).orElse(null);
        Block grid = BuiltInRegistries.BLOCK.getOptional(GRID).orElse(null);
        if (controller == null || grid == null) {
            src.sendFailure(Component.literal("[TerminalOrganizer] RS blocks not found — is Refined Storage loaded?"));
            return 0;
        }
        BlockPos ctrlPos = player.blockPosition().above(2);
        BlockPos gridPos = ctrlPos.above();
        level.setBlockAndUpdate(ctrlPos, controller.defaultBlockState());
        level.setBlockAndUpdate(gridPos, grid.defaultBlockState());
        lastGridPos = gridPos;
        src.sendSuccess(() -> Component.literal(
                "[TerminalOrganizer] Placed creative_controller + grid at " + gridPos + ". Run /rsorgtest open."), false);
        return 1;
    }

    /**
     * Opens the grid through RS's own grid manager — the same call
     * {@code GridBlock#use} makes — so the client gets the full grid menu and view.
     */
    private static int open(CommandSourceStack src) {
        ServerPlayer player = src.getPlayer();
        if (player == null) {
            src.sendFailure(Component.literal("[TerminalOrganizer] open needs a player context."));
            return 0;
        }
        ServerLevel level = player.serverLevel();
        BlockPos pos = lastGridPos != null ? lastGridPos : findGrid(level, player.blockPosition());
        if (pos == null) {
            src.sendFailure(Component.literal("[TerminalOrganizer] No grid found — run /rsorgtest build first."));
            return 0;
        }
        try {
            API.instance().getGridManager().openGrid(GridBlockGridFactory.ID, player, pos);
        } catch (Throwable t) {
            TerminalOrganizer.LOGGER.debug("[TerminalOrganizer] openGrid failed", t);
            src.sendFailure(Component.literal("[TerminalOrganizer] Could not open the grid at " + pos + "."));
            return 0;
        }
        final BlockPos opened = pos;
        src.sendSuccess(() -> Component.literal("[TerminalOrganizer] Opened grid at " + opened + "."), false);
        return 1;
    }

    @Nullable
    private static BlockPos findGrid(ServerLevel level, BlockPos around) {
        Block grid = BuiltInRegistries.BLOCK.getOptional(GRID).orElse(null);
        if (grid == null) {
            return null;
        }
        for (BlockPos p : BlockPos.betweenClosed(around.offset(-8, -4, -8), around.offset(8, 4, 8))) {
            if (level.getBlockState(p).is(grid)) {
                return p.immutable();
            }
        }
        return null;
    }

    /** Send a dev action to the executing player's client. */
    private static int send(CommandSourceStack src, String action, String arg) {
        ServerPlayer player = src.getPlayer();
        if (player == null) {
            src.sendFailure(Component.literal("[TerminalOrganizer] needs a player context (use: execute as <player> run ...)."));
            return 0;
        }
        DevSignal.send(player, action, arg);
        src.sendSuccess(() -> Component.literal("[TerminalOrganizer] sent " + action), false);
        return 1;
    }
}
