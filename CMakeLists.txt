cmake_minimum_required(VERSION 3.14)
project(avif-imageio C)

set(CMAKE_C_STANDARD 11)
set(CMAKE_C_STANDARD_REQUIRED ON)

# 静态链接选项
option(BUILD_STATIC "Build with static linking" OFF)

# 设置输出目录
set(CMAKE_LIBRARY_OUTPUT_DIRECTORY ${CMAKE_BINARY_DIR})
set(CMAKE_RUNTIME_OUTPUT_DIRECTORY ${CMAKE_BINARY_DIR})

# 查找 JNI
find_package(JNI REQUIRED)

# 查找 libavif - 优先使用 find_package (vcpkg 兼容)
find_package(libavif CONFIG QUIET)
if(libavif_FOUND)
    message(STATUS "Found libavif via find_package")
    set(AVIF_FOUND TRUE)
    set(AVIF_TARGET avif)
else()
    # 尝试 pkg-config
    find_package(PkgConfig QUIET)
    if(PkgConfig_FOUND)
        pkg_check_modules(AVIF libavif)
    endif()
    
    if(NOT AVIF_FOUND)
        # 手动查找 libavif (优先静态库)
        find_path(AVIF_INCLUDE_DIRS avif/avif.h
            PATHS
                /usr/local/include
                /usr/include
                /opt/homebrew/include
                $ENV{AVIF_ROOT}/include
        )
        if(BUILD_STATIC)
            find_library(AVIF_LIBRARIES
                NAMES libavif.a avif
                PATHS
                    /usr/local/lib
                    /usr/lib
                    /opt/homebrew/lib
                    $ENV{AVIF_ROOT}/lib
            )
            # 查找 dav1d 静态库
            find_library(DAV1D_LIBRARIES
                NAMES libdav1d.a dav1d
                PATHS
                    /usr/local/lib
                    /usr/lib
                    /opt/homebrew/lib
            )
        else()
            find_library(AVIF_LIBRARIES avif
                PATHS
                    /usr/local/lib
                    /usr/lib
                    /opt/homebrew/lib
                    $ENV{AVIF_ROOT}/lib
            )
        endif()
        if(AVIF_INCLUDE_DIRS AND AVIF_LIBRARIES)
            set(AVIF_FOUND TRUE)
        endif()
    endif()
endif()

if(NOT AVIF_FOUND)
    message(FATAL_ERROR "libavif not found. Please install libavif or set AVIF_ROOT environment variable.")
endif()

message(STATUS "Found libavif: ${AVIF_LIBRARIES}")
message(STATUS "libavif include: ${AVIF_INCLUDE_DIRS}")
if(DAV1D_LIBRARIES)
    message(STATUS "Found dav1d: ${DAV1D_LIBRARIES}")
endif()

add_subdirectory(src/main/c)
