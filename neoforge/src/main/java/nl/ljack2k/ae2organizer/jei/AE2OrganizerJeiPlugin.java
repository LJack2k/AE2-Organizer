package nl.ljack2k.ae2organizer.jei;

import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.registration.IGuiHandlerRegistration;
import mezz.jei.api.runtime.IIngredientFilter;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.resources.Identifier;
import nl.ljack2k.ae2organizer.AE2Organizer;
import nl.ljack2k.ae2organizer.client.JeiSync;
import nl.ljack2k.ae2organizer.client.gui.TabEditorScreen;
import nl.ljack2k.ae2organizer.filter.Condition;
import nl.ljack2k.ae2organizer.filter.MatchMode;
import nl.ljack2k.ae2organizer.filter.ModCondition;
import nl.ljack2k.ae2organizer.filter.Tab;
import nl.ljack2k.ae2organizer.filter.TagCondition;
import nl.ljack2k.ae2organizer.filter.TextCondition;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Optional JEI integration. Loaded only when JEI is present (JEI scans for
 * {@code @JeiPlugin}). Registers a ghost-ingredient handler so items can be
 * dragged from JEI directly onto the tab editor's icon slot and condition
 * fields. Also wires {@link JeiSync} so tab selection can update JEI's search.
 */
@JeiPlugin
public class AE2OrganizerJeiPlugin implements IModPlugin {

    @Override
    public Identifier getPluginUid() {
        return Identifier.fromNamespaceAndPath(AE2Organizer.MODID, "jei_plugin");
    }

    @Override
    public void registerGuiHandlers(IGuiHandlerRegistration registration) {
        registration.addGhostIngredientHandler(TabEditorScreen.class, new EditorGhostHandler());
        registration.addGuiScreenHandler(TabEditorScreen.class, EditorGuiProperties::new);
    }

    @Override
    public void onRuntimeAvailable(IJeiRuntime runtime) {
        IIngredientFilter filter = runtime.getIngredientFilter();
        JeiSync.setHandler(tab -> {
            String search = buildJeiFilter(tab);
            if (search != null) {
                filter.setFilterText(search);
            }
        });
    }

    @Override
    public void onRuntimeUnavailable() {
        JeiSync.setHandler(null);
    }

    /**
     * Translates a tab's conditions to a JEI filter string, mirroring the tab's
     * own match logic so JEI shows the same items as the terminal: ANY mode joins
     * with {@code " | "} (OR), ALL mode joins with a space (AND).
     * Returns {@code ""} for the "All" pseudo-tab (clears JEI's search) and
     * {@code null} when no condition is translatable.
     */
    @Nullable
    private static String buildJeiFilter(@Nullable Tab tab) {
        if (tab == null) return "";
        List<Condition> conditions = tab.conditions();
        if (conditions.isEmpty()) return "";

        List<String> parts = new ArrayList<>();
        for (Condition condition : conditions) {
            String part = conditionToJei(condition);
            if (part != null && !part.isBlank()) {
                parts.add(part);
            }
        }
        if (parts.isEmpty()) return null;
        // Mirror the tab's match logic: ANY → OR (" | "), ALL → AND (space).
        String separator = tab.mode() == MatchMode.ANY ? " | " : " ";
        return String.join(separator, parts);
    }

    @Nullable
    private static String conditionToJei(Condition condition) {
        return switch (condition.type()) {
            case MOD -> "@" + ((ModCondition) condition).modId();
            case TAG -> "#" + ((TagCondition) condition).tagId().getPath();
            case TEXT -> ((TextCondition) condition).text().trim();
            case COMPONENT -> null;
        };
    }
}
