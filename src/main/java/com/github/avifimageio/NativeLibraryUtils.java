package com.github.avifimageio;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 原生库加载工具类
 * 
 * <p>负责从 JAR 包中提取并加载平台特定的原生库。
 * 支持 Windows x64、Linux x64 和 macOS arm64。</p>
 */
class NativeLibraryUtils {
    
    private static final AtomicReference<Path> extractedLibrary = new AtomicReference<>();
    
    /**
     * 从 JAR 包中加载原生库
     * 
     * <p>优先使用 Thread Context ClassLoader 加载资源，以兼容各类模块化/插件环境，
     * 包括 OSGi、Java EE 容器、Spring Boot、PF4J 等使用自定义 ClassLoader 的场景。</p>
     * 
     * @throws UnsatisfiedLinkError 如果平台不支持或加载失败
     */
    public static void loadFromJar() {
        PlatformInfo platform = detectPlatform();
        // ClassLoader.getResourceAsStream() 不需要前导斜杠
        String resourcePath = String.format("native/%s/%s/%s", 
            platform.os, platform.arch, platform.libName);
        
        // 优先使用 Context ClassLoader，兼容插件环境
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        if (cl == null) {
            cl = NativeLibraryUtils.class.getClassLoader();
        }
        
        try (InputStream in = cl.getResourceAsStream(resourcePath)) {
            if (in == null) {
                throw new UnsatisfiedLinkError(String.format(
                    "Native library not found for platform: %s/%s. " +
                    "Resource path: %s. Supported platforms: win/64, linux/64, mac/arm64",
                    platform.os, platform.arch, resourcePath));
            }
            
            Path tmpFile = extractToTempFile(in, platform.libName);
            extractedLibrary.set(tmpFile);
            System.load(tmpFile.toString());
            
            // 注册 shutdown hook 进行清理
            Runtime.getRuntime().addShutdownHook(new Thread(NativeLibraryUtils::cleanup));
            
        } catch (IOException e) {
            throw new UnsatisfiedLinkError("Failed to extract native library: " + e.getMessage());
        }
    }
    
    /**
     * 从指定路径加载原生库
     * 
     * @param path 原生库文件路径
     */
    public static void loadFromPath(String path) {
        System.load(path);
    }
    
    /**
     * 清理提取的临时文件
     */
    private static void cleanup() {
        Path lib = extractedLibrary.get();
        if (lib != null) {
            try {
                Files.deleteIfExists(lib);
                Path parent = lib.getParent();
                if (parent != null) {
                    Files.deleteIfExists(parent);
                }
            } catch (IOException ignored) {
                // Best effort cleanup
            }
        }
    }
    
    /**
     * 将原生库提取到临时文件
     */
    private static Path extractToTempFile(InputStream in, String libName) throws IOException {
        Path tmpDir = Files.createTempDirectory("avif-native-");
        Path tmpFile = tmpDir.resolve(libName);
        
        Files.copy(in, tmpFile, StandardCopyOption.REPLACE_EXISTING);
        
        // 设置 deleteOnExit 作为备份清理机制
        tmpFile.toFile().deleteOnExit();
        tmpDir.toFile().deleteOnExit();
        
        return tmpFile;
    }
    
    /**
     * 检测当前平台信息
     */
    private static PlatformInfo detectPlatform() {
        String os = System.getProperty("os.name").toLowerCase();
        String arch = System.getProperty("os.arch").toLowerCase();
        
        String platform;
        String bits;
        String libName;
        
        if (os.contains("win")) {
            platform = "win";
            bits = "64";
            libName = "avif-imageio.dll";
        } else if (os.contains("mac") || os.contains("darwin")) {
            platform = "mac";
            bits = (arch.contains("aarch64") || arch.contains("arm")) ? "arm64" : "64";
            libName = "libavif-imageio.dylib";
        } else if (os.contains("linux")) {
            platform = "linux";
            bits = "64";
            libName = "libavif-imageio.so";
        } else {
            throw new UnsatisfiedLinkError("Unsupported OS: " + os + 
                ". Supported: win, linux, mac");
        }
        
        return new PlatformInfo(platform, bits, libName);
    }
    
    /**
     * 平台信息（Java 8 兼容，使用类代替 record）
     */
    private static final class PlatformInfo {
        final String os;
        final String arch;
        final String libName;
        
        PlatformInfo(String os, String arch, String libName) {
            this.os = os;
            this.arch = arch;
            this.libName = libName;
        }
    }
}
