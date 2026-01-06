package com.github.avifimageio;

import javax.imageio.spi.ImageReaderSpi;
import javax.imageio.stream.ImageInputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Locale;

/**
 * AVIF ImageReader 服务提供者接口
 * 
 * <p>通过 Java ImageIO SPI 机制注册 AVIF 读取支持。</p>
 */
public class AvifImageReaderSpi extends ImageReaderSpi {
    
    private static final byte[] FTYP = {0x66, 0x74, 0x79, 0x70};  // "ftyp"
    private static final byte[] AVIF = {0x61, 0x76, 0x69, 0x66};  // "avif"
    private static final byte[] AVIS = {0x61, 0x76, 0x69, 0x73};  // "avis"
    
    /**
     * 创建 AVIF ImageReader SPI
     */
    public AvifImageReaderSpi() {
        super(
            "avif-imageio",                              // vendorName
            "1.0",                                       // version
            new String[]{"avif", "AVIF"},               // names
            new String[]{"avif"},                        // suffixes
            new String[]{"image/avif"},                  // MIMETypes
            AvifImageReader.class.getName(),             // readerClassName
            new Class[]{ImageInputStream.class},         // inputTypes
            new String[]{AvifImageWriterSpi.class.getName()}, // writerSpiNames
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
    public boolean canDecodeInput(Object source) throws IOException {
        if (!(source instanceof ImageInputStream)) {
            return false;
        }
        
        ImageInputStream stream = (ImageInputStream) source;
        stream.mark();
        
        try {
            // 跳过 box size (4 bytes)
            stream.skipBytes(4);
            
            // 读取 box type，应为 "ftyp"
            byte[] ftyp = new byte[4];
            if (stream.read(ftyp) != 4) {
                return false;
            }
            
            if (!Arrays.equals(ftyp, FTYP)) {
                return false;
            }
            
            // 读取 major brand，应为 "avif" 或 "avis"
            byte[] brand = new byte[4];
            if (stream.read(brand) != 4) {
                return false;
            }
            
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
