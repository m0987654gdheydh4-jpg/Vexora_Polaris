package polaris.api.module.impl.visual;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ClientboundGameEventPacket;
import net.minecraft.network.protocol.game.ClientboundSetTimePacket;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.biome.Biome;
import polaris.api.events.annotation.SubscribeEvent;
import polaris.api.events.impl.PacketEvent;
import polaris.api.events.impl.TickEvent;
import polaris.api.module.Module;
import polaris.api.module.ModuleCategory;
import polaris.api.settings.impl.BooleanSetting;
import polaris.api.settings.impl.ColorSetting;
import polaris.api.settings.impl.ModeSetting;
import polaris.api.settings.impl.NumberSetting;

import java.awt.Color;


public class Ambience extends Module {
    private static Ambience instance;

    private static final String GAMMA = "Gamma";
    private static final String NIGHT_VISION = "Night Vision";
    private static final String DYNAMIC = "Dynamic";
    private static final String ADAPTIVE = "Adaptive";
    private static final String TORCH = "Torch";

    private final ModeSetting mode = register(new ModeSetting("Mode", "World time mode.", "Day", "Day", "Midday", "Night", "Midnight", "Custom"));
    private final NumberSetting customTime = register(new NumberSetting("Time", "Custom day time.", 1000.0, 0.0, 24000.0, 100.0));
    private final ModeSetting weather = register(new ModeSetting("Weather", "Client weather override.", "Sunny", "Sunny", "Rain", "Thunder", "Snow"));
    private final NumberSetting saturation = register(new NumberSetting("Saturation", "World saturation multiplier offset.", 0.0, -1.0, 1.0, 0.05));
    private final NumberSetting brightness = register(new NumberSetting("Brightness", "World brightness offset.", 0.0, -1.0, 1.0, 0.05));
    
    private final BooleanSetting customFog = register(new BooleanSetting("Custom Fog", "Recolor the sky (not atmospheric fog).", false));
    private final ColorSetting customFogColor = register(new ColorSetting("Fog Color", "Custom sky color.", new Color(200, 214, 229, 255)));

    private final BooleanSetting fullBright = register(new BooleanSetting("Full Bright", "Light the world up.", false));
    private final ModeSetting brightMode = register(new ModeSetting("Bright Mode", "Full bright technique.",
            GAMMA, GAMMA, NIGHT_VISION, DYNAMIC, ADAPTIVE, TORCH));
    private final NumberSetting brightFloor = register(new NumberSetting("Bright Floor", "Ambient light floor (Adaptive).", 0.53, 0.5, 0.6, 0.01));
    private final NumberSetting brightCurve = register(new NumberSetting("Bright Curve", "Gamma curve (Adaptive).", 1.4, 0.5, 3.0, 0.1));
    private final NumberSetting torchRadius = register(new NumberSetting("Torch Radius", "Dynamic light radius (Torch).", 10.0, 5.0, 20.0, 1.0));

    
    private static volatile boolean torchActive;
    private static volatile float torchRadiusValue = 10.0f;
    private static volatile double torchX;
    private static volatile double torchY;
    private static volatile double torchZ;

    private int lastTorchBlockX = Integer.MIN_VALUE;
    private int lastTorchBlockY = Integer.MIN_VALUE;
    private int lastTorchBlockZ = Integer.MIN_VALUE;
    private boolean torchRangeDirty;
    private int dirtyMinX;
    private int dirtyMinY;
    private int dirtyMinZ;
    private int dirtyMaxX;
    private int dirtyMaxY;
    private int dirtyMaxZ;
    private boolean nightVisionApplied;

    private boolean weatherOverrideActive;
    private boolean cachedServerRaining;
    private float cachedServerRainLevel;
    private float cachedServerThunderLevel;
    private ClientLevel weatherSnapshotLevel;

    public Ambience() {
        super("Ambience", "Changes time, weather and world atmosphere.", ModuleCategory.VISUAL);
        customTime.visibleWhen(() -> mode.is("Custom"));
        customFogColor.visibleWhen(customFog::getValue);
        brightMode.visibleWhen(fullBright::getValue);
        brightFloor.visibleWhen(() -> fullBright.getValue() && brightMode.is(ADAPTIVE));
        brightCurve.visibleWhen(() -> fullBright.getValue() && brightMode.is(ADAPTIVE));
        torchRadius.visibleWhen(() -> fullBright.getValue() && brightMode.is(TORCH));
        fullBright.addListener((setting, oldValue, newValue) -> onBrightSettingChanged());
        brightMode.addListener((setting, oldValue, newValue) -> onBrightSettingChanged());
        instance = this;
    }

    public static Ambience getInstance() {
        return instance;
    }

    public float getBrightnessValue() {
        return brightness.getFloat();
    }

    

    private boolean brightActive(String which) {
        return isEnabled() && fullBright.getValue() && brightMode.is(which);
    }

    
    public float getGammaOverride() {
        if (!isEnabled() || !fullBright.getValue()) {
            return -1.0f;
        }
        if (brightMode.is(GAMMA)) {
            return 200.0f;
        }
        if (brightMode.is(DYNAMIC)) {
            return dynamicGamma();
        }
        if (brightMode.is(ADAPTIVE)) {
            return clamp(brightCurve.getFloat(), 0.5f, 3.0f);
        }
        return -1.0f;
    }

    
    public float getAmbientLightFloor() {
        return brightActive(ADAPTIVE) ? clamp(brightFloor.getFloat(), 0.0f, 1.0f) : -1.0f;
    }

    
    public static boolean isTorchLightActive() {
        return torchActive;
    }

    
    public static int torchLightAt(int x, int y, int z) {
        return torchLightAt(x + 0.5, y + 0.5, z + 0.5);
    }

    
    public static int torchLightAt(double x, double y, double z) {
        if (!torchActive) {
            return 0;
        }
        float radius = torchRadiusValue;
        if (radius <= 0.0f) {
            return 0;
        }
        double dx = x - torchX;
        double dy = y - torchY;
        double dz = z - torchZ;
        double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (distance >= radius) {
            return 0;
        }
        int light = Math.round(15.0f * (float) (1.0 - distance / radius));
        return light < 1 ? 0 : Math.min(light, 15);
    }

    private float dynamicGamma() {
        if (mc.player == null || mc.level == null) {
            return 80.0f;
        }
        BlockPos pos = mc.player.blockPosition();
        float blockLight = mc.level.getBrightness(LightLayer.BLOCK, pos) / 15.0f;
        float skyLight = mc.level.getBrightness(LightLayer.SKY, pos) / 15.0f;
        float night = nightFactor(mc.level.getDayTime() % 24000L);
        float ambient = Math.max(blockLight, skyLight * (1.0f - night * 0.7f));
        float darkness = clamp(Math.max(1.0f - ambient, night * 0.65f), 0.0f, 1.0f);
        float breathe = (float) Math.sin(System.currentTimeMillis() * 0.0018) * 0.035f;
        return 2.0f + clamp(darkness + breathe, 0.0f, 1.0f) * 198.0f;
    }

    private static float nightFactor(long dayTime) {
        if (dayTime < 12000L) {
            return 0.0f;
        }
        if (dayTime < 14000L) {
            return (dayTime - 12000L) / 2000.0f;
        }
        if (dayTime < 22000L) {
            return 1.0f;
        }
        return 1.0f - (dayTime - 22000L) / 2000.0f;
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    
    private void onBrightSettingChanged() {
        clearTorchLight();
        clearNightVision();
        if (isEnabled() && mc.levelRenderer != null) {
            mc.levelRenderer.allChanged();
        }
    }

    private void tickFullBright() {
        if (mc.player == null) {
            return;
        }
        if (fullBright.getValue() && brightMode.is(NIGHT_VISION)) {
            mc.player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 300, 0, false, false));
            nightVisionApplied = true;
        } else {
            clearNightVision();
        }
        tickTorchLight();
    }

    private void tickTorchLight() {
        if (!brightActive(TORCH) || mc.level == null || mc.levelRenderer == null) {
            clearTorchLight();
            return;
        }

        torchRadiusValue = torchRadius.getFloat();
        torchX = mc.player.getX();
        torchY = mc.player.getEyeY();
        torchZ = mc.player.getZ();
        torchActive = true;

        int blockX = Mth.floor(torchX);
        int blockY = Mth.floor(mc.player.getY());
        int blockZ = Mth.floor(torchZ);
        if (blockX == lastTorchBlockX && blockY == lastTorchBlockY && blockZ == lastTorchBlockZ) {
            return;
        }

        
        if (torchRangeDirty) {
            mc.levelRenderer.setSectionRangeDirty(dirtyMinX, dirtyMinY, dirtyMinZ, dirtyMaxX, dirtyMaxY, dirtyMaxZ);
        }

        int reach = Mth.ceil(torchRadiusValue) + 1;
        dirtyMinX = blockX - reach;
        dirtyMinY = blockY - reach;
        dirtyMinZ = blockZ - reach;
        dirtyMaxX = blockX + reach;
        dirtyMaxY = blockY + reach;
        dirtyMaxZ = blockZ + reach;
        torchRangeDirty = true;
        mc.levelRenderer.setSectionRangeDirty(dirtyMinX, dirtyMinY, dirtyMinZ, dirtyMaxX, dirtyMaxY, dirtyMaxZ);

        lastTorchBlockX = blockX;
        lastTorchBlockY = blockY;
        lastTorchBlockZ = blockZ;
    }

    private void clearTorchLight() {
        if (!torchActive && !torchRangeDirty) {
            return;
        }
        torchActive = false;
        if (torchRangeDirty && mc.levelRenderer != null) {
            mc.levelRenderer.setSectionRangeDirty(dirtyMinX, dirtyMinY, dirtyMinZ, dirtyMaxX, dirtyMaxY, dirtyMaxZ);
        }
        torchRangeDirty = false;
        lastTorchBlockX = Integer.MIN_VALUE;
        lastTorchBlockY = Integer.MIN_VALUE;
        lastTorchBlockZ = Integer.MIN_VALUE;
    }

    private void clearNightVision() {
        if (nightVisionApplied && mc.player != null) {
            mc.player.removeEffect(MobEffects.NIGHT_VISION);
        }
        nightVisionApplied = false;
    }

    public float getSaturationFactor() {
        return Math.clamp(1.0f + saturation.getFloat(), 0.0f, 2.0f);
    }

    
    public boolean hasCustomSkyColor() {
        return isEnabled() && customFog.getValue();
    }

    
    public boolean hasCustomFog() {
        return hasCustomSkyColor();
    }

    public int getCustomFogColor() {
        return customFogColor.getValue().getRGB();
    }

    public int getCustomSkyColor() {
        return customFogColor.getValue().getRGB();
    }

    public long getInternalTime() {
        if (mode.is("Day")) {
            return 1000L;
        }
        if (mode.is("Midday")) {
            return 6000L;
        }
        if (mode.is("Night")) {
            return 13000L;
        }
        if (mode.is("Midnight")) {
            return 18000L;
        }
        return (long) customTime.getValue().doubleValue();
    }

    public void syncWeather(ClientLevel level, ClientLevel.ClientLevelData levelData) {
        if (level == null || levelData == null) {
            clearWeatherSnapshot();
            return;
        }

        if (weatherSnapshotLevel != level) {
            clearWeatherSnapshot();
        }

        if (!weatherOverrideActive) {
            cachedServerRaining = levelData.isRaining();
            cachedServerRainLevel = level.getRainLevel(1.0f);
            cachedServerThunderLevel = level.getThunderLevel(1.0f);
            weatherOverrideActive = true;
            weatherSnapshotLevel = level;
        }

        boolean raining = shouldForcePrecipitation();
        levelData.setRaining(raining);
        level.setRainLevel(raining ? 1.0f : 0.0f);
        level.setThunderLevel(weather.is("Thunder") ? 1.0f : 0.0f);
    }

    public Biome.Precipitation getForcedPrecipitation() {
        if (!isEnabled()) {
            return null;
        }
        if (weather.is("Snow")) {
            return Biome.Precipitation.SNOW;
        }
        if (weather.is("Rain") || weather.is("Thunder")) {
            return Biome.Precipitation.RAIN;
        }
        return Biome.Precipitation.NONE;
    }

    public boolean shouldForceSnow() {
        return getForcedPrecipitation() == Biome.Precipitation.SNOW;
    }

    @Override
    protected void onEnable() {
        if (fullBright.getValue() && mc.levelRenderer != null) {
            mc.levelRenderer.allChanged();
        }
    }

    @Override
    protected void onDisable() {
        if (mc.level instanceof ClientLevel level) {
            restoreWeather(level, level.getLevelData());
        } else {
            clearWeatherSnapshot();
        }
        boolean rebake = fullBright.getValue();
        clearTorchLight();
        clearNightVision();
        if (rebake && mc.levelRenderer != null) {
            mc.levelRenderer.allChanged();
        }
    }

    @SubscribeEvent
    private void onTick(TickEvent.Post event) {
        tickFullBright();
    }

    @SubscribeEvent
    private void onPacket(PacketEvent event) {
        if (!event.isReceive()) {
            return;
        }
        if (event.getPacket() instanceof ClientboundSetTimePacket && isEnabled()) {
            event.cancel();
            return;
        }
        if (!(event.getPacket() instanceof ClientboundGameEventPacket packet)) {
            return;
        }
        ClientboundGameEventPacket.Type type = packet.getEvent();
        if (!isWeatherPacket(type)) {
            return;
        }
        updateCachedWeather(packet);
        if (isEnabled()) {
            event.cancel();
        }
    }

    private boolean shouldForcePrecipitation() {
        return weather.is("Rain") || weather.is("Thunder") || weather.is("Snow");
    }

    private void restoreWeather(ClientLevel level, ClientLevel.ClientLevelData levelData) {
        if (!weatherOverrideActive) {
            return;
        }
        levelData.setRaining(cachedServerRaining);
        level.setRainLevel(cachedServerRainLevel);
        level.setThunderLevel(cachedServerThunderLevel);
        clearWeatherSnapshot();
    }

    private void clearWeatherSnapshot() {
        weatherOverrideActive = false;
        weatherSnapshotLevel = null;
    }

    private void updateCachedWeather(ClientboundGameEventPacket packet) {
        ClientboundGameEventPacket.Type type = packet.getEvent();
        if (type == ClientboundGameEventPacket.START_RAINING) {
            cachedServerRaining = true;
            cachedServerRainLevel = Math.max(cachedServerRainLevel, 1.0f);
            return;
        }
        if (type == ClientboundGameEventPacket.STOP_RAINING) {
            cachedServerRaining = false;
            cachedServerRainLevel = 0.0f;
            cachedServerThunderLevel = 0.0f;
            return;
        }
        if (type == ClientboundGameEventPacket.RAIN_LEVEL_CHANGE) {
            cachedServerRainLevel = Math.clamp(packet.getParam(), 0.0f, 1.0f);
            cachedServerRaining = cachedServerRainLevel > 0.0001f;
            return;
        }
        if (type == ClientboundGameEventPacket.THUNDER_LEVEL_CHANGE) {
            cachedServerThunderLevel = Math.clamp(packet.getParam(), 0.0f, 1.0f);
        }
    }

    private static boolean isWeatherPacket(ClientboundGameEventPacket.Type type) {
        return type == ClientboundGameEventPacket.START_RAINING
                || type == ClientboundGameEventPacket.STOP_RAINING
                || type == ClientboundGameEventPacket.RAIN_LEVEL_CHANGE
                || type == ClientboundGameEventPacket.THUNDER_LEVEL_CHANGE;
    }
}
