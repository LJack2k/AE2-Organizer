package nl.ljack2k.ae2organizer.persist;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;
import nl.ljack2k.ae2organizer.filter.Condition;
import nl.ljack2k.ae2organizer.filter.FilterWindow;
import nl.ljack2k.ae2organizer.filter.MatchMode;
import nl.ljack2k.ae2organizer.filter.Tab;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Clipboard import/export for a window's tabs — the shareable filter content
 * (name, icon, match mode, conditions), with no window/layout data. The wrapper
 * carries a magic key + version so import can reject unrelated clipboard text.
 */
public final class TabShare {
    private TabShare() {}

    private static final String MAGIC = "ae2organizer";
    private static final String KIND = "tabs";
    private static final String KIND_ALL = "windows";
    private static final int VERSION = 1;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /** A full export: every window (with layout) and all tabs. */
    public record AllData(List<FilterWindow> windows, List<Tab> tabs) {}

    /** Like {@code Tab.CODEC} but with {@code id}/{@code window} optional — they're per-target, not shared. */
    private static final Codec<Tab> SHARE = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.optionalFieldOf("id", "").forGetter(Tab::id),
            Codec.STRING.fieldOf("name").forGetter(Tab::name),
            ResourceLocation.CODEC.optionalFieldOf("icon", Tab.DEFAULT_ICON).forGetter(Tab::icon),
            MatchMode.CODEC.optionalFieldOf("mode", MatchMode.ANY).forGetter(Tab::mode),
            Condition.CODEC.listOf().optionalFieldOf("conditions", List.of()).forGetter(Tab::conditions),
            Codec.STRING.optionalFieldOf("window", "main").forGetter(Tab::window)
    ).apply(i, Tab::new));

    /** Serialize tabs to a shareable JSON string (id/window stripped). */
    public static String export(List<Tab> tabs) {
        JsonArray arr = new JsonArray();
        for (Tab tab : tabs) {
            JsonElement el = SHARE.encodeStart(JsonOps.INSTANCE, tab).result().orElse(null);
            if (el instanceof JsonObject obj) {
                obj.remove("id");
                obj.remove("window");
                arr.add(obj);
            }
        }
        JsonObject root = new JsonObject();
        root.addProperty(MAGIC, KIND);
        root.addProperty("version", VERSION);
        root.add("tabs", arr);
        return GSON.toJson(root);
    }

    /**
     * Parse tabs from clipboard text. Returns empty if the text isn't an
     * AE2Organizer tab export (so import can fail gracefully). Imported tabs keep
     * no id/window — the caller assigns fresh ids and the target window.
     */
    public static Optional<List<Tab>> parse(String text) {
        if (text == null || text.isBlank()) {
            return Optional.empty();
        }
        try {
            JsonElement root = JsonParser.parseString(text);
            if (!root.isJsonObject()) {
                return Optional.empty();
            }
            JsonObject obj = root.getAsJsonObject();
            if (!obj.has(MAGIC) || !KIND.equals(obj.get(MAGIC).getAsString()) || !obj.has("tabs")) {
                return Optional.empty();
            }
            List<Tab> tabs = SHARE.listOf().parse(JsonOps.INSTANCE, obj.get("tabs"))
                    .result().map(ArrayList<Tab>::new).orElse(null);
            return Optional.ofNullable(tabs);
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    /** Serialize every window (full layout) and all tabs to a shareable JSON string. */
    public static String exportAll(List<FilterWindow> windows, List<Tab> tabs) {
        JsonObject root = new JsonObject();
        root.addProperty(MAGIC, KIND_ALL);
        root.addProperty("version", VERSION);
        root.add("windows", FilterWindow.CODEC.listOf().encodeStart(JsonOps.INSTANCE, windows)
                .result().orElseGet(JsonArray::new));
        root.add("tabs", Tab.CODEC.listOf().encodeStart(JsonOps.INSTANCE, tabs)
                .result().orElseGet(JsonArray::new));
        return GSON.toJson(root);
    }

    /** Parse a full export. Empty if the text isn't an AE2Organizer windows export. */
    public static Optional<AllData> parseAll(String text) {
        if (text == null || text.isBlank()) {
            return Optional.empty();
        }
        try {
            JsonElement root = JsonParser.parseString(text);
            if (!root.isJsonObject()) {
                return Optional.empty();
            }
            JsonObject obj = root.getAsJsonObject();
            if (!obj.has(MAGIC) || !KIND_ALL.equals(obj.get(MAGIC).getAsString())
                    || !obj.has("windows") || !obj.has("tabs")) {
                return Optional.empty();
            }
            List<FilterWindow> windows = FilterWindow.CODEC.listOf().parse(JsonOps.INSTANCE, obj.get("windows"))
                    .result().map(ArrayList<FilterWindow>::new).orElse(null);
            List<Tab> tabs = Tab.CODEC.listOf().parse(JsonOps.INSTANCE, obj.get("tabs"))
                    .result().map(ArrayList<Tab>::new).orElse(null);
            if (windows == null || windows.isEmpty() || tabs == null) {
                return Optional.empty();
            }
            return Optional.of(new AllData(windows, tabs));
        } catch (Exception e) {
            return Optional.empty();
        }
    }
}
