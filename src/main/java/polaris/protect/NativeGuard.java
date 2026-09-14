package polaris.protect;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;


public final class NativeGuard {

    public static final String PRIMARY_LIB = "cataguard";
    public static final String FALLBACK_LIB = "obfcore";

    private static final AtomicBoolean INIT = new AtomicBoolean(false);
    private static volatile boolean nativeReady;
    private static volatile String jarFingerprint = "";
    private static volatile boolean tampered;

    private NativeGuard() {
    }

    
    public static void init() {
        if (!INIT.compareAndSet(false, true)) {
            return;
        }
        try {
            jarFingerprint = fingerprintSelf();
            nativeReady = loadNative(PRIMARY_LIB) || loadNative(FALLBACK_LIB);
            if (nativeReady) {
                try {
                    
                    nativePing();
                } catch (UnsatisfiedLinkError ignored) {
                    
                }
            }
            tampered = false;
        } catch (Throwable t) {
            nativeReady = false;
            tampered = true;
        }
    }

    public static boolean isNativeReady() {
        return nativeReady;
    }

    public static boolean isTampered() {
        return tampered;
    }

    public static String jarFingerprint() {
        return jarFingerprint;
    }

    
    @Protect("gate")
    public static boolean allowSensitive() {
        
        if (tampered) {
            return false;
        }
        
        
        boolean expectsNative = resourceExists("/META-INF/cataguard/native/win-x64/" + PRIMARY_LIB + ".dll")
                || resourceExists("/META-INF/cataguard/native/win-x64/" + FALLBACK_LIB + ".dll")
                || resourceExists("/META-INF/cataguard/native/linux-x64/lib" + PRIMARY_LIB + ".so")
                || resourceExists("/META-INF/cataguard/native/linux-x64/lib" + FALLBACK_LIB + ".so");
        if (expectsNative && !nativeReady) {
            return false;
        }
        return true;
    }

    private static boolean resourceExists(String path) {
        try (InputStream in = NativeGuard.class.getResourceAsStream(path)) {
            return in != null;
        } catch (Throwable t) {
            return false;
        }
    }

    private static boolean loadNative(String libraryName) {
        try {
            String resourcePath = resourcePath(libraryName);
            if (resourcePath != null) {
                Path extracted = extract(resourcePath);
                if (extracted != null) {
                    System.load(extracted.toAbsolutePath().toString());
                    return true;
                }
            }
        } catch (Throwable ignored) {
        }
        try {
            System.loadLibrary(libraryName);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static Path extract(String resourcePath) {
        try (InputStream in = NativeGuard.class.getResourceAsStream(resourcePath)) {
            if (in == null) {
                return null;
            }
            String suffix = resourcePath.endsWith(".dll") ? ".dll" : ".so";
            Path temp = Files.createTempFile("polaris-native-", suffix);
            Files.copy(in, temp, StandardCopyOption.REPLACE_EXISTING);
            temp.toFile().deleteOnExit();
            return temp;
        } catch (Throwable t) {
            return null;
        }
    }

    private static String resourcePath(String libraryName) {
        String os = lower(System.getProperty("os.name", ""));
        String arch = lower(System.getProperty("os.arch", ""));
        if (os.contains("win") && isX64(arch)) {
            return "/META-INF/cataguard/native/win-x64/" + libraryName + ".dll";
        }
        if (os.contains("linux") && isX64(arch)) {
            return "/META-INF/cataguard/native/linux-x64/lib" + libraryName + ".so";
        }
        return null;
    }

    private static boolean isX64(String arch) {
        return arch.contains("amd64") || arch.contains("x86_64");
    }

    private static String lower(String s) {
        return s.toLowerCase(Locale.ROOT);
    }

    private static String fingerprintSelf() {
        try {
            var codeSource = NativeGuard.class.getProtectionDomain().getCodeSource();
            if (codeSource == null || codeSource.getLocation() == null) {
                return "dev";
            }
            Path path = Path.of(codeSource.getLocation().toURI());
            if (!Files.isRegularFile(path)) {
                return "classpath";
            }
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] all = Files.readAllBytes(path);
            
            int n = all.length;
            int head = Math.min(n, 64 * 1024);
            int tail = Math.min(n, 64 * 1024);
            md.update(all, 0, head);
            if (n > head) {
                md.update(all, n - tail, tail);
            }
            md.update(intBytes(n));
            byte[] dig = md.digest();
            StringBuilder sb = new StringBuilder(16);
            for (int i = 0; i < 8 && i < dig.length; i++) {
                sb.append(String.format("%02x", dig[i]));
            }
            return sb.toString();
        } catch (Throwable t) {
            return "unknown";
        }
    }

    private static byte[] intBytes(int v) {
        return new byte[]{
                (byte) (v >>> 24), (byte) (v >>> 16), (byte) (v >>> 8), (byte) v
        };
    }

    
    private static native void nativePing();
}
