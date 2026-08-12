package nl.ljack2k.ae2organizer.backend;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * Per-open view of a terminal/grid screen, abstracting the few things the shared
 * tab UI needs from AE2's {@code MEStorageScreen} or RS's {@code AbstractGridScreen}.
 */
public interface ScreenAdapter {

    int guiLeft();

    int guiTop();

    /** GUI image width (AE2 {@code imageWidth} / vanilla {@code getXSize}). */
    int xSize();

    /** GUI image height (AE2 {@code imageHeight} / vanilla {@code getYSize}). */
    int ySize();

    /** The menu's slots — used to dock the tab bar past protruding card slots. */
    List<Slot> slots();

    /** Stable per-terminal key (menu-type id), used for per-terminal placement. */
    String terminalKey();

    /** The screen title (for the visibility list / friendly names). */
    Component title();

    /**
     * The stack on the player's cursor (the menu's client-side carried stack), or
     * {@link ItemStack#EMPTY}. Drives the tab bar's drag-and-drop affordances.
     */
    ItemStack carried();

    /**
     * Re-run the storage view's filter+sort so a tab change is reflected now.
     * AE2: {@code repo.updateView()}; RS: {@code menu.getRepository().sort()}.
     */
    void refilter();

    /**
     * Banks the carried stack back into the player inventory (best effort; any
     * remainder stays on the cursor). Called after a drop on the tab bar so the
     * player isn't left holding the item.
     */
    void returnCarriedToInventory();

    /**
     * Shared implementation of {@link #returnCarriedToInventory()}: returns the
     * menu's carried stack via vanilla container clicks — merge into matching
     * player-inventory stacks first, then empty slots. These are the same
     * server-validated click packets a real click sends, so this stays safe on
     * servers without the mod. Both AE2 and RS menus use vanilla {@link Slot}s
     * over the player {@link net.minecraft.world.entity.player.Inventory} for
     * the inventory rows, which is how the slots are recognized.
     */
    static void returnCarried(AbstractContainerScreen<?> screen) {
        Minecraft mc = Minecraft.getInstance();
        var player = mc.player;
        var gameMode = mc.gameMode;
        if (player == null || gameMode == null) {
            return;
        }
        AbstractContainerMenu menu = screen.getMenu();
        for (boolean mergePass : new boolean[]{true, false}) {
            for (Slot slot : menu.slots) {
                ItemStack carried = menu.getCarried();
                if (carried.isEmpty()) {
                    return;
                }
                if (!(slot.container instanceof net.minecraft.world.entity.player.Inventory)) {
                    continue;
                }
                ItemStack inSlot = slot.getItem();
                boolean target = mergePass
                        ? !inSlot.isEmpty() && inSlot.getCount() < inSlot.getMaxStackSize()
                            && ItemStack.isSameItemSameComponents(inSlot, carried)
                        : inSlot.isEmpty() && slot.mayPlace(carried);
                if (target) {
                    gameMode.handleContainerInput(menu.containerId, slot.index, 0, ContainerInput.PICKUP, player);
                }
            }
        }
    }
}
