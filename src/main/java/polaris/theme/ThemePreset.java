package polaris.theme;

import java.util.Objects;
import java.util.UUID;


public final class ThemePreset {
    private final String id;
    private final String name;
    private final boolean builtIn;
    private final ThemeState state;

    public ThemePreset(String id, String name, boolean builtIn, ThemeState state) {
        this.id = id == null || id.isBlank() ? UUID.randomUUID().toString() : id;
        this.name = name == null || name.isBlank() ? "Theme" : name;
        this.builtIn = builtIn;
        this.state = state == null ? ThemeState.defaults() : state;
    }

    public static ThemePreset builtin(String id, String name, ThemeState state) {
        return new ThemePreset(id, name, true, state);
    }

    public static ThemePreset custom(String name, ThemeState state) {
        return new ThemePreset(UUID.randomUUID().toString(), name, false, state);
    }

    public String id() {
        return id;
    }

    public String name() {
        return name;
    }

    public boolean builtIn() {
        return builtIn;
    }

    public ThemeState state() {
        return state;
    }

    public ThemePreset withName(String newName) {
        return new ThemePreset(id, newName, builtIn, state);
    }

    public ThemePreset withState(ThemeState newState) {
        return new ThemePreset(id, name, builtIn, newState);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ThemePreset that)) return false;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
