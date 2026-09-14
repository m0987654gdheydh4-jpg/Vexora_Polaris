package polaris.api.module.impl.visual;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import polaris.api.events.annotation.SubscribeEvent;
import polaris.api.events.impl.WorldRenderEvent;
import polaris.api.module.Module;
import polaris.api.module.ModuleCategory;
import polaris.api.settings.impl.BooleanSetting;
import polaris.api.settings.impl.ColorSetting;
import polaris.api.settings.impl.MultiModeSetting;
import polaris.api.settings.impl.NumberSetting;
import polaris.utils.render.Render3D;

import java.awt.Color;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;


public final class StorageESP extends Module {
    private static StorageESP instance;

    private static final String[] TARGETS = {
            "Chests", "Ender Chests", "Trapped Chests", "Shulkers",
            "Barrels", "Furnaces", "Blast Furnaces", "Smokers", "Hoppers", "Villagers"
    };

    private final MultiModeSetting targets = register(new MultiModeSetting(
            "Targets",
            "What to highlight.",
            TARGETS,
            "Chests", "Ender Chests", "Trapped Chests", "Shulkers",
            "Barrels", "Furnaces", "Blast Furnaces", "Smokers", "Hoppers", "Villagers"
    ));

    // Цвета по категориям
    private final ColorSetting chestColor     = register(new ColorSetting("Chest Color",     "Chests, Trapped Chests, Barrels, Hoppers.", new Color(255, 200, 80, 140)));
    private final ColorSetting enderColor     = register(new ColorSetting("Ender Chest Color", "Ender Chests.",                            new Color(30,  30,  30, 140)));
    private final ColorSetting furnaceColor   = register(new ColorSetting("Furnace Color",   "Furnaces, Blast Furnaces, Smokers.",         new Color(170, 170, 170, 140)));
    private final ColorSetting shulkerColor   = register(new ColorSetting("Shulker Color",   "All shulker boxes.",                         new Color(220, 70,  70, 140)));
    private final ColorSetting villagerColor  = register(new ColorSetting("Villager Color",  "Villagers.",                                 new Color(90,  220, 120, 150)));

    private final NumberSetting range = register(new NumberSetting("Range", "Search radius.", 48.0, 8.0, 128.0, 1.0));
    private final BooleanSetting fill = register(new BooleanSetting("Fill", "Fill boxes.", true));

    private final Map<BlockPos, BlockState> renderBlocks = new HashMap<>();
    /** Карта blockId -> ColorSetting, чтобы быстро брать нужный цвет в рендере. */
    private final Map<String, ColorSetting> colorByBlockId = new HashMap<>();
    private final Set<String> blockIds = new HashSet<>();
    private long lastScanTime;
    private int checkCounter;

    public StorageESP() {
        super("Storage ESP", "Highlights storages and villagers.", ModuleCategory.VISUAL);
        instance = this;
        rebuildBlockIds();
        targets.addListener((setting, oldValue, newValue) -> rebuildBlockIds());
    }

    public static StorageESP getInstance() {
        return instance;
    }

    private void rebuildBlockIds() {
        blockIds.clear();
        colorByBlockId.clear();

        // Жёлтый (chestColor) — сундуки, ловушки, бочки, воронки
        addColored(id(Blocks.CHEST),          chestColor);
        addColored(id(Blocks.TRAPPED_CHEST),  chestColor);
        addColored(id(Blocks.BARREL),         chestColor);
        addColored(id(Blocks.HOPPER),         chestColor);

        // Чёрный (enderColor) — эндер-сундук
        addColored(id(Blocks.ENDER_CHEST),    enderColor);

        // Серый (furnaceColor) — печи
        addColored(id(Blocks.FURNACE),        furnaceColor);
        addColored(id(Blocks.BLAST_FURNACE),  furnaceColor);
        addColored(id(Blocks.SMOKER),         furnaceColor);

        // Красный (shulkerColor) — все шалкеры
        if (targets.isSelected("Shulkers")) {
            BuiltInRegistries.BLOCK.forEach(block -> {
                String key = BuiltInRegistries.BLOCK.getKey(block).getPath();
                if (key.endsWith("shulker_box")) {
                    String full = BuiltInRegistries.BLOCK.getKey(block).toString();
                    addColored(full, shulkerColor);
                }
            });
        }
    }

    private void addColored(String id, ColorSetting color) {
        if (blockIds.contains(id)) return;
        blockIds.add(id);
        colorByBlockId.put(id, color);
    }

    private static String id(Block block) {
        return BuiltInRegistries.BLOCK.getKey(block).toString();
    }

    @Override
    protected void onEnable() {
        rebuildBlockIds();
        renderBlocks.clear();
        lastScanTime = 0L;
        checkCounter = 0;
    }

    @Override
    protected void onDisable() {
        renderBlocks.clear();
    }

    @Override
    public void onTick(Minecraft client) {
        if (client == null || client.level == null || client.player == null) {
            renderBlocks.clear();
            return;
        }
        if (blockIds.isEmpty() && !targets.isSelected("Villagers")) {
            renderBlocks.clear();
            return;
        }

        BlockPos playerPos = client.player.blockPosition();
        long now = System.nanoTime() / 1_000_000L;
        if (now - lastScanTime >= 2000L) {
            scanChunkArea(playerPos, 2, 48, true);
            lastScanTime = now;
            checkCounter = 0;
        }
        if (checkCounter % 5 == 0) {
            scanChunkArea(playerPos, 1, 24, false);
        }
        if (checkCounter % 60 == 0) {
            renderBlocks.entrySet().removeIf(entry -> {
                Block block = client.level.getBlockState(entry.getKey()).getBlock();
                return !blockIds.contains(BuiltInRegistries.BLOCK.getKey(block).toString());
            });
        }
        checkCounter++;
    }

    @SubscribeEvent
    private void onRender3D(WorldRenderEvent event) {
        if (!isEnabled() || mc.level == null || mc.player == null) {
            return;
        }

        for (Map.Entry<BlockPos, BlockState> entry : renderBlocks.entrySet()) {
            BlockPos pos = entry.getKey();
            BlockState state = entry.getValue();

            String blockName = BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
            ColorSetting colorSetting = colorByBlockId.get(blockName);
            if (colorSetting == null) continue;
            int rgb = colorSetting.getValue().getRGB();

            VoxelShape shape = state.getShape(mc.level, pos);
            if (shape.isEmpty()) {
                shape = state.getCollisionShape(mc.level, pos);
            }
            if (shape.isEmpty() || shape == Shapes.block()) {
                AABB box = new AABB(pos);
                Render3D.drawBox(box, rgb, 1.0f, true, fill.getValue(), false);
                Render3D.drawBoxOverlay(box, rgb, 1.25f);
            } else {
                Render3D.drawShapeAlternative(pos, shape, rgb, 1.0f, true, false);
                Render3D.drawShapeOverlay(pos, shape, rgb, 1.25f);
            }
        }

        if (targets.isSelected("Villagers")) {
            int villagerRgb = villagerColor.getValue().getRGB();
            double max = range.getFloat();
            double maxSq = max * max;
            for (AbstractVillager villager : mc.level.getEntitiesOfClass(AbstractVillager.class, mc.player.getBoundingBox().inflate(max))) {
                if (villager == null || !villager.isAlive()) {
                    continue;
                }
                if (mc.player.distanceToSqr(villager) > maxSq) {
                    continue;
                }
                AABB box = villager.getBoundingBox().inflate(0.05);
                Render3D.drawBox(box, villagerRgb, 1.0f, true, fill.getValue(), false);
                Render3D.drawBoxOverlay(box, villagerRgb, 1.2f);
            }
        }
    }

    private void scanChunkArea(BlockPos playerPos, int chunkRange, int yRange, boolean fullRefresh) {
        if (mc.level == null || mc.player == null || blockIds.isEmpty()) {
            if (fullRefresh) {
                renderBlocks.clear();
            }
            return;
        }
        if (fullRefresh) {
            renderBlocks.clear();
        }
        double maxDistanceSqr = (double) range.getFloat() * range.getFloat();
        int minYBase = mc.level.getMinY();
        BlockPos.MutableBlockPos mutablePos = new BlockPos.MutableBlockPos();
        for (int x = -chunkRange; x <= chunkRange; x++) {
            for (int z = -chunkRange; z <= chunkRange; z++) {
                int chunkX = (playerPos.getX() >> 4) + x;
                int chunkZ = (playerPos.getZ() >> 4) + z;
                if (!mc.level.getChunkSource().hasChunk(chunkX, chunkZ)) {
                    continue;
                }
                LevelChunk chunk = mc.level.getChunkSource().getChunkNow(chunkX, chunkZ);
                if (chunk == null) {
                    continue;
                }
                int cx = chunk.getPos().x << 4;
                int cz = chunk.getPos().z << 4;
                for (int bx = 0; bx < 16; bx++) {
                    for (int bz = 0; bz < 16; bz++) {
                        int columnX = cx + bx;
                        int columnZ = cz + bz;
                        int minY = Math.max(minYBase, playerPos.getY() - yRange);
                        int maxY = Math.min(mc.level.getHeight(Heightmap.Types.WORLD_SURFACE, columnX, columnZ), playerPos.getY() + yRange);
                        for (int by = minY; by <= maxY; by++) {
                            mutablePos.set(columnX, by, columnZ);
                            if (mc.player.distanceToSqr(columnX + 0.5D, by + 0.5D, columnZ + 0.5D) > maxDistanceSqr) {
                                continue;
                            }
                            BlockState state = mc.level.getBlockState(mutablePos);
                            String blockName = BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
                            if (!blockIds.contains(blockName)) {
                                continue;
                            }
                            if (!renderBlocks.containsKey(mutablePos)) {
                                renderBlocks.put(mutablePos.immutable(), state);
                            }
                        }
                    }
                }
            }
        }
    }
}