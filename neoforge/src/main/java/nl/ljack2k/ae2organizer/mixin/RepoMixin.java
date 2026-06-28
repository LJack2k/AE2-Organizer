package nl.ljack2k.ae2organizer.mixin;

import appeng.api.stacks.AEKey;
import appeng.client.gui.me.common.Repo;
import appeng.menu.me.common.GridInventoryEntry;
import nl.ljack2k.ae2organizer.filter.TabFilterHolder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.function.Predicate;

/**
 * Injects the active tab's filter into AE2's client-side view pipeline.
 * <p>
 * {@code Repo.updateView()} routes every candidate entry — in both the
 * full-rebuild and paused-incremental branches — through the single private
 * funnel {@code addEntriesToView(Collection)}, before sorting. We shrink the
 * incoming collection at HEAD, so our predicate AND-combines with AE2's own
 * search box (whose per-entry predicate runs later inside the same method).
 * <p>
 * The filter is stored on the {@code Repo} instance via {@link TabFilterHolder}
 * and persists across incremental updates, so newly streamed entries stay
 * filtered too. {@code null} means "show everything".
 */
// remap = false: AE2's classes ship un-obfuscated, so the mixin AP must not try
// to map this target to SRG (only vanilla targets go through the refmap).
@Mixin(value = Repo.class, remap = false)
public abstract class RepoMixin implements TabFilterHolder {

    @Unique
    private Predicate<AEKey> ae2organizer$tabFilter;

    @Override
    @Unique
    public void ae2organizer$setTabFilter(Predicate<AEKey> predicate) {
        this.ae2organizer$tabFilter = predicate;
    }

    @ModifyVariable(method = "addEntriesToView", at = @At("HEAD"), argsOnly = true)
    private Collection<GridInventoryEntry> ae2organizer$filterEntries(Collection<GridInventoryEntry> in) {
        Predicate<AEKey> predicate = this.ae2organizer$tabFilter;
        if (predicate == null || in == null || in.isEmpty()) {
            return in;
        }
        ArrayList<GridInventoryEntry> out = new ArrayList<>(in.size());
        for (GridInventoryEntry entry : in) {
            AEKey what = entry.getWhat();
            if (what != null && predicate.test(what)) {
                out.add(entry);
            }
        }
        return out;
    }
}
