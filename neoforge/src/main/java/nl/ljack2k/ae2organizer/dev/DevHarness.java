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
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import appeng.blockentity.storage.MEChestBlockEntity;
import appeng.items.storage.CreativeCellItem;
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

    // AE2's ME Terminal is a cable part, so the harness uses the ME Chest instead:
    // it is a plain block with the same MEStorageScreen GUI our AE2 backend hooks.
    private static final ResourceLocation AE2_CREATIVE_ENERGY_CELL =
            ResourceLocation.fromNamespaceAndPath("ae2", "creative_energy_cell");
    private static final ResourceLocation AE2_CHEST =
            ResourceLocation.fromNamespaceAndPath("ae2", "chest");

    @Nullable
    private static BlockPos lastChestPos;

    public static void init(IEventBus modBus, Dist dist) {
        modBus.addListener(DevHarness::registerPayloads);
        if (dist == Dist.CLIENT) {
            NeoForge.EVENT_BUS.register(DevKeybinds.class);
        }
        NeoForge.EVENT_BUS.addListener(DevHarness::registerCommands);
        NeoForge.EVENT_BUS.addListener(DevHarness::onServerStarted);
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
            registrar.playToClient(SetPackPayload.TYPE, SetPackPayload.STREAM_CODEC,
                    (payload, context) -> context.enqueueWork(
                            () -> nl.ljack2k.ae2organizer.client.DevClientActions.setResourcePack(payload.packId())));
            registrar.playToClient(SetGuiScalePayload.TYPE, SetGuiScalePayload.STREAM_CODEC,
                    (payload, context) -> context.enqueueWork(
                            () -> nl.ljack2k.ae2organizer.client.DevClientActions.setGuiScale(String.valueOf(payload.scale()))));
        } else {
            // Server must still register the types to be allowed to send them; it never receives them.
            registrar.playToClient(ScreenshotRequestPayload.TYPE, ScreenshotRequestPayload.STREAM_CODEC,
                    (payload, context) -> {});
            registrar.playToClient(EditorRequestPayload.TYPE, EditorRequestPayload.STREAM_CODEC,
                    (payload, context) -> {});
            registrar.playToClient(SelectTabPayload.TYPE, SelectTabPayload.STREAM_CODEC,
                    (payload, context) -> {});
            registrar.playToClient(SetPackPayload.TYPE, SetPackPayload.STREAM_CODEC,
                    (payload, context) -> {});
            registrar.playToClient(SetGuiScalePayload.TYPE, SetGuiScalePayload.STREAM_CODEC,
                    (payload, context) -> {});
        }
    }


    /**
     * Pins the harness world to clear noon and stops both cycles.
     * <p>
     * This world exists purely to screenshot GUIs, and rain or nightfall changes the
     * colour of every pixel behind the panel -- which makes two shots of the same
     * screen impossible to compare, and repeatedly cost time re-shooting. Driven
     * through vanilla commands rather than the level API because that API is not
     * stable across the three MC lines (26.1 moved GameRules and reworked weather
     * into WeatherData), and this way all three carry the same implementation.
     */

    /**
     * The AE2 counterpart of {@code build}: a creative energy cell under an ME Chest,
     * the chest holding a creative cell pre-filled with a spread of test items. The
     * ME Chest is a plain block whose storage view is an MEStorageScreen, which is
     * exactly what the AE2 backend hooks -- the ME Terminal itself is a cable part
     * and cannot be placed by the harness.
     */
    private static int ae2Build(CommandSourceStack src) {
        ServerPlayer player = src.getPlayer();
        if (player == null) {
            src.sendFailure(Component.literal("[StorageOrganizer] ae2build needs a player context."));
            return 0;
        }
        if (!ModList.get().isLoaded("ae2")) {
            src.sendFailure(Component.literal("[StorageOrganizer] AE2 is not loaded."));
            return 0;
        }
        ServerLevel level = player.serverLevel();
        Block cell = BuiltInRegistries.BLOCK.getOptional(AE2_CREATIVE_ENERGY_CELL).orElse(null);
        Block chest = BuiltInRegistries.BLOCK.getOptional(AE2_CHEST).orElse(null);
        if (cell == null || chest == null) {
            src.sendFailure(Component.literal("[StorageOrganizer] AE2 blocks not found."));
            return 0;
        }
        BlockPos cellPos = player.blockPosition().above(2).east(3);
        BlockPos chestPos = cellPos.above();
        level.setBlockAndUpdate(cellPos, cell.defaultBlockState());
        level.setBlockAndUpdate(chestPos, chest.defaultBlockState());
        // The chest only opens a storage view when it holds a cell.
        if (level.getBlockEntity(chestPos) instanceof MEChestBlockEntity be) {
            be.setCell(CreativeCellItem.ofItems(
                    Items.IRON_INGOT, Items.GOLD_INGOT, Items.COPPER_INGOT, Items.NETHERITE_INGOT,
                    Items.DIAMOND, Items.OAK_LOG, Items.COBBLESTONE));
        }
        lastChestPos = chestPos;
        src.sendSuccess(() -> Component.literal(
                "[StorageOrganizer] Placed creative_energy_cell + ME chest at " + chestPos
                        + ". Run /rsorgtest ae2open."), false);
        return 1;
    }

    /** Opens the ME Chest's storage GUI -- an {@code MEStorageScreen}, which is what the AE2 backend hooks. */
    private static int ae2Open(CommandSourceStack src) {
        ServerPlayer player = src.getPlayer();
        if (player == null) {
            src.sendFailure(Component.literal("[StorageOrganizer] ae2open needs a player context."));
            return 0;
        }
        BlockPos pos = lastChestPos;
        if (pos == null) {
            src.sendFailure(Component.literal("[StorageOrganizer] No ME chest -- run /rsorgtest ae2build first."));
            return 0;
        }
        if (!(player.serverLevel().getBlockEntity(pos) instanceof MEChestBlockEntity chest)) {
            src.sendFailure(Component.literal("[StorageOrganizer] Block at " + pos + " is not an ME chest."));
            return 0;
        }
        // openGui() opens the terminal-style storage view when the chest is powered.
        boolean opened = chest.openGui(player);
        final BlockPos at = pos;
        if (!opened) {
            src.sendFailure(Component.literal(
                    "[StorageOrganizer] ME chest at " + at + " refused to open (no power / no cell?)."));
            return 0;
        }
        src.sendSuccess(() -> Component.literal("[StorageOrganizer] Opened ME chest at " + at + "."), false);
        return 1;
    }

    private static void onServerStarted(ServerStartedEvent event) {
        var server = event.getServer();
        var src = server.createCommandSourceStack();
        for (String cmd : new String[]{
                "gamerule doWeatherCycle false",
                "gamerule doDaylightCycle false",
                "weather clear 1000000",
                "time set noon"}) {
            server.getCommands().performPrefixedCommand(src, cmd);
        }
        StorageOrganizer.LOGGER.info("[StorageOrganizer] Dev harness: weather cleared, cycles off, time pinned to noon.");
    }

    private static void registerCommands(RegisterCommandsEvent event) {
        LiteralArgumentBuilder<CommandSourceStack> test = Commands.literal("rsorgtest")
                .then(Commands.literal("build").executes(ctx -> build(ctx.getSource())))
                .then(Commands.literal("open").executes(ctx -> open(ctx.getSource())))
                .then(Commands.literal("ae2build").executes(ctx -> ae2Build(ctx.getSource())))
                .then(Commands.literal("ae2open").executes(ctx -> ae2Open(ctx.getSource())))
                .then(Commands.literal("editor").executes(ctx -> send(ctx.getSource(), new EditorRequestPayload())))
                .then(Commands.literal("tab")
                        .executes(ctx -> send(ctx.getSource(), new SelectTabPayload("")))
                        .then(Commands.argument("id", com.mojang.brigadier.arguments.StringArgumentType.string())
                                .executes(ctx -> send(ctx.getSource(),
                                        new SelectTabPayload(com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "id"))))))
                // Toggle a resource pack at runtime, so the harness can reproduce a
                // player enabling an AE2 dark-mode pack mid-session (no arg = none).
                .then(Commands.literal("pack")
                        .executes(ctx -> send(ctx.getSource(), new SetPackPayload("")))
                        .then(Commands.argument("id", com.mojang.brigadier.arguments.StringArgumentType.string())
                                .executes(ctx -> send(ctx.getSource(),
                                        new SetPackPayload(com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "id"))))))
                // Change the GUI scale without restarting, so a scale change can be
                // tested the way a player makes it: live, with the screen re-inited
                // rather than constructed fresh. 0 = auto.
                .then(Commands.literal("guiscale")
                        .then(Commands.argument("scale", com.mojang.brigadier.arguments.IntegerArgumentType.integer(0, 8))
                                .executes(ctx -> send(ctx.getSource(),
                                        new SetGuiScalePayload(com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "scale"))))));
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
