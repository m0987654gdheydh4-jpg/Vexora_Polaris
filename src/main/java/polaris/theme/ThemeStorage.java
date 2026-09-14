package polaris.theme;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.fabricmc.loader.api.FabricLoader;

import java.awt.Color;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;


public final class ThemeStorage {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private ThemeStorage() {
    }

    public static Path customsPath() {
        return FabricLoader.getInstance().getGameDir().resolve("Polaris").resolve("themes").resolve("customs.json");
    }

    public static List<ThemePreset> loadCustoms() {
        Path path = customsPath();
        if (!Files.isRegularFile(path)) {
            return List.of();
        }
        try (Reader reader = Files.newBufferedReader(path)) {
            JsonObject root = GSON.fromJson(reader, JsonObject.class);
            if (root == null || !root.has("presets")) {
                return List.of();
            }
            JsonArray arr = root.getAsJsonArray("presets");
            List<ThemePreset> out = new ArrayList<>();
            for (JsonElement el : arr) {
                if (!el.isJsonObject()) {
                    continue;
                }
                ThemePreset p = fromJson(el.getAsJsonObject());
                if (p != null) {
                    out.add(p);
                }
            }
            return out;
        } catch (Exception ignored) {
            return List.of();
        }
    }

    public static void saveCustoms(List<ThemePreset> customs) {
        Path path = customsPath();
        try {
            Files.createDirectories(path.getParent());
            JsonObject root = new JsonObject();
            JsonArray arr = new JsonArray();
            for (ThemePreset p : customs) {
                if (p == null || p.builtIn()) {
                    continue;
                }
                arr.add(toJson(p));
            }
            root.add("presets", arr);
            try (Writer writer = Files.newBufferedWriter(path)) {
                GSON.toJson(root, writer);
            }
        } catch (Exception ignored) {
        }
    }

    private static JsonObject toJson(ThemePreset p) {
        JsonObject o = new JsonObject();
        o.addProperty("id", p.id());
        o.addProperty("name", p.name());
        ThemeState s = p.state();
        o.addProperty("accent", s.accent.getRGB() & 0xFFFFFF);
        o.addProperty("background", s.background.getRGB() & 0xFFFFFF);
        o.addProperty("style", s.style.name());
        o.addProperty("blurRadius", s.blurRadius);
        o.addProperty("opacity", s.opacity);
        o.addProperty("rounding", s.rounding);
        o.addProperty("glassStrength", s.glassStrength);
        o.addProperty("glassDistortion", s.glassDistortion);
        o.addProperty("shine", s.shine);
        o.addProperty("shineIntensity", s.shineIntensity);
        o.addProperty("glow", s.glow);
        o.addProperty("glowSize", s.glowSize);
        o.addProperty("glowIntensity", s.glowIntensity);
        return o;
    }

    private static ThemePreset fromJson(JsonObject o) {
        try {
            String id = o.has("id") ? o.get("id").getAsString() : null;
            String name = o.has("name") ? o.get("name").getAsString() : "Custom";
            Color accent = new Color(o.get("accent").getAsInt() & 0xFFFFFF);
            Color background = new Color(o.get("background").getAsInt() & 0xFFFFFF);
            ThemeStyle style = ThemeStyle.valueOf(o.get("style").getAsString());
            ThemeState state = new ThemeState(
                    accent,
                    background,
                    Color.WHITE,
                    ThemeState.deriveOutline(accent),
                    style,
                    o.get("blurRadius").getAsFloat(),
                    o.get("opacity").getAsFloat(),
                    o.get("rounding").getAsFloat(),
                    o.get("glassStrength").getAsFloat(),
                    o.get("glassDistortion").getAsFloat(),
                    o.get("shine").getAsBoolean(),
                    o.get("shineIntensity").getAsFloat(),
                    o.get("glow").getAsBoolean(),
                    o.get("glowSize").getAsFloat(),
                    o.get("glowIntensity").getAsFloat()
            );
            return new ThemePreset(id, name, false, state);
        } catch (Exception e) {
            return null;
        }
    }
}
