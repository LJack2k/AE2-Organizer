package nl.ljack2k.ae2organizer.mixin;

import appeng.client.gui.me.common.MEStorageScreen;
import appeng.client.gui.me.common.Repo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Exposes the {@code protected final Repo repo} field of AE2's terminal screen
 * so we can push a tab filter into it from outside.
 */
@Mixin(MEStorageScreen.class)
public interface MEStorageScreenAccessor {
    @Accessor("repo")
    Repo ae2organizer$getRepo();
}
