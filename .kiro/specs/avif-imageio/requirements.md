# Requirements Document

## Introduction

avif-imageio 是一个独立的 Java ImageIO AVIF 库，参照 [sejda-pdf/webp-imageio](https://github.com/sejda-pdf/webp-imageio) 项目的架构设计。该库通过 JNI 封装 libavif，为 Java 应用提供开箱即用的 AVIF 图片读写能力，可作为 Maven/Gradle 依赖直接引入使用。

项目结构：
- Java 层：JNI 桥接类 + ImageIO SPI 实现
- Native 层：C 代码封装 libavif，通过 CMake 构建
- 原生库打包在 JAR 的 `/native/{platform}/{bits}/` 路径下

## Glossary

- **avif_imageio**: 本项目的库名称，提供 Java ImageIO 的 AVIF 读写支持
- **Avif**: JNI 桥接类（类似 webp-imageio 的 WebP.java），声明 native 方法并负责加载原生库
- **AvifDecoderOptions**: 解码选项类，封装 libavif 的解码参数
- **AvifEncoderOptions**: 编码选项类，封装 libavif 的编码参数（质量、速度等）
- **NativeLibraryUtils**: 原生库加载工具类，从 JAR 提取并加载平台对应的原生库
- **AvifImageReader**: ImageIO Reader 实现，负责解码 AVIF 图片
- **AvifImageWriter**: ImageIO Writer 实现，负责编码 AVIF 图片
- **AvifImageReaderSpi**: ImageIO Reader 服务提供者接口
- **AvifImageWriterSpi**: ImageIO Writer 服务提供者接口
- **libavif**: AOMedia 官方的 AVIF 编解码 C 库

## Requirements

### Requirement 1: 项目结构

**User Story:** As a library developer, I want the project structure to be consistent with webp-imageio, so that it is easy to maintain and understand.

#### Acceptance Criteria

1. THE avif_imageio project SHALL contain Java source directory `src/main/java/`
2. THE avif_imageio project SHALL contain C source directory `src/main/c/`
3. THE avif_imageio project SHALL contain native library resource directory `src/main/resources/native/{platform}/{bits}/`
4. THE avif_imageio project SHALL use Gradle for build with support for publishing to Maven Central
5. THE avif_imageio project SHALL support Java 8 and above

### Requirement 2: 原生库打包

**User Story:** As a library user, I want native libraries bundled with the JAR, so that I can use it immediately after adding the dependency.

#### Acceptance Criteria

1. THE JAR SHALL contain pre-compiled native libraries at path `/native/{platform}/{bits}/{libname}`
2. THE avif_imageio SHALL support platforms: win/64, linux/64, mac/64 (Intel), mac/arm64 (Apple Silicon)
3. THE native library SHALL be named `avif-imageio.dll` on Windows, `libavif-imageio.so` on Linux, `libavif-imageio.dylib` on macOS
4. THE JAR SHALL have minimal external dependencies (only JDK standard library)

### Requirement 3: 原生库自动加载

**User Story:** As a library user, I want native libraries to load automatically, so that I don't need manual configuration.

#### Acceptance Criteria

1. WHEN the library is first used, THE NativeLibraryUtils SHALL detect current OS (win/linux/mac) and architecture (64/arm64)
2. WHEN loading native library, THE NativeLibraryUtils SHALL extract matching library from JAR resources to a temporary file
3. WHEN library is extracted, THE NativeLibraryUtils SHALL use System.load() to load the extracted library
4. THE temporary file SHALL be set with deleteOnExit for automatic cleanup when JVM exits
5. IF no matching native library is found for current platform, THEN THE NativeLibraryUtils SHALL throw RuntimeException with supported platforms list
6. THE NativeLibraryUtils SHALL support loading native library from custom path via system property

### Requirement 4: JNI 桥接类

**User Story:** As a library developer, I want clear JNI interface definitions, similar to webp-imageio's WebP.java.

#### Acceptance Criteria

1. THE Avif class SHALL call NativeLibraryUtils.loadFromJar() in static initializer block to load native library
2. THE Avif class SHALL provide decode method accepting AVIF byte data and decoder options, returning pixel data
3. THE Avif class SHALL provide encodeRGB method accepting RGB pixel data and encoder options, returning AVIF bytes
4. THE Avif class SHALL provide encodeRGBA method accepting RGBA pixel data and encoder options, returning AVIF bytes
5. THE Avif class SHALL provide getInfo method to get AVIF image dimensions without full decoding
6. THE Avif class SHALL properly manage native memory to prevent memory leaks

### Requirement 5: 编码选项类

**User Story:** As a library user, I want to configure AVIF encoding parameters.

#### Acceptance Criteria

1. THE AvifEncoderOptions SHALL support quality parameter (0-100, default 75)
2. THE AvifEncoderOptions SHALL support encoding speed parameter (0-10, 0=slowest/best quality, 10=fastest, default 6)
3. THE AvifEncoderOptions SHALL support lossless encoding mode when quality is set to 100
4. WHEN invalid parameter values are provided, THE AvifEncoderOptions SHALL throw IllegalArgumentException with valid range
5. THE AvifEncoderOptions SHALL pass configuration pointer to native code via JNI

### Requirement 6: 解码选项类

**User Story:** As a library user, I want to configure AVIF decoding parameters.

#### Acceptance Criteria

1. THE AvifDecoderOptions SHALL support basic decoding configuration
2. THE AvifDecoderOptions SHALL pass configuration pointer to native code via JNI

### Requirement 7: ImageIO Writer 实现

**User Story:** As a library user, I want to write AVIF images using standard ImageIO API.

#### Acceptance Criteria

1. WHEN ImageIO.write(image, "avif", output) is called, THE AvifImageWriter SHALL encode the image to AVIF format
2. THE AvifImageWriter SHALL support setting compression quality (0.0-1.0) via ImageWriteParam
3. THE AvifImageWriter SHALL handle BufferedImage of TYPE_INT_RGB and TYPE_INT_ARGB types
4. WHEN a BufferedImage with alpha channel is provided, THE AvifImageWriter SHALL preserve the alpha channel in output
5. THE AvifImageWriter SHALL support writing to OutputStream and File destinations

### Requirement 8: ImageIO Reader 实现

**User Story:** As a library user, I want to read AVIF images using standard ImageIO API.

#### Acceptance Criteria

1. WHEN ImageIO.read(avifFile) is called, THE AvifImageReader SHALL decode and return a BufferedImage
2. WHEN an AVIF file with alpha channel is provided, THE AvifImageReader SHALL preserve the alpha channel in returned BufferedImage
3. WHEN an invalid or corrupted AVIF file is provided, THE AvifImageReader SHALL throw IIOException with descriptive error message
4. THE AvifImageReader SHALL support reading from InputStream, File, and URL sources

### Requirement 9: ImageIO SPI 注册

**User Story:** As a library user, I want the library to auto-register with ImageIO, so that I don't need manual configuration.

#### Acceptance Criteria

1. THE avif_imageio SHALL provide AvifImageReaderSpi and AvifImageWriterSpi implementations
2. THE SPI SHALL auto-register via META-INF/services/javax.imageio.spi.ImageReaderSpi and ImageWriterSpi files
3. WHEN ImageIO.getReaderFormatNames() is called, THE result SHALL include "avif" and "AVIF"
4. WHEN ImageIO.getWriterFormatNames() is called, THE result SHALL include "avif" and "AVIF"
5. THE AvifImageReaderSpi SHALL correctly identify AVIF files by magic bytes

### Requirement 10: C 原生代码实现

**User Story:** As a library developer, I want clear C code implementing JNI interfaces.

#### Acceptance Criteria

1. THE C code SHALL implement all native methods declared in Avif.java
2. THE C code SHALL correctly call libavif API for encoding and decoding
3. THE C code SHALL properly handle memory allocation and deallocation to avoid memory leaks
4. IF encoding or decoding fails, THEN THE C code SHALL throw Java exception with error information
5. THE C code SHALL be buildable with CMake

### Requirement 11: 错误处理

**User Story:** As a library user, I want clear error messages when something goes wrong.

#### Acceptance Criteria

1. IF platform is not supported (no matching native library), THEN THE NativeLibraryUtils SHALL throw RuntimeException with supported platforms list
2. IF AVIF data is corrupted or invalid, THEN THE AvifImageReader SHALL throw IOException with reason
3. IF encoding parameters are invalid, THEN THE AvifEncoderOptions SHALL throw IllegalArgumentException with valid range
4. IF native library version is incompatible, THEN THE NativeLibraryUtils SHALL report version mismatch clearly
