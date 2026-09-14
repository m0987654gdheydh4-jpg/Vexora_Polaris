package polaris.api.command.impl;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import polaris.api.command.Command;
import polaris.api.events.annotation.SubscribeEvent;
import polaris.api.events.impl.AttackEvent;
import polaris.api.events.impl.TickEvent;
import polaris.api.module.impl.combat.AuraModule;
import polaris.api.module.impl.combat.aura.ai.AiRotationTrainer;
import polaris.manager.Manager;
import polaris.screens.ailab.AiLabScreen;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

public final class AiRotationCommand extends Command {
    public AiRotationCommand() {
        super("ai", "AI rotation train / learn / run / lab");
        if (Manager.getInstance() != null && Manager.getInstance().getEventBus() != null) {
            Manager.getInstance().getEventBus().register(this);
        }
    }

    @Override
    public void execute(String label, String[] args) {
        if (args.length == 0) {
            logDirect("Usage: .ai <train|learn|run|stop|log|lab|profile|list>", ChatFormatting.RED);
            logDirect(".ai profile new [name] | .ai profile delete [name]", ChatFormatting.GRAY);
            return;
        }
        String op = args[0].toLowerCase(Locale.ROOT);
        if (args.length >= 2 && (op.equals("train") || op.equals("learn") || op.equals("run") || op.equals("profile"))) {
            logDirect(AiRotationTrainer.resolve26(args[1]));
        }
        String msg = switch (op) {
            case "train" -> AiRotationTrainer.resolve();
            case "learn" -> AiRotationTrainer.resolve4();
            case "run" -> {
                String r = AiRotationTrainer.resolve3();
                enableAiAura();
                yield r;
            }
            case "stop" -> AiRotationTrainer.resolve2();
            case "profile" -> {
                if (args.length >= 2 && args[1].equalsIgnoreCase("new")) {
                    yield args.length >= 3
                            ? AiRotationTrainer.createProfile(args[2])
                            : AiRotationTrainer.createNextProfile();
                }
                if (args.length >= 2 && args[1].equalsIgnoreCase("delete")) {
                    yield args.length >= 3
                            ? AiRotationTrainer.deleteProfile(args[2])
                            : AiRotationTrainer.deleteProfile(AiRotationTrainer.getDefaultValue());
                }
                yield args.length >= 2
                        ? AiRotationTrainer.resolve26(args[1])
                        : ("Active profile: " + AiRotationTrainer.getDefaultValue() + " | .ai profile new [name]");
            }
            case "list" -> AiRotationTrainer.resolve28();
            case "log" -> "AI log: " + AiRotationTrainer.resolve10().toAbsolutePath();
            case "lab" -> {
                Minecraft mc = Minecraft.getInstance();
                if (mc != null) {
                    mc.setScreen(new AiLabScreen());
                }
                yield "AI Lab открыт.";
            }
            default -> "Usage: .ai <train|learn|run|stop|log|lab|profile|list>";
        };
        logDirect(msg);
    }

    private void enableAiAura() {
        AuraModule aura = AuraModule.getInstance();
        if (aura == null) {
            logDirect("Aura недоступна.", ChatFormatting.RED);
            AiRotationTrainer.resolve2();
            return;
        }
        
        
        
        if (!aura.mode.getModes().contains("AI")) {
            logDirect("Режим AI сейчас отключён в Aura — включать нечего.", ChatFormatting.RED);
            return;
        }
        aura.mode.setValue("AI");
        if (!aura.isEnabled()) {
            aura.setEnabled(true);
        }
    }

    @SubscribeEvent
    private void onAttack(AttackEvent event) {
        if (event != null && event.getTarget() != null) {
            AiRotationTrainer.invoke(event.getTarget());
        }
    }

    @SubscribeEvent
    private void onTick(TickEvent.Pre event) {
        AiRotationTrainer.invoke2();
    }

    @Override
    public Stream<String> tabComplete(String label, String[] args) {
        if (args.length == 1) {
            String p = args[0].toLowerCase(Locale.ROOT);
            return Stream.of("train", "learn", "run", "stop", "log", "lab", "profile", "list")
                    .filter(s -> s.startsWith(p));
        }
        if (args.length == 2) {
            String op = args[0].toLowerCase(Locale.ROOT);
            if (op.equals("profile") || op.equals("run") || op.equals("train") || op.equals("learn")) {
                String p = args[1].toLowerCase(Locale.ROOT);
                return AiRotationTrainer.resolve27().stream()
                        .filter(s -> s.toLowerCase(Locale.ROOT).startsWith(p));
            }
        }
        return Stream.empty();
    }

    @Override
    public List<String> getLongDesc() {
        return Arrays.asList(
                "AI rotation recorder / trainer / lab.",
                "Usage:",
                "> ai train [profile]",
                "> ai learn [profile]",
                "> ai run [profile]",
                "> ai stop",
                "> ai lab",
                "> ai profile <name|new|delete>",
                "> ai list",
                "> ai log"
        );
    }
}
