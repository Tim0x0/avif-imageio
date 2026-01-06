package com.github.avifimageio;

import javax.imageio.ImageWriteParam;
import java.util.Locale;

/**
 * AVIF 图片写入参数
 * 
 * <p>扩展 ImageWriteParam 以支持 AVIF 特定的编码参数。</p>
 */
public class AvifWriteParam extends ImageWriteParam {
    
    private int quality = AvifEncoderOptions.DEFAULT_QUALITY;
    private int speed = AvifEncoderOptions.DEFAULT_SPEED;
    private int bitDepth = AvifEncoderOptions.DEFAULT_BIT_DEPTH;
    private boolean lossless = false;
    
    /**
     * 创建 AVIF 写入参数
     * 
     * @param locale 区域设置
     */
    public AvifWriteParam(Locale locale) {
        super(locale);
        canWriteCompressed = true;
        compressionTypes = new String[]{"AVIF"};
        compressionType = "AVIF";
    }
    
    /**
     * 获取质量值
     * @return 质量值 (0-100)
     */
    public int getQuality() { 
        return quality; 
    }
    
    /**
     * 设置质量值
     * @param quality 质量值 (0-100)
     * @throws IllegalArgumentException 如果质量值超出范围
     */
    public void setQuality(int quality) {
        if (quality < 0 || quality > 100) {
            throw new IllegalArgumentException("Quality must be between 0 and 100, got: " + quality);
        }
        this.quality = quality;
    }
    
    /**
     * 获取编码速度
     * @return 编码速度 (0-10)
     */
    public int getSpeed() { 
        return speed; 
    }
    
    /**
     * 设置编码速度
     * @param speed 编码速度 (0-10, 0=最慢最好, 10=最快)
     * @throws IllegalArgumentException 如果速度值超出范围
     */
    public void setSpeed(int speed) {
        if (speed < 0 || speed > 10) {
            throw new IllegalArgumentException("Speed must be between 0 and 10, got: " + speed);
        }
        this.speed = speed;
    }
    
    /**
     * 获取位深度
     * @return 位深度 (8, 10, 12)
     */
    public int getBitDepth() { 
        return bitDepth; 
    }
    
    /**
     * 设置位深度
     * @param bitDepth 位深度 (8, 10, 12)
     * @throws IllegalArgumentException 如果位深度无效
     */
    public void setBitDepth(int bitDepth) {
        if (bitDepth != 8 && bitDepth != 10 && bitDepth != 12) {
            throw new IllegalArgumentException("Bit depth must be 8, 10, or 12, got: " + bitDepth);
        }
        this.bitDepth = bitDepth;
    }
    
    /**
     * 是否为无损模式
     * @return true 如果启用无损模式
     */
    public boolean isLossless() { 
        return lossless; 
    }
    
    /**
     * 设置无损模式
     * @param lossless true 启用无损模式
     */
    public void setLossless(boolean lossless) { 
        this.lossless = lossless; 
    }
}
