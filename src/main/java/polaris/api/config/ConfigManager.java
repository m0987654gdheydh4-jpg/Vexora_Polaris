package polaris.api.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;
import polaris.api.config.exception.ConfigException;
import polaris.api.events.annotation.SubscribeEvent;
import polaris.api.events.impl.TickEvent;
import polaris.api.module.ModuleManager;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

public final class ConfigManager {
    public static final String ROOT_FOLDER = "Polaris";
    public static final String USER_CONFIG_FOLDER = "config";
    public static final String SYSTEM_FOLDER = "system";
    public static final String CONFIG_EXTENSION = ".polaris";
    private static final long AUTO_SAVE_DELAY_MS = 1000L;
    private static final long AUTO_SAVE_RETRY_DELAY_MS = 5000L;

    private final Gson gson = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private final Path systemDirectory;
    private final Path modulesFile;
    private final ModuleConfigStorage moduleStorage;
    private boolean dirty;
    private long dirtySince;

    public ConfigManager(ModuleManager moduleManager) {
        this.systemDirectory = systemDirectory();
        this.modulesFile = systemDirectory.resolve("modules" + CONFIG_EXTENSION);
        this.moduleStorage = new ModuleConfigStorage(moduleManager);
    }

    public static Path rootDirectory() {
        return FabricLoader.getInstance().getGameDir().resolve(ROOT_FOLDER);
    }

    public static Path userConfigDirectory() {
        return rootDirectory().resolve(USER_CONFIG_FOLDER);
    }

    public static Path systemDirectory() {
        return rootDirectory().resolve(SYSTEM_FOLDER);
    }

    public static Path legacyDirectory() {
        return FabricLoader.getInstance().getConfigDir().resolve("cataclysm");
    }

    public void init() {
        try {
            Files.createDirectories(userConfigDirectory());
            Files.createDirectories(systemDirectory);
        } catch (IOException exception) {
            throw new ConfigException("Failed to create config directories under: " + rootDirectory(), exception);
        }
    }

    public void loadAll() {
        loadModules();
        
        try {
            polaris.api.module.impl.visual.Hud hud = polaris.api.module.impl.visual.Hud.getInstance();
            if (hud != null) {
                polaris.theme.ThemeManager.get().loadFromHud(hud);
            }
        } catch (Throwable throwable) {
            
            
            System.err.println("[Polaris] Failed to rebuild theme from HUD settings: " + throwable);
            throwable.printStackTrace();
        }
        dirty = false;
    }

    public void saveAll() {
        saveModules();
        dirty = false;
    }

    public List<String> listUserConfigs() {
        try {
            Files.createDirectories(userConfigDirectory());
            List<String> result = new ArrayList<>();
            try (Stream<Path> paths = Files.list(userConfigDirectory())) {
                paths.filter(Files::isRegularFile)
                        .map(Path::getFileName)
                        .map(Path::toString)
                        .filter(name -> name.endsWith(CONFIG_EXTENSION))
                        .map(name -> name.substring(0, name.length() - CONFIG_EXTENSION.length()))
                        .sorted(String.CASE_INSENSITIVE_ORDER)
                        .forEach(result::add);
            }
            return result;
        } catch (IOException exception) {
            return List.of();
        }
    }

    public boolean savePreset(String name) {
        String normalized = normalizePresetName(name);
        if (normalized == null) {
            return false;
        }
        saveAll();
        Path target = userConfigDirectory().resolve(normalized + CONFIG_EXTENSION);
        try {
            Files.createDirectories(userConfigDirectory());
            Files.copy(modulesFile, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            return true;
        } catch (IOException exception) {
            return false;
        }
    }

    public boolean loadPreset(String name) {
        String normalized = normalizePresetName(name);
        if (normalized == null) {
            return false;
        }
        Path source = userConfigDirectory().resolve(normalized + CONFIG_EXTENSION);
        if (!Files.exists(source)) {
            return false;
        }
        try {
            Files.createDirectories(systemDirectory);
            Files.copy(source, modulesFile, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            loadAll();
            return true;
        } catch (IOException exception) {
            return false;
        }
    }

    public boolean deletePreset(String name) {
        String normalized = normalizePresetName(name);
        if (normalized == null) {
            return false;
        }
        Path source = userConfigDirectory().resolve(normalized + CONFIG_EXTENSION);
        try {
            return Files.deleteIfExists(source);
        } catch (IOException exception) {
            return false;
        }
    }

    private String normalizePresetName(String name) {
        if (name == null) {
            return null;
        }
        String normalized = name.trim();
        if (normalized.isEmpty()) {
            return null;
        }
        normalized = normalized.replaceAll("[\\\\/:*?\"<>|]", "_");
        return normalized.isEmpty() ? null : normalized;
    }

    public void markDirty() {
        if (!dirty) {
            dirtySince = System.currentTimeMillis();
        }
        dirty = true;
    }

    @SubscribeEvent
    private void onTick(TickEvent.Post event) {
        if (dirty && System.currentTimeMillis() - dirtySince >= AUTO_SAVE_DELAY_MS) {
            try {
                saveAll();
            } catch (Exception exception) {
                
                
                
                dirtySince = System.currentTimeMillis() + AUTO_SAVE_RETRY_DELAY_MS - AUTO_SAVE_DELAY_MS;
                System.err.println("[Polaris] Auto-save failed, retrying in "
                        + (AUTO_SAVE_RETRY_DELAY_MS / 1000L) + "s: " + exception.getMessage());
                exception.printStackTrace();
            }
        }
    }

    private void loadModules() {
        Path file = resolveModulesFileForRead();
        if (!Files.exists(file)) {
            saveModules();
            return;
        }

        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            moduleStorage.load(root);
        } catch (Exception exception) {
            
            System.err.println("[Polaris] Failed to load module config (" + file + "): " + exception.getMessage());
            exception.printStackTrace();
        }
    }

    private void saveModules() {
        try {
            Files.createDirectories(systemDirectory);
            
            
            Path temporary = modulesFile.resolveSibling(modulesFile.getFileName() + ".tmp");
            try (Writer writer = Files.newBufferedWriter(temporary, StandardCharsets.UTF_8)) {
                gson.toJson(moduleStorage.save(), writer);
            }
            try {
                Files.move(temporary, modulesFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, modulesFile, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException exception) {
            throw new ConfigException("Failed to save module config: " + modulesFile, exception);
        }
    }

    private Path resolveModulesFileForRead() {
        if (Files.exists(modulesFile)) {
            return modulesFile;
        }

        Path legacyJson = legacyDirectory().resolve("modules.json");
        if (Files.exists(legacyJson)) {
            return legacyJson;
        }

        return modulesFile;
    }
}

