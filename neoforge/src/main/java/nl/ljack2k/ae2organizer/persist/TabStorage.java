package nl.ljack2k.ae2organizer.persist;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.loading.FMLPaths;
import nl.ljack2k.ae2organizer.AE2Organizer;
import nl.ljack2k.ae2organizer.filter.ComponentCondition;
import nl.ljack2k.ae2organizer.filter.ComponentMatch;
import nl.ljack2k.ae2organizer.filter.FilterWindow;
import nl.ljack2k.ae2organizer.filter.MatchMode;
import nl.ljack2k.ae2organizer.filter.Settings;
import nl.ljack2k.ae2organizer.filter.Tab;
import nl.ljack2k.ae2organizer.filter.TagCondition;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Reads/writes the per-client config at {@code config/ae2organizer/tabs.json}.
 * Format (v2): {@code {"version":2,"settings":{...},"windows":[...],"tabs":[...]}}.
 * Missing pieces fall back to defaults rather than crashing the client.
 * <p>
 * v1 files (no {@code windows}, with {@code settings.showTabLabels}/{@code tabScale})
 * are migrated on load: a single {@link FilterWindow#MAIN_ID main} window is
 * synthesised carrying those legacy presentation values, and every tab keeps its
 * default {@code window = "main"} assignment.
 */
public final class TabStorage {
    private TabStorage() {}

    private static final int CURRENT_VERSION = 2;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /** Everything persisted in the config file. */
    public record StoredData(Settings settings, List<FilterWindow> windows, List<Tab> tabs,
                             Map<String, String> terminalNames) {}

    private static Path file() {
        return FMLPaths.CONFIGDIR.get().resolve("ae2organizer").resolve("tabs.json");
    }

    public static StoredData load() {
        Path path = file();
        if (!Files.exists(path)) {
            AE2Organizer.LOGGER.info("[AE2Organizer] No tabs.json found — seeding default tabs.");
            return new StoredData(Settings.DEFAULT, List.of(defaultWindow()), defaults(), new HashMap<>());
        }
        try {
            JsonElement root = JsonParser.parseString(Files.readString(path));
            JsonObject obj = root.isJsonObject() ? root.getAsJsonObject() : new JsonObject();

            Settings settings = Settings.DEFAULT;
            if (obj.has("settings")) {
                settings = Settings.CODEC.parse(JsonOps.INSTANCE, obj.get("settings"))
                        .resultOrPartial(err -> AE2Organizer.LOGGER.error("[AE2Organizer] Bad settings: {}", err))
                        .orElse(Settings.DEFAULT);
            }

            List<Tab> tabs;
            JsonElement tabsElement = obj.get("tabs");
            if (tabsElement == null) {
                tabs = defaults();
            } else {
                tabs = Tab.CODEC.listOf().parse(JsonOps.INSTANCE, tabsElement)
                        .resultOrPartial(err -> AE2Organizer.LOGGER.error("[AE2Organizer] Bad tab: {}", err))
                        .<List<Tab>>map(ArrayList::new)
                        .orElseGet(TabStorage::defaults);
            }

            List<FilterWindow> windows;
            JsonElement windowsElement = obj.get("windows");
            if (windowsElement == null) {
                // Migrate a v1 file: one window carrying the legacy label/scale settings.
                windows = new ArrayList<>();
                windows.add(migratedWindow(obj.getAsJsonObject("settings")));
            } else {
                windows = FilterWindow.CODEC.listOf().parse(JsonOps.INSTANCE, windowsElement)
                        .resultOrPartial(err -> AE2Organizer.LOGGER.error("[AE2Organizer] Bad window: {}", err))
                        .<List<FilterWindow>>map(ArrayList::new)
                        .orElseGet(() -> new ArrayList<>(List.of(defaultWindow())));
            }
            if (windows.isEmpty()) {
                windows.add(defaultWindow());
            }

            Map<String, String> terminalNames = new HashMap<>();
            if (obj.has("terminalNames") && obj.get("terminalNames").isJsonObject()) {
                for (var e : obj.getAsJsonObject("terminalNames").entrySet()) {
                    try {
                        terminalNames.put(e.getKey(), e.getValue().getAsString());
                    } catch (Exception ignored) {
                        // skip malformed entry
                    }
                }
            }
            return new StoredData(settings, windows, tabs, terminalNames);
        } catch (Exception e) {
            AE2Organizer.LOGGER.error("[AE2Organizer] Could not read tabs.json — using defaults.", e);
            return new StoredData(Settings.DEFAULT, List.of(defaultWindow()), defaults(), new HashMap<>());
        }
    }

    public static void save(Settings settings, List<FilterWindow> windows, List<Tab> tabs,
                            Map<String, String> terminalNames) {
        Path path = file();
        try {
            JsonElement windowsElement = FilterWindow.CODEC.listOf().encodeStart(JsonOps.INSTANCE, windows)
                    .resultOrPartial(err -> AE2Organizer.LOGGER.error("[AE2Organizer] Failed to encode windows: {}", err))
                    .orElseThrow(() -> new IllegalStateException("window encoding failed"));
            JsonElement tabsElement = Tab.CODEC.listOf().encodeStart(JsonOps.INSTANCE, tabs)
                    .resultOrPartial(err -> AE2Organizer.LOGGER.error("[AE2Organizer] Failed to encode tabs: {}", err))
                    .orElseThrow(() -> new IllegalStateException("tab encoding failed"));
            JsonElement settingsElement = Settings.CODEC.encodeStart(JsonOps.INSTANCE, settings)
                    .resultOrPartial(err -> AE2Organizer.LOGGER.error("[AE2Organizer] Failed to encode settings: {}", err))
                    .orElseThrow(() -> new IllegalStateException("settings encoding failed"));

            JsonObject namesObj = new JsonObject();
            for (Map.Entry<String, String> e : terminalNames.entrySet()) {
                namesObj.addProperty(e.getKey(), e.getValue());
            }

            JsonObject out = new JsonObject();
            out.addProperty("version", CURRENT_VERSION);
            out.add("settings", settingsElement);
            out.add("windows", windowsElement);
            out.add("tabs", tabsElement);
            out.add("terminalNames", namesObj);

            Files.createDirectories(path.getParent());
            Path tmp = path.resolveSibling("tabs.json.tmp");
            Files.writeString(tmp, GSON.toJson(out));
            Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (Exception e) {
            AE2Organizer.LOGGER.error("[AE2Organizer] Could not write tabs.json.", e);
        }
    }

    private static FilterWindow defaultWindow() {
        return FilterWindow.createDefault(false, FilterWindow.DEFAULT_SCALE);
    }

    /** Build the single window for a migrated v1 file, honoring its legacy presentation fields. */
    private static FilterWindow migratedWindow(JsonObject legacySettings) {
        boolean labels = legacySettings != null && legacySettings.has("showTabLabels")
                && legacySettings.get("showTabLabels").getAsBoolean();
        double scale = FilterWindow.DEFAULT_SCALE;
        if (legacySettings != null && legacySettings.has("tabScale")) {
            try {
                scale = legacySettings.get("tabScale").getAsDouble();
            } catch (Exception ignored) {
                // keep default
            }
        }
        return FilterWindow.createDefault(labels, scale);
    }

    /** A couple of example tabs so the feature is discoverable on first use. */
    public static List<Tab> defaults() {
        List<Tab> tabs = new ArrayList<>();
        tabs.add(new Tab("enchanted", "Enchanted",
                ResourceLocation.withDefaultNamespace("enchanted_book"), MatchMode.ANY,
                List.of(new ComponentCondition(ComponentMatch.ENCHANTED, "", false)), FilterWindow.MAIN_ID));
        tabs.add(new Tab("ingots", "Ingots",
                ResourceLocation.withDefaultNamespace("iron_ingot"), MatchMode.ANY,
                List.of(new TagCondition(ResourceLocation.parse("c:ingots"), false)), FilterWindow.MAIN_ID));
        tabs.add(new Tab("named", "Named",
                ResourceLocation.withDefaultNamespace("name_tag"), MatchMode.ANY,
                List.of(new ComponentCondition(ComponentMatch.HAS_CUSTOM_NAME, "", false)), FilterWindow.MAIN_ID));
        return tabs;
    }
}
