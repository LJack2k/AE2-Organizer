package nl.ljack2k.ae2organizer.backend.rslegacy;

import com.refinedmods.refinedstorage.screen.grid.stack.IGridStack;
import com.refinedmods.refinedstorage.screen.grid.stack.ItemGridStack;
import net.minecraft.world.item.ItemStack;

import java.util.function.Predicate;

/**
 * The seam between the client-side active tab and RS 1.12's grid-view mixin.
 * <p>
 * The client (see {@code TabManager}) pushes the active tab's predicate in via
 * {@link #setActiveFilter}; {@code GridViewImplMixin} reads it through
 * {@link #accepts} for every grid stack. With no active tab the predicate is
 * {@code null} and {@link #accepts} is a no-op.
 * <p>
 * The predicate is over an {@link ItemStack}; fluid stacks are tested as
 * {@link ItemStack#EMPTY} (this mod filters items only), which is the same rule
 * the RS2 backend uses on the newer lines.
 */
public final class GridFilterBridge {

    /** {@code null} means "no active tab" → show everything (the "All" tab). */
    private static volatile Predicate<ItemStack> active = null;

    private GridFilterBridge() {
    }

    /** Push the active tab's predicate, or {@code null} to show everything. */
    public static void setActiveFilter(Predicate<ItemStack> predicate) {
        active = predicate;
    }

    public static void clear() {
        active = null;
    }

    /** Whether the grid should currently display the given stack. */
    public static boolean accepts(IGridStack gridStack) {
        Predicate<ItemStack> predicate = active;
        if (predicate == null) {
            return true;
        }
        ItemStack stack = gridStack instanceof ItemGridStack item ? item.getStack() : ItemStack.EMPTY;
        return predicate.test(stack);
    }
}
