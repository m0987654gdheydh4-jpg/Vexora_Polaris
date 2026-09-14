package polaris.api.module;

import net.minecraft.client.Minecraft;
import polaris.api.events.annotation.SubscribeEvent;
import polaris.api.events.bus.EventBus;
import polaris.api.events.impl.TickEvent;
import polaris.api.module.impl.combat.*;
import polaris.api.module.impl.movement.CastleFly;
import polaris.api.module.impl.movement.*;
import polaris.api.module.impl.misc.AutoAuth;
import polaris.api.module.impl.misc.AutoDuel;
import polaris.api.module.impl.misc.AutoLes;
import polaris.api.module.impl.misc.AutoLeave;
import polaris.api.module.impl.misc.AutoTpAccept;
import polaris.api.module.impl.misc.ClickGuiModule;
import polaris.api.module.impl.misc.ClientSounds;
import polaris.api.module.impl.misc.CustomSword;
import polaris.api.module.impl.misc.DeathCoords;
import polaris.api.module.impl.misc.ElytraHelper;
import polaris.api.module.impl.misc.JoinerHelper;
import polaris.api.module.impl.misc.ServerHelper;
import polaris.api.module.impl.misc.UseTracker;

import polaris.api.module.impl.player.AhHelper;
import polaris.api.module.impl.player.AutoBuy;
import polaris.api.module.impl.player.AutoRespawn;
import polaris.api.module.impl.player.AutoTool;
import polaris.api.module.impl.player.ChestStealer;
import polaris.api.module.impl.player.ClickPearl;
import polaris.api.module.impl.player.FakePing;
import polaris.api.module.impl.player.FastBreak;
import polaris.api.module.impl.misc.ServerRPSpoofer;
import polaris.api.module.impl.player.NoEntityTrace;
import polaris.api.module.impl.player.NoDelay;
import polaris.api.module.impl.player.NoPush;
import polaris.api.module.impl.player.FakePlayer;
import polaris.api.module.impl.player.NameProtect;
import polaris.api.module.impl.player.ItemScroller;
import polaris.api.module.impl.player.FreeLook;
import polaris.api.module.impl.player.FreeCam;
import polaris.api.module.impl.player.OpenWalls;
import polaris.api.module.impl.player.WindJump;
import polaris.api.module.impl.visual.*;

import polaris.api.module.impl.visual.richdog.RichDog;
import polaris.api.settings.bind.KeyBind;
import polaris.utils.inventory.InventoryFlowManager;
import polaris.utils.network.Network;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class ModuleManager {
    private final EventBus eventBus;
    private final List<Module> modules = new ArrayList<>();
    private final Map<String, Module> byName = new HashMap<>();
    
    
    private final Map<Class<? extends Module>, Module> byType = new ConcurrentHashMap<>();
    private final Map<ModuleCategory, List<Module>> byCategory = new EnumMap<>(ModuleCategory.class);
    private final Map<ModuleCategory, List<Module>> byCategoryViews = new EnumMap<>(ModuleCategory.class);
    private Collection<Module> modulesView = List.of();
    private final Map<Module, Boolean> bindStates = new IdentityHashMap<>();
    private Runnable dirtyListener = () -> {};

    public ModuleManager(EventBus eventBus) {
        this.eventBus = eventBus;
    }

    public void init() {
        registerDefaults();
        eventBus.register(this);
    }

    public void setDirtyListener(Runnable dirtyListener) {
        this.dirtyListener = dirtyListener == null ? () -> {} : dirtyListener;
    }

    public void register(Module module) {
        String key = normalize(module.getName());
        if (byName.containsKey(key)) {
            return;
        }
        modules.add(module);
        byName.put(key, module);
        byType.putIfAbsent(module.getClass(), module);
        byCategory.computeIfAbsent(module.getCategory(), ignored -> new ArrayList<>()).add(module);
        module.addStateListener(changedModule -> dirtyListener.run());
        module.initialize(eventBus);
        sortViews();
    }

    public Collection<Module> getModules() {
        return modulesView;
    }

    public List<Module> getByCategory(ModuleCategory category) {
        return byCategoryViews.getOrDefault(category, List.of());
    }

    public Optional<Module> getByName(String name) {
        return Optional.ofNullable(byName.get(normalize(name)));
    }

    public <T extends Module> Optional<T> getByType(Class<T> type) {
        Module module = byType.get(type);
        if (type.isInstance(module)) {
            return Optional.of(type.cast(module));
        }
        
        
        for (Module candidate : modules) {
            if (type.isInstance(candidate)) {
                byType.putIfAbsent(type, candidate);
                return Optional.of(type.cast(candidate));
            }
        }
        return Optional.empty();
    }

    public boolean isEnabled(Class<? extends Module> type) {
        return getByType(type).filter(Module::isEnabled).isPresent();
    }

    public Optional<AuraModule> getAura() {
        return getByType(AuraModule.class);
    }

    public Optional<AuraModule> getKillAura() {
        return getAura();
    }

    public Optional<HitBoxModule> getHitBox() {
        return getByType(HitBoxModule.class);
    }

    public boolean isHitBoxEnabled() {
        return isEnabled(HitBoxModule.class);
    }

    public Optional<NoInteract> getNoInteract() {
        return getByType(NoInteract.class);
    }

    public boolean isNoInteractEnabled() {
        return isEnabled(NoInteract.class);
    }

    public Optional<ServerRPSpoofer> getServerRPSpoofer() {
        return getByType(ServerRPSpoofer.class);
    }

    public boolean isServerRPSpooferEnabled() {
        return isEnabled(ServerRPSpoofer.class);
    }

    @SubscribeEvent
    private void onPreTick(TickEvent.Pre event) {
        InventoryFlowManager.update();
    }

    @SubscribeEvent
    private void onTick(TickEvent.Post event) {
        Minecraft client = event.getClient();
        Network.tick();
        handleBinds(client);
        for (Module module : modules) {
            if (module.isEnabled()) {
                module.onTick(client);
            }
        }
    }

    private void registerDefaults() {
        register(new AntiBot());
        register(new AuraModule());
        register(new AutoCrystal());
        register(new AutoGApple());
        register(new AutoSwap());
        register(new AutoTotem());
        register(new Criticals());
        register(new HitBoxModule());
        register(new MaceTarget());
        register(new HitSound());
        register(new NoFriendDamage());
        register(new KillAuraModule());
        register(new AutoWebV2());

        register(new AntiLegitMiss());
        register(new NoInteract());
        register(new Velocity());
        register(new LegitAura());
        register(new AimAssist());
        register(new AimAssistV2());
        register(new AutoArmor());
        register(new FireballHelper());
        register(new BreadCrumbs());
        register(new BreakHighLight());
        register(new AutoPotion());
        register(new AutoMace());

        register(new AutoSprint());
        register(new Fly());
        register(new CastleFly());
        register(new ElytraTarget());
        register(new NoWeb());
        register(new AutoWeb());
        register(new NoSlow());
        register(new InventoryMove());
        register(new Spider());
        register(new Speed());
        register(new Strafe());
        register(new SuperFireWork());
        register(new TargetStrafe());
        register(new AirStuck());
        register(new Ambience());
        register(new Animations());
        register(new ChinaHat());
        register(new ElytraRecast());
        register(new MotionBlur());
        register(new Arrows());
        register(new AspectRatio());
        register(new BlockESP());
        register(new StorageESP());
        register(new BlockOutline());
        register(new ESP());
        register(new FogBlur());
        register(new ShaderChams());
        register(new ShaderFog());
        register(new Hands());
        register(new ShaderHand());
        register(new Hud());
        register(new ItemPhysics());
        register(new KillEffect());
        register(new Particles());
        register(new GoldHPIndicator());
        register(new DamageParticles());
        register(new Predictions());
        register(new SeeInvisible());
        register(new SwingAnimation());
        register(new HandTweaker());
        
        register(new NoRender());
        register(new TargetESP());
        register(new RichDog());
        register(new ItemHighlighter());
        register(new Emotions());
        register(new CameraSettings());
        register(new AfkCamera());
        register(new Cosmetics());
        register(new TNTTimer());

        register(new WindJump());
        register(new OpenWalls());
        register(new NoPush());
        register(new NoEntityTrace());
        register(new NoDelay());
        register(new FastBreak());
        register(new FakePing());
        register(new NameProtect());
        register(new ItemScroller());
        register(new FreeLook());
        register(new FreeCam());
        register(new FakePlayer());
        register(new AhHelper());
        register(new AutoBuy());
        register(new AutoRespawn());
        register(new AutoTool());
        register(new ChestStealer());

        register(new JoinerHelper());
        register(new ServerRPSpoofer());
        register(new AutoAuth());
        register(new AutoDuel());
        register(new AutoLeave());
        register(new AutoTpAccept());
        register(new AutoLes());
        register(new ClientSounds());
        register(new ClickPearl());
        register(new CustomSword());
        register(new DeathCoords());
        register(new ElytraHelper());
        register(new ServerHelper());
        register(new UseTracker());
        register(new ClickGuiModule());
    }

    private void handleBinds(Minecraft client) {
        if (client == null || client.getWindow() == null || client.screen != null) {
            bindStates.clear();
            return;
        }

        long handle = client.getWindow().handle();
        for (Module module : modules) {
            
            
            
            if (module instanceof ClickGuiModule) {
                continue;
            }
            KeyBind bind = module.getBind();
            boolean down = bind.isDown(handle);
            boolean wasDown = bindStates.getOrDefault(module, false);
            if (down && !wasDown) {
                module.toggle();
            }
            bindStates.put(module, down);
        }
    }

    private void sortViews() {
        modules.sort(Comparator.comparing(module -> module.getName().toLowerCase(Locale.ROOT)));
        for (List<Module> categoryModules : byCategory.values()) {
            categoryModules.sort(Comparator.comparing(module -> module.getName().toLowerCase(Locale.ROOT)));
        }
        modulesView = Collections.unmodifiableList(modules);
        byCategoryViews.clear();
        for (Map.Entry<ModuleCategory, List<Module>> entry : byCategory.entrySet()) {
            byCategoryViews.put(entry.getKey(), Collections.unmodifiableList(entry.getValue()));
        }
    }

    private String normalize(String name) {
        return name == null ? "" : name.toLowerCase(Locale.ROOT).replace(" ", "");
    }
}

