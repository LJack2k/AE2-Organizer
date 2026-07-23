package nl.ljack2k.ae2organizer.backend.rs.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.refinedmods.refinedstorage.api.resource.repository.ResourceRepositoryFilter;
import com.refinedmods.refinedstorage.common.api.grid.view.GridResource;
import com.refinedmods.refinedstorage.common.grid.AbstractGridContainerMenu;
import nl.ljack2k.ae2organizer.backend.rs.GridFilterBridge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Hooks RS2's grid filter funnel. {@code AbstractGridContainerMenu#createBaseFilter()}
 * is the single method every filter-establishing path runs through:
 * <ul>
 *   <li>the constructor's initial {@code setFilterAndSort(createBaseFilter())};</li>
 *   <li>{@code onSearchTextChanged}'s {@code setFilterAndSort(andFilter(query, createBaseFilter()))};</li>
 *   <li>view-type / resource-type / sort changes call {@code repository.sort()}, which
 *       re-applies the <em>stored</em> filter — and that stored filter already includes
 *       our wrapper, so the tab filter persists across them.</li>
 * </ul>
 * We wrap the returned {@link ResourceRepositoryFilter} so it AND-combines the active
 * tab's predicate (via {@link GridFilterBridge}) with RS's own search box. This mirrors
 * AE2Organizer's {@code @ModifyVariable} on {@code Repo#addEntriesToView}.
 * <p>
 * {@code remap = false}: RS ships official (unobfuscated) names in its NeoForge jar, so
 * no refmap is needed for RS-owned targets. The bridge lookup is per-resource and cheap;
 * it returns {@code true} unconditionally when no tab is active (and always on a server).
 * <p>
 * To force a re-filter when the active tab changes, the client calls
 * {@code menu.getRepository().sort()} (the analog of AE2's {@code Repo#updateView()}).
 */
@Mixin(value = AbstractGridContainerMenu.class, remap = false)
public class AbstractGridContainerMenuMixin {

    @ModifyReturnValue(method = "createBaseFilter", at = @At("RETURN"))
    private ResourceRepositoryFilter<GridResource> ae2organizer$andTabFilter(ResourceRepositoryFilter<GridResource> original) {
        return (view, resource) -> GridFilterBridge.accepts(resource) && original.test(view, resource);
    }
}
