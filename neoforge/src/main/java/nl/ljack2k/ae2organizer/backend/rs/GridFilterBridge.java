package nl.ljack2k.ae2organizer.backend.rs;

import com.refinedmods.refinedstorage.common.api.grid.view.GridResource;
import com.refinedmods.refinedstorage.common.grid.view.ItemGridResource;
import net.minecraft.world.item.ItemStack;

import java.util.function.Predicate;

/**
 * The seam between the client-side active tab and RS2's grid filter mixin.
 * <p>
 * Deliberately common-safe: it references only RS + Minecraft types that exist
 * on both the client and the dedicated server, and it never touches
 * {@code Minecraft}/client-only code. The client (see {@code TabManager}) pushes
 * the active tab's predicate in via {@link #setActiveFilter}; the mixin
 * ({@code AbstractGridContainerMenuMixin}) reads it through {@link #accepts} for
 * every grid resource. On a server the predicate stays {@code null}, so
 * {@link #accepts} is a no-op and the server never classloads client code.
 * <p>
 * The predicate is over an {@link ItemStack}; non-item grid resources are tested
 * as {@link ItemStack#EMPTY} (this port filters items only).
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

    /** Whether the grid should currently display the given resource. */
    public static boolean accepts(GridResource resource) {
        Predicate<ItemStack> predicate = active;
        if (predicate == null) {
            return true;
        }
        ItemStack stack = resource instanceof ItemGridResource item ? item.getItemStack() : ItemStack.EMPTY;
        return predicate.test(stack);
    }
}
