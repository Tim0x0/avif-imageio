package com.github.avifimageio;

import javax.imageio.IIOException;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.ImageTypeSpecifier;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.spi.ImageReaderSpi;
import javax.imageio.stream.ImageInputStream;
import java.awt.color.ICC_Profile;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * AVIF 图片读取器
 * 
 * <p>实现 ImageIO ImageReader 接口，支持读取 AVIF 图片。</p>
 */
public class AvifImageReader extends ImageReader {
    
    private byte[] avifData;
    private ImageInfo imageInfo;
    private boolean headerRead = false;
    
    /**
     * 创建 AVIF 图片读取器
     * 
     * @param spi 服务提供者接口
     */
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
    
    /**
     * 读取 AVIF 头信息（懒加载）
     */
    private void readHeader() throws IOException {
        if (headerRead) return;
        
        Object input = getInput();
        if (input == null) {
            throw new IllegalStateException("Input not set");
        }
        
        if (!(input instanceof ImageInputStream)) {
            throw new IIOException("Input must be an ImageInputStream");
        }
        
        ImageInputStream stream = (ImageInputStream) input;
        avifData = readAllBytes(stream);
        imageInfo = Avif.getInfo(avifData, 0, avifData.length);
        headerRead = true;
    }
    
    /**
     * 从流中读取所有字节
     */
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
            
            // 应用 ICC 色彩配置（可选）
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
        
        List<ImageTypeSpecifier> types = new ArrayList<ImageTypeSpecifier>();
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
    
    /**
     * 检查图像索引是否有效
     */
    private void checkIndex(int imageIndex) throws IOException {
        readHeader();
        if (imageIndex < 0 || imageIndex >= imageInfo.frameCount()) {
            throw new IndexOutOfBoundsException(
                "Image index " + imageIndex + " out of range [0, " + imageInfo.frameCount() + ")");
        }
    }
}
