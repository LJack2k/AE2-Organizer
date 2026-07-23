package nl.ljack2k.ae2organizer.jei;

import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.registration.IGuiHandlerRegistration;
import mezz.jei.api.runtime.IIngredientFilter;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.resources.ResourceLocation;
import nl.ljack2k.ae2organizer.TerminalOrganizer;
import nl.ljack2k.ae2organizer.client.ViewerSync;
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
 * fields, and a screen handler so JEI draws its list beside the (non-container)
 * editor. Also wires {@link ViewerSync} so tab selection updates JEI's search.
 */
@JeiPlugin
public class TerminalOrganizerJeiPlugin implements IModPlugin {

    @Override
    public ResourceLocation getPluginUid() {
        return ResourceLocation.fromNamespaceAndPath(TerminalOrganizer.MODID, "jei_plugin");
    }

    @Override
    public void registerGuiHandlers(IGuiHandlerRegistration registration) {
        registration.addGhostIngredientHandler(TabEditorScreen.class, new EditorGhostHandler());
        registration.addGuiScreenHandler(TabEditorScreen.class, EditorGuiProperties::new);
    }

    @Override
    public void onRuntimeAvailable(IJeiRuntime runtime) {
        IIngredientFilter filter = runtime.getIngredientFilter();
        ViewerSync.setHandler(tab -> {
            String search = buildJeiFilter(tab);
            if (search != null) {
                filter.setFilterText(search);
            }
        });
    }

    @Override
    public void onRuntimeUnavailable() {
        ViewerSync.setHandler(null);
    }

    /**
     * Translates a tab's conditions to a JEI filter string, mirroring the tab's
     * match logic so JEI shows the same items as the grid.
     * <p>
     * Semantics match {@link Tab#toPredicate()}: positives combine by mode
     * (ANY → OR, ALL → AND), and negated conditions are exclusions AND-combined
     * on top. JEI's grammar splits on {@code |} at the top level (OR) with no
     * parentheses, and a {@code -} token prefix excludes within a chunk. So we
     * <em>distribute</em> the exclusions into every OR branch:
     * <ul>
     *   <li>ALL: {@code p1 p2 -n1}</li>
     *   <li>ANY: {@code p1 -n1 | p2 -n1}</li>
     *   <li>only exclusions: {@code -n1 -n2} (everything except)</li>
     * </ul>
     * Returns {@code ""} for the "All" pseudo-tab (clears JEI's search) and
     * {@code null} when no condition is translatable. Note {@code COMPONENT}
     * conditions can't be expressed in JEI's grammar; a negated component is
     * therefore dropped, so JEI may show slightly more than the grid.
     */
    @Nullable
    private static String buildJeiFilter(@Nullable Tab tab) {
        if (tab == null) return "";
        List<Condition> conditions = tab.conditions();
        if (conditions.isEmpty()) return "";

        List<String> positives = new ArrayList<>();
        List<String> exclusions = new ArrayList<>();
        for (Condition condition : conditions) {
            String token = conditionToJei(condition);
            if (token == null || token.isBlank()) continue;
            if (condition.negate()) {
                exclusions.add("-" + token);
            } else {
                positives.add(token);
            }
        }
        if (positives.isEmpty() && exclusions.isEmpty()) return null;

        String exclusionSuffix = String.join(" ", exclusions);
        if (positives.isEmpty()) {
            return exclusionSuffix;
        }
        if (tab.mode() == MatchMode.ALL) {
            List<String> all = new ArrayList<>(positives);
            all.addAll(exclusions);
            return String.join(" ", all);
        }
        List<String> branches = new ArrayList<>(positives.size());
        for (String positive : positives) {
            branches.add(exclusions.isEmpty() ? positive : positive + " " + exclusionSuffix);
        }
        return String.join(" | ", branches);
    }

    @Nullable
    private static String conditionToJei(Condition condition) {
        return switch (condition.type()) {
            case MOD -> "@" + ((ModCondition) condition).modId();
            case TAG -> "#" + ((TagCondition) condition).tagId().getPath();
            // Quote so a multi-word name stays one phrase token (mirrors the grid's
            // substring match); unquoted it would split into ANDed words.
            case TEXT -> {
                String text = ((TextCondition) condition).text().trim();
                yield text.isEmpty() ? null : (text.contains(" ") ? "\"" + text + "\"" : text);
            }
            case COMPONENT -> null;
        };
    }
}
