package com.github.avifimageio;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * AVIF 编码选项
 * 
 * <p>此类管理原生编码器配置的生命周期，实现 AutoCloseable 接口以确保资源正确释放。
 * 所有方法都是线程安全的。</p>
 * 
 * <p>使用示例：</p>
 * <pre>{@code
 * try (AvifEncoderOptions options = new AvifEncoderOptions()) {
 *     options.setQuality(80);
 *     options.setSpeed(6);
 *     byte[] encoded = Avif.encodeRGB(rgbData, width, height, stride, options);
 * }
 * }</pre>
 */
public class AvifEncoderOptions implements AutoCloseable {
    
    /** 默认质量值 (0-100) */
    public static final int DEFAULT_QUALITY = 60;
    
    /** 默认编码速度 (0-10, 0=最慢最好, 10=最快) */
    public static final int DEFAULT_SPEED = 6;
    
    /** 默认位深度 */
    public static final int DEFAULT_BIT_DEPTH = 8;
    
    private volatile long fPointer;
    private final AtomicBoolean closed = new AtomicBoolean(false);
    
    /**
     * 创建默认编码选项
     * 
     * @throws UnsupportedOperationException 如果原生库不可用
     */
    public AvifEncoderOptions() {
        if (!Avif.isAvailable()) {
            throw new UnsupportedOperationException(
                "AVIF native library not available",
                Avif.getLoadError());
        }
        fPointer = createConfig();
    }
    
    private static native long createConfig();
    private static native void deleteConfig(long ptr);
    
    /**
     * 获取质量值
     * @return 质量值 (0-100)
     */
    public native int getQuality();
    
    /**
     * 设置质量值
     * @param quality 质量值 (0-100, 默认 60)
     * @throws IllegalArgumentException 如果质量值超出范围
     */
    public native void setQuality(int quality);
    
    /**
     * 获取编码速度
     * @return 编码速度 (0-10)
     */
    public native int getSpeed();
    
    /**
     * 设置编码速度
     * @param speed 编码速度 (0-10, 0=最慢最好, 10=最快, 默认 6)
     * @throws IllegalArgumentException 如果速度值超出范围
     */
    public native void setSpeed(int speed);
    
    /**
     * 获取位深度
     * @return 位深度 (8, 10, 12)
     */
    public native int getBitDepth();
    
    /**
     * 设置位深度
     * @param bitDepth 位深度 (8, 10, 12, 默认 8)
     * @throws IllegalArgumentException 如果位深度无效
     */
    public native void setBitDepth(int bitDepth);
    
    /**
     * 是否为无损模式
     * @return true 如果启用无损模式
     */
    public native boolean isLossless();
    
    /**
     * 设置无损模式
     * @param lossless true 启用无损模式
     */
    public native void setLossless(boolean lossless);
    
    /**
     * 获取原生指针（线程安全版本）
     * 
     * <p>先读取 fPointer，再检查 closed 状态，确保在并发关闭时
     * 要么返回有效指针，要么抛出异常，不会返回已释放的指针。</p>
     * 
     * @return 原生配置指针
     * @throws IllegalStateException 如果选项已关闭
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
