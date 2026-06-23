package nl.ljack2k.ae2organizer.mixin;

import appeng.client.gui.me.common.MEStorageScreen;
import appeng.client.gui.me.common.Repo;
import appeng.client.gui.widgets.AETextField;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Exposes internals of AE2's terminal screen we need to interact with.
 */
@Mixin(MEStorageScreen.class)
public interface MEStorageScreenAccessor {
    @Accessor("repo")
    Repo ae2organizer$getRepo();

    @Accessor("searchField")
    AETextField ae2organizer$getSearchField();
}
