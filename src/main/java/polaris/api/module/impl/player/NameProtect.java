package polaris.api.module.impl.player;

import net.minecraft.client.Minecraft;
import polaris.api.events.annotation.SubscribeEvent;
import polaris.api.events.impl.TextFactoryEvent;
import polaris.api.module.Module;
import polaris.api.module.ModuleCategory;
import polaris.api.settings.impl.BooleanSetting;
import polaris.api.settings.impl.StringSetting;

public final class NameProtect extends Module {
    private final StringSetting nameSetting = register(new StringSetting("Name", "Replacement nickname.", "vexora.net", 32));
    private final BooleanSetting friendsSetting = register(new BooleanSetting("Friends", "Hide friend nicknames when friend repository is available.", true));

    public NameProtect() {
        super("Name Protect", "Hides your nickname in rendered text.", ModuleCategory.PLAYER);
    }

    @SubscribeEvent
    private void onTextFactory(TextFactoryEvent event) {
        Minecraft client = Minecraft.getInstance();
        if (client.getUser() != null) {
            event.replaceText(client.getUser().getName(), nameSetting.getValue());
        }
    }
}

