package com.github.avifimageio;

/**
 * AVIF 图片信息（不完全解码获取）
 */
public final class ImageInfo {
    
    private final int width;
    private final int height;
    private final int bitDepth;
    private final boolean hasAlpha;
    private final int frameCount;
    private final double duration;
    private final boolean hasIccProfile;
    private final boolean hasExif;
    
    /**
     * 创建图片信息
     * 
     * @param width 图片宽度
     * @param height 图片高度
     * @param bitDepth 位深度 (8, 10, 12)
     * @param hasAlpha 是否有 Alpha 通道
     * @param frameCount 帧数（动画 AVIF）
     * @param duration 总时长（秒，动画 AVIF）
     * @param hasIccProfile 是否有 ICC 色彩配置
     * @param hasExif 是否有 EXIF 元数据
     */
    public ImageInfo(int width, int height, int bitDepth, boolean hasAlpha,
                     int frameCount, double duration, 
                     boolean hasIccProfile, boolean hasExif) {
        this.width = width;
        this.height = height;
        this.bitDepth = bitDepth;
        this.hasAlpha = hasAlpha;
        this.frameCount = frameCount;
        this.duration = duration;
        this.hasIccProfile = hasIccProfile;
        this.hasExif = hasExif;
    }
    
    /** 获取图片宽度 */
    public int width() { return width; }
    
    /** 获取图片高度 */
    public int height() { return height; }
    
    /** 获取位深度 (8, 10, 12) */
    public int bitDepth() { return bitDepth; }
    
    /** 是否有 Alpha 通道 */
    public boolean hasAlpha() { return hasAlpha; }
    
    /** 获取帧数（动画 AVIF，静态图片为 1） */
    public int frameCount() { return frameCount; }
    
    /** 获取总时长（秒，动画 AVIF） */
    public double duration() { return duration; }
    
    /** 是否有 ICC 色彩配置 */
    public boolean hasIccProfile() { return hasIccProfile; }
    
    /** 是否有 EXIF 元数据 */
    public boolean hasExif() { return hasExif; }
}
