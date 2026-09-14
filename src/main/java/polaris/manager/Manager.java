package polaris.manager;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import polaris.api.command.CommandManager;
import polaris.api.config.ConfigManager;
import polaris.api.drag.core.ElementManager;
import polaris.api.events.Event;
import polaris.api.events.bus.EventBus;
import polaris.api.events.impl.TickEvent;
import polaris.api.module.ModuleManager;
import polaris.api.module.impl.combat.aura.AngleConnection;
import polaris.api.module.impl.misc.ClickGuiModule;
import polaris.screens.clickgui.ClickGui;
import polaris.screens.clickgui.dropdown.DropDownScreen;
import polaris.screens.csgui.CsGui;
import polaris.screens.modernui.ClickGuiScreen;
import polaris.screens.modernui.impl.WorldAnimation;
import polaris.utils.repository.blockesp.BlockESPConfig;
import polaris.utils.inventory.item.ItemToolkit;
import polaris.utils.repository.friend.FriendUtils;
import polaris.utils.repository.macro.MacroRepository;
import polaris.utils.repository.staff.StaffUtils;
import polaris.utils.repository.way.WayRepository;
public class Manager {
    private static Manager instance;

    private EventBus eventBus;
    private ModuleManager moduleManager;
    private ConfigManager configManager;
    private CommandManager commandManager;
    private boolean clickGuiTextWarmed;

    public void initClient() {
        instance = this;
        eventBus = new EventBus();
        FriendUtils.load();
        StaffUtils.load();
        MacroRepository.getInstance().load();
        WayRepository.getInstance().load();
        BlockESPConfig.getInstance().load();
        moduleManager = new ModuleManager(eventBus);
        moduleManager.init();
        commandManager = new CommandManager();
        commandManager.init();
        eventBus.register(AngleConnection.INSTANCE);
        eventBus.register(polaris.emotions.EmotionWheelManager.create());
        configManager = new ConfigManager(moduleManager);
        moduleManager.setDirtyListener(configManager::markDirty);
        configManager.init();
        ElementManager.getInstance().load();
        configManager.loadAll();
        eventBus.register(configManager);
        eventBus.register(commandManager);
        eventBus.register(MacroRepository.getInstance());
        eventBus.register(WayRepository.getInstance());
        eventBus.register(ItemToolkit.INSTANCE);
        polaris.api.module.impl.combat.aura.ai.AiRotationTrainer.invoke12();

        ClientTickEvents.START_CLIENT_TICK.register(client -> {
            warmClickGuiText();
            WorldAnimation.tick();
            ClickGui.tickMovementKeys();
            postEvent(new TickEvent.Pre(client));
        });
        ClientTickEvents.END_CLIENT_TICK.register(client -> postEvent(new TickEvent.Post(client)));
    }

    public static Manager getInstance() {
        return instance;
    }

    public EventBus getEventBus() {
        return eventBus;
    }

    public ModuleManager getModuleManager() {
        return moduleManager;
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public CommandManager getCommandManager() {
        return commandManager;
    }

    public static ModuleManager getModules() {
        return instance == null ? null : instance.moduleManager;
    }

    public static <T extends Event> T postEvent(T event) {
        if (instance == null || instance.eventBus == null) {
            return event;
        }
        
        
        
        
        
        
        
        
        if (event instanceof polaris.api.events.impl.PacketEvent packetEvent
                && packetEvent.isReceive()
                && !Minecraft.getInstance().isSameThread()) {
            return event;
        }
        return instance.eventBus.post(event);
    }

    public static void toggleClickGui(Minecraft client) {
        if (client == null) {
            return;
        }
        if (client.screen instanceof CsGui screen) {
            screen.onClose();
            return;
        }
        if (client.screen instanceof ClickGuiScreen) {
            client.screen.onClose();
            return;
        }
        if (client.screen instanceof DropDownScreen dropDown) {
            dropDown.close();
            return;
        }
        if (WorldAnimation.isActive() && client.screen == null) {
            return;
        }

        if (instance != null && instance.moduleManager != null) {
            var clickGuiOpt = instance.moduleManager.getByType(ClickGuiModule.class);
            if (clickGuiOpt.isPresent()) {
                clickGuiOpt.get().openGui(client);
                return;
            }
        }

        DropDownScreen.INSTANCE.openGui();
    }

    
    public static boolean handleClickGuiKeyPressed(Minecraft client, int key) {
        if (instance == null || instance.moduleManager == null || client == null) {
            return false;
        }
        return instance.moduleManager.getByType(ClickGuiModule.class)
                .filter(module -> module.getBind().getType() == polaris.api.settings.bind.InputType.KEYBOARD
                        && module.getBind().getCode() == key)
                .map(module -> {
                    toggleClickGui(client);
                    return true;
                })
                .orElse(false);
    }

    private void warmClickGuiText() {
        if (clickGuiTextWarmed) {
            return;
        }
        clickGuiTextWarmed = true;
        ClickGui.warmupText();
    }
}

