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
import nl.ljack2k.ae2organizer.filter.MatchMode;
import nl.ljack2k.ae2organizer.filter.Settings;
import nl.ljack2k.ae2organizer.filter.Tab;
import nl.ljack2k.ae2organizer.filter.TagCondition;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads/writes the per-client config at {@code config/ae2organizer/tabs.json}.
 * Format: {@code {"version":1,"settings":{...},"tabs":[...]}}. Missing pieces
 * fall back to defaults rather than crashing the client.
 */
public final class TabStorage {
    private TabStorage() {}

    private static final int CURRENT_VERSION = 1;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /** Everything persisted in the config file. */
    public record StoredData(Settings settings, List<Tab> tabs) {}

    private static Path file() {
        return FMLPaths.CONFIGDIR.get().resolve("ae2organizer").resolve("tabs.json");
    }

    public static StoredData load() {
        Path path = file();
        if (!Files.exists(path)) {
            AE2Organizer.LOGGER.info("[AE2Organizer] No tabs.json found — seeding default tabs.");
            return new StoredData(Settings.DEFAULT, defaults());
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
            return new StoredData(settings, tabs);
        } catch (Exception e) {
            AE2Organizer.LOGGER.error("[AE2Organizer] Could not read tabs.json — using defaults.", e);
            return new StoredData(Settings.DEFAULT, defaults());
        }
    }

    public static void save(Settings settings, List<Tab> tabs) {
        Path path = file();
        try {
            JsonElement tabsElement = Tab.CODEC.listOf().encodeStart(JsonOps.INSTANCE, tabs)
                    .resultOrPartial(err -> AE2Organizer.LOGGER.error("[AE2Organizer] Failed to encode tabs: {}", err))
                    .orElseThrow(() -> new IllegalStateException("tab encoding failed"));
            JsonElement settingsElement = Settings.CODEC.encodeStart(JsonOps.INSTANCE, settings)
                    .resultOrPartial(err -> AE2Organizer.LOGGER.error("[AE2Organizer] Failed to encode settings: {}", err))
                    .orElseThrow(() -> new IllegalStateException("settings encoding failed"));

            JsonObject out = new JsonObject();
            out.addProperty("version", CURRENT_VERSION);
            out.add("settings", settingsElement);
            out.add("tabs", tabsElement);

            Files.createDirectories(path.getParent());
            Path tmp = path.resolveSibling("tabs.json.tmp");
            Files.writeString(tmp, GSON.toJson(out));
            Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (Exception e) {
            AE2Organizer.LOGGER.error("[AE2Organizer] Could not write tabs.json.", e);
        }
    }

    /** A couple of example tabs so the feature is discoverable on first use. */
    public static List<Tab> defaults() {
        List<Tab> tabs = new ArrayList<>();
        tabs.add(new Tab("enchanted", "Enchanted",
                ResourceLocation.withDefaultNamespace("enchanted_book"), MatchMode.ANY,
                List.of(new ComponentCondition(ComponentMatch.ENCHANTED, ""))));
        tabs.add(new Tab("ingots", "Ingots",
                ResourceLocation.withDefaultNamespace("iron_ingot"), MatchMode.ANY,
                List.of(new TagCondition(ResourceLocation.parse("c:ingots")))));
        tabs.add(new Tab("named", "Named",
                ResourceLocation.withDefaultNamespace("name_tag"), MatchMode.ANY,
                List.of(new ComponentCondition(ComponentMatch.HAS_CUSTOM_NAME, ""))));
        return tabs;
    }
}
