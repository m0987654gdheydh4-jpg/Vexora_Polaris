package polaris.api.module.impl.combat.aura.attack;


public interface TriggerController {
    int getAttackDelayMs();

    boolean isResetSprintLegit();

    boolean shouldPassHitChance();

    boolean isOnlyCrits();

    boolean isSmartCritsEnabled();

    long getActivationTimeMs();

    boolean isEnabled();
}

