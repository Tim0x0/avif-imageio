package com.github.avifimageio;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * AVIF 解码选项
 * 
 * <p>此类管理原生解码器配置的生命周期，实现 AutoCloseable 接口以确保资源正确释放。
 * 所有方法都是线程安全的。</p>
 * 
 * <p>使用示例：</p>
 * <pre>{@code
 * try (AvifDecoderOptions options = new AvifDecoderOptions()) {
 *     options.setIgnoreIcc(true);
 *     DecodeResult result = Avif.decode(avifData, 0, avifData.length, options);
 * }
 * }</pre>
 */
public class AvifDecoderOptions implements AutoCloseable {
    
    private volatile long fPointer;
    private final AtomicBoolean closed = new AtomicBoolean(false);
    
    /**
     * 创建默认解码选项
     * 
     * @throws UnsupportedOperationException 如果原生库不可用
     */
    public AvifDecoderOptions() {
        if (!Avif.isAvailable()) {
            throw new UnsupportedOperationException(
                "AVIF native library not available",
                Avif.getLoadError());
        }
        fPointer = createOptions();
    }
    
    private static native long createOptions();
    private static native void deleteOptions(long ptr);
    
    /**
     * 是否忽略 ICC 色彩配置
     * @return true 如果忽略 ICC 配置
     */
    public native boolean isIgnoreIcc();
    
    /**
     * 设置是否忽略 ICC 色彩配置
     * @param ignore true 忽略 ICC 配置
     */
    public native void setIgnoreIcc(boolean ignore);
    
    /**
     * 是否忽略 EXIF 元数据
     * @return true 如果忽略 EXIF 元数据
     */
    public native boolean isIgnoreExif();
    
    /**
     * 设置是否忽略 EXIF 元数据
     * @param ignore true 忽略 EXIF 元数据
     */
    public native void setIgnoreExif(boolean ignore);
    
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
