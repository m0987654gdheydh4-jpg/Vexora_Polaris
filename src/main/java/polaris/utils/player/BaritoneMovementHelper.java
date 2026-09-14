package polaris.utils.player;

import baritone.api.BaritoneAPI;
import baritone.api.IBaritone;
import baritone.api.pathing.goals.Goal;
import baritone.api.pathing.goals.GoalNear;
import baritone.api.pathing.goals.GoalXZ;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;


public final class BaritoneMovementHelper {
    private BaritoneMovementHelper() {
    }

    public static boolean isAvailable() {
        try {
            return BaritoneAPI.getProvider() != null && BaritoneAPI.getProvider().getPrimaryBaritone() != null;
        } catch (Throwable t) {
            return false;
        }
    }

    public static IBaritone getPrimary() {
        try {
            return BaritoneAPI.getProvider().getPrimaryBaritone();
        } catch (Throwable t) {
            return null;
        }
    }

    public static boolean isBaritoneActive() {
        return isBaritoneActive(Minecraft.getInstance().player);
    }

    public static boolean isBaritoneActive(LocalPlayer player) {
        if (isPathing()) return true;
        return isMovementControlled(player);
    }

    public static boolean isPathing() {
        try {
            IBaritone baritone = getPrimary();
            return baritone != null && baritone.getPathingBehavior() != null
                    && baritone.getPathingBehavior().isPathing();
        } catch (Throwable t) {
            return false;
        }
    }

    public static boolean isMovementControlled(LocalPlayer player) {
        if (player == null || player.input == null) {
            return false;
        }
        String inputName = player.input.getClass().getName().toLowerCase(java.util.Locale.ROOT);
        return inputName.contains("baritone") || inputName.contains("playermovementinput");
    }

    public static void cancel() {
        try {
            IBaritone baritone = getPrimary();
            if (baritone != null && baritone.getPathingBehavior() != null) {
                baritone.getPathingBehavior().cancelEverything();
            }
        } catch (Throwable ignored) {
        }
    }

    
    public static boolean isBusy() {
        if (isPathing()) {
            return true;
        }
        try {
            IBaritone b = getPrimary();
            if (b == null) {
                return false;
            }
            if (b.getBuilderProcess() != null && b.getBuilderProcess().isActive()) {
                return true;
            }
            if (b.getCustomGoalProcess() != null && b.getCustomGoalProcess().isActive()) {
                return true;
            }
        } catch (Throwable ignored) {
        }
        return isMovementControlled(Minecraft.getInstance().player);
    }

    
    public static boolean startTunnel(float yawDeg, int height, int width, int depth) {
        try {
            IBaritone baritone = getPrimary();
            if (baritone == null) {
                return false;
            }
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) {
                
                mc.player.setYRot(yawDeg);
                mc.player.setYHeadRot(yawDeg);
                mc.player.yBodyRot = yawDeg;
            }
            cancel();
            height = Math.max(1, Math.min(10, height));
            width = Math.max(1, Math.min(10, width));
            String cmd;
            if (depth > 0) {
                cmd = "tunnel " + height + " " + width + " " + depth;
            } else {
                cmd = "tunnel " + height + " " + width;
            }
            if (baritone.getCommandManager() != null && baritone.getCommandManager().execute(cmd)) {
                return true;
            }
            
            return baritone.getCommandManager() != null && baritone.getCommandManager().execute("tunnel");
        } catch (Throwable t) {
            return false;
        }
    }

    public static boolean startTunnel(float yawDeg) {
        return startTunnel(yawDeg, 2, 1, 0);
    }

    public static void goNear(BlockPos pos, int range) {
        if (pos == null) return;
        try {
            IBaritone baritone = getPrimary();
            if (baritone == null) return;
            Goal goal = new GoalNear(pos, Math.max(0, range));
            baritone.getCustomGoalProcess().setGoalAndPath(goal);
        } catch (Throwable ignored) {
        }
    }

    
    public static void goOnBlock(BlockPos pos) {
        goNear(pos, 0);
    }

    public static void goXZ(int x, int z) {
        try {
            IBaritone baritone = getPrimary();
            if (baritone == null) return;
            baritone.getCustomGoalProcess().setGoalAndPath(new GoalXZ(x, z));
        } catch (Throwable ignored) {
        }
    }

    public static void setAllowSprint(boolean allow) {
        try {
            BaritoneAPI.getSettings().allowSprint.value = allow;
        } catch (Throwable ignored) {
        }
    }

    public static boolean getAllowSprint() {
        try {
            return Boolean.TRUE.equals(BaritoneAPI.getSettings().allowSprint.value);
        } catch (Throwable t) {
            return true;
        }
    }

    
    public static void applySmoothDigProfile(boolean allowBreak, boolean allowPlace) {
        try {
            var s = BaritoneAPI.getSettings();
            setBool(s, "allowBreak", allowBreak);
            setBool(s, "allowPlace", allowPlace);
            setBool(s, "allowParkour", false);
            setBool(s, "allowParkourPlace", false);
            setBool(s, "allowParkourAscend", false);
            setBool(s, "allowDiagonalAscend", true);
            setBool(s, "allowDiagonalDescend", true);
            setBool(s, "allowSprint", true);
            setBool(s, "sprintAscend", true);
            setBool(s, "antiCheatCompatibility", true);
            setBool(s, "chatDebug", false);
            setBool(s, "chatControl", false);
            setBool(s, "freeLook", true);
            setBool(s, "blockFreeLook", false);
            setDouble(s, "blockPlacementPenalty", 2.0);
            setDouble(s, "blockBreakAdditionalPenalty", 1.5);
            setDouble(s, "jumpPenalty", 3.0);
            setDouble(s, "costHeuristic", 3.5);
            setDouble(s, "pathCutoffFactor", 0.92);
            setLong(s, "primaryTimeoutMS", 2500L);
            setLong(s, "failureTimeoutMS", 4000L);
            setInt(s, "maxFallHeightNoWater", 3);
            setInt(s, "planningTickLookahead", 140);
        } catch (Throwable ignored) {
        }
    }

    public static void goForwardXZ(double yawDeg, int distanceBlocks) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }
        double rad = Math.toRadians(yawDeg);
        
        double dx = -Math.sin(rad) * distanceBlocks;
        double dz = Math.cos(rad) * distanceBlocks;
        int tx = (int) Math.floor(mc.player.getX() + dx);
        int tz = (int) Math.floor(mc.player.getZ() + dz);
        goXZ(tx, tz);
    }

    
    public static void avoidSoftTerrain() {
        String[] soft = {
                "minecraft:dirt", "minecraft:grass_block", "minecraft:dirt_path",
                "minecraft:coarse_dirt", "minecraft:rooted_dirt", "minecraft:mud",
                "minecraft:muddy_mangrove_roots", "minecraft:podzol", "minecraft:mycelium",
                "minecraft:farmland", "minecraft:gravel", "minecraft:sand", "minecraft:red_sand",
                "minecraft:clay", "minecraft:soul_sand", "minecraft:soul_soil",
                "minecraft:snow", "minecraft:snow_block", "minecraft:powder_snow",
                "minecraft:suspicious_sand", "minecraft:suspicious_gravel",
                "minecraft:pale_moss_block", "minecraft:moss_block"
        };
        for (String id : soft) {
            addToAvoidBreaking(id);
            addToAvoidWalking(id);
        }
    }

    private static void addToAvoidBreaking(String id) {
        try {
            var s = BaritoneAPI.getSettings();
            Object field = s.getClass().getField("blocksToAvoidBreaking").get(s);
            Object value = field.getClass().getField("value").get(field);
            if (value instanceof java.util.List<?> list) {
                @SuppressWarnings("unchecked")
                java.util.List<Object> mut = (java.util.List<Object>) list;
                addAvoidEntry(mut, id);
            }
        } catch (Throwable ignored) {
            try {
                var s = BaritoneAPI.getSettings();
                Object field = s.getClass().getField("blocksToDisallowBreaking").get(s);
                Object value = field.getClass().getField("value").get(field);
                if (value instanceof java.util.List<?> list) {
                    @SuppressWarnings("unchecked")
                    java.util.List<Object> mut = (java.util.List<Object>) list;
                    addAvoidEntry(mut, id);
                }
            } catch (Throwable ignored2) {
            }
        }
    }

    private static void addToAvoidWalking(String id) {
        try {
            var s = BaritoneAPI.getSettings();
            Object field = s.getClass().getField("blocksToAvoid").get(s);
            Object value = field.getClass().getField("value").get(field);
            if (value instanceof java.util.List<?> list) {
                @SuppressWarnings("unchecked")
                java.util.List<Object> mut = (java.util.List<Object>) list;
                addAvoidEntry(mut, id);
            }
        } catch (Throwable ignored) {
        }
    }

    
    public static void avoidBreakingEmeraldOre() {
        try {
            var s = BaritoneAPI.getSettings();
            
            Object field = s.getClass().getField("blocksToAvoidBreaking").get(s);
            Object value = field.getClass().getField("value").get(field);
            if (value instanceof java.util.List<?> list) {
                @SuppressWarnings("unchecked")
                java.util.List<Object> mut = (java.util.List<Object>) list;
                addAvoidEntry(mut, "minecraft:emerald_ore");
                addAvoidEntry(mut, "minecraft:deepslate_emerald_ore");
            }
        } catch (Throwable ignored) {
            
            try {
                var s = BaritoneAPI.getSettings();
                Object field = s.getClass().getField("blocksToDisallowBreaking").get(s);
                Object value = field.getClass().getField("value").get(field);
                if (value instanceof java.util.List<?> list) {
                    @SuppressWarnings("unchecked")
                    java.util.List<Object> mut = (java.util.List<Object>) list;
                    addAvoidEntry(mut, "minecraft:emerald_ore");
                    addAvoidEntry(mut, "minecraft:deepslate_emerald_ore");
                }
            } catch (Throwable ignored2) {
            }
        }
    }

    private static void addAvoidEntry(java.util.List<Object> list, String id) {
        if (list == null || id == null) {
            return;
        }
        for (Object o : list) {
            if (o != null && id.equalsIgnoreCase(String.valueOf(o))) {
                return;
            }
        }
        try {
            list.add(id);
        } catch (Throwable t) {
            
        }
    }

    private static void setBool(Object settings, String name, boolean value) {
        try {
            Object field = settings.getClass().getField(name).get(settings);
            field.getClass().getField("value").set(field, value);
        } catch (Throwable ignored) {
        }
    }

    private static void setDouble(Object settings, String name, double value) {
        try {
            Object field = settings.getClass().getField(name).get(settings);
            field.getClass().getField("value").set(field, value);
        } catch (Throwable ignored) {
        }
    }

    private static void setLong(Object settings, String name, long value) {
        try {
            Object field = settings.getClass().getField(name).get(settings);
            field.getClass().getField("value").set(field, value);
        } catch (Throwable ignored) {
        }
    }

    private static void setInt(Object settings, String name, int value) {
        try {
            Object field = settings.getClass().getField(name).get(settings);
            field.getClass().getField("value").set(field, value);
        } catch (Throwable ignored) {
        }
    }
}
