package polaris.utils.cosmetics;

import net.minecraft.resources.Identifier;

import java.nio.file.Path;


public final class CosmeticEntry {
    
    public enum Kind {
        MODEL("Models"),
        HEAD("Head"),
        WEAPON("Weapons");

        private final String tabName;

        Kind(String tabName) {
            this.tabName = tabName;
        }

        public String tabName() {
            return tabName;
        }
    }

    private final Path folder;
    private final String id;
    private final String displayName;
    private final Kind kind;
    private Identifier preview;
    private boolean previewLoaded;

    CosmeticEntry(Path folder, String id, String displayName, Kind kind) {
        this.folder = folder;
        this.id = id;
        this.displayName = displayName;
        this.kind = kind;
    }

    public Path folder() {
        return folder;
    }

    
    public String id() {
        return id;
    }

    public String displayName() {
        return displayName;
    }

    public Kind kind() {
        return kind;
    }

    public Identifier preview() {
        return preview;
    }

    boolean previewLoaded() {
        return previewLoaded;
    }

    void setPreview(Identifier preview) {
        this.preview = preview;
        this.previewLoaded = true;
    }
}
