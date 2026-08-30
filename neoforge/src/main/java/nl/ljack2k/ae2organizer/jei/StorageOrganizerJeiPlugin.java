package nl.ljack2k.ae2organizer.jei;

import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.gui.handlers.IGlobalGuiHandler;
import mezz.jei.api.registration.IGuiHandlerRegistration;
import mezz.jei.api.runtime.IIngredientFilter;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.resources.ResourceLocation;
import nl.ljack2k.ae2organizer.StorageOrganizer;
import nl.ljack2k.ae2organizer.client.ClientEvents;
import nl.ljack2k.ae2organizer.client.ViewerFilterText;
import nl.ljack2k.ae2organizer.client.ViewerSync;
import nl.ljack2k.ae2organizer.client.gui.TabEditorScreen;

import java.util.Collection;

/**
 * Optional JEI integration. Loaded only when JEI is present (JEI scans for
 * {@code @JeiPlugin}). Registers a ghost-ingredient handler so items can be
 * dragged from JEI directly onto the tab editor's icon slot and condition
 * fields, and a screen handler so JEI draws its list beside the (non-container)
 * editor, and a global GUI handler that reports our filter panels as extra areas
 * so JEI's item list wraps around them. Also wires {@link ViewerSync} so tab
 * selection updates JEI's search.
 */
@JeiPlugin
public class StorageOrganizerJeiPlugin implements IModPlugin {

    @Override
    public ResourceLocation getPluginUid() {
        return ResourceLocation.fromNamespaceAndPath(StorageOrganizer.MODID, "jei_plugin");
    }

    @Override
    public void registerGuiHandlers(IGuiHandlerRegistration registration) {
        registration.addGhostIngredientHandler(TabEditorScreen.class, new EditorGhostHandler());
        registration.addGuiScreenHandler(TabEditorScreen.class, EditorGuiProperties::new);
        // Drag-from-JEI onto the tab bars, on the storage screens themselves. One
        // registration per present backend's screen class (the JEI registry keys
        // by class but *combines* every handler matching the screen's hierarchy,
        // so AE2's/RS's own ghost handlers are unaffected). BackendRegistry is
        // populated at client setup, before JEI loads plugins.
        BarGhostHandler barHandler = new BarGhostHandler();
        for (nl.ljack2k.ae2organizer.backend.StorageBackend backend : nl.ljack2k.ae2organizer.backend.BackendRegistry.all()) {
            registerBarHandler(registration, backend.screenClass(), barHandler);
        }
    }

    /** Captures the backend's screen class as {@code T}; the handler itself is typed over plain {@code Screen}. */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static <T extends net.minecraft.client.gui.screens.Screen> void registerBarHandler(
            IGuiHandlerRegistration registration, Class<T> screenClass, BarGhostHandler handler) {
        registration.addGhostIngredientHandler(screenClass, (mezz.jei.api.gui.handlers.IGhostIngredientHandler) handler);
        // Global (not per-screen): our panels can appear over any AE2 terminal or RS
        // grid, including addon terminals we never name. JEI adds these to whatever
        // the host mod already excludes and skips the item slots they cover, so the
        // list wraps around a panel instead of being drawn under it.
        // (IGlobalGuiHandler has no abstract method, so it can't be a lambda target.)
        registration.addGlobalGuiHandler(new IGlobalGuiHandler() {
            @Override
            public Collection<Rect2i> getGuiExtraAreas() {
                return ClientEvents.activeBarBounds();
            }
        });
    }

    @Override
    public void onRuntimeAvailable(IJeiRuntime runtime) {
        IIngredientFilter filter = runtime.getIngredientFilter();
        ViewerSync.setHandler(tab -> {
            String search = ViewerFilterText.build(tab);
            if (search != null) {
                filter.setFilterText(ViewerFilterText.clamp(search));
            }
        });
    }

    @Override
    public void onRuntimeUnavailable() {
        ViewerSync.setHandler(null);
    }
}
