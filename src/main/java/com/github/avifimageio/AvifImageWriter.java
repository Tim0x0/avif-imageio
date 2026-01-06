package com.github.avifimageio;

import javax.imageio.IIOImage;
import javax.imageio.ImageTypeSpecifier;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.spi.ImageWriterSpi;
import javax.imageio.stream.ImageOutputStream;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.awt.image.RenderedImage;
import java.io.IOException;

/**
 * AVIF 图片写入器
 * 
 * <p>实现 ImageIO ImageWriter 接口，支持将图片编码为 AVIF 格式。</p>
 */
public class AvifImageWriter extends ImageWriter {
    
    private ImageOutputStream output;
    
    /**
     * 创建 AVIF 图片写入器
     * 
     * @param spi 服务提供者接口
     */
    protected AvifImageWriter(ImageWriterSpi spi) {
        super(spi);
    }
    
    @Override
    public void setOutput(Object output) {
        super.setOutput(output);
        if (output instanceof ImageOutputStream) {
            this.output = (ImageOutputStream) output;
        } else {
            this.output = null;
        }
    }
    
    @Override
    public void write(IIOMetadata streamMetadata, IIOImage image, ImageWriteParam param) 
            throws IOException {
        
        if (output == null) {
            throw new IllegalStateException("Output not set");
        }
        
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
     * 提取 RGB 数据
     * 
     * <p>使用 getRGB() 保证统一的 ARGB 格式，避免不同 BufferedImage 类型的像素顺序问题。</p>
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
     * 提取 RGBA 数据
     * 
     * <p>使用 getRGB() 保证统一的 ARGB 格式。</p>
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
        Graphics2D g = buffered.createGraphics();
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
