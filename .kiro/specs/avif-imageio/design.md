# Design Document: avif-imageio

## Overview

avif-imageio 是一个 Java ImageIO 扩展库，通过 JNI 封装 libavif 提供 AVIF 图片的读写能力。项目架构参照 [sejda-pdf/webp-imageio](https://github.com/sejda-pdf/webp-imageio)，保持一致的设计模式和代码风格。

核心设计原则：
- **开箱即用**：原生库打包在 JAR 中，引入依赖即可使用
- **标准 API**：通过 ImageIO SPI 机制集成，使用标准的 `ImageIO.read()` / `ImageIO.write()` API
- **跨平台**：支持 Windows、Linux、macOS（Intel 和 Apple Silicon）
- **完整功能**：支持动画 AVIF、ICC 色彩配置、EXIF 元数据、10/12-bit 深度

## Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                      Java Application                        │
│                                                              │
│   ImageIO.read(avifFile)      ImageIO.write(img, "avif", f) │
└──────────────┬────────────────────────────┬─────────────────┘
               │                            │
               ▼                            ▼
┌──────────────────────────┐  ┌──────────────────────────────┐
│    AvifImageReaderSpi    │  │     AvifImageWriterSpi       │
│    AvifImageReader       │  │     AvifImageWriter          │
└──────────────┬───────────┘  └──────────────┬───────────────┘
               │                              │
               ▼                              ▼
┌─────────────────────────────────────────────────────────────┐
│                         Avif.java                            │
│  ┌─────────────────┐  ┌─────────────────┐  ┌──────────────┐ │
│  │ decode()        │  │ encodeRGB()     │  │ getInfo()    │ │
│  │ decodeFrame()   │  │ encodeRGBA()    │  │ getMetadata()│ │
│  │ (native)        │  │ (native)        │  │ (native)     │ │
│  └─────────────────┘  └─────────────────┘  └──────────────┘ │
│                                                              │
│  ┌─────────────────────────────────────────────────────────┐│
│  │              NativeLibraryUtils.loadFromJar()           ││
│  └─────────────────────────────────────────────────────────┘│
└──────────────────────────────┬──────────────────────────────┘
                               │ JNI
                               ▼
┌─────────────────────────────────────────────────────────────┐
│                    avif-imageio.c (Native)                   │
│                                                              │
│  ┌─────────────────┐  ┌─────────────────┐  ┌──────────────┐ │
│  │ avifDecoder*    │  │ avifEncoder*    │  │ avifImage*   │ │
│  └─────────────────┘  └─────────────────┘  └──────────────┘ │
└──────────────────────────────┬──────────────────────────────┘
                               │
                               ▼
┌─────────────────────────────────────────────────────────────┐
│                         libavif                              │
│                    (AOMedia AVIF Library)                    │
└─────────────────────────────────────────────────────────────┘
```

### 项目目录结构

```
avif-imageio/
├── build.gradle
├── settings.gradle
├── src/
│   ├── main/
│   │   ├── java/com/github/avifimageio/
│   │   │   ├── Avif.java                    # JNI 桥接类
│   │   │   ├── DecodeResult.java            # 解码结果 record
│   │   │   ├── ImageInfo.java               # 图片信息 record
│   │   │   ├── AvifEncoderOptions.java      # 编码选项
│   │   │   ├── AvifDecoderOptions.java      # 解码选项
│   │   │   ├── AvifImageReader.java         # ImageIO Reader
│   │   │   ├── AvifImageWriter.java         # ImageIO Writer
│   │   │   ├── AvifImageReaderSpi.java      # Reader SPI
│   │   │   ├── AvifImageWriterSpi.java      # Writer SPI
│   │   │   ├── AvifWriteParam.java          # 写入参数
│   │   │   ├── AvifMetadata.java            # AVIF 元数据
│   │   │   └── NativeLibraryUtils.java      # 原生库加载
│   │   ├── c/
│   │   │   ├── avif_imageio.c               # JNI 实现
│   │   │   ├── avif_imageio.h               # JNI 头文件
│   │   │   └── CMakeLists.txt               # CMake 构建
│   │   └── resources/
│   │       ├── native/
│   │       │   ├── win/64/avif-imageio.dll
│   │       │   ├── linux/64/libavif-imageio.so
│   │       │   ├── mac/64/libavif-imageio.dylib
│   │       │   └── mac/arm64/libavif-imageio.dylib
│   │       └── META-INF/services/
│   │           ├── javax.imageio.spi.ImageReaderSpi
│   │           └── javax.imageio.spi.ImageWriterSpi
│   └── test/java/com/github/avifimageio/
│       └── AvifImageIOTest.java
└── CMakeLists.txt
```


## Components and Interfaces

### 1. Data Transfer Objects

#### DecodeResult.java - 解码结果（Java 风格 API）

```java
package com.github.avifimageio;

/**
 * AVIF 解码结果
 * 
 * <p>注意：此 record 中的数组字段（pixels, iccProfile）是直接引用，
 * 调用者不应修改这些数组的内容，以保持数据完整性。
 * 如需修改，请先进行防御性拷贝。</p>
 */
public record DecodeResult(
    int[] pixels,      // ARGB 格式像素数据（不可修改）
    int width,         // 图片宽度
    int height,        // 图片高度
    boolean hasAlpha,  // 是否有 Alpha 通道
    int bitDepth,      // 位深度 (8, 10, 12)
    byte[] iccProfile  // ICC 色彩配置（可为 null，不可修改）
) {}
```

#### ImageInfo.java - 图片信息

```java
package com.github.avifimageio;

/**
 * AVIF 图片信息（不完全解码获取）
 */
public record ImageInfo(
    int width,
    int height,
    int bitDepth,
    boolean hasAlpha,
    int frameCount,        // 帧数（动画 AVIF）
    double duration,       // 总时长（秒，动画 AVIF）
    boolean hasIccProfile,
    boolean hasExif
) {}
```

### 2. Avif.java - JNI 桥接类

```java
package com.github.avifimageio;

import java.io.IOException;
import java.util.Optional;

public final class Avif {
    private static volatile boolean NATIVE_LIBRARY_LOADED = false;
    private static volatile Throwable LOAD_ERROR = null;

    static synchronized void loadNativeLibrary() {
        if (NATIVE_LIBRARY_LOADED || LOAD_ERROR != null) {
            return;
        }
        try {
            String customPath = System.getProperty("avif.native.path");
            if (customPath != null) {
                NativeLibraryUtils.loadFromPath(customPath);
            } else {
                NativeLibraryUtils.loadFromJar();
            }
            NATIVE_LIBRARY_LOADED = true;
        } catch (Throwable e) {
            LOAD_ERROR = e;
        }
    }

    private Avif() {}

    public static boolean isAvailable() {
        loadNativeLibrary();
        return NATIVE_LIBRARY_LOADED;
    }

    public static Optional<Throwable> getLoadError() {
        loadNativeLibrary();
        return Optional.ofNullable(LOAD_ERROR);
    }

    private static void ensureAvailable() {
        loadNativeLibrary();
        if (!NATIVE_LIBRARY_LOADED) {
            throw new UnsupportedOperationException(
                "AVIF native library not available: " + 
                (LOAD_ERROR != null ? LOAD_ERROR.getMessage() : "unknown error"),
                LOAD_ERROR);
        }
    }

    /**
     * 获取 AVIF 图片信息（不完全解码）
     */
    public static ImageInfo getInfo(byte[] data, int offset, int length) throws IOException {
        ensureAvailable();
        if (data == null) throw new NullPointerException("Input data may not be null");
        if (offset < 0 || length < 0 || offset + length > data.length) {
            throw new IllegalArgumentException("Invalid offset/length");
        }
        return getInfoNative(data, offset, length);
    }

    private static native ImageInfo getInfoNative(byte[] data, int offset, int length) 
        throws IOException;

    /**
     * 解码 AVIF 图片（Java 风格 API）
     */
    public static DecodeResult decode(byte[] data, int offset, int length,
                                      AvifDecoderOptions options) throws IOException {
        ensureAvailable();
        if (data == null) throw new NullPointerException("Input data may not be null");
        if (offset < 0 || length < 0 || offset + length > data.length) {
            throw new IllegalArgumentException("Invalid offset/length");
        }
        long optionsPtr = (options != null) ? options.getPointer() : 0;
        return decodeNative(optionsPtr, data, offset, length);
    }

    private static native DecodeResult decodeNative(long optionsPtr, byte[] data, 
                                                     int offset, int length) throws IOException;

    /**
     * 解码动画 AVIF 的指定帧
     */
    public static DecodeResult decodeFrame(byte[] data, int offset, int length,
                                           int frameIndex, AvifDecoderOptions options) 
            throws IOException {
        ensureAvailable();
        if (data == null) throw new NullPointerException("Input data may not be null");
        if (frameIndex < 0) throw new IllegalArgumentException("Frame index must be >= 0");
        long optionsPtr = (options != null) ? options.getPointer() : 0;
        return decodeFrameNative(optionsPtr, data, offset, length, frameIndex);
    }

    private static native DecodeResult decodeFrameNative(long optionsPtr, byte[] data,
                                                          int offset, int length, int frameIndex) 
        throws IOException;

    /**
     * 编码 RGB 图片为 AVIF
     */
    public static byte[] encodeRGB(byte[] rgbData, int width, int height, int stride,
                                   AvifEncoderOptions options) throws IOException {
        ensureAvailable();
        if (rgbData == null) throw new NullPointerException("RGB data may not be null");
        if (width <= 0 || height <= 0) throw new IllegalArgumentException("Invalid dimensions");
        long configPtr = (options != null) ? options.getPointer() : 0;
        return encodeRGBNative(configPtr, rgbData, width, height, stride);
    }

    private static native byte[] encodeRGBNative(long configPtr, byte[] rgbData, 
                                                  int width, int height, int stride) 
        throws IOException;

    /**
     * 编码 RGBA 图片为 AVIF
     */
    public static byte[] encodeRGBA(byte[] rgbaData, int width, int height, int stride,
                                    AvifEncoderOptions options) throws IOException {
        ensureAvailable();
        if (rgbaData == null) throw new NullPointerException("RGBA data may not be null");
        if (width <= 0 || height <= 0) throw new IllegalArgumentException("Invalid dimensions");
        long configPtr = (options != null) ? options.getPointer() : 0;
        return encodeRGBANative(configPtr, rgbaData, width, height, stride);
    }

    private static native byte[] encodeRGBANative(long configPtr, byte[] rgbaData, 
                                                   int width, int height, int stride) 
        throws IOException;

    /**
     * 获取 EXIF 元数据
     */
    public static byte[] getExif(byte[] data, int offset, int length) throws IOException {
        ensureAvailable();
        return getExifNative(data, offset, length);
    }

    private static native byte[] getExifNative(byte[] data, int offset, int length) 
        throws IOException;

    /**
     * 获取 ICC 色彩配置
     */
    public static byte[] getIccProfile(byte[] data, int offset, int length) throws IOException {
        ensureAvailable();
        return getIccProfileNative(data, offset, length);
    }

    private static native byte[] getIccProfileNative(byte[] data, int offset, int length) 
        throws IOException;
}
```


### 3. NativeLibraryUtils.java - 原生库加载器（改进版）

```java
package com.github.avifimageio;

import java.io.*;
import java.nio.file.*;
import java.util.concurrent.atomic.AtomicReference;

class NativeLibraryUtils {
    
    private static final AtomicReference<Path> extractedLibrary = new AtomicReference<>();
    
    public static void loadFromJar() {
        PlatformInfo platform = detectPlatform();
        String resourcePath = String.format("/native/%s/%s/%s", 
            platform.os, platform.arch, platform.libName);
        
        try (InputStream in = NativeLibraryUtils.class.getResourceAsStream(resourcePath)) {
            if (in == null) {
                throw new UnsatisfiedLinkError(String.format(
                    "Native library not found for platform: %s/%s. " +
                    "Resource path: %s. Supported platforms: win/64, linux/64, mac/64, mac/arm64",
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
    
    public static void loadFromPath(String path) {
        System.load(path);
    }
    
    private static void cleanup() {
        Path lib = extractedLibrary.get();
        if (lib != null) {
            try {
                Files.deleteIfExists(lib);
                Files.deleteIfExists(lib.getParent());
            } catch (IOException ignored) {
                // Best effort cleanup
            }
        }
    }
    
    private static Path extractToTempFile(InputStream in, String libName) throws IOException {
        Path tmpDir = Files.createTempDirectory("avif-native-");
        Path tmpFile = tmpDir.resolve(libName);
        
        Files.copy(in, tmpFile, StandardCopyOption.REPLACE_EXISTING);
        
        // 设置 deleteOnExit 作为备份清理机制
        tmpFile.toFile().deleteOnExit();
        tmpDir.toFile().deleteOnExit();
        
        return tmpFile;
    }
    
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
        } else if (os.contains("mac")) {
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
    
    private record PlatformInfo(String os, String arch, String libName) {}
}
```

### 4. AvifEncoderOptions.java - 编码选项（线程安全改进版）

```java
package com.github.avifimageio;

import java.util.concurrent.atomic.AtomicBoolean;

public class AvifEncoderOptions implements AutoCloseable {
    
    public static final int DEFAULT_QUALITY = 60;
    public static final int DEFAULT_SPEED = 6;
    public static final int DEFAULT_BIT_DEPTH = 8;
    
    private volatile long fPointer;
    private final AtomicBoolean closed = new AtomicBoolean(false);
    
    public AvifEncoderOptions() {
        if (!Avif.isAvailable()) {
            throw new UnsupportedOperationException(
                "AVIF native library not available",
                Avif.getLoadError().orElse(null));
        }
        fPointer = createConfig();
    }
    
    private static native long createConfig();
    private static native void deleteConfig(long ptr);
    
    // 质量 (0-100, 默认 60)
    public native int getQuality();
    public native void setQuality(int quality);
    
    // 编码速度 (0-10, 0=最慢最好, 10=最快, 默认 6)
    public native int getSpeed();
    public native void setSpeed(int speed);
    
    // 位深度 (8, 10, 12, 默认 8)
    public native int getBitDepth();
    public native void setBitDepth(int bitDepth);
    
    // 无损模式
    public native boolean isLossless();
    public native void setLossless(boolean lossless);
    
    /**
     * 获取原生指针（线程安全版本）
     * 先读取 fPointer，再检查 closed 状态
     */
    public long getPointer() {
        long ptr = fPointer;
        if (ptr == 0 || closed.get()) {
            throw new IllegalStateException("AvifEncoderOptions has been closed");
        }
        return ptr;
    }
    
    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            long ptr = fPointer;
            fPointer = 0;
            if (ptr != 0) {
                deleteConfig(ptr);
            }
        }
    }
}
```

### 5. AvifDecoderOptions.java - 解码选项（线程安全改进版）

```java
package com.github.avifimageio;

import java.util.concurrent.atomic.AtomicBoolean;

public class AvifDecoderOptions implements AutoCloseable {
    
    private volatile long fPointer;
    private final AtomicBoolean closed = new AtomicBoolean(false);
    
    public AvifDecoderOptions() {
        if (!Avif.isAvailable()) {
            throw new UnsupportedOperationException(
                "AVIF native library not available",
                Avif.getLoadError().orElse(null));
        }
        fPointer = createOptions();
    }
    
    private static native long createOptions();
    private static native void deleteOptions(long ptr);
    
    // 是否忽略 ICC 色彩配置
    public native boolean isIgnoreIcc();
    public native void setIgnoreIcc(boolean ignore);
    
    // 是否忽略 EXIF 元数据
    public native boolean isIgnoreExif();
    public native void setIgnoreExif(boolean ignore);
    
    /**
     * 获取原生指针（线程安全版本）
     */
    public long getPointer() {
        long ptr = fPointer;
        if (ptr == 0 || closed.get()) {
            throw new IllegalStateException("AvifDecoderOptions has been closed");
        }
        return ptr;
    }
    
    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            long ptr = fPointer;
            fPointer = 0;
            if (ptr != 0) {
                deleteOptions(ptr);
            }
        }
    }
}
```


### 6. AvifImageReader.java - ImageIO Reader（完整功能版）

```java
package com.github.avifimageio;

import javax.imageio.*;
import javax.imageio.spi.ImageReaderSpi;
import javax.imageio.stream.ImageInputStream;
import java.awt.color.ICC_Profile;
import java.awt.image.*;
import java.io.*;
import java.nio.ByteOrder;
import java.util.*;

public class AvifImageReader extends ImageReader {
    
    private byte[] avifData;
    private ImageInfo imageInfo;
    private boolean headerRead = false;
    
    protected AvifImageReader(ImageReaderSpi spi) {
        super(spi);
    }
    
    @Override
    public void setInput(Object input, boolean seekForwardOnly, boolean ignoreMetadata) {
        super.setInput(input, seekForwardOnly, ignoreMetadata);
        avifData = null;
        imageInfo = null;
        headerRead = false;
    }
    
    private void readHeader() throws IOException {
        if (headerRead) return;
        
        ImageInputStream stream = (ImageInputStream) getInput();
        avifData = readAllBytes(stream);
        imageInfo = Avif.getInfo(avifData, 0, avifData.length);
        headerRead = true;
    }
    
    private byte[] readAllBytes(ImageInputStream stream) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int read;
        while ((read = stream.read(buffer)) != -1) {
            baos.write(buffer, 0, read);
        }
        return baos.toByteArray();
    }
    
    @Override
    public int getWidth(int imageIndex) throws IOException {
        checkIndex(imageIndex);
        readHeader();
        return imageInfo.width();
    }
    
    @Override
    public int getHeight(int imageIndex) throws IOException {
        checkIndex(imageIndex);
        readHeader();
        return imageInfo.height();
    }
    
    @Override
    public int getNumImages(boolean allowSearch) throws IOException {
        readHeader();
        return imageInfo.frameCount();
    }
    
    @Override
    public BufferedImage read(int imageIndex, ImageReadParam param) throws IOException {
        checkIndex(imageIndex);
        readHeader();
        
        try (AvifDecoderOptions options = new AvifDecoderOptions()) {
            DecodeResult result;
            if (imageInfo.frameCount() > 1) {
                result = Avif.decodeFrame(avifData, 0, avifData.length, imageIndex, options);
            } else {
                result = Avif.decode(avifData, 0, avifData.length, options);
            }
            
            int imageType = result.hasAlpha() ? 
                BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB;
            BufferedImage image = new BufferedImage(result.width(), result.height(), imageType);
            image.setRGB(0, 0, result.width(), result.height(), result.pixels(), 0, result.width());
            
            // 应用 ICC 色彩配置
            if (result.iccProfile() != null && result.iccProfile().length > 0) {
                try {
                    ICC_Profile profile = ICC_Profile.getInstance(result.iccProfile());
                    // 可以在这里进行色彩空间转换
                } catch (Exception e) {
                    // 忽略无效的 ICC 配置
                }
            }
            
            return image;
        }
    }
    
    @Override
    public Iterator<ImageTypeSpecifier> getImageTypes(int imageIndex) throws IOException {
        checkIndex(imageIndex);
        readHeader();
        
        List<ImageTypeSpecifier> types = new ArrayList<>();
        if (imageInfo.hasAlpha()) {
            types.add(ImageTypeSpecifier.createFromBufferedImageType(BufferedImage.TYPE_INT_ARGB));
            types.add(ImageTypeSpecifier.createFromBufferedImageType(BufferedImage.TYPE_4BYTE_ABGR));
        } else {
            types.add(ImageTypeSpecifier.createFromBufferedImageType(BufferedImage.TYPE_INT_RGB));
            types.add(ImageTypeSpecifier.createFromBufferedImageType(BufferedImage.TYPE_3BYTE_BGR));
        }
        return types.iterator();
    }
    
    @Override
    public IIOMetadata getStreamMetadata() throws IOException {
        return null;
    }
    
    @Override
    public IIOMetadata getImageMetadata(int imageIndex) throws IOException {
        checkIndex(imageIndex);
        readHeader();
        
        // 返回包含 EXIF 和 ICC 信息的元数据
        if (imageInfo.hasExif() || imageInfo.hasIccProfile()) {
            return new AvifMetadata(avifData);
        }
        return null;
    }
    
    private void checkIndex(int imageIndex) throws IOException {
        readHeader();
        if (imageIndex < 0 || imageIndex >= imageInfo.frameCount()) {
            throw new IndexOutOfBoundsException(
                "Image index " + imageIndex + " out of range [0, " + imageInfo.frameCount() + ")");
        }
    }
}
```

### 7. AvifImageWriter.java - ImageIO Writer（优化内存版）

```java
package com.github.avifimageio;

import javax.imageio.*;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.spi.ImageWriterSpi;
import javax.imageio.stream.ImageOutputStream;
import java.awt.image.*;
import java.io.IOException;

public class AvifImageWriter extends ImageWriter {
    
    private ImageOutputStream output;
    
    protected AvifImageWriter(ImageWriterSpi spi) {
        super(spi);
    }
    
    @Override
    public void setOutput(Object output) {
        super.setOutput(output);
        this.output = (ImageOutputStream) output;
    }
    
    @Override
    public void write(IIOMetadata streamMetadata, IIOImage image, ImageWriteParam param) 
            throws IOException {
        
        RenderedImage renderedImage = image.getRenderedImage();
        BufferedImage bufferedImage = toBufferedImage(renderedImage);
        
        int width = bufferedImage.getWidth();
        int height = bufferedImage.getHeight();
        
        // 获取编码参数
        int quality = AvifEncoderOptions.DEFAULT_QUALITY;
        int speed = AvifEncoderOptions.DEFAULT_SPEED;
        int bitDepth = AvifEncoderOptions.DEFAULT_BIT_DEPTH;
        boolean lossless = false;
        
        if (param instanceof AvifWriteParam) {
            AvifWriteParam avifParam = (AvifWriteParam) param;
            quality = avifParam.getQuality();
            speed = avifParam.getSpeed();
            bitDepth = avifParam.getBitDepth();
            lossless = avifParam.isLossless();
        } else if (param != null && param.getCompressionMode() == ImageWriteParam.MODE_EXPLICIT) {
            quality = (int) (param.getCompressionQuality() * 100);
        }
        
        try (AvifEncoderOptions options = new AvifEncoderOptions()) {
            options.setQuality(quality);
            options.setSpeed(speed);
            options.setBitDepth(bitDepth);
            options.setLossless(lossless);
            
            byte[] encoded;
            boolean hasAlpha = bufferedImage.getColorModel().hasAlpha();
            
            if (hasAlpha) {
                byte[] rgba = extractRGBA(bufferedImage);
                encoded = Avif.encodeRGBA(rgba, width, height, width * 4, options);
            } else {
                byte[] rgb = extractRGB(bufferedImage);
                encoded = Avif.encodeRGB(rgb, width, height, width * 3, options);
            }
            
            output.write(encoded);
        }
    }
    
    /**
     * 提取 RGB 数据（使用 getRGB 保证统一的 ARGB 格式，避免不同 BufferedImage 类型的像素顺序问题）
     */
    private byte[] extractRGB(BufferedImage image) {
        int width = image.getWidth();
        int height = image.getHeight();
        byte[] rgb = new byte[width * height * 3];
        
        // 使用 getRGB 保证统一的 ARGB 格式，避免 TYPE_INT_RGB 和 TYPE_3BYTE_BGR 等类型的差异
        int[] pixels = image.getRGB(0, 0, width, height, null, 0, width);
        for (int i = 0; i < pixels.length; i++) {
            rgb[i * 3]     = (byte) ((pixels[i] >> 16) & 0xFF); // R
            rgb[i * 3 + 1] = (byte) ((pixels[i] >> 8) & 0xFF);  // G
            rgb[i * 3 + 2] = (byte) (pixels[i] & 0xFF);         // B
        }
        return rgb;
    }
    
    /**
     * 提取 RGBA 数据（使用 getRGB 保证统一的 ARGB 格式）
     */
    private byte[] extractRGBA(BufferedImage image) {
        int width = image.getWidth();
        int height = image.getHeight();
        byte[] rgba = new byte[width * height * 4];
        
        int[] pixels = image.getRGB(0, 0, width, height, null, 0, width);
        for (int i = 0; i < pixels.length; i++) {
            rgba[i * 4]     = (byte) ((pixels[i] >> 16) & 0xFF); // R
            rgba[i * 4 + 1] = (byte) ((pixels[i] >> 8) & 0xFF);  // G
            rgba[i * 4 + 2] = (byte) (pixels[i] & 0xFF);         // B
            rgba[i * 4 + 3] = (byte) ((pixels[i] >> 24) & 0xFF); // A
        }
        return rgba;
    }
    
    /**
     * 将 RenderedImage 转换为 BufferedImage
     */
    private BufferedImage toBufferedImage(RenderedImage img) {
        if (img instanceof BufferedImage) {
            return (BufferedImage) img;
        }
        BufferedImage buffered = new BufferedImage(
            img.getWidth(), img.getHeight(), BufferedImage.TYPE_INT_ARGB);
        java.awt.Graphics2D g = buffered.createGraphics();
        try {
            g.drawRenderedImage(img, null);
        } finally {
            g.dispose();  // 确保释放 Graphics 资源
        }
        return buffered;
    }
    
    @Override
    public ImageWriteParam getDefaultWriteParam() {
        return new AvifWriteParam(getLocale());
    }
    
    @Override
    public IIOMetadata getDefaultStreamMetadata(ImageWriteParam param) {
        return null;
    }
    
    @Override
    public IIOMetadata getDefaultImageMetadata(ImageTypeSpecifier imageType, ImageWriteParam param) {
        return null;
    }
    
    @Override
    public IIOMetadata convertStreamMetadata(IIOMetadata inData, ImageWriteParam param) {
        return null;
    }
    
    @Override
    public IIOMetadata convertImageMetadata(IIOMetadata inData, ImageTypeSpecifier imageType, 
                                            ImageWriteParam param) {
        return null;
    }
}
```


### 8. AvifWriteParam.java - 写入参数（完整版）

```java
package com.github.avifimageio;

import javax.imageio.ImageWriteParam;
import java.util.Locale;

public class AvifWriteParam extends ImageWriteParam {
    
    private int quality = AvifEncoderOptions.DEFAULT_QUALITY;
    private int speed = AvifEncoderOptions.DEFAULT_SPEED;
    private int bitDepth = AvifEncoderOptions.DEFAULT_BIT_DEPTH;
    private boolean lossless = false;
    
    public AvifWriteParam(Locale locale) {
        super(locale);
        canWriteCompressed = true;
        compressionTypes = new String[]{"AVIF"};
        compressionType = "AVIF";
    }
    
    public int getQuality() { return quality; }
    
    public void setQuality(int quality) {
        if (quality < 0 || quality > 100) {
            throw new IllegalArgumentException("Quality must be between 0 and 100, got: " + quality);
        }
        this.quality = quality;
    }
    
    public int getSpeed() { return speed; }
    
    public void setSpeed(int speed) {
        if (speed < 0 || speed > 10) {
            throw new IllegalArgumentException("Speed must be between 0 and 10, got: " + speed);
        }
        this.speed = speed;
    }
    
    public int getBitDepth() { return bitDepth; }
    
    public void setBitDepth(int bitDepth) {
        if (bitDepth != 8 && bitDepth != 10 && bitDepth != 12) {
            throw new IllegalArgumentException("Bit depth must be 8, 10, or 12, got: " + bitDepth);
        }
        this.bitDepth = bitDepth;
    }
    
    public boolean isLossless() { return lossless; }
    
    public void setLossless(boolean lossless) { this.lossless = lossless; }
}
```

### 9. AvifMetadata.java - AVIF 元数据

```java
package com.github.avifimageio;

import javax.imageio.metadata.IIOMetadata;
import javax.imageio.metadata.IIOMetadataNode;
import org.w3c.dom.Node;
import java.io.IOException;

public class AvifMetadata extends IIOMetadata {
    
    private final byte[] avifData;
    private byte[] exifData;
    private byte[] iccProfile;
    private boolean loaded = false;
    
    public AvifMetadata(byte[] avifData) {
        this.avifData = avifData;
    }
    
    private void loadMetadata() {
        if (loaded) return;
        try {
            exifData = Avif.getExif(avifData, 0, avifData.length);
            iccProfile = Avif.getIccProfile(avifData, 0, avifData.length);
        } catch (IOException e) {
            // 忽略元数据加载错误
        }
        loaded = true;
    }
    
    public byte[] getExifData() {
        loadMetadata();
        return exifData;
    }
    
    public byte[] getIccProfile() {
        loadMetadata();
        return iccProfile;
    }
    
    @Override
    public boolean isReadOnly() { return true; }
    
    @Override
    public Node getAsTree(String formatName) {
        loadMetadata();
        IIOMetadataNode root = new IIOMetadataNode(formatName);
        
        if (exifData != null && exifData.length > 0) {
            IIOMetadataNode exifNode = new IIOMetadataNode("EXIF");
            exifNode.setUserObject(exifData);
            root.appendChild(exifNode);
        }
        
        if (iccProfile != null && iccProfile.length > 0) {
            IIOMetadataNode iccNode = new IIOMetadataNode("ICC");
            iccNode.setUserObject(iccProfile);
            root.appendChild(iccNode);
        }
        
        return root;
    }
    
    @Override
    public void mergeTree(String formatName, Node root) {
        throw new IllegalStateException("Metadata is read-only");
    }
    
    @Override
    public void reset() {
        loaded = false;
        exifData = null;
        iccProfile = null;
    }
}
```

### 10. AvifImageReaderSpi.java - Reader SPI（修复版）

```java
package com.github.avifimageio;

import javax.imageio.spi.ImageReaderSpi;
import javax.imageio.stream.ImageInputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Locale;

public class AvifImageReaderSpi extends ImageReaderSpi {
    
    private static final byte[] FTYP = {0x66, 0x74, 0x79, 0x70};  // "ftyp"
    private static final byte[] AVIF = {0x61, 0x76, 0x69, 0x66};  // "avif"
    private static final byte[] AVIS = {0x61, 0x76, 0x69, 0x73};  // "avis"
    
    public AvifImageReaderSpi() {
        super(
            "avif-imageio",
            "1.0",
            new String[]{"avif", "AVIF"},
            new String[]{"avif"},
            new String[]{"image/avif"},
            AvifImageReader.class.getName(),
            new Class[]{ImageInputStream.class},
            new String[]{AvifImageWriterSpi.class.getName()},
            false, null, null, null, null,
            false, null, null, null, null
        );
    }
    
    @Override
    public boolean canDecodeInput(Object source) throws IOException {
        if (!(source instanceof ImageInputStream)) {
            return false;
        }
        
        ImageInputStream stream = (ImageInputStream) source;
        stream.mark();
        
        try {
            stream.skipBytes(4);
            byte[] ftyp = new byte[4];
            if (stream.read(ftyp) != 4) return false;
            
            if (!Arrays.equals(ftyp, FTYP)) {
                return false;
            }
            
            byte[] brand = new byte[4];
            if (stream.read(brand) != 4) return false;
            
            return Arrays.equals(brand, AVIF) || Arrays.equals(brand, AVIS);
        } finally {
            stream.reset();
        }
    }
    
    @Override
    public AvifImageReader createReaderInstance(Object extension) {
        return new AvifImageReader(this);
    }
    
    @Override
    public String getDescription(Locale locale) {
        return "AVIF Image Reader";
    }
}
```

### 11. AvifImageWriterSpi.java - Writer SPI

```java
package com.github.avifimageio;

import javax.imageio.ImageTypeSpecifier;
import javax.imageio.spi.ImageWriterSpi;
import javax.imageio.stream.ImageOutputStream;
import java.util.Locale;

public class AvifImageWriterSpi extends ImageWriterSpi {
    
    public AvifImageWriterSpi() {
        super(
            "avif-imageio",
            "1.0",
            new String[]{"avif", "AVIF"},
            new String[]{"avif"},
            new String[]{"image/avif"},
            AvifImageWriter.class.getName(),
            new Class[]{ImageOutputStream.class},
            new String[]{AvifImageReaderSpi.class.getName()},
            false, null, null, null, null,
            false, null, null, null, null
        );
    }
    
    @Override
    public boolean canEncodeImage(ImageTypeSpecifier type) {
        return true;
    }
    
    @Override
    public AvifImageWriter createWriterInstance(Object extension) {
        return new AvifImageWriter(this);
    }
    
    @Override
    public String getDescription(Locale locale) {
        return "AVIF Image Writer";
    }
}
```


## Data Models

### 像素格式转换

```
Java BufferedImage          JNI Layer              libavif
─────────────────          ─────────              ───────
TYPE_INT_ARGB    ────────► RGBA bytes ──────────► avifRGBImage
TYPE_INT_RGB     ────────► RGB bytes  ──────────► avifRGBImage
TYPE_4BYTE_ABGR  ────────► RGBA bytes ──────────► avifRGBImage
TYPE_3BYTE_BGR   ────────► RGB bytes  ──────────► avifRGBImage
                                                        │
                                                        ▼
                                                  avifImageRGBToYUV()
                                                        │
                                                        ▼
                                                  avifImage (YUV)
                                                        │
                                                        ▼
                                                  avifEncoder
                                                        │
                                                        ▼
                                                  AVIF bytes
```

### 位深度支持

| 位深度 | Java 类型 | libavif 格式 |
|--------|-----------|--------------|
| 8-bit  | byte[]    | AVIF_PIXEL_FORMAT_YUV444 |
| 10-bit | short[]   | AVIF_PIXEL_FORMAT_YUV444 |
| 12-bit | short[]   | AVIF_PIXEL_FORMAT_YUV444 |

### AVIF 文件格式识别

```
AVIF 文件结构:
┌─────────────────────────────────────────┐
│ Box Size (4 bytes)                      │
├─────────────────────────────────────────┤
│ Box Type: "ftyp" (4 bytes)              │
├─────────────────────────────────────────┤
│ Major Brand: "avif" or "avis" (4 bytes) │
├─────────────────────────────────────────┤
│ Minor Version (4 bytes)                 │
├─────────────────────────────────────────┤
│ Compatible Brands...                    │
└─────────────────────────────────────────┘
```

### 原生库资源路径

```
src/main/resources/
└── native/
    ├── win/64/avif-imageio.dll
    ├── linux/64/libavif-imageio.so
    └── mac/
        ├── 64/libavif-imageio.dylib
        └── arm64/libavif-imageio.dylib
```

### SPI 注册文件

```
src/main/resources/META-INF/services/
├── javax.imageio.spi.ImageReaderSpi
│   └── com.github.avifimageio.AvifImageReaderSpi
└── javax.imageio.spi.ImageWriterSpi
    └── com.github.avifimageio.AvifImageWriterSpi
```

## Correctness Properties

*正确性属性是一种特征或行为，应该在系统的所有有效执行中保持为真。*

### Property 1: 编解码往返一致性 (Round-Trip)

*For any* 有效的 BufferedImage，将其编码为 AVIF 然后解码回 BufferedImage，解码后的图像尺寸应与原图完全一致。对于无损模式，像素值应完全一致。

**Validates: Requirements 4.2, 4.3, 4.4, 7.1, 7.3, 7.4, 8.1, 8.2, 10.2**

### Property 2: 质量参数影响文件大小

*For any* 有效的 BufferedImage 和两个不同的质量值 q1 < q2，使用较低质量编码的文件大小应小于或等于较高质量的文件大小。

**Validates: Requirements 5.1, 7.2**

### Property 3: getInfo 返回正确尺寸

*For any* 有效的 AVIF 数据，getInfo() 返回的宽高应与完整解码后的图像尺寸完全一致。

**Validates: Requirements 4.5**

### Property 4: 损坏数据解码抛出异常

*For any* 非有效 AVIF 数据，调用 decode() 应抛出 IOException。

**Validates: Requirements 8.3, 11.2**

### Property 5: 无效编码参数抛出异常

*For any* 超出有效范围的参数值，设置时应抛出 IllegalArgumentException。

**Validates: Requirements 5.4, 11.3**

### Property 6: AVIF 格式正确识别

*For any* 字节数组，canDecodeInput() 应当且仅当数据以有效 AVIF magic bytes 开头时返回 true。

**Validates: Requirements 9.5**

### Property 7: 动画帧数一致性

*For any* 动画 AVIF 文件，getNumImages() 返回的帧数应与实际可解码的帧数一致。

**Validates: Requirements (动画支持)**

### Property 8: 元数据完整性

*For any* 包含 EXIF/ICC 的 AVIF 文件，getExif()/getIccProfile() 返回的数据应与原始嵌入数据一致。

**Validates: Requirements (元数据支持)**


## Error Handling

| 错误场景 | 异常类型 | 消息格式 |
|---------|---------|---------|
| 平台不支持 | UnsatisfiedLinkError | "Unsupported OS: {os}. Supported: win, linux, mac" |
| AVIF 数据损坏 | IOException | "AVIF decode failed: {libavif error}" |
| 质量参数无效 | IllegalArgumentException | "Quality must be between 0 and 100, got: {value}" |
| 速度参数无效 | IllegalArgumentException | "Speed must be between 0 and 10, got: {value}" |
| 位深度无效 | IllegalArgumentException | "Bit depth must be 8, 10, or 12, got: {value}" |
| Options 已关闭 | IllegalStateException | "AvifEncoderOptions has been closed" |
| 输入为 null | NullPointerException | "Input data may not be null" |
| 参数越界 | IllegalArgumentException | "Invalid offset/length" |
| 帧索引越界 | IndexOutOfBoundsException | "Image index {idx} out of range [0, {count})" |

### JNI 错误传播

```c
static void throwIOException(JNIEnv *env, const char* message) {
    jclass excClass = (*env)->FindClass(env, "java/io/IOException");
    if (excClass != NULL) {
        (*env)->ThrowNew(env, excClass, message);
    }
}

static void throwIllegalArgumentException(JNIEnv *env, const char* message) {
    jclass excClass = (*env)->FindClass(env, "java/lang/IllegalArgumentException");
    if (excClass != NULL) {
        (*env)->ThrowNew(env, excClass, message);
    }
}
```

## Testing Strategy

### Property-Based Testing Configuration

- **测试框架**: jqwik 1.7.0
- **最小迭代次数**: 100 次
- **标签格式**: `@Tag("Feature: avif-imageio, Property N: {property_text}")`

### Test Categories

#### Unit Tests

```java
@Test
void testReaderSpiRegistration() {
    assertTrue(Arrays.asList(ImageIO.getReaderFormatNames()).contains("avif"));
}

@Test
void testWriterSpiRegistration() {
    assertTrue(Arrays.asList(ImageIO.getWriterFormatNames()).contains("avif"));
}

@Test
void testQualityBoundary() {
    try (AvifEncoderOptions options = new AvifEncoderOptions()) {
        assertDoesNotThrow(() -> options.setQuality(0));
        assertDoesNotThrow(() -> options.setQuality(100));
        assertThrows(IllegalArgumentException.class, () -> options.setQuality(-1));
        assertThrows(IllegalArgumentException.class, () -> options.setQuality(101));
    }
}

@Test
void testOptionsThreadSafety() {
    AvifEncoderOptions options = new AvifEncoderOptions();
    options.close();
    assertThrows(IllegalStateException.class, () -> options.getPointer());
}

@Test
void testGetImageTypesNotNull() throws IOException {
    // 确保 getImageTypes() 返回有效的 Iterator
    AvifImageReader reader = new AvifImageReader(new AvifImageReaderSpi());
    // ... 设置输入
    Iterator<ImageTypeSpecifier> types = reader.getImageTypes(0);
    assertNotNull(types);
    assertTrue(types.hasNext());
}
```

#### Property-Based Tests

```java
@Property(tries = 100)
@Tag("Feature: avif-imageio, Property 1: Round-Trip")
void roundTripPreservesDimensions(
    @ForAll @IntRange(min = 1, max = 500) int width,
    @ForAll @IntRange(min = 1, max = 500) int height) {
    
    BufferedImage original = createRandomImage(width, height);
    byte[] avifData = encodeToAvif(original, 60);
    DecodeResult result = Avif.decode(avifData, 0, avifData.length, null);
    
    assertThat(result.width()).isEqualTo(width);
    assertThat(result.height()).isEqualTo(height);
}

@Property(tries = 100)
@Tag("Feature: avif-imageio, Property 2: Quality affects file size")
void lowerQualityProducesSmallerFile(
    @ForAll @IntRange(min = 1, max = 200) int width,
    @ForAll @IntRange(min = 1, max = 200) int height) {
    
    BufferedImage image = createRandomImage(width, height);
    byte[] lowQuality = encodeToAvif(image, 20);
    byte[] highQuality = encodeToAvif(image, 80);
    
    assertThat(lowQuality.length).isLessThanOrEqualTo(highQuality.length);
}

@Property(tries = 100)
@Tag("Feature: avif-imageio, Property 3: getInfo matches decoded")
void getInfoMatchesDecoded(@ForAll("validAvifData") byte[] avifData) {
    ImageInfo info = Avif.getInfo(avifData, 0, avifData.length);
    DecodeResult result = Avif.decode(avifData, 0, avifData.length, null);
    
    assertThat(info.width()).isEqualTo(result.width());
    assertThat(info.height()).isEqualTo(result.height());
}

@Property(tries = 100)
@Tag("Feature: avif-imageio, Property 4: Corrupted data throws")
void corruptedDataThrows(@ForAll byte[] randomData) {
    Assume.that(randomData.length > 0);
    
    assertThatThrownBy(() -> Avif.decode(randomData, 0, randomData.length, null))
        .isInstanceOf(IOException.class);
}

@Property(tries = 100)
@Tag("Feature: avif-imageio, Property 5: Invalid params throw")
void invalidQualityThrows(@ForAll @IntRange(min = 101, max = 1000) int invalidQuality) {
    try (AvifEncoderOptions options = new AvifEncoderOptions()) {
        assertThrows(IllegalArgumentException.class, () -> options.setQuality(invalidQuality));
    }
}

@Property(tries = 100)
@Tag("Feature: avif-imageio, Property 6: Format detection")
void formatDetection(@ForAll("avifOrRandom") byte[] data, @ForAll boolean isAvif) {
    AvifImageReaderSpi spi = new AvifImageReaderSpi();
    ImageInputStream iis = ImageIO.createImageInputStream(new ByteArrayInputStream(data));
    
    assertThat(spi.canDecodeInput(iis)).isEqualTo(isAvif);
}
```

### Test Dependencies

```groovy
dependencies {
    testImplementation 'org.junit.jupiter:junit-jupiter:5.9.0'
    testImplementation 'net.jqwik:jqwik:1.7.0'
    testImplementation 'org.assertj:assertj-core:3.24.0'
}

test {
    useJUnitPlatform {
        includeEngines 'junit-jupiter', 'jqwik'
    }
}
```

### Coverage Requirements

- 所有公共 API 方法必须有单元测试
- 所有正确性属性必须有对应的属性测试
- 错误处理路径必须有测试覆盖
- 线程安全性必须有测试覆盖
- 资源管理（AutoCloseable）必须有测试覆盖
