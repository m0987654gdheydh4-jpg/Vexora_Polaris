package polaris.api.module.impl.combat;

import polaris.api.module.Module;
import polaris.api.module.ModuleCategory;

public class NoInteract extends Module {
    public NoInteract() {
        super("No Interact", "Blocks unwanted interactions.", ModuleCategory.COMBAT);
    }
}

