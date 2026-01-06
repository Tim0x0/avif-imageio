package com.github.avifimageio;

import javax.imageio.ImageTypeSpecifier;
import javax.imageio.spi.ImageWriterSpi;
import javax.imageio.stream.ImageOutputStream;
import java.util.Locale;

/**
 * AVIF ImageWriter 服务提供者接口
 * 
 * <p>通过 Java ImageIO SPI 机制注册 AVIF 写入支持。</p>
 */
public class AvifImageWriterSpi extends ImageWriterSpi {
    
    /**
     * 创建 AVIF ImageWriter SPI
     */
    public AvifImageWriterSpi() {
        super(
            "avif-imageio",                              // vendorName
            "1.0",                                       // version
            new String[]{"avif", "AVIF"},               // names
            new String[]{"avif"},                        // suffixes
            new String[]{"image/avif"},                  // MIMETypes
            AvifImageWriter.class.getName(),             // writerClassName
            new Class[]{ImageOutputStream.class},        // outputTypes
            new String[]{AvifImageReaderSpi.class.getName()}, // readerSpiNames
            false,                                       // supportsStandardStreamMetadataFormat
            null,                                        // nativeStreamMetadataFormatName
            null,                                        // nativeStreamMetadataFormatClassName
            null,                                        // extraStreamMetadataFormatNames
            null,                                        // extraStreamMetadataFormatClassNames
            false,                                       // supportsStandardImageMetadataFormat
            null,                                        // nativeImageMetadataFormatName
            null,                                        // nativeImageMetadataFormatClassName
            null,                                        // extraImageMetadataFormatNames
            null                                         // extraImageMetadataFormatClassNames
        );
    }
    
    @Override
    public boolean canEncodeImage(ImageTypeSpecifier type) {
        // 支持所有图像类型
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
