package nl.ljack2k.ae2organizer.dev;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.refinedmods.refinedstorage.common.Platform;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import nl.ljack2k.ae2organizer.StorageOrganizer;
import org.jetbrains.annotations.Nullable;

/**
 * Dev-only test harness — enabled only when {@code -Dae2organizer.devHarness} is set
 * (the {@code runServer}/{@code clientJoin} Gradle runs set it; a normal install
 * never does, so none of this ships as active behaviour).
 * <p>
 * It exists to drive the mod over RCON on a dedicated server + auto-joining client,
 * so the tab bar can be screenshotted on a real grid:
 * <ul>
 *   <li>{@code /rsorgtest build} — places a {@code creative_controller} + {@code grid}
 *       above the player (adjacent → one powered network).</li>
 *   <li>{@code /rsorgtest open} — server-opens that grid's menu for the player using RS's
 *       own {@link Platform} menu opener (so the extended {@code GridData} is sent).</li>
 *   <li>{@code /rsorgshot} — sends {@link ScreenshotRequestPayload} so the client grabs a frame.</li>
 * </ul>
 */
public final class DevHarness {
    private DevHarness() {}

    private static final ResourceLocation CREATIVE_CONTROLLER =
            ResourceLocation.fromNamespaceAndPath("refinedstorage", "creative_controller");
    private static final ResourceLocation GRID =
            ResourceLocation.fromNamespaceAndPath("refinedstorage", "grid");

    @Nullable
    private static BlockPos lastGridPos;

    public static void init(IEventBus modBus, Dist dist) {
        modBus.addListener(DevHarness::registerPayloads);
        NeoForge.EVENT_BUS.addListener(DevHarness::registerCommands);
    }

    private static void registerPayloads(RegisterPayloadHandlersEvent event) {
        // optional(): dev-only channels must never break the connection handshake on
        // a version/side skew (and keeps faith with the mod's client-only, any-server design).
        var registrar = event.registrar("1").optional();
        if (FMLEnvironment.dist == Dist.CLIENT) {
            // Client actually performs these. Referenced only on the client dist so
            // the dedicated server never classloads net.minecraft.client.* .
            registrar.playToClient(ScreenshotRequestPayload.TYPE, ScreenshotRequestPayload.STREAM_CODEC,
                    (payload, context) -> context.enqueueWork(
                            nl.ljack2k.ae2organizer.client.ClientScreenshot::take));
            registrar.playToClient(EditorRequestPayload.TYPE, EditorRequestPayload.STREAM_CODEC,
                    (payload, context) -> context.enqueueWork(
                            nl.ljack2k.ae2organizer.client.DevClientActions::openEditor));
            registrar.playToClient(SelectTabPayload.TYPE, SelectTabPayload.STREAM_CODEC,
                    (payload, context) -> context.enqueueWork(
                            () -> nl.ljack2k.ae2organizer.client.DevClientActions.selectTab(payload.tabId())));
        } else {
            // Server must still register the types to be allowed to send them; it never receives them.
            registrar.playToClient(ScreenshotRequestPayload.TYPE, ScreenshotRequestPayload.STREAM_CODEC,
                    (payload, context) -> {});
            registrar.playToClient(EditorRequestPayload.TYPE, EditorRequestPayload.STREAM_CODEC,
                    (payload, context) -> {});
            registrar.playToClient(SelectTabPayload.TYPE, SelectTabPayload.STREAM_CODEC,
                    (payload, context) -> {});
        }
    }

    private static void registerCommands(RegisterCommandsEvent event) {
        LiteralArgumentBuilder<CommandSourceStack> test = Commands.literal("rsorgtest")
                .then(Commands.literal("build").executes(ctx -> build(ctx.getSource())))
                .then(Commands.literal("open").executes(ctx -> open(ctx.getSource())))
                .then(Commands.literal("editor").executes(ctx -> send(ctx.getSource(), new EditorRequestPayload())))
                .then(Commands.literal("tab")
                        .executes(ctx -> send(ctx.getSource(), new SelectTabPayload("")))
                        .then(Commands.argument("id", com.mojang.brigadier.arguments.StringArgumentType.string())
                                .executes(ctx -> send(ctx.getSource(),
                                        new SelectTabPayload(com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "id"))))));
        event.getDispatcher().register(test);
        event.getDispatcher().register(Commands.literal("rsorgshot").executes(ctx -> shot(ctx.getSource())));
    }

    private static int build(CommandSourceStack src) {
        ServerPlayer player = src.getPlayer();
        if (player == null) {
            src.sendFailure(Component.literal("[StorageOrganizer] build needs a player context."));
            return 0;
        }
        ServerLevel level = player.serverLevel();
        Block controller = BuiltInRegistries.BLOCK.getOptional(CREATIVE_CONTROLLER).orElse(null);
        Block grid = BuiltInRegistries.BLOCK.getOptional(GRID).orElse(null);
        if (controller == null || grid == null) {
            src.sendFailure(Component.literal("[StorageOrganizer] RS blocks not found — is Refined Storage loaded?"));
            return 0;
        }
        BlockPos ctrlPos = player.blockPosition().above(2);
        BlockPos gridPos = ctrlPos.above();
        level.setBlockAndUpdate(ctrlPos, controller.defaultBlockState());
        level.setBlockAndUpdate(gridPos, grid.defaultBlockState());
        lastGridPos = gridPos;
        src.sendSuccess(() -> Component.literal(
                "[StorageOrganizer] Placed creative_controller + grid at " + gridPos + ". Run /rsorgtest open."), false);
        return 1;
    }

    private static int open(CommandSourceStack src) {
        ServerPlayer player = src.getPlayer();
        if (player == null) {
            src.sendFailure(Component.literal("[StorageOrganizer] open needs a player context."));
            return 0;
        }
        ServerLevel level = player.serverLevel();
        BlockPos pos = lastGridPos != null ? lastGridPos : findGrid(level, player.blockPosition());
        if (pos == null) {
            src.sendFailure(Component.literal("[StorageOrganizer] No grid found — run /rsorgtest build first."));
            return 0;
        }
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof MenuProvider provider) {
            Platform.INSTANCE.getMenuOpener().openMenu(player, provider);
            src.sendSuccess(() -> Component.literal("[StorageOrganizer] Opened grid at " + pos + "."), false);
            return 1;
        }
        src.sendFailure(Component.literal("[StorageOrganizer] Block at " + pos + " is not a grid menu provider."));
        return 0;
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

    private static int shot(CommandSourceStack src) {
        return send(src, new ScreenshotRequestPayload());
    }

    /** Send a dev payload to the executing player's client. */
    private static int send(CommandSourceStack src, net.minecraft.network.protocol.common.custom.CustomPacketPayload payload) {
        ServerPlayer player = src.getPlayer();
        if (player == null) {
            src.sendFailure(Component.literal("[StorageOrganizer] needs a player context (use: execute as <player> run ...)."));
            return 0;
        }
        PacketDistributor.sendToPlayer(player, payload);
        src.sendSuccess(() -> Component.literal("[StorageOrganizer] sent " + payload.type().id()), false);
        return 1;
    }
}
