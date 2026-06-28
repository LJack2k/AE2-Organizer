package nl.ljack2k.ae2organizer.mixin;

import appeng.client.gui.me.common.MEStorageScreen;
import appeng.client.gui.me.common.Repo;
import appeng.client.gui.widgets.AETextField;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Exposes internals of AE2's terminal screen we need to interact with.
 */
// remap = false: AE2's classes ship un-obfuscated, so the mixin AP must not try
// to map these fields to SRG (only vanilla targets go through the refmap).
@Mixin(value = MEStorageScreen.class, remap = false)
public interface MEStorageScreenAccessor {
    @Accessor("repo")
    Repo ae2organizer$getRepo();

    @Accessor("searchField")
    AETextField ae2organizer$getSearchField();
}
