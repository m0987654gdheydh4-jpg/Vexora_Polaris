package polaris.api.config;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import polaris.api.module.Module;
import polaris.api.module.ModuleManager;
import polaris.api.module.impl.misc.ClickGuiModule;
import polaris.api.settings.Setting;
import polaris.api.settings.bind.KeyBind;

public final class ModuleConfigStorage {
    private final ModuleManager moduleManager;

    public ModuleConfigStorage(ModuleManager moduleManager) {
        this.moduleManager = moduleManager;
    }

    public JsonObject save() {
        JsonObject root = new JsonObject();
        root.addProperty("version", 1);
        JsonObject modules = new JsonObject();
        for (Module module : moduleManager.getModules()) {
            modules.add(module.getName(), saveModule(module));
        }
        root.add("modules", modules);
        return root;
    }

    public void load(JsonObject root) {
        if (root == null || !root.has("modules") || !root.get("modules").isJsonObject()) {
            return;
        }

        JsonObject modules = root.getAsJsonObject("modules");
        for (String moduleName : modules.keySet()) {
            
            
            try {
                JsonElement entry = modules.get(moduleName);
                if (entry == null || !entry.isJsonObject()) {
                    continue;
                }
                moduleManager.getByName(moduleName).ifPresent(module -> loadModule(module, entry.getAsJsonObject()));
            } catch (Exception exception) {
                System.err.println("[Polaris] Skipped corrupt config entry for module '" + moduleName
                        + "': " + exception.getMessage());
            }
        }
    }

    private JsonObject saveModule(Module module) {
        JsonObject object = new JsonObject();
        object.addProperty("enabled", module.isEnabled());
        object.addProperty("hidden", module.isHidden());
        object.addProperty("starred", module.isStarred());
        object.add("bind", module.getBind().toJson());

        JsonObject settings = new JsonObject();
        for (Setting<?> setting : module.getSettings()) {
            if (setting.isPersistent()) {
                settings.add(setting.getName(), setting.toJson());
            }
        }
        object.add("settings", settings);
        return object;
    }

    private void loadModule(Module module, JsonObject object) {
        if (object == null) {
            return;
        }
        boolean forceVisible = module instanceof ClickGuiModule;

        
        
        if (object.has("bind")) {
            try {
                module.setBind(KeyBind.fromJson(object.get("bind")));
            } catch (Exception ignored) {
            }
        }
        if (!forceVisible && object.has("hidden")) {
            try {
                module.setHidden(object.get("hidden").getAsBoolean());
            } catch (Exception ignored) {
            }
        } else if (forceVisible) {
            module.setHidden(false);
        }
        if (object.has("starred")) {
            try {
                module.setStarred(object.get("starred").getAsBoolean());
            } catch (Exception ignored) {
            }
        }
        if (object.has("settings") && object.get("settings").isJsonObject()) {
            JsonObject settings = object.getAsJsonObject("settings");
            for (Setting<?> setting : module.getSettings()) {
                JsonElement saved = settings.get(setting.getName());
                if (saved != null) {
                    try {
                        setting.fromJson(saved);
                    } catch (Exception ignored) {
                        
                    }
                }
            }
        }
        if (object.has("enabled")) {
            try {
                module.setEnabled(object.get("enabled").getAsBoolean());
            } catch (Exception ignored) {
            }
        }
    }
}

