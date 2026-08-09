package nl.ljack2k.ae2organizer.backend.ae2;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import net.minecraft.world.item.ItemStack;

import java.util.function.Predicate;

/**
 * The seam between the client-side active tab and AE2's {@code Repo} filter mixin
 * ({@code RepoMixin}). Mirrors RS's {@code GridFilterBridge}: the client pushes
 * the active tab's {@link Predicate} of {@link ItemStack} in via
 * {@link #setActiveFilter}, and the mixin reads it per entry through
 * {@link #accepts}.
 * <p>
 * The predicate is over an {@link ItemStack}; a non-item {@link AEKey} (fluid,
 * gas, …) is tested as {@link ItemStack#EMPTY} so this item-only filter simply
 * hides it when a tab is active.
 */
public final class RepoFilterBridge {

    /** {@code null} means "no active tab" → show everything (the "All" tab). */
    private static volatile Predicate<ItemStack> active = null;

    private RepoFilterBridge() {
    }

    public static void setActiveFilter(Predicate<ItemStack> predicate) {
        active = predicate;
    }

    public static void clear() {
        active = null;
    }

    public static boolean hasFilter() {
        return active != null;
    }

    /** Whether the terminal should currently display the given resource. */
    public static boolean accepts(AEKey what) {
        Predicate<ItemStack> predicate = active;
        if (predicate == null) {
            return true;
        }
        ItemStack stack = what instanceof AEItemKey itemKey ? itemKey.getReadOnlyStack() : ItemStack.EMPTY;
        return predicate.test(stack);
    }
}
