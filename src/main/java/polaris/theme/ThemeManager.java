package polaris.theme;

import polaris.api.module.impl.visual.Hud;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;


public final class ThemeManager {
    private static final ThemeManager INSTANCE = new ThemeManager();
    private static final int MAX_CUSTOM = 24;

    private final List<ThemePreset> builtins;
    private final List<ThemePreset> customs = new CopyOnWriteArrayList<>();
    private volatile ThemeState active = ThemeState.defaults();
    private volatile String activePresetId = "ocean";
    private boolean loadedFromHud;

    private ThemeManager() {
        this.builtins = List.copyOf(ThemeBuiltins.create());
        if (!builtins.isEmpty()) {
            active = builtins.get(0).state().copy();
            activePresetId = builtins.get(0).id();
        }
    }

    public static ThemeManager get() {
        return INSTANCE;
    }

    public ThemeState active() {
        return active;
    }

    public String activePresetId() {
        return activePresetId;
    }

    public List<ThemePreset> builtins() {
        return builtins;
    }

    public List<ThemePreset> customs() {
        return Collections.unmodifiableList(customs);
    }

    public List<ThemePreset> allPresets() {
        List<ThemePreset> all = new ArrayList<>(builtins.size() + customs.size());
        all.addAll(builtins);
        all.addAll(customs);
        return all;
    }

    public synchronized void applyPreset(String id) {
        ThemePreset preset = findPreset(id);
        if (preset == null) {
            return;
        }
        active = preset.state().copy();
        activePresetId = preset.id();
        writeToHud();
    }

    public synchronized void setActive(ThemeState state) {
        if (state == null) {
            return;
        }
        active = state.copy();
        activePresetId = matchPresetId(active);
        writeToHud();
    }

    public synchronized void mutate(ThemeState state) {
        setActive(state);
    }

    public synchronized ThemePreset saveCustom(String name) {
        String n = name == null || name.isBlank() ? nextCustomName() : name.trim();
        ThemePreset preset = ThemePreset.custom(n, active.copy());
        
        customs.removeIf(p -> !p.builtIn() && p.name().equalsIgnoreCase(n));
        while (customs.size() >= MAX_CUSTOM) {
            customs.remove(0);
        }
        customs.add(preset);
        activePresetId = preset.id();
        writeToHud();
        ThemeStorage.saveCustoms(customs);
        return preset;
    }

    public synchronized boolean deleteCustom(String id) {
        boolean removed = customs.removeIf(p -> !p.builtIn() && p.id().equals(id));
        if (removed) {
            if (id.equals(activePresetId)) {
                activePresetId = matchPresetId(active);
            }
            ThemeStorage.saveCustoms(customs);
        }
        return removed;
    }

    public synchronized void loadFromHud(Hud hud) {
        if (hud == null) {
            return;
        }
        customs.clear();
        customs.addAll(ThemeStorage.loadCustoms());
        active = readHudState(hud);
        activePresetId = matchPresetId(active);
        loadedFromHud = true;
    }

    public synchronized void writeToHud() {
        Hud hud = Hud.getInstance();
        if (hud == null) {
            return;
        }
        writeHudState(hud, active);
    }

    public boolean isLoadedFromHud() {
        return loadedFromHud;
    }

    public ThemePreset findPreset(String id) {
        if (id == null) {
            return null;
        }
        for (ThemePreset p : builtins) {
            if (p.id().equals(id)) {
                return p;
            }
        }
        for (ThemePreset p : customs) {
            if (p.id().equals(id)) {
                return p;
            }
        }
        return null;
    }

    private String matchPresetId(ThemeState state) {
        for (ThemePreset p : allPresets()) {
            if (p.state().equals(state)) {
                return p.id();
            }
        }
        return "";
    }

    private String nextCustomName() {
        int n = 1;
        outer:
        while (true) {
            String candidate = "Своя " + n;
            for (ThemePreset p : customs) {
                if (p.name().equalsIgnoreCase(candidate)) {
                    n++;
                    continue outer;
                }
            }
            return candidate;
        }
    }

    private static ThemeState readHudState(Hud hud) {
        Color accent = hud.accentSetting().getRawValue();
        Color background = hud.backgroundSetting().getRawValue();
        ThemeStyle style = ThemeStyle.fromHud(hud.styleSetting().getValue());
        float opacity = (float) (hud.opacitySetting().getValue() / 100.0);
        float shineIntensity = (float) (hud.shineIntensitySetting().getValue() / 100.0);
        float glowIntensity = (float) (hud.glowIntensitySetting().getValue() / 100.0);
        return new ThemeState(
                accent,
                background,
                Color.WHITE,
                ThemeState.deriveOutline(accent),
                style,
                hud.blurStrengthSetting().getFloat(),
                opacity,
                hud.roundingSetting().getFloat(),
                hud.glassStrengthSetting().getFloat(),
                hud.glassDistortionSetting().getFloat(),
                hud.shineSetting().getValue(),
                shineIntensity,
                hud.glowSetting().getValue(),
                hud.glowSizeSetting().getFloat(),
                glowIntensity
        );
    }

    private static void writeHudState(Hud hud, ThemeState s) {
        hud.accentSetting().setValue(new Color(s.accent.getRed(), s.accent.getGreen(), s.accent.getBlue(), 255));
        hud.backgroundSetting().setValue(new Color(s.background.getRed(), s.background.getGreen(), s.background.getBlue(), 255));
        hud.styleSetting().setValue(s.style.toHudMode());
        hud.blurStrengthSetting().setValue((double) s.blurRadius);
        hud.opacitySetting().setValue((double) Math.round(s.opacity * 100f));
        hud.roundingSetting().setValue((double) s.rounding);
        hud.glassStrengthSetting().setValue((double) s.glassStrength);
        hud.glassDistortionSetting().setValue((double) s.glassDistortion);
        hud.shineSetting().setValue(s.shine);
        hud.shineIntensitySetting().setValue((double) Math.round(s.shineIntensity * 100f));
        hud.glowSetting().setValue(s.glow);
        hud.glowSizeSetting().setValue((double) s.glowSize);
        hud.glowIntensitySetting().setValue((double) Math.round(s.glowIntensity * 100f));
    }
}
