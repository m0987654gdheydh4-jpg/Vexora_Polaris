package polaris.utils.cosmetics;

import com.mojang.blaze3d.platform.NativeImage;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import polaris.utils.render.ui.Render2D;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;


public final class CosmeticsRepository {
    private static final String FOLDER = "cosmetics";
    private static final String AVATAR_FILE = "avatar.json";
    private static final String PREVIEW_FILE = "avatar.png";
    private static final String HEAD_PREFIX = "head-";
    private static final String WEAPON_PREFIX = "weapon-";

    private static final List<CosmeticEntry> entries = new ArrayList<>();
    private static boolean scanned;

    private CosmeticsRepository() {
    }

    public static Path directory() {
        return FabricLoader.getInstance().getGameDir().resolve("Polaris").resolve(FOLDER);
    }

    public static List<CosmeticEntry> all() {
        if (!scanned) {
            rescan();
        }
        return entries;
    }

    public static List<CosmeticEntry> of(CosmeticEntry.Kind kind) {
        List<CosmeticEntry> filtered = new ArrayList<>();
        for (CosmeticEntry entry : all()) {
            if (entry.kind() == kind) {
                filtered.add(entry);
            }
        }
        return filtered;
    }

    public static CosmeticEntry byId(String id) {
        if (id == null || id.isBlank()) {
            return null;
        }
        for (CosmeticEntry entry : all()) {
            if (entry.id().equals(id)) {
                return entry;
            }
        }
        return null;
    }

    public static void rescan() {
        scanned = true;
        entries.clear();
        Path root = directory();
        if (!Files.isDirectory(root)) {
            try {
                Files.createDirectories(root);
            } catch (Throwable ignored) {
                
                
            }
            return;
        }
        try (Stream<Path> stream = Files.list(root)) {
            stream.filter(Files::isDirectory)
                    .filter(dir -> Files.isRegularFile(dir.resolve(AVATAR_FILE)))
                    .forEach(dir -> entries.add(toEntry(dir)));
        } catch (Throwable ignored) {
        }
        entries.sort(Comparator
                .comparing((CosmeticEntry e) -> e.kind().ordinal())
                .thenComparing(e -> e.displayName().toLowerCase(Locale.ROOT)));
    }

    private static CosmeticEntry toEntry(Path dir) {
        String folderName = dir.getFileName().toString();
        String lower = folderName.toLowerCase(Locale.ROOT);
        CosmeticEntry.Kind kind;
        String label;
        if (lower.startsWith(HEAD_PREFIX)) {
            kind = CosmeticEntry.Kind.HEAD;
            label = folderName.substring(HEAD_PREFIX.length());
        } else if (lower.startsWith(WEAPON_PREFIX)) {
            kind = CosmeticEntry.Kind.WEAPON;
            label = folderName.substring(WEAPON_PREFIX.length());
        } else {
            kind = CosmeticEntry.Kind.MODEL;
            label = folderName;
        }
        
        int dash = label.indexOf(" - ");
        if (dash > 0) {
            label = label.substring(0, dash);
        }
        return new CosmeticEntry(dir, folderName, label.replace('_', ' ').trim(), kind);
    }

    
    public static Identifier preview(CosmeticEntry entry) {
        if (entry == null) {
            return null;
        }
        if (entry.previewLoaded()) {
            return entry.preview();
        }
        entry.setPreview(null);
        Path file = entry.folder().resolve(PREVIEW_FILE);
        if (!Files.isRegularFile(file)) {
            return null;
        }
        try (InputStream in = Files.newInputStream(file)) {
            NativeImage image = NativeImage.read(in);
            Identifier id = Identifier.fromNamespaceAndPath("cataclysm",
                    "cosmetics/" + sanitize(entry.id()));
            Minecraft.getInstance().getTextureManager()
                    .register(id, new DynamicTexture(entry::id, image));
            Render2D.invalidateImageTexture(id);
            entry.setPreview(id);
            return id;
        } catch (Throwable ignored) {
            return null;
        }
    }

    
    private static String sanitize(String raw) {
        StringBuilder out = new StringBuilder(raw.length());
        for (char c : raw.toLowerCase(Locale.ROOT).toCharArray()) {
            out.append((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '_' || c == '.' || c == '-'
                    ? c : '_');
        }
        return out.toString();
    }
}
