package nl.ljack2k.ae2organizer.backend.ae2.mixin;

import appeng.api.stacks.AEKey;
import appeng.client.gui.me.common.Repo;
import appeng.menu.me.common.GridInventoryEntry;
import nl.ljack2k.ae2organizer.backend.ae2.RepoFilterBridge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import java.util.ArrayList;
import java.util.Collection;

/**
 * Injects the active tab's filter into AE2's client-side view pipeline.
 * <p>
 * {@code Repo.updateView()} routes every candidate entry — in both the
 * full-rebuild and paused-incremental branches — through the single private
 * funnel {@code addEntriesToView(Collection)}, before sorting. We shrink the
 * incoming collection at HEAD, so our predicate AND-combines with AE2's own
 * search box (whose per-entry predicate runs later inside the same method).
 * <p>
 * The active predicate lives in {@link RepoFilterBridge} (a static holder the
 * client pushes into, mirroring RS's {@code GridFilterBridge}); it applies a
 * {@code Predicate<ItemStack>} — a non-item {@link AEKey} is tested as an empty
 * stack, so a tab hides it. {@code remap = false}: AE2 ships official names in
 * its NeoForge jar. To re-filter when the active tab changes, the client calls
 * {@code Repo#updateView()}.
 */
@Mixin(value = Repo.class, remap = false)
public abstract class RepoMixin {

    @ModifyVariable(method = "addEntriesToView", at = @At("HEAD"), argsOnly = true)
    private Collection<GridInventoryEntry> ae2organizer$filterEntries(Collection<GridInventoryEntry> in) {
        if (in == null || in.isEmpty() || !RepoFilterBridge.hasFilter()) {
            return in;
        }
        ArrayList<GridInventoryEntry> out = new ArrayList<>(in.size());
        for (GridInventoryEntry entry : in) {
            AEKey what = entry.getWhat();
            if (what != null && RepoFilterBridge.accepts(what)) {
                out.add(entry);
            }
        }
        return out;
    }
}
