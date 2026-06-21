package nl.ljack2k.ae2organizer.jei;

import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.registration.IGuiHandlerRegistration;
import net.minecraft.resources.Identifier;
import nl.ljack2k.ae2organizer.AE2Organizer;
import nl.ljack2k.ae2organizer.client.gui.TabEditorScreen;

/**
 * Optional JEI integration. Loaded only when JEI is present (JEI scans for
 * {@code @JeiPlugin}). Registers a ghost-ingredient handler so items can be
 * dragged from JEI directly onto the tab editor's icon slot and condition
 * fields.
 */
@JeiPlugin
public class AE2OrganizerJeiPlugin implements IModPlugin {

    @Override
    public Identifier getPluginUid() {
        return Identifier.fromNamespaceAndPath(AE2Organizer.MODID, "jei_plugin");
    }

    @Override
    public void registerGuiHandlers(IGuiHandlerRegistration registration) {
        // Ghost handler: accept items dropped onto the editor's targets.
        registration.addGhostIngredientHandler(TabEditorScreen.class, new EditorGhostHandler());
        // Screen handler: tell JEI the editor's bounds so it shows its item list
        // overlay there (otherwise a non-container screen gets no JEI overlay).
        registration.addGuiScreenHandler(TabEditorScreen.class, EditorGuiProperties::new);
    }
}
