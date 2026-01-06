# 从零开始：为 Java 打造一个 AVIF 图片处理库

最近花了不少时间折腾一个 Java ImageIO 插件项目 [avif-imageio](https://github.com/Tim0x0/avif-imageio)，让 Java 能够原生读写 AVIF 格式图片。这篇文章记录一下整个开发过程中遇到的坑和最终的解决方案。

## 为什么要做这个？

起因是想给 [Halo](https://github.com/halo-dev/halo) 博客写一个图片处理插件，支持 AVIF 格式。Halo 是基于 Spring Boot 的博客系统，插件通过 PF4J 框架加载。

AVIF 是目前最先进的图片格式之一，压缩率比 WebP 还要好 20-30%，主流浏览器都已经支持。但 Java 生态里一直没有一个好用的 AVIF 库——要么依赖外部命令行工具，要么需要用户自己安装系统库。对于 Halo 这种需要简单部署的应用来说，让用户装一堆依赖是不现实的。

我的目标很简单：**一个 JAR 包搞定一切**，不需要用户安装任何额外依赖，直接在 Halo 插件里引用就能用。

## 技术选型

### 核心库：libavif

[libavif](https://github.com/AOMediaCodec/libavif) 是 AOMedia 官方的 AVIF 编解码库，C 语言实现，跨平台。它本身不包含 AV1 编解码器，而是作为一个抽象层，可以对接多种后端。

### 编解码器选择

AV1 编解码器有好几个选择：

| 编解码器 | 功能 | 特点 |
|---------|------|------|
| dav1d | 仅解码 | VideoLAN 出品，最快的解码器 |
| aom (libaom) | 编码+解码 | AOMedia 参考实现，兼容性最好 |
| rav1e | 仅编码 | Rust 实现 |
| SVT-AV1 | 仅编码 | Intel 出品，速度快 |

最终选择了 **dav1d + aom** 的组合：
- 解码用 dav1d（Chrome、Firefox 都用它，速度最快）
- 编码用 aom（参考实现，兼容性有保障）

这也是业界主流方案。

### 色彩转换：libyuv

libavif 内部使用 YUV 色彩空间，而 Java 的 BufferedImage 用的是 RGB。libyuv 是 Google 的色彩空间转换库，性能很好，libavif 原生支持。

## 构建方案演进

### 第一版：动态链接（失败）

最开始想的是动态链接系统库，让用户自己安装 libavif。但这样用户体验太差了——不同系统安装方式不同，版本也可能不兼容。对于 Halo 用户来说，很多人用的是 Docker 部署或者云服务器，让他们去装 libavif 基本不现实。

### 第二版：静态链接（成功）

最终方案是把所有依赖静态链接到一个 native library 里，打包进 JAR。用户引入依赖就能用，零配置。

这个方案对 Halo 插件环境特别友好——插件 JAR 里自带原生库，加载时自动解压到临时目录并加载，完全透明。

## 分发方案：为什么选择 JitPack + GitHub Actions

### 为什么用 GitHub Actions 构建原生库？

说实话，我本身不太熟悉 Java 和 C/C++ 这套工具链，也不想在本地装一堆编译环境。GitHub Actions 完美解决了这个问题：

1. **不用装环境**：runner 上该有的都有，CMake、编译器、Ninja 都是现成的
2. **跨平台编译**：需要支持 Windows、Linux、macOS 三个平台，本地交叉编译很麻烦，Actions 直接在目标平台上编译
3. **可复现**：构建过程全在 YAML 里定义，换台机器也能跑

构建流程：
1. 编译 dav1d（解码器）
2. 编译 aom（编码器）
3. 编译 libyuv（色彩转换）
4. 编译 libavif（链接上面三个）
5. 编译 JNI 层，静态链接 libavif
6. Strip 符号表优化体积
7. 打包上传到 GitHub Release

### 为什么用 JitPack 分发？

几个选择：

| 方案 | 优点 | 缺点 |
|------|------|------|
| Maven Central | 最权威 | 发布流程繁琐，需要申请 |
| GitHub Packages | 和 GitHub 集成好 | 需要认证才能拉取 |
| JitPack | 零配置，tag 即发布 | 从源码构建，需要处理原生库 |

最终选了 JitPack：
- **零配置**：打个 tag 就自动发布，不需要额外操作
- **对开源友好**：公开仓库免费
- **用户使用简单**：加个 repository 就能用

JitPack 的问题是它从源码构建，而原生库是 GitHub Actions 构建的。解决方案是在 `jitpack.yml` 里先从 GitHub Release 下载原生库，再执行 Gradle 构建。

## 踩过的坑

### 坑1：aom encoder-only 构建失败

为了减小体积，我尝试只编译 aom 的编码器部分（解码用 dav1d）：

```cmake
# aom 构建时禁用解码器
-DCONFIG_AV1_DECODER=0
```

结果链接时报错：

```
error LNK2019: unresolved external symbol aom_codec_av1_dx
```

原因是 libavif 的 `codec_aom.c` 默认会同时启用编码和解码，即使 aom 没编译解码器，libavif 还是会引用解码符号。

**解决方案**：在 libavif 构建时也要告诉它不用 aom 解码：

```cmake
# libavif 构建时
-DAVIF_CODEC_AOM_DECODE=OFF
```

两个配置配合使用：
- `-DCONFIG_AV1_DECODER=0`：aom 不编译解码器代码（减小体积）
- `-DAVIF_CODEC_AOM_DECODE=OFF`：libavif 不链接 aom 解码功能（避免符号引用）

### 坑2：JitPack 版本号问题

GitHub Release 用 `v0.1.0` 这样的 tag，但 JitPack 会把 `v` 前缀也当成版本号的一部分。

**解决方案**：在 `build.gradle` 里处理：

```groovy
def versionFromEnv = System.getenv('VERSION') ?: '0.0.0-SNAPSHOT'
// 去掉 v 前缀
version = versionFromEnv.startsWith('v') ? versionFromEnv.substring(1) : versionFromEnv
```

### 坑3：插件环境下的类加载问题

这个坑是专门为 Halo 踩的。Halo 用 PF4J 框架加载插件，每个插件有自己的 ClassLoader。原生库加载时用的 `Class.getClassLoader()` 在插件环境下拿不到正确的 ClassLoader，导致找不到 JAR 里的原生库文件。

类似的问题在 OSGi、Spring Boot DevTools 等环境下也会出现。

**解决方案**：优先使用 Thread Context ClassLoader：

```java
private static InputStream getResourceAsStream(String name) {
    // 优先使用 Thread Context ClassLoader（插件环境兼容）
    ClassLoader tcl = Thread.currentThread().getContextClassLoader();
    if (tcl != null) {
        InputStream is = tcl.getResourceAsStream(name);
        if (is != null) return is;
    }
    // 回退到 Class ClassLoader
    return NativeLibraryUtils.class.getResourceAsStream("/" + name);
}
```

PF4J 在调用插件代码时会设置正确的 Thread Context ClassLoader，所以这个方案能正确工作。

### 坑4：JitPack 构建时没有原生库

JitPack 从源码构建，但原生库是通过 GitHub Actions 单独构建的。JitPack 构建时找不到 native libraries。

**解决方案**：在 `jitpack.yml` 里先从 GitHub Release 下载原生库：

```yaml
before_install:
  - VERSION=${VERSION#v}
  - curl -L "https://github.com/Tim0x0/avif-imageio/releases/download/v${VERSION}/native-libraries.zip" -o native.zip
  - unzip native.zip -d src/main/resources/
```

## 体积优化

静态链接后，native library 体积不小。做了几个优化：

### 1. aom encoder-only

如前所述，aom 只编译编码器，解码用 dav1d。

### 2. Strip 符号表

```bash
# Linux
strip --strip-unneeded libavif-imageio.so

# macOS
strip -x libavif-imageio.dylib
```

### 3. 禁用不需要的功能

```cmake
-DENABLE_DOCS=0
-DENABLE_EXAMPLES=0
-DENABLE_TESTDATA=0
-DENABLE_TESTS=0
-DENABLE_TOOLS=0
```

## 最终架构

```
┌─────────────────────────────────────────┐
│           Java Application              │
├─────────────────────────────────────────┤
│         Java ImageIO API                │
├─────────────────────────────────────────┤
│    avif-imageio (ImageReader/Writer)    │
├─────────────────────────────────────────┤
│              JNI Layer                  │
├─────────────────────────────────────────┤
│              libavif                    │
├──────────┬──────────┬───────────────────┤
│  dav1d   │   aom    │     libyuv        │
│ (decode) │ (encode) │ (color convert)   │
└──────────┴──────────┴───────────────────┘
```

## 使用示例

### 读取 AVIF

```java
BufferedImage image = ImageIO.read(new File("photo.avif"));
```

### 写入 AVIF

```java
ImageIO.write(image, "avif", new File("output.avif"));
```

### 自定义编码参数

```java
ImageWriter writer = ImageIO.getImageWritersByFormatName("avif").next();
AvifWriteParam param = (AvifWriteParam) writer.getDefaultWriteParam();

param.setQuality(80);      // 质量 0-100
param.setSpeed(6);         // 速度 0-10（越高越快，质量略降）
param.setLossless(false);  // 无损模式

try (ImageOutputStream ios = ImageIO.createImageOutputStream(new File("output.avif"))) {
    writer.setOutput(ios);
    writer.write(null, new IIOImage(image, null, null), param);
}
writer.dispose();
```

## 支持的平台

- Windows x64
- Linux x64
- macOS arm64 (Apple Silicon)

## 总结

这个项目的初衷是给 Halo 博客插件提供 AVIF 支持，但做着做着就变成了一个通用的 Java AVIF 库。几个关键点：

1. **静态链接是王道**：对于需要在插件环境运行的库，静态链接能大大简化部署
2. **CI/CD 很重要**：跨平台构建用 GitHub Actions 自动化，省心省力
3. **JitPack 真香**：对于开源项目，JitPack 的零配置发布体验很好
4. **主流方案优先**：选择 dav1d + aom 这样的主流组合，踩坑的人多，资料也多
5. **细节决定成败**：ClassLoader、版本号处理这些小问题，不注意就会让用户用不了

接下来就是基于这个库写 Halo 的图片处理插件了，敬请期待。

项目地址：[https://github.com/Tim0x0/avif-imageio](https://github.com/Tim0x0/avif-imageio)

欢迎 Star 和提 Issue！
