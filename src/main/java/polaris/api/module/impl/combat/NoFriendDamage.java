package polaris.api.module.impl.combat;

import polaris.api.events.annotation.SubscribeEvent;
import polaris.api.events.impl.InteractEntityEvent;
import polaris.api.module.Module;
import polaris.api.module.ModuleCategory;
import polaris.utils.repository.friend.FriendUtils;

public class NoFriendDamage extends Module {
    public NoFriendDamage() {
        super("No Friend Damage", "Blocks attacks on friends.", ModuleCategory.COMBAT);
    }

    @SubscribeEvent
    private void onInteract(InteractEntityEvent event) {
        if (FriendUtils.isFriend(event.getEntity())) {
            event.cancel();
        }
    }
}

