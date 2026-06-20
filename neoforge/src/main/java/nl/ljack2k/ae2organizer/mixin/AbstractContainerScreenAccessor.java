package nl.ljack2k.ae2organizer.mixin;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Exposes the container panel dimensions so the tab bar can align itself to the
 * left edge of the terminal and size its visible cells to the panel height.
 */
@Mixin(AbstractContainerScreen.class)
public interface AbstractContainerScreenAccessor {
    @Accessor("imageHeight")
    int ae2organizer$getImageHeight();

    @Accessor("imageWidth")
    int ae2organizer$getImageWidth();
}
