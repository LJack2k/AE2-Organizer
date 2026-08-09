package nl.ljack2k.ae2organizer.backend.rslegacy.mixin;

import com.refinedmods.refinedstorage.screen.grid.stack.IGridStack;
import com.refinedmods.refinedstorage.screen.grid.view.GridViewImpl;
import nl.ljack2k.ae2organizer.backend.rslegacy.GridFilterBridge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.function.Predicate;

/**
 * Hooks RS 1.12's grid filter funnel. {@code GridViewImpl#getActiveFilters()} builds
 * the predicate that {@code forceSort()} applies to the whole stack map before sorting:
 * <pre>map.values().stream().filter(getActiveFilters()).sorted(getActiveSort())…</pre>
 * so AND-ing the active tab's predicate into its return value filters the entire grid
 * view, combined with RS's own search box and its craftable/view-type filters. This is
 * the 1.12 analogue of the RS2 line's {@code createBaseFilter} hook and of AE2's
 * {@code Repo#addEntriesToView}.
 * <p>
 * {@code remap = false}: RS ships unobfuscated names, so no refmap entry is needed for
 * RS-owned targets. The bridge lookup is per-stack and cheap — it returns {@code true}
 * unconditionally when no tab is active.
 * <p>
 * A tab change re-filters by calling {@code view.forceSort()} (not {@code sort()}, which
 * no-ops unless the screen currently allows sorting).
 * <p>
 * Plain {@code @Inject} + {@code setReturnValue} rather than MixinExtras'
 * {@code @ModifyReturnValue} (which the RS2 mixin uses): MixinExtras isn't on the
 * userdev compile classpath for Forge 1.20.1, and this needs no extra dependency.
 */
@Mixin(value = GridViewImpl.class, remap = false)
public class GridViewImplMixin {

    @Inject(method = "getActiveFilters", at = @At("RETURN"), cancellable = true)
    private void ae2organizer$andTabFilter(CallbackInfoReturnable<Predicate<IGridStack>> cir) {
        Predicate<IGridStack> original = cir.getReturnValue();
        if (original == null) {
            return;
        }
        cir.setReturnValue(stack -> GridFilterBridge.accepts(stack) && original.test(stack));
    }
}
