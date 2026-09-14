package polaris.utils.render.ui.gif;

import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
import net.fabricmc.fabric.api.resource.v1.reloader.SimpleResourceReloader;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.PreparableReloadListener;


public final class MainMenuGifPreloader {
    public static final Identifier BACK_GIF = Identifier.fromNamespaceAndPath("cataclysm", "ui/back.gif");
    public static final float BACK_GIF_FPS = 30f;

    private static final Identifier RELOADER_ID = Identifier.fromNamespaceAndPath("cataclysm", "mainmenu_gif");
    private static final GifRenderer BACKGROUND = new GifRenderer(BACK_GIF, BACK_GIF_FPS);

    private static boolean registered;

    private MainMenuGifPreloader() {
    }

    public static void init() {
        if (registered) {
            return;
        }
        ResourceLoader.get(PackType.CLIENT_RESOURCES).registerReloader(RELOADER_ID,
                new SimpleResourceReloader<GifRenderer.Decoded>() {
                    @Override
                    protected GifRenderer.Decoded prepare(PreparableReloadListener.SharedState state) {
                        try {
                            return BACKGROUND.decodeFrom(state.resourceManager());
                        } catch (Throwable t) {
                            return null;
                        }
                    }

                    @Override
                    protected void apply(GifRenderer.Decoded prepared, PreparableReloadListener.SharedState state) {
                        if (prepared == null) {
                            BACKGROUND.markFailed("Failed to decode main menu GIF during resource reload");
                            return;
                        }
                        BACKGROUND.applyDecoded(prepared);
                    }
                });
        registered = true;
    }

    public static GifRenderer background() {
        return BACKGROUND;
    }
}
