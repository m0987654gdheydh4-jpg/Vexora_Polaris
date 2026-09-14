package polaris.api.drag.impl;

import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.entity.player.Player;
import polaris.utils.render.animation.Easings;
import polaris.utils.render.animation.SmoothAnimation;
import polaris.utils.render.color.ColorUtil;
import polaris.utils.render.ui.Render2D;
import polaris.utils.render.ScissorUtil;
import polaris.utils.render.ui.Render2DCoordinateSpace;
import polaris.utils.repository.staff.StaffUtils;

import java.awt.Color;
import java.util.*;

public final class Staff extends HudPanel {
    private static final float FACE_SIZE = 10f;
    private static final float ROW_HEIGHT = 16f;
    private static final float HEADER_HEIGHT = 20f;
    private static final float PANEL_ANIM = 0.30F;
    private static final float ROW_SLIDE_IN = 5.0F;

    private static final String TEXT_FONT = "main";
    private static final int TEXT_COLOR = 0xFFFFFFFF;
    private static final float CORNER_MEDIUM = 4.0F;

    private final Map<String, StaffInfo> staffMap = new LinkedHashMap<>();
    private final Map<String, Float> staffAnimations = new LinkedHashMap<>();
    private final Set<String> activeStaffIds = new HashSet<>();
    private long lastUpdateTime = System.currentTimeMillis();
    private float animatedWidth = 120;
    private float animatedHeight = 20;

    private final SmoothAnimation panelAnimation = new SmoothAnimation();

    public Staff() {
        super("staff", "Staff", 300.0F, 150.0F, 140.0F, 20.0F);
    }

    @Override
    public void render() {
        if (!selected()) {
            contentVisible(false);
            return;
        }
        StaffState state = logics();
        if (state == null) return;
        renderStaff(state);
    }

    private StaffState logics() {
        if (mc.player == null || mc.level == null) {
            staffMap.clear();
            activeStaffIds.clear();
            contentVisible(false);
            return null;
        }

        activeStaffIds.clear();
        if (mc.getConnection() != null) {
            for (PlayerInfo entry : mc.getConnection().getOnlinePlayers()) {
                String name = entry.getProfile().name();
                boolean isSpec = entry.getGameMode() == net.minecraft.world.level.GameType.SPECTATOR;
                String displayStr = entry.getTabListDisplayName() != null
                        ? entry.getTabListDisplayName().getString().toLowerCase()
                        : "";

                if (isStaffPrefix(displayStr) || isSpec || isStaffName(name)) {
                    activeStaffIds.add(name);

                    String role = isSpec && !isStaffPrefix(displayStr) ? "Спектатор" : detectRole(displayStr);
                    Color roleColor = detectRoleColor(displayStr);
                    String status = "ИГРАЕТ";
                    Color statusColor = new Color(50, 200, 50);

                    if (isSpec) {
                        status = "СПЕК";
                        statusColor = new Color(255, 75, 75);
                    }
                    Player worldPlayer = mc.level.getPlayerByUUID(entry.getProfile().id());
                    if (worldPlayer == null) {
                        worldPlayer = mc.level.players().stream()
                                .filter(p -> p.getUUID().equals(entry.getProfile().id()))
                                .findFirst()
                                .orElse(null);
                    }

                    if (worldPlayer != null && worldPlayer != mc.player) {
                        float dist = mc.player.distanceTo(worldPlayer);
                        status = String.format(Locale.US, "%.1fm", dist);
                        if (!isSpec) statusColor = new Color(255, 200, 50);
                    }


                    String skin = skin(worldPlayer, entry, name);
                    if (!staffMap.containsKey(name)) {
                        staffMap.put(name, new StaffInfo(name, role, roleColor, skin, status, statusColor));
                    } else {
                        StaffInfo info = staffMap.get(name);
                        info.skin = skin;
                        info.status = status;
                        info.statusColor = statusColor;
                        info.role = role;
                        info.roleColor = roleColor;
                    }
                    staffAnimations.putIfAbsent(name, 0f);
                }
            }
        }

        boolean hasActiveStaff = !activeStaffIds.isEmpty() || !staffAnimations.isEmpty();
        boolean preview = !hasActiveStaff && editPreview();

        panelAnimation.update();
        panelAnimation.run(hasActiveStaff || preview ? 1.0 : 0.0, PANEL_ANIM, hasActiveStaff || preview ? Easings.EXPO_OUT : Easings.EXPO_IN, true);
        float alpha = panelAnimation.get();
        contentVisible(hasActiveStaff || preview || alpha > 0.01F);
        if (alpha <= 0.0F && !panelAnimation.isAlive() && !hasActiveStaff && !preview) return null;

        long currentTime = System.currentTimeMillis();
        float deltaTime = (currentTime - lastUpdateTime) / 1000.0f;
        lastUpdateTime = currentTime;
        deltaTime = Math.min(deltaTime, 0.1f);

        List<String> toRemove = new ArrayList<>();
        for (Map.Entry<String, Float> entry : staffAnimations.entrySet()) {
            String id = entry.getKey();
            float currentAnim = entry.getValue();
            float targetAnim = activeStaffIds.contains(id) ? 1f : 0f;
            float speed = targetAnim > currentAnim ? 11.0F : 8.5F;
            float newAnim = smooth(currentAnim, targetAnim, deltaTime, speed);
            if (Math.abs(newAnim - targetAnim) < 0.01f) newAnim = targetAnim;
            if (newAnim <= 0.01f && targetAnim == 0f) toRemove.add(id);
            else staffAnimations.put(id, newAnim);
        }
        for (String id : toRemove) {
            staffAnimations.remove(id);
            staffMap.remove(id);
        }

        float x = drag.x();
        float y = drag.y();

        float targetWidth = 120;
        float contentRows = 0;

        if (preview) {
            contentRows = 1;
            float roleW = Math.max(26, Render2D.textWidth(TEXT_FONT, "Хелпер", 5.5f) + 8);
            float nameWidth = Render2D.textWidth(TEXT_FONT, "PIVONER", 7f);
            float statusWidth = Render2D.textWidth(TEXT_FONT, "15.4m", 5.5f) + 14;
            targetWidth = Math.max(8 + roleW + 6 + FACE_SIZE + 5 + nameWidth + 8 + statusWidth + 8, targetWidth);
        } else {
            for (Map.Entry<String, Float> entry : staffAnimations.entrySet()) {
                float anim = entry.getValue();
                if (anim <= 0) continue;
                StaffInfo info = staffMap.get(entry.getKey());
                if (info == null) continue;
                contentRows += anim;
                float roleW = 26;
                if (info.role != null) roleW = Math.max(roleW, Render2D.textWidth(TEXT_FONT, info.role, 5.5f) + 8);
                float nameWidth = Render2D.textWidth(TEXT_FONT, info.name, 7f);
                float statusWidth = Render2D.textWidth(TEXT_FONT, info.status, 5.5f) + 14;
                float rowWidth = 8 + roleW + 6 + FACE_SIZE + 5 + nameWidth + 8 + statusWidth + 8;
                targetWidth = Math.max(rowWidth, targetWidth);
            }
        }

        float targetHeight = HEADER_HEIGHT + contentRows * ROW_HEIGHT + 4;
        animatedWidth = smooth(animatedWidth, targetWidth, deltaTime, 8.0F);
        animatedHeight = smooth(animatedHeight, targetHeight, deltaTime, 8.0F);
        if (Math.abs(animatedWidth - targetWidth) < 0.3f) animatedWidth = targetWidth;
        if (Math.abs(animatedHeight - targetHeight) < 0.3f) animatedHeight = targetHeight;

        size(animatedWidth, animatedHeight);

        return new StaffState(preview, alpha, x, y, animatedWidth, animatedHeight);
    }

    private void renderStaff(StaffState state) {
        float x = state.x;
        float y = state.y;
        float w = state.width;
        float h = state.height;
        int bgAlpha = (int) (255 * state.alpha);

        drawPanel(x, y, w, h, bgAlpha, CORNER_MEDIUM);
        drawIosIndicator(x, y, w, h, bgAlpha);

        drawPanelHeader(x, y, w, "Персонал", "c", bgAlpha);

        float rowY = y + HEADER_HEIGHT;

        if (state.preview) {
            String defaultSkin = defaultSkin("PIVONER");
            drawStaffRow(x, rowY, animatedWidth,
                    "Хелпер", new Color(100, 100, 255),
                    defaultSkin, "PIVONER",
                    "15.4m", new Color(255, 200, 50),
                    1.0f, (int) (255 * state.alpha));
        } else {
            for (Map.Entry<String, Float> entry : staffAnimations.entrySet()) {
                float anim = entry.getValue();
                if (anim <= 0) continue;
                StaffInfo info = staffMap.get(entry.getKey());
                if (info == null) continue;

                int rowAlpha = (int) (255 * anim * state.alpha);
                float slide = (1f - anim) * -ROW_SLIDE_IN;
                float lift = rowAppearLift(anim);
                drawStaffRow(x + slide, rowY + lift, animatedWidth,
                        info.role, info.roleColor,
                        info.skin, info.name,
                        info.status, info.statusColor,
                        anim, rowAlpha);
                rowY += anim * ROW_HEIGHT;
            }
        }
    }

    private void drawStaffRow(float x, float rowY, float width,
                              String role, Color roleColor, String skin,
                              String name, String status, Color statusColor,
                              float animation, int alpha) {
        float centerY = rowY + (ROW_HEIGHT / 2f);
        int textColor = withAlpha(TEXT_COLOR, alpha);

        final float pad = 8f;
        final float gap = 6f;
        final float gapName = 5f;

        float roleW = 26f;
        if (role != null) roleW = Math.max(roleW, Render2D.textWidth(TEXT_FONT, role, 5.5f) + 8);

        float roleX = x + pad;
        float faceX = roleX + roleW + gap;
        float nameX = faceX + FACE_SIZE + gapName;

        String cleanName = (name == null || name.isEmpty()) ? "Игрок" : name;

        float statusW = Render2D.textWidth(TEXT_FONT, status, 5.5f) + 14f;
        float statusX = x + width - statusW - pad;

        Render2D.text(TEXT_FONT, cleanName, nameX, centerY - 3f, 7f, textColor);

        int roleBgAlpha = (int) (alpha * 0.22f);
        int roleBgColor = role != null
                ? new Color(roleColor.getRed(), roleColor.getGreen(), roleColor.getBlue(), roleBgAlpha).getRGB()
                : new Color(50, 50, 55, (int) (alpha * 0.4f)).getRGB();
        Render2D.rect(roleX, centerY - 4.5f, roleW, 9f, 2.5f, roleBgColor);
        if (role != null) {
            float roleTextW = Render2D.textWidth(TEXT_FONT, role, 5.5f);
            Render2D.text(TEXT_FONT, role, roleX + (roleW - roleTextW) / 2f, centerY - 3f, 5.5f,
                    new Color(roleColor.getRed(), roleColor.getGreen(), roleColor.getBlue(), alpha).getRGB());
        }

        int statusBgAlpha = (int) (alpha * 0.18f);
        Render2D.rect(statusX, centerY - 4.5f, statusW, 9f, 2.5f,
                new Color(statusColor.getRed(), statusColor.getGreen(), statusColor.getBlue(), statusBgAlpha).getRGB());

        Render2D.rect(statusX + 4f, centerY - 1.5f, 3f, 3f, 1.5f,
                new Color(statusColor.getRed(), statusColor.getGreen(), statusColor.getBlue(), alpha).getRGB());

        Render2D.text(TEXT_FONT, status, statusX + 11f, centerY - 3f, 5.5f,
                new Color(statusColor.getRed(), statusColor.getGreen(), statusColor.getBlue(), alpha).getRGB());

        drawFace(skin, faceX, centerY - FACE_SIZE / 2f, alpha);
    }

    private void drawFace(String skin, float faceX, float faceY, int alpha) {
        int color = ColorUtil.rgba(255, 255, 255, alpha);
        String texture = skin == null || skin.isBlank() ? defaultSkin("default") : skin;
        boolean base = renderSkinPart(texture, faceX, faceY, FACE_SIZE, 8.0F / 64.0F, 8.0F / 64.0F, 16.0F / 64.0F, 16.0F / 64.0F, color);
        boolean overlay = renderSkinPart(texture, faceX, faceY, FACE_SIZE, 40.0F / 64.0F, 8.0F / 64.0F, 48.0F / 64.0F, 16.0F / 64.0F, color);
        if (!base && !overlay) {
            Render2D.image(texture, faceX, faceY, FACE_SIZE, 2.0F, color);
        }
    }

    private boolean renderSkinPart(String texture, float x, float y, float size, float u0, float v0, float u1, float v1, int color) {
        if (texture == null || texture.isBlank() || color >>> 24 == 0) return false;
        Render2D.imageUvNearest(texture, x, y, size, size, 2.0F, 1.0F, u0, v0, u1, v1, color);
        return true;
    }

    private String skin(Player player, PlayerInfo info, String name) {
        try {
            if (player instanceof AbstractClientPlayer clientPlayer) {
                return clientPlayer.getSkin().body().texturePath().toString();
            }
            if (info != null) {
                return info.getSkin().body().texturePath().toString();
            }
        } catch (RuntimeException ignored) {
        }
        return defaultSkin(name);
    }

    private String defaultSkin(String name) {
        return ((name == null ? 0 : name.hashCode()) & 1) == 0
                ? "minecraft:textures/entity/player/wide/steve.png"
                : "minecraft:textures/entity/player/slim/alex.png";
    }

    private boolean isStaffName(String name) {
        for (String staff : StaffUtils.getStaffNames()) {
            String key = StaffUtils.normalizeName(staff).toLowerCase();
            if (StaffUtils.normalizeName(name).toLowerCase().equals(key)) return true;
        }
        return false;
    }

    private boolean isStaffPrefix(String text) {
        String low = text.toLowerCase();
        return low.contains("стажер") || low.contains("хелпер") || low.contains("админ") ||
                low.contains("модер") || low.contains("ютубер") || low.contains("владелец") ||
                low.contains("куратор") || low.contains("owner") || low.contains("admin") ||
                low.contains("mod") || low.contains("helper") || low.contains("support");
    }

    private String detectRole(String prefix) {
        if (prefix.contains("владелец") || prefix.contains("owner")) return "Владелец";
        if (prefix.contains("админ") || prefix.contains("admin")) return "Админ";
        if (prefix.contains("модер") || prefix.contains("mod")) return "Модер";
        if (prefix.contains("хелпер") || prefix.contains("helper")) return "Хелпер";
        if (prefix.contains("стажер")) return "Стажёр";
        if (prefix.contains("ютубер") || prefix.contains("youtube")) return "ЮТубер";
        if (prefix.contains("куратор")) return "Куратор";
        if (prefix.contains("support")) return "Саппорт";
        return "Staff";
    }


    private Color detectRoleColor(String prefix) {
        if (prefix.contains("владелец") || prefix.contains("owner")) return new Color(255, 50, 50);
        if (prefix.contains("админ") || prefix.contains("admin")) return new Color(255, 75, 75);
        if (prefix.contains("модер") || prefix.contains("mod")) return new Color(75, 150, 255);
        if (prefix.contains("хелпер") || prefix.contains("helper")) return new Color(100, 100, 255);
        if (prefix.contains("стажер")) return new Color(150, 255, 150);
        if (prefix.contains("ютубер")) return new Color(255, 50, 50);
        if (prefix.contains("куратор")) return new Color(255, 150, 50);
        return new Color(150, 150, 150);
    }

    private static class StaffInfo {
        String name;
        String role;
        Color roleColor;
        String skin;
        String status;
        Color statusColor;

        StaffInfo(String name, String role, Color roleColor, String skin, String status, Color statusColor) {
            this.name = name;
            this.role = role;
            this.roleColor = roleColor;
            this.skin = skin;
            this.status = status;
            this.statusColor = statusColor;
        }
    }

    private record StaffState(boolean preview, float alpha, float x, float y, float width, float height) {
    }
}
