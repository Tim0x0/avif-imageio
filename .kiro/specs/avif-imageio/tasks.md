# Implementation Plan: avif-imageio

## Overview

本实现计划将 avif-imageio 库分解为可执行的编码任务，按照依赖关系排序，确保每个任务都能在前一个任务的基础上构建。

## Tasks

- [x] 1. 项目初始化和构建配置
  - [x] 1.1 创建 Gradle 项目结构
    - 创建 `build.gradle` 和 `settings.gradle`
    - 配置 Java 8+ 编译目标
    - 添加 JUnit 5 和 jqwik 测试依赖
    - 配置 Maven Central 发布
    - _Requirements: 1.1, 1.4, 1.5_

  - [x] 1.2 创建目录结构
    - 创建 `src/main/java/com/github/avifimageio/`
    - 创建 `src/main/c/`
    - 创建 `src/main/resources/native/{platform}/{bits}/`
    - 创建 `src/main/resources/META-INF/services/`
    - 创建 `src/test/java/com/github/avifimageio/`
    - _Requirements: 1.1, 1.2, 1.3_

  - [x] 1.3 创建 CMake 构建配置
    - 创建顶层 `CMakeLists.txt`
    - 创建 `src/main/c/CMakeLists.txt`
    - 配置 libavif 依赖
    - 配置跨平台编译选项
    - _Requirements: 10.5_

- [x] 2. 数据传输对象和选项类
  - [x] 2.1 实现 DecodeResult 和 ImageInfo record
    - 创建 `DecodeResult.java` - 解码结果
    - 创建 `ImageInfo.java` - 图片信息
    - 添加 Javadoc 说明数组不可变约定
    - _Requirements: 4.2, 4.5_

  - [x] 2.2 实现 AvifEncoderOptions
    - 创建 `AvifEncoderOptions.java`
    - 实现 AutoCloseable 接口
    - 实现线程安全的 getPointer() 方法
    - 声明 native 方法（createConfig, deleteConfig, getters/setters）
    - _Requirements: 5.1, 5.2, 5.3, 5.4, 5.5_

  - [ ]* 2.3 编写 AvifEncoderOptions 单元测试
    - 测试质量参数边界 (0, 100, -1, 101)
    - 测试速度参数边界 (0, 10, -1, 11)
    - 测试位深度参数 (8, 10, 12, 其他)
    - 测试 AutoCloseable 和线程安全
    - _Requirements: 5.4, 11.3_

  - [x] 2.4 实现 AvifDecoderOptions
    - 创建 `AvifDecoderOptions.java`
    - 实现 AutoCloseable 接口
    - 实现线程安全的 getPointer() 方法
    - 声明 native 方法
    - _Requirements: 6.1, 6.2_

- [x] 3. 原生库加载器
  - [x] 3.1 实现 NativeLibraryUtils
    - 创建 `NativeLibraryUtils.java`
    - 实现 detectPlatform() 方法
    - 实现 extractToTempFile() 方法
    - 实现 loadFromJar() 方法
    - 实现 loadFromPath() 方法
    - 添加 shutdown hook 清理机制
    - _Requirements: 3.1, 3.2, 3.3, 3.4, 3.5, 3.6_

  - [ ]* 3.2 编写 NativeLibraryUtils 单元测试
    - 测试平台检测逻辑
    - 测试不支持平台的异常
    - _Requirements: 3.1, 3.5, 11.1_

- [x] 4. JNI 桥接类
  - [x] 4.1 实现 Avif.java
    - 创建 `Avif.java`
    - 实现懒加载机制 (loadNativeLibrary)
    - 实现 isAvailable() 和 getLoadError()
    - 声明所有 native 方法
    - 实现 Java 包装方法（参数校验、异常处理）
    - _Requirements: 4.1, 4.2, 4.3, 4.4, 4.5, 4.6_

  - [x] 4.2 生成 JNI 头文件
    - 使用 javac -h 生成 JNI 头文件
    - 创建 `src/main/c/avif_imageio.h`
    - _Requirements: 10.1_

- [x] 5. Checkpoint - 确保 Java 层编译通过
  - 确保所有 Java 类编译通过
  - 如有问题请询问用户

- [x] 6. C 原生代码实现
  - [x] 6.1 实现编码器配置 JNI 方法
    - 创建 `src/main/c/avif_imageio.c`
    - 实现 AvifEncoderOptions_createConfig
    - 实现 AvifEncoderOptions_deleteConfig
    - 实现 getter/setter 方法（带参数校验）
    - _Requirements: 5.5, 10.1, 10.3_

  - [x] 6.2 实现解码器配置 JNI 方法
    - 实现 AvifDecoderOptions_createOptions
    - 实现 AvifDecoderOptions_deleteOptions
    - 实现 getter/setter 方法
    - _Requirements: 6.2, 10.1_

  - [x] 6.3 实现 getInfo JNI 方法
    - 实现 Avif_getInfoNative
    - 创建 avifDecoder，解析但不完全解码
    - 返回 ImageInfo 对象
    - 正确释放资源
    - _Requirements: 4.5, 10.2, 10.3_

  - [x] 6.4 实现 decode JNI 方法
    - 实现 Avif_decodeNative
    - 创建 avifDecoder，完整解码
    - 转换 YUV 到 RGB
    - 返回 DecodeResult 对象
    - 处理 ICC 色彩配置
    - 正确释放资源
    - _Requirements: 4.2, 10.2, 10.3, 10.4_

  - [x] 6.5 实现 decodeFrame JNI 方法
    - 实现 Avif_decodeFrameNative
    - 支持动画 AVIF 指定帧解码
    - _Requirements: (动画支持)_

  - [x] 6.6 实现 encodeRGB JNI 方法
    - 实现 Avif_encodeRGBNative
    - 创建 avifImage 和 avifEncoder
    - 转换 RGB 到 YUV
    - 编码并返回 AVIF 字节数据
    - 正确释放资源
    - _Requirements: 4.3, 10.2, 10.3, 10.4_

  - [x] 6.7 实现 encodeRGBA JNI 方法
    - 实现 Avif_encodeRGBANative
    - 处理 Alpha 通道
    - _Requirements: 4.4, 10.2, 10.3_

  - [x] 6.8 实现元数据 JNI 方法
    - 实现 Avif_getExifNative
    - 实现 Avif_getIccProfileNative
    - _Requirements: (元数据支持)_

- [ ] 7. Checkpoint - 确保 JNI 编译通过
  - 编译原生库
  - 确保 JNI 方法签名匹配
  - 如有问题请询问用户


- [x] 8. ImageIO SPI 实现
  - [x] 8.1 实现 AvifImageReaderSpi
    - 创建 `AvifImageReaderSpi.java`
    - 实现 canDecodeInput() - AVIF magic bytes 检测
    - 使用 Arrays.equals() 进行字节比较
    - 实现 createReaderInstance()
    - _Requirements: 9.1, 9.5_

  - [x] 8.2 实现 AvifImageWriterSpi
    - 创建 `AvifImageWriterSpi.java`
    - 实现 canEncodeImage()
    - 实现 createWriterInstance()
    - _Requirements: 9.1_

  - [x] 8.3 创建 SPI 注册文件
    - 创建 `META-INF/services/javax.imageio.spi.ImageReaderSpi`
    - 创建 `META-INF/services/javax.imageio.spi.ImageWriterSpi`
    - _Requirements: 9.2_

  - [ ]* 8.4 编写 SPI 注册测试
    - 测试 ImageIO.getReaderFormatNames() 包含 "avif"
    - 测试 ImageIO.getWriterFormatNames() 包含 "avif"
    - _Requirements: 9.3, 9.4_

- [x] 9. ImageIO Reader 实现
  - [x] 9.1 实现 AvifImageReader
    - 创建 `AvifImageReader.java`
    - 实现 setInput() - 重置状态
    - 实现 readHeader() - 懒加载头信息
    - 实现 getWidth()/getHeight()
    - 实现 getNumImages() - 支持动画帧数
    - 实现 read() - 完整解码
    - 实现 getImageTypes() - 返回有效 Iterator
    - 实现 getImageMetadata() - 返回 AvifMetadata
    - _Requirements: 8.1, 8.2, 8.3, 8.4_

  - [x] 9.2 实现 AvifMetadata
    - 创建 `AvifMetadata.java`
    - 实现 getExifData()
    - 实现 getIccProfile()
    - 实现 getAsTree()
    - _Requirements: (元数据支持)_

  - [ ]* 9.3 编写 AvifImageReader 属性测试
    - **Property 3: getInfo 返回正确尺寸**
    - **Validates: Requirements 4.5**

  - [ ]* 9.4 编写损坏数据属性测试
    - **Property 4: 损坏数据解码抛出异常**
    - **Validates: Requirements 8.3, 11.2**

- [x] 10. ImageIO Writer 实现
  - [x] 10.1 实现 AvifWriteParam
    - 创建 `AvifWriteParam.java`
    - 实现 quality/speed/bitDepth/lossless 参数
    - 实现参数校验
    - _Requirements: 5.1, 5.2, 5.4_

  - [x] 10.2 实现 AvifImageWriter
    - 创建 `AvifImageWriter.java`
    - 实现 setOutput()
    - 实现 write() - 编码图片
    - 实现 extractRGB()/extractRGBA() - 使用 getRGB() 保证格式统一
    - 实现 toBufferedImage() - 带 Graphics dispose
    - 实现 getDefaultWriteParam()
    - _Requirements: 7.1, 7.2, 7.3, 7.4, 7.5_

  - [ ]* 10.3 编写 AvifImageWriter 属性测试
    - **Property 1: 编解码往返一致性**
    - **Validates: Requirements 4.2, 4.3, 4.4, 7.1, 7.3, 7.4, 8.1, 8.2, 10.2**

  - [ ]* 10.4 编写质量参数属性测试
    - **Property 2: 质量参数影响文件大小**
    - **Validates: Requirements 5.1, 7.2**

- [ ] 11. Checkpoint - 确保 ImageIO 集成测试通过
  - 运行所有单元测试
  - 运行所有属性测试
  - 如有问题请询问用户

- [ ] 12. 参数验证属性测试
  - [ ]* 12.1 编写无效参数属性测试
    - **Property 5: 无效编码参数抛出异常**
    - **Validates: Requirements 5.4, 11.3**

  - [ ]* 12.2 编写格式检测属性测试
    - **Property 6: AVIF 格式正确识别**
    - **Validates: Requirements 9.5**

- [ ] 13. 高级功能测试
  - [ ]* 13.1 编写动画帧数属性测试
    - **Property 7: 动画帧数一致性**
    - **Validates: (动画支持)**

  - [ ]* 13.2 编写元数据属性测试
    - **Property 8: 元数据完整性**
    - **Validates: (元数据支持)**

- [ ] 14. 原生库编译和打包
  - [ ] 14.1 编译 Windows x64 原生库
    - 使用 CMake 和 MSVC 编译
    - 生成 `avif-imageio.dll`
    - 放置到 `src/main/resources/native/win/64/`
    - _Requirements: 2.1, 2.2, 2.3_

  - [ ] 14.2 编译 Linux x64 原生库
    - 使用 CMake 和 GCC 编译
    - 生成 `libavif-imageio.so`
    - 放置到 `src/main/resources/native/linux/64/`
    - _Requirements: 2.1, 2.2, 2.3_

  - [ ] 14.3 编译 macOS x64 原生库
    - 使用 CMake 和 Clang 编译
    - 生成 `libavif-imageio.dylib`
    - 放置到 `src/main/resources/native/mac/64/`
    - _Requirements: 2.1, 2.2, 2.3_

  - [ ] 14.4 编译 macOS arm64 原生库
    - 使用 CMake 和 Clang 交叉编译
    - 生成 `libavif-imageio.dylib`
    - 放置到 `src/main/resources/native/mac/arm64/`
    - _Requirements: 2.1, 2.2, 2.3_

- [ ] 15. Final Checkpoint - 完整测试
  - 运行所有测试
  - 验证跨平台原生库加载
  - 确保所有属性测试通过
  - 如有问题请询问用户

## Notes

- 任务标记 `*` 的为可选测试任务，可跳过以加快 MVP 开发
- 每个任务引用具体的需求以保证可追溯性
- Checkpoint 任务确保增量验证
- 属性测试验证通用正确性属性
- 单元测试验证特定示例和边界情况
