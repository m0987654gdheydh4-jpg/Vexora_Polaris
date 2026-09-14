package polaris.utils.render.ui.gif;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import polaris.utils.render.ui.Render2D;
import polaris.utils.render.ui.image.ImageRenderer;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.metadata.IIOMetadataNode;
import javax.imageio.stream.ImageInputStream;
import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;


public final class GifRenderer implements AutoCloseable {
    public static final float DEFAULT_FPS = 30f;

    private final Identifier resourceId;
    private final Identifier textureId;
    private final float fps;

    private final AtomicBoolean loading = new AtomicBoolean(false);
    private volatile boolean ready;
    private volatile boolean failed;
    private volatile String failReason;

    
    private static final long DECODE_BUDGET_BYTES = 192L << 20;

    private int frameW;
    private int frameH;
    
    private int[][] framePixels = new int[0][];
    private NativeImage uploadImage;

    private DynamicTexture texture;
    private int currentFrame = -1;
    private long lastFrameNs;
    private long frameDurationNs;

    public GifRenderer(Identifier resourceId) {
        this(resourceId, DEFAULT_FPS);
    }

    public GifRenderer(Identifier resourceId, float fps) {
        this.resourceId = resourceId;
        this.fps = Math.max(1f, fps);
        this.frameDurationNs = (long) (1_000_000_000L / this.fps);
        this.textureId = Identifier.fromNamespaceAndPath(
                resourceId.getNamespace(),
                "dynamic/gif/" + resourceId.getPath().replace('/', '_').replace('.', '_')
        );
    }

    public boolean isReady() {
        return ready && framePixels.length > 0 && texture != null;
    }

    public boolean isFailed() {
        return failed;
    }

    public String failReason() {
        return failReason;
    }

    public boolean isLoading() {
        return loading.get();
    }

    public int frameCount() {
        return framePixels.length;
    }

    public int width() {
        return frameW;
    }

    public int height() {
        return frameH;
    }

    public Identifier textureId() {
        return textureId;
    }

    
    public Decoded decodeFrom(net.minecraft.server.packs.resources.ResourceManager resources) throws Exception {
        Optional<Resource> res = resources.getResource(resourceId);
        if (res.isEmpty()) {
            throw new IllegalStateException("Resource missing: " + resourceId);
        }

        try (InputStream in = res.get().open();
             ImageInputStream iis = ImageIO.createImageInputStream(in)) {
            if (iis == null) {
                throw new IllegalStateException("Cannot open image stream");
            }

            Iterator<ImageReader> readers = ImageIO.getImageReadersByFormatName("gif");
            if (!readers.hasNext()) {
                readers = ImageIO.getImageReaders(iis);
                iis.seek(0);
            }
            if (!readers.hasNext()) {
                throw new IllegalStateException("No GIF ImageReader available");
            }

            ImageReader reader = readers.next();
            try {
                reader.setInput(iis, false, false);
                int count = reader.getNumImages(true);
                if (count <= 0) {
                    throw new IllegalStateException("GIF has no frames");
                }

                int w = reader.getWidth(0);
                int h = reader.getHeight(0);
                try {
                    IIOMetadata sm = reader.getStreamMetadata();
                    if (sm != null) {
                        IIOMetadataNode root = (IIOMetadataNode) sm.getAsTree(sm.getNativeMetadataFormatName());
                        IIOMetadataNode desc = getNode(root, "LogicalScreenDescriptor");
                        if (desc != null) {
                            w = Integer.parseInt(desc.getAttribute("logicalScreenWidth"));
                            h = Integer.parseInt(desc.getAttribute("logicalScreenHeight"));
                        }
                    }
                } catch (Throwable ignored) {
                }

                List<int[]> decodedArgb = compositeFrames(reader, count, w, h);
                if (decodedArgb == null || decodedArgb.isEmpty()) {
                    throw new IllegalStateException("No frames decoded");
                }
                return new Decoded(w, h, decodedArgb.toArray(new int[0][]));
            } finally {
                reader.dispose();
            }
        }
    }

    
    public void applyDecoded(Decoded decoded) {
        if (decoded == null || decoded.frames == null || decoded.frames.length == 0) {
            fail("No frames decoded");
            return;
        }
        try {
            releaseGpu();
            this.failed = false;
            this.failReason = null;
            this.frameW = decoded.width;
            this.frameH = decoded.height;
            this.framePixels = decoded.frames;
            uploadInitial(decoded.frames[0], decoded.width, decoded.height);
            this.ready = true;
            this.lastFrameNs = System.nanoTime();
            this.loading.set(false);
        } catch (Throwable t) {
            fail(t.getMessage() != null ? t.getMessage() : t.getClass().getSimpleName());
        }
    }

    public void markFailed(String reason) {
        fail(reason);
    }

    public void ensureLoaded() {
        if (ready || failed || !loading.compareAndSet(false, true)) {
            return;
        }
        Thread t = new Thread(this::loadBlocking, "gif-load-" + resourceId.getPath());
        t.setDaemon(true);
        t.start();
    }

    private void loadBlocking() {
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc == null || mc.getResourceManager() == null) {
                fail("Minecraft not ready");
                return;
            }

            Decoded decoded = decodeFrom(mc.getResourceManager());
            Minecraft.getInstance().execute(() -> applyDecoded(decoded));
        } catch (Throwable t) {
            fail(t.getMessage() != null ? t.getMessage() : t.getClass().getSimpleName());
        }
    }

    public record Decoded(int width, int height, int[][] frames) {
    }

    
    private static List<int[]> compositeFrames(ImageReader reader, int count, int screenW, int screenH) throws Exception {
        
        
        
        long bytesPerFrame = (long) screenW * screenH * Integer.BYTES;
        int budgetFrames = bytesPerFrame <= 0
                ? count
                : (int) Math.max(1L, DECODE_BUDGET_BYTES / bytesPerFrame);
        if (budgetFrames < count) {
            System.err.println("[Polaris] GIF has " + count + " frames at " + screenW + "x" + screenH
                    + "; keeping the first " + budgetFrames + " to stay inside the "
                    + (DECODE_BUDGET_BYTES >> 20) + " MB decode budget.");
            count = budgetFrames;
        }

        List<int[]> out = new ArrayList<>(count);

        BufferedImage canvas = new BufferedImage(screenW, screenH, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = canvas.createGraphics();
        g.setComposite(AlphaComposite.SrcOver);
        g.setBackground(new Color(0, 0, 0, 0));
        g.clearRect(0, 0, screenW, screenH);

        BufferedImage restore = null;
        int lastDisposal = 0;
        int lastX = 0, lastY = 0, lastW = 0, lastH = 0;

        for (int i = 0; i < count; i++) {
            BufferedImage raw = reader.read(i);
            if (raw == null) continue;

            FrameMeta meta = readFrameMeta(reader, i);
            int fx = meta.x;
            int fy = meta.y;
            int fw = raw.getWidth();
            int fh = raw.getHeight();

            
            if (i > 0) {
                applyDisposal(g, canvas, restore, lastDisposal, lastX, lastY, lastW, lastH, screenW, screenH);
            }

            if (meta.disposal == 3) {
                
                restore = copyImage(canvas);
            } else {
                restore = null;
            }

            g.setComposite(AlphaComposite.SrcOver);
            g.drawImage(raw, fx, fy, null);

            out.add(copyArgb(canvas));

            lastDisposal = meta.disposal;
            lastX = fx;
            lastY = fy;
            lastW = fw;
            lastH = fh;
        }
        g.dispose();
        return out;
    }

    private static void applyDisposal(Graphics2D g, BufferedImage canvas, BufferedImage restore,
                                      int disposal, int x, int y, int w, int h, int sw, int sh) {
        switch (disposal) {
            case 2 -> { 
                g.setComposite(AlphaComposite.Clear);
                g.fillRect(x, y, w, h);
                g.setComposite(AlphaComposite.SrcOver);
            }
            case 3 -> { 
                if (restore != null) {
                    g.setComposite(AlphaComposite.Src);
                    g.drawImage(restore, 0, 0, null);
                    g.setComposite(AlphaComposite.SrcOver);
                }
            }
            default -> {
                
            }
        }
    }

    private static BufferedImage copyImage(BufferedImage src) {
        BufferedImage copy = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = copy.createGraphics();
        g.setComposite(AlphaComposite.Src);
        g.drawImage(src, 0, 0, null);
        g.dispose();
        return copy;
    }

    private static int[] copyArgb(BufferedImage img) {
        int w = img.getWidth();
        int h = img.getHeight();
        int[] pixels = new int[w * h];
        
        img.getRGB(0, 0, w, h, pixels, 0, w);
        
        for (int i = 0; i < pixels.length; i++) {
            int a = (pixels[i] >>> 24) & 0xFF;
            if (a >= 250) {
                pixels[i] = 0xFF000000 | (pixels[i] & 0x00FFFFFF);
            } else if (a <= 5) {
                pixels[i] = 0;
            }
        }
        return pixels;
    }

    private record FrameMeta(int disposal, int x, int y) {
    }

    private static FrameMeta readFrameMeta(ImageReader reader, int index) {
        int disposal = 0;
        int x = 0, y = 0;
        try {
            IIOMetadata meta = reader.getImageMetadata(index);
            if (meta == null) return new FrameMeta(0, 0, 0);
            String format = meta.getNativeMetadataFormatName();
            IIOMetadataNode root = (IIOMetadataNode) meta.getAsTree(format);

            IIOMetadataNode gce = getNode(root, "GraphicControlExtension");
            if (gce != null) {
                String d = gce.getAttribute("disposalMethod");
                
                if ("restoreToBackgroundColor".equals(d)) disposal = 2;
                else if ("restoreToPrevious".equals(d)) disposal = 3;
                else if ("doNotDispose".equals(d)) disposal = 1;
                else disposal = 0;
            }

            IIOMetadataNode imgDesc = getNode(root, "ImageDescriptor");
            if (imgDesc != null) {
                String xs = imgDesc.getAttribute("imageLeftPosition");
                String ys = imgDesc.getAttribute("imageTopPosition");
                if (xs != null && !xs.isEmpty()) x = Integer.parseInt(xs);
                if (ys != null && !ys.isEmpty()) y = Integer.parseInt(ys);
            }
        } catch (Throwable ignored) {
        }
        return new FrameMeta(disposal, x, y);
    }

    private static IIOMetadataNode getNode(IIOMetadataNode root, String name) {
        if (root == null) return null;
        if (name.equals(root.getNodeName())) return root;
        for (int i = 0; i < root.getLength(); i++) {
            if (root.item(i) instanceof IIOMetadataNode child) {
                if (name.equals(child.getNodeName())) return child;
                IIOMetadataNode deep = getNode(child, name);
                if (deep != null) return deep;
            }
        }
        return null;
    }

    private void fail(String reason) {
        failed = true;
        failReason = reason;
        loading.set(false);
    }

    private void uploadInitial(int[] argb, int w, int h) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) throw new IllegalStateException("no mc");

        uploadImage = new NativeImage(w, h, false);
        writeArgbToNative(argb, uploadImage);

        texture = new DynamicTexture(() -> "gif/" + resourceId.getPath(), uploadImage);
        mc.getTextureManager().register(textureId, texture);
        ImageRenderer.invalidateTexture(textureId);
        currentFrame = 0;
    }

    private void releaseGpu() {
        ready = false;
        Minecraft mc = Minecraft.getInstance();
        if (mc != null && mc.getTextureManager() != null && texture != null) {
            try {
                mc.getTextureManager().release(textureId);
            } catch (Throwable ignored) {
            }
        }
        ImageRenderer.invalidateTexture(textureId);
        texture = null;
        uploadImage = null;
        currentFrame = -1;
    }

    private static void writeArgbToNative(int[] argb, NativeImage img) {
        int w = img.getWidth();
        int h = img.getHeight();
        int n = Math.min(argb.length, w * h);
        for (int i = 0; i < n; i++) {
            int px = argb[i];
            int a = (px >>> 24) & 0xFF;
            int r = (px >>> 16) & 0xFF;
            int g = (px >>> 8) & 0xFF;
            int b = px & 0xFF;
            
            int abgr = (a << 24) | (b << 16) | (g << 8) | r;
            img.setPixelABGR(i % w, i / w, abgr);
        }
    }

    public void tick() {
        ensureLoaded();
        if (!isReady() || framePixels.length <= 1) return;

        long now = System.nanoTime();
        if (lastFrameNs == 0L) {
            lastFrameNs = now;
            return;
        }
        long elapsed = now - lastFrameNs;
        if (elapsed < frameDurationNs) return;

        int steps = (int) (elapsed / frameDurationNs);
        lastFrameNs += steps * frameDurationNs;
        int next = (currentFrame + steps) % framePixels.length;
        if (next != currentFrame) {
            setFrame(next);
        }
    }

    private void setFrame(int index) {
        if (texture == null || framePixels.length == 0) return;
        index = Math.floorMod(index, framePixels.length);
        NativeImage pixels = texture.getPixels();
        if (pixels == null) pixels = uploadImage;
        if (pixels == null) return;
        writeArgbToNative(framePixels[index], pixels);
        texture.upload();
        ImageRenderer.invalidateTexture(textureId);
        currentFrame = index;
    }

    public void renderCover(float x, float y, float w, float h) {
        renderCover(x, y, w, h, 0xFFFFFFFF);
    }

    
    public void renderCover(float x, float y, float w, float h, int color) {
        tick();
        if (!isReady() || frameW <= 0 || frameH <= 0) {
            return;
        }

        float scale = Math.max(w / (float) frameW, h / (float) frameH);
        float srcW = w / scale;
        float srcH = h / scale;
        float srcX = ((float) frameW - srcW) * 0.5f;
        float srcY = ((float) frameH - srcH) * 0.5f;

        float u0 = srcX / frameW;
        float v0 = srcY / frameH;
        float u1 = (srcX + srcW) / frameW;
        float v1 = (srcY + srcH) / frameH;

        String path = textureId.toString();
        
        Render2D.imageUvSmooth(path, x, y, w, h, 0f, 0.001f, u0, v0, u1, v1, color);
    }

    @Override
    public void close() {
        releaseGpu();
        framePixels = new int[0][];
        failed = false;
        failReason = null;
        loading.set(false);
    }
}
