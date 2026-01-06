package com.github.avifimageio;

import java.io.IOException;

/**
 * AVIF 编解码 JNI 桥接类
 * 
 * <p>提供 AVIF 图片的编码、解码和元数据读取功能。
 * 原生库采用懒加载机制，首次调用时自动加载。</p>
 * 
 * <p>使用示例：</p>
 * <pre>{@code
 * // 检查原生库是否可用
 * if (Avif.isAvailable()) {
 *     // 解码 AVIF
 *     DecodeResult result = Avif.decode(avifData, 0, avifData.length, null);
 *     
 *     // 编码为 AVIF
 *     byte[] encoded = Avif.encodeRGB(rgbData, width, height, stride, null);
 * }
 * }</pre>
 */
public final class Avif {
    
    private static volatile boolean NATIVE_LIBRARY_LOADED = false;
    private static volatile Throwable LOAD_ERROR = null;
    
    /**
     * 加载原生库（线程安全）
     */
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
    
    /**
     * 检查原生库是否可用
     * 
     * @return true 如果原生库已成功加载
     */
    public static boolean isAvailable() {
        loadNativeLibrary();
        return NATIVE_LIBRARY_LOADED;
    }
    
    /**
     * 获取原生库加载错误（如果有）
     * 
     * @return 加载错误，如果加载成功则返回 null
     */
    public static Throwable getLoadError() {
        loadNativeLibrary();
        return LOAD_ERROR;
    }
    
    /**
     * 确保原生库可用，否则抛出异常
     */
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
     * 
     * @param data AVIF 数据
     * @param offset 数据偏移量
     * @param length 数据长度
     * @return 图片信息
     * @throws IOException 如果数据无效或解析失败
     * @throws NullPointerException 如果 data 为 null
     * @throws IllegalArgumentException 如果 offset/length 无效
     */
    public static ImageInfo getInfo(byte[] data, int offset, int length) throws IOException {
        ensureAvailable();
        if (data == null) {
            throw new NullPointerException("Input data may not be null");
        }
        if (offset < 0 || length < 0 || offset + length > data.length) {
            throw new IllegalArgumentException("Invalid offset/length");
        }
        return getInfoNative(data, offset, length);
    }
    
    private static native ImageInfo getInfoNative(byte[] data, int offset, int length) 
        throws IOException;
    
    /**
     * 解码 AVIF 图片
     * 
     * @param data AVIF 数据
     * @param offset 数据偏移量
     * @param length 数据长度
     * @param options 解码选项（可为 null 使用默认选项）
     * @return 解码结果
     * @throws IOException 如果解码失败
     * @throws NullPointerException 如果 data 为 null
     * @throws IllegalArgumentException 如果 offset/length 无效
     */
    public static DecodeResult decode(byte[] data, int offset, int length,
                                      AvifDecoderOptions options) throws IOException {
        ensureAvailable();
        if (data == null) {
            throw new NullPointerException("Input data may not be null");
        }
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
     * 
     * @param data AVIF 数据
     * @param offset 数据偏移量
     * @param length 数据长度
     * @param frameIndex 帧索引（从 0 开始）
     * @param options 解码选项（可为 null 使用默认选项）
     * @return 解码结果
     * @throws IOException 如果解码失败
     * @throws NullPointerException 如果 data 为 null
     * @throws IllegalArgumentException 如果 frameIndex &lt; 0 或 offset/length 无效
     */
    public static DecodeResult decodeFrame(byte[] data, int offset, int length,
                                           int frameIndex, AvifDecoderOptions options) 
            throws IOException {
        ensureAvailable();
        if (data == null) {
            throw new NullPointerException("Input data may not be null");
        }
        if (frameIndex < 0) {
            throw new IllegalArgumentException("Frame index must be >= 0");
        }
        if (offset < 0 || length < 0 || offset + length > data.length) {
            throw new IllegalArgumentException("Invalid offset/length");
        }
        long optionsPtr = (options != null) ? options.getPointer() : 0;
        return decodeFrameNative(optionsPtr, data, offset, length, frameIndex);
    }
    
    private static native DecodeResult decodeFrameNative(long optionsPtr, byte[] data,
                                                          int offset, int length, int frameIndex) 
        throws IOException;
    
    /**
     * 编码 RGB 图片为 AVIF
     * 
     * @param rgbData RGB 像素数据（每像素 3 字节：R, G, B）
     * @param width 图片宽度
     * @param height 图片高度
     * @param stride 行字节数
     * @param options 编码选项（可为 null 使用默认选项）
     * @return AVIF 编码数据
     * @throws IOException 如果编码失败
     * @throws NullPointerException 如果 rgbData 为 null
     * @throws IllegalArgumentException 如果尺寸无效
     */
    public static byte[] encodeRGB(byte[] rgbData, int width, int height, int stride,
                                   AvifEncoderOptions options) throws IOException {
        ensureAvailable();
        if (rgbData == null) {
            throw new NullPointerException("RGB data may not be null");
        }
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("Invalid dimensions");
        }
        long configPtr = (options != null) ? options.getPointer() : 0;
        return encodeRGBNative(configPtr, rgbData, width, height, stride);
    }
    
    private static native byte[] encodeRGBNative(long configPtr, byte[] rgbData, 
                                                  int width, int height, int stride) 
        throws IOException;
    
    /**
     * 编码 RGBA 图片为 AVIF
     * 
     * @param rgbaData RGBA 像素数据（每像素 4 字节：R, G, B, A）
     * @param width 图片宽度
     * @param height 图片高度
     * @param stride 行字节数
     * @param options 编码选项（可为 null 使用默认选项）
     * @return AVIF 编码数据
     * @throws IOException 如果编码失败
     * @throws NullPointerException 如果 rgbaData 为 null
     * @throws IllegalArgumentException 如果尺寸无效
     */
    public static byte[] encodeRGBA(byte[] rgbaData, int width, int height, int stride,
                                    AvifEncoderOptions options) throws IOException {
        ensureAvailable();
        if (rgbaData == null) {
            throw new NullPointerException("RGBA data may not be null");
        }
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("Invalid dimensions");
        }
        long configPtr = (options != null) ? options.getPointer() : 0;
        return encodeRGBANative(configPtr, rgbaData, width, height, stride);
    }
    
    private static native byte[] encodeRGBANative(long configPtr, byte[] rgbaData, 
                                                   int width, int height, int stride) 
        throws IOException;
    
    /**
     * 获取 EXIF 元数据
     * 
     * @param data AVIF 数据
     * @param offset 数据偏移量
     * @param length 数据长度
     * @return EXIF 数据，如果不存在则返回 null
     * @throws IOException 如果读取失败
     */
    public static byte[] getExif(byte[] data, int offset, int length) throws IOException {
        ensureAvailable();
        if (data == null) {
            throw new NullPointerException("Input data may not be null");
        }
        if (offset < 0 || length < 0 || offset + length > data.length) {
            throw new IllegalArgumentException("Invalid offset/length");
        }
        return getExifNative(data, offset, length);
    }
    
    private static native byte[] getExifNative(byte[] data, int offset, int length) 
        throws IOException;
    
    /**
     * 获取 ICC 色彩配置
     * 
     * @param data AVIF 数据
     * @param offset 数据偏移量
     * @param length 数据长度
     * @return ICC 配置数据，如果不存在则返回 null
     * @throws IOException 如果读取失败
     */
    public static byte[] getIccProfile(byte[] data, int offset, int length) throws IOException {
        ensureAvailable();
        if (data == null) {
            throw new NullPointerException("Input data may not be null");
        }
        if (offset < 0 || length < 0 || offset + length > data.length) {
            throw new IllegalArgumentException("Invalid offset/length");
        }
        return getIccProfileNative(data, offset, length);
    }
    
    private static native byte[] getIccProfileNative(byte[] data, int offset, int length) 
        throws IOException;
}
