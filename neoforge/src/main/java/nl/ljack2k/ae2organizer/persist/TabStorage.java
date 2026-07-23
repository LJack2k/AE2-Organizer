package nl.ljack2k.ae2organizer.persist;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.loading.FMLPaths;
import nl.ljack2k.ae2organizer.TerminalOrganizer;
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
 * A file without {@code windows} is migrated on load: a single
 * {@link FilterWindow#MAIN_ID main} window is synthesised carrying any legacy
 * {@code settings.showTabLabels}/{@code tabScale} presentation values, and every
 * tab keeps its default {@code window = "main"} assignment.
 */
public final class TabStorage {
    private TabStorage() {}

    private static final int CURRENT_VERSION = 2;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /** Everything persisted in the config file. */
    public record StoredData(Settings settings, List<FilterWindow> windows, List<Tab> tabs,
                             Map<String, String> terminalNames) {}

    private static Path file(String fileName) {
        return FMLPaths.CONFIGDIR.get().resolve("ae2organizer").resolve(fileName);
    }

    public static StoredData load(String fileName) {
        Path path = file(fileName);
        if (!Files.exists(path)) {
            TerminalOrganizer.LOGGER.info("[TerminalOrganizer] No {} found — seeding default tabs.", fileName);
            return new StoredData(Settings.DEFAULT, List.of(defaultWindow()), defaults(), new HashMap<>());
        }
        try {
            JsonElement root = JsonParser.parseString(Files.readString(path));
            JsonObject obj = root.isJsonObject() ? root.getAsJsonObject() : new JsonObject();

            Settings settings = Settings.DEFAULT;
            if (obj.has("settings")) {
                settings = Settings.CODEC.parse(JsonOps.INSTANCE, obj.get("settings"))
                        .resultOrPartial(err -> TerminalOrganizer.LOGGER.error("[TerminalOrganizer] Bad settings: {}", err))
                        .orElse(Settings.DEFAULT);
            }

            List<Tab> tabs;
            JsonElement tabsElement = obj.get("tabs");
            if (tabsElement == null) {
                tabs = defaults();
            } else {
                tabs = Tab.CODEC.listOf().parse(JsonOps.INSTANCE, tabsElement)
                        .resultOrPartial(err -> TerminalOrganizer.LOGGER.error("[TerminalOrganizer] Bad tab: {}", err))
                        .<List<Tab>>map(ArrayList::new)
                        .orElseGet(TabStorage::defaults);
            }

            List<FilterWindow> windows;
            JsonElement windowsElement = obj.get("windows");
            if (windowsElement == null) {
                windows = new ArrayList<>();
                windows.add(migratedWindow(obj.getAsJsonObject("settings")));
            } else {
                windows = FilterWindow.CODEC.listOf().parse(JsonOps.INSTANCE, windowsElement)
                        .resultOrPartial(err -> TerminalOrganizer.LOGGER.error("[TerminalOrganizer] Bad window: {}", err))
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
            TerminalOrganizer.LOGGER.error("[TerminalOrganizer] Could not read tabs.json — using defaults.", e);
            return new StoredData(Settings.DEFAULT, List.of(defaultWindow()), defaults(), new HashMap<>());
        }
    }

    public static void save(String fileName, Settings settings, List<FilterWindow> windows, List<Tab> tabs,
                            Map<String, String> terminalNames) {
        Path path = file(fileName);
        try {
            JsonElement windowsElement = FilterWindow.CODEC.listOf().encodeStart(JsonOps.INSTANCE, windows)
                    .resultOrPartial(err -> TerminalOrganizer.LOGGER.error("[TerminalOrganizer] Failed to encode windows: {}", err))
                    .orElseThrow(() -> new IllegalStateException("window encoding failed"));
            JsonElement tabsElement = Tab.CODEC.listOf().encodeStart(JsonOps.INSTANCE, tabs)
                    .resultOrPartial(err -> TerminalOrganizer.LOGGER.error("[TerminalOrganizer] Failed to encode tabs: {}", err))
                    .orElseThrow(() -> new IllegalStateException("tab encoding failed"));
            JsonElement settingsElement = Settings.CODEC.encodeStart(JsonOps.INSTANCE, settings)
                    .resultOrPartial(err -> TerminalOrganizer.LOGGER.error("[TerminalOrganizer] Failed to encode settings: {}", err))
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
            Path tmp = path.resolveSibling(fileName + ".tmp");
            Files.writeString(tmp, GSON.toJson(out));
            Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (Exception e) {
            TerminalOrganizer.LOGGER.error("[TerminalOrganizer] Could not write tabs.json.", e);
        }
    }

    private static FilterWindow defaultWindow() {
        return FilterWindow.createDefault(false, FilterWindow.DEFAULT_SCALE);
    }

    /** Build the single window for a legacy/window-less file, honoring any legacy presentation fields. */
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
