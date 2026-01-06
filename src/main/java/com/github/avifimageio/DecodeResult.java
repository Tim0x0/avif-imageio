package com.github.avifimageio;

/**
 * AVIF 解码结果
 * 
 * <p>注意：此类中的数组字段（pixels, iccProfile）是直接引用，
 * 调用者不应修改这些数组的内容，以保持数据完整性。
 * 如需修改，请先进行防御性拷贝。</p>
 */
public final class DecodeResult {
    
    private final int[] pixels;
    private final int width;
    private final int height;
    private final boolean hasAlpha;
    private final int bitDepth;
    private final byte[] iccProfile;
    
    /**
     * 创建解码结果
     * 
     * @param pixels ARGB 格式像素数据（不可修改）
     * @param width 图片宽度
     * @param height 图片高度
     * @param hasAlpha 是否有 Alpha 通道
     * @param bitDepth 位深度 (8, 10, 12)
     * @param iccProfile ICC 色彩配置（可为 null，不可修改）
     */
    public DecodeResult(int[] pixels, int width, int height, 
                        boolean hasAlpha, int bitDepth, byte[] iccProfile) {
        this.pixels = pixels;
        this.width = width;
        this.height = height;
        this.hasAlpha = hasAlpha;
        this.bitDepth = bitDepth;
        this.iccProfile = iccProfile;
    }
    
    /** 获取 ARGB 格式像素数据（不可修改） */
    public int[] pixels() { return pixels; }
    
    /** 获取图片宽度 */
    public int width() { return width; }
    
    /** 获取图片高度 */
    public int height() { return height; }
    
    /** 是否有 Alpha 通道 */
    public boolean hasAlpha() { return hasAlpha; }
    
    /** 获取位深度 (8, 10, 12) */
    public int bitDepth() { return bitDepth; }
    
    /** 获取 ICC 色彩配置（可为 null，不可修改） */
    public byte[] iccProfile() { return iccProfile; }
}
