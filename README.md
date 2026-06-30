# avif-imageio

[![构建原生库](https://github.com/Tim0x0/avif-imageio/actions/workflows/build-native.yml/badge.svg)](https://github.com/Tim0x0/avif-imageio/actions/workflows/build-native.yml)
[![GitHub Release](https://img.shields.io/github/v/release/Tim0x0/avif-imageio)](https://github.com/Tim0x0/avif-imageio/releases)
[![JitPack](https://jitpack.io/v/Tim0x0/avif-imageio.svg)](https://jitpack.io/#Tim0x0/avif-imageio)

Java ImageIO 插件，用于读写 AVIF 图片格式。

## 功能特性

- 🖼️ 通过标准 Java ImageIO API 读写 AVIF 图片
- 🎬 支持动画 AVIF（多帧）
- 🎨 支持 10/12 位色深
- 📊 支持 ICC 色彩配置文件
- 📷 支持 EXIF 元数据
- 🔧 可配置编码参数（质量、速度、无损）
- 💻 跨平台支持（Windows x64、Linux x64、macOS arm64）
- ☕ 兼容 Java 8+

## 安装

> 💡 将 `VERSION` 替换为最新版本号，见 [![JitPack](https://jitpack.io/v/Tim0x0/avif-imageio.svg)](https://jitpack.io/#Tim0x0/avif-imageio) 或 [Releases](https://github.com/Tim0x0/avif-imageio/releases)

### Gradle (Groovy)

```groovy
repositories {
    mavenCentral()
    maven { url 'https://jitpack.io' }
}

dependencies {
    implementation 'com.github.Tim0x0:avif-imageio:VERSION'
}
```

### Gradle (Kotlin)

```kotlin
repositories {
    mavenCentral()
    maven("https://jitpack.io")
}

dependencies {
    implementation("com.github.Tim0x0:avif-imageio:VERSION")
}
```

### Maven

```xml
<repositories>
    <repository>
        <id>jitpack.io</id>
        <url>https://jitpack.io</url>
    </repository>
</repositories>

<dependency>
    <groupId>com.github.Tim0x0</groupId>
    <artifactId>avif-imageio</artifactId>
    <version>VERSION</version>
</dependency>
```

## 使用方法

### 读取 AVIF 图片

```java
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;

// 简单读取
BufferedImage image = ImageIO.read(new File("image.avif"));

// 从字节数组读取
byte[] avifData = Files.readAllBytes(Paths.get("image.avif"));
BufferedImage image = ImageIO.read(new ByteArrayInputStream(avifData));
```

### 写入 AVIF 图片

```java
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;

BufferedImage image = /* 你的图片 */;
ImageIO.write(image, "avif", new File("output.avif"));
```

### 高级编码选项

```java
import com.github.avifimageio.AvifWriteParam;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;

ImageWriter writer = ImageIO.getImageWritersByFormatName("avif").next();
AvifWriteParam param = (AvifWriteParam) writer.getDefaultWriteParam();

param.setQuality(80);      // 0-100，默认 75
param.setSpeed(6);         // 0-10，默认 6（越高越快）
param.setLossless(false);  // true 为无损编码

try (ImageOutputStream ios = ImageIO.createImageOutputStream(new File("output.avif"))) {
    writer.setOutput(ios);
    writer.write(null, new IIOImage(image, null, null), param);
}
writer.dispose();
```

### 检查原生库是否可用

```java
import com.github.avifimageio.Avif;

if (Avif.isAvailable()) {
    // AVIF 支持可用
} else {
    Throwable error = Avif.getLoadError();
    System.err.println("AVIF 不可用: " + error.getMessage());
}
```

## 支持的平台

| 平台     | 架构          | 状态 |
|----------|--------------|------|
| Windows  | x64          | ✅   |
| Linux    | x64          | ✅   |
| macOS    | arm64 (M1/M2/M3) | ✅  |

> ℹ️ **Linux 兼容性**：Linux 原生库基于 glibc 2.17（manylinux2014）编译，兼容 CentOS 7 / RHEL 7 及以后所有主流 Linux 发行版。早期版本（≤ 0.1.2）依赖更高版本 glibc，在 CentOS 7 等老系统上会加载失败（报错 `GLIBC_2.32 not found`），请升级到最新版本。

## 从源码构建

### 前置条件

- JDK 8+
- CMake 3.16+
- C 编译器（MSVC/GCC/Clang）
- vcpkg（用于 libavif 依赖）

### 构建步骤

```bash
# 克隆仓库
git clone https://github.com/Tim0x0/avif-imageio.git
cd avif-imageio

# 构建 Java 代码
./gradlew build

# 构建原生库（需要 vcpkg）
cmake -B build -S . -DCMAKE_TOOLCHAIN_FILE=[vcpkg-root]/scripts/buildsystems/vcpkg.cmake
cmake --build build --config Release
```

## 许可证

本项目采用 GPL-3.0 许可证。

### 第三方库

本项目静态链接了以下开源库：

- [libavif](https://github.com/AOMediaCodec/libavif) - BSD-2-Clause License
- [dav1d](https://code.videolan.org/videolan/dav1d) - BSD-2-Clause License（AV1 解码）
- [aom](https://aomedia.googlesource.com/aom/) - BSD-2-Clause License（AV1 编码）
- [libyuv](https://chromium.googlesource.com/libyuv/libyuv/) - BSD-3-Clause License

## 致谢

- [libavif](https://github.com/AOMediaCodec/libavif) - AVIF 编解码库
- [dav1d](https://code.videolan.org/videolan/dav1d) - AV1 解码器（VideoLAN）
- [aom](https://aomedia.googlesource.com/aom/) - AV1 参考编码器（AOMedia）
- [libyuv](https://chromium.googlesource.com/libyuv/libyuv/) - 色彩空间转换（Google）
- [webp-imageio](https://github.com/nicoulaj/webp-imageio) - 架构参考
