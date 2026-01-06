package com.github.avifimageio;

import javax.imageio.metadata.IIOMetadata;
import javax.imageio.metadata.IIOMetadataNode;
import org.w3c.dom.Node;
import java.io.IOException;

/**
 * AVIF 图片元数据
 * 
 * <p>提供对 AVIF 图片中 EXIF 和 ICC 色彩配置的访问。</p>
 */
public class AvifMetadata extends IIOMetadata {
    
    private static final String NATIVE_FORMAT_NAME = "avif_metadata_1.0";
    
    private final byte[] avifData;
    private byte[] exifData;
    private byte[] iccProfile;
    private boolean loaded = false;
    
    /**
     * 创建 AVIF 元数据
     * 
     * @param avifData AVIF 图片数据
     */
    public AvifMetadata(byte[] avifData) {
        super(false, NATIVE_FORMAT_NAME, null, null, null);
        this.avifData = avifData;
    }
    
    /**
     * 懒加载元数据
     */
    private void loadMetadata() {
        if (loaded) return;
        try {
            exifData = Avif.getExif(avifData, 0, avifData.length);
            iccProfile = Avif.getIccProfile(avifData, 0, avifData.length);
        } catch (IOException e) {
            // 忽略元数据加载错误
        }
        loaded = true;
    }
    
    /**
     * 获取 EXIF 数据
     * 
     * @return EXIF 数据，如果不存在则返回 null
     */
    public byte[] getExifData() {
        loadMetadata();
        return exifData;
    }
    
    /**
     * 获取 ICC 色彩配置
     * 
     * @return ICC 配置数据，如果不存在则返回 null
     */
    public byte[] getIccProfile() {
        loadMetadata();
        return iccProfile;
    }
    
    @Override
    public boolean isReadOnly() { 
        return true; 
    }
    
    @Override
    public Node getAsTree(String formatName) {
        loadMetadata();
        IIOMetadataNode root = new IIOMetadataNode(formatName);
        
        if (exifData != null && exifData.length > 0) {
            IIOMetadataNode exifNode = new IIOMetadataNode("EXIF");
            exifNode.setUserObject(exifData);
            root.appendChild(exifNode);
        }
        
        if (iccProfile != null && iccProfile.length > 0) {
            IIOMetadataNode iccNode = new IIOMetadataNode("ICC");
            iccNode.setUserObject(iccProfile);
            root.appendChild(iccNode);
        }
        
        return root;
    }
    
    @Override
    public void mergeTree(String formatName, Node root) {
        throw new IllegalStateException("Metadata is read-only");
    }
    
    @Override
    public void reset() {
        loaded = false;
        exifData = null;
        iccProfile = null;
    }
}
