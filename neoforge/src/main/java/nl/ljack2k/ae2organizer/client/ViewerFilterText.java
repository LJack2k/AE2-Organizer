package nl.ljack2k.ae2organizer.client;

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
 * Builds the ingredient-viewer search string for a tab, and trims it to what the
 * viewer can actually hold.
 * <p>
 * Deliberately free of any {@code mezz.jei.*} import so the tab editor can ask
 * "would this tab's filter be trimmed?" without classloading JEI - the editor
 * runs whether or not JEI is installed. The JEI plugin is the only caller that
 * actually pushes the result into a viewer.
 */
public final class ViewerFilterText {
    private ViewerFilterText() {}

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
    public static String build(@Nullable Tab tab) {
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

    /**
     * JEI's own search-field cap ({@code GuiTextFieldFilter} calls
     * {@code setMaxLength(128)}). Verified identical on JEI 15.x and 19.x.
     */
    private static final int JEI_MAX_SEARCH_LENGTH = 128;

    /**
     * Trims a filter string to something JEI's search field can actually hold.
     * <p>
     * Handing {@code IIngredientFilter#setFilterText} more than 128 characters
     * <strong>crashes the game with a StackOverflowError</strong>. JEI's
     * {@code FilterTextSource} stores the full string and notifies its listeners,
     * but the listening {@code GuiTextFieldFilter} is an {@code EditBox} capped at
     * 128, so the value it reports back can never equal the value that was set.
     * Each side then re-notifies the other and the two recurse until the stack
     * dies -- roughly 490 frames deep in the report this was diagnosed from.
     * Nothing on JEI's side breaks the loop, so the length has to be bounded here.
     * <p>
     * Cuts on a token boundary rather than mid-token, and drops a trailing token
     * that would be left with an unbalanced quote, since a dangling {@code "} makes
     * JEI's {@code (-?".*?(?:"|$)|\S+)} tokeniser treat the rest of the string as
     * one quoted phrase. A trimmed filter means JEI may show a little more than the
     * grid does -- the same trade-off already accepted for COMPONENT conditions,
     * and strictly better than crashing.
     */
    public static String clamp(String search) {
        if (search.length() <= JEI_MAX_SEARCH_LENGTH) {
            return search;
        }
        // Reserve one character in case the cut lands inside a quoted phrase and we
        // have to close it again.
        String cut = search.substring(0, JEI_MAX_SEARCH_LENGTH - 1);
        // Back off to the last token boundary so we never emit a half word.
        int lastSpace = cut.lastIndexOf(' ');
        if (lastSpace > 0) {
            cut = cut.substring(0, lastSpace);
        }
        cut = cut.strip();
        // Never end on a dangling OR branch or a lone exclusion marker.
        while (cut.endsWith("|") || cut.endsWith("-")) {
            cut = cut.substring(0, cut.length() - 1).strip();
        }
        // An odd number of quotes means we cut inside a quoted phrase. Close it
        // rather than dropping the phrase: a shortened phrase still substring-matches
        // (JEI shows a superset of the grid), whereas dropping the only token would
        // clear the search and show everything.
        if ((cut.chars().filter(c -> c == '"').count() & 1L) == 1L) {
            cut = cut.equals("\"") || cut.equals("-\"") ? "" : cut + "\"";
        }
        return cut;
    }


    /**
     * Whether this tab's filter is too long for the viewer's search field and will
     * therefore be trimmed by {@link #clamp}. Drives the editor's warning, so the
     * person sees it while composing the conditions rather than wondering later why
     * the viewer shows more than the grid.
     */
    public static boolean exceedsLimit(@Nullable Tab tab) {
        String built = build(tab);
        return built != null && built.length() > JEI_MAX_SEARCH_LENGTH;
    }

    @Nullable
    private static String conditionToJei(Condition condition) {
        return switch (condition.type()) {
            case MOD -> "@" + ((ModCondition) condition).modId();
            // JEI 15.x (the 1.20.1 line) prefixes tags with '$'; JEI 19.x uses '#'.
            case TAG -> "$" + ((TagCondition) condition).tagId().getPath();
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
