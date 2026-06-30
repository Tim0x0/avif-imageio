#!/usr/bin/env bash
# 在 manylinux2014 (CentOS 7 / glibc 2.17) 容器内编译 Linux 原生库。
# 由 .github/workflows/build-native.yml 通过 `docker run` 调用。
# 产出的 .so 只依赖 glibc <= 2.17 的符号，可在 CentOS 7 及以后所有主流 Linux 上加载。
set -euo pipefail

# 1) 启用镜像自带的最新 devtoolset（现代 gcc/g++），不写死版本号以兼容镜像更新
DTS=$(ls -d /opt/rh/devtoolset-* | sort -V | tail -1)
# shellcheck source=/dev/null
source "${DTS}/enable"

# 2) meson / ninja（镜像自带 Python 3 + pip）
pip3 install meson ninja

# 3) nasm：镜像未预装足够新版本，从源码编译 2.15.05
#    (dav1d/aom 汇编所需；2.15.05 是最普及版本，官方源 URL 最稳定)
NASM_VERSION=2.15.05
curl -fsSL "https://www.nasm.us/pub/nasm/releasebuilds/${NASM_VERSION}/nasm-${NASM_VERSION}.tar.xz" -o /tmp/nasm.tar.xz
[ -s /tmp/nasm.tar.xz ] || { echo "ERROR: nasm 下载失败，请检查 URL/网络"; exit 1; }
tar -C /tmp -xf /tmp/nasm.tar.xz
( cd "/tmp/nasm-${NASM_VERSION}" && ./configure --prefix=/usr/local && make -j"$(nproc)" install )

# 4) 打印工具链版本（任一缺失会在 set -e 下失败，便于排查）
echo "=== toolchain versions ==="
gcc --version | head -1
g++ --version | head -1
cmake --version | head -1
ninja --version
meson --version
nasm -v

SRC=/work            # 挂载进来的仓库源码（host 的 GITHUB_WORKSPACE）
BW=/tmp/bw           # 容器内构建工作区（构建垃圾随容器销毁）
mkdir -p "$BW"
cd "$BW"

# 复制本项目源码到构建区
cp -a "$SRC/CMakeLists.txt" "$SRC/src" ./

# 5) 构建 libavif（含本地 dav1d / aom(仅编码器) / libyuv，全部静态链接）
git clone --depth 1 --branch v1.3.0 https://github.com/AOMediaCodec/libavif.git
cd libavif/ext
./dav1d.cmd
./libyuv.cmd
git clone -b v3.12.1 --depth 1 https://aomedia.googlesource.com/aom
cmake -G Ninja -S aom -B aom/build.libavif \
  -DBUILD_SHARED_LIBS=OFF \
  -DCONFIG_PIC=1 \
  -DCMAKE_BUILD_TYPE=Release \
  -DENABLE_DOCS=0 \
  -DENABLE_EXAMPLES=0 \
  -DENABLE_TESTDATA=0 \
  -DENABLE_TESTS=0 \
  -DENABLE_TOOLS=0 \
  -DCONFIG_AV1_DECODER=0
cmake --build aom/build.libavif --config Release --parallel
cd ..
cmake -B build -S . \
  -DCMAKE_BUILD_TYPE=Release \
  -DBUILD_SHARED_LIBS=OFF \
  -DAVIF_CODEC_DAV1D=LOCAL \
  -DAVIF_CODEC_AOM=LOCAL \
  -DAVIF_CODEC_AOM_DECODE=OFF \
  -DAVIF_LIBYUV=LOCAL
cmake --build build -j"$(nproc)"
cmake --install build

# 6) 配置并构建本项目 .so
cd "$BW"
cmake -B build -S . -DCMAKE_BUILD_TYPE=Release -DBUILD_STATIC=ON
cmake --build build --config Release

# 7) 校验产物 glibc 兼容性（最高符号应 <= GLIBC_2.17）+ strip
echo "=== required glibc versions (max should be <= 2.17) ==="
objdump -T build/libavif-imageio.so | grep -oE 'GLIBC_[0-9.]+' | sort -uV | tail -5 || true

strip --strip-unneeded build/libavif-imageio.so

# 8) 输出产物到挂载目录（host 可见，供 upload-artifact 上传）
mkdir -p "$SRC/artifacts/linux/64"
cp build/libavif-imageio.so "$SRC/artifacts/linux/64/"
echo "BUILD OK: $(ls -la "$SRC/artifacts/linux/64/libavif-imageio.so")"
