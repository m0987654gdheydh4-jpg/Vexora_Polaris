package polaris.api.events.impl;

import polaris.api.events.Event;
import polaris.api.module.Module;

public final class ModuleToggleEvent implements Event {
    private final Module module;
    private final boolean enabled;

    public ModuleToggleEvent(Module module, boolean enabled) {
        this.module = module;
        this.enabled = enabled;
    }

    public Module getModule() {
        return module;
    }

    public boolean isEnabled() {
        return enabled;
    }
}

