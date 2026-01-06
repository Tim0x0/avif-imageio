set(NATIVE_LIB_NAME avif-imageio)

# 源文件
set(SOURCES
    avif_imageio.c
)

# 创建共享库
add_library(${NATIVE_LIB_NAME} SHARED ${SOURCES})

# 包含目录
target_include_directories(${NATIVE_LIB_NAME} PRIVATE
    ${JNI_INCLUDE_DIRS}
    ${AVIF_INCLUDE_DIRS}
    ${CMAKE_CURRENT_SOURCE_DIR}
)

# 链接库
if(TARGET avif)
    # 使用 find_package(libavif CONFIG) 找到的 target
    # libavif 静态库已经包含了 dav1d 和 libyuv
    target_link_libraries(${NATIVE_LIB_NAME} PRIVATE avif)
    
    # 静态链接时需要额外的系统库
    if(BUILD_STATIC)
        if(UNIX AND NOT APPLE)
            target_link_libraries(${NATIVE_LIB_NAME} PRIVATE pthread dl m)
        elseif(APPLE)
            target_link_libraries(${NATIVE_LIB_NAME} PRIVATE pthread)
        endif()
    endif()
elseif(BUILD_STATIC AND DAV1D_LIBRARIES)
    # 手动查找的静态库
    target_link_libraries(${NATIVE_LIB_NAME} PRIVATE ${AVIF_LIBRARIES} ${DAV1D_LIBRARIES})
    if(UNIX AND NOT APPLE)
        target_link_libraries(${NATIVE_LIB_NAME} PRIVATE pthread dl m)
    endif()
else()
    target_link_libraries(${NATIVE_LIB_NAME} PRIVATE ${AVIF_LIBRARIES})
endif()

# 平台特定设置
if(WIN32)
    set_target_properties(${NATIVE_LIB_NAME} PROPERTIES
        PREFIX ""
        SUFFIX ".dll"
    )
elseif(APPLE)
    set_target_properties(${NATIVE_LIB_NAME} PROPERTIES
        PREFIX "lib"
        SUFFIX ".dylib"
    )
else()
    set_target_properties(${NATIVE_LIB_NAME} PROPERTIES
        PREFIX "lib"
        SUFFIX ".so"
    )
endif()

# 编译选项
if(MSVC)
    target_compile_options(${NATIVE_LIB_NAME} PRIVATE /W4)
else()
    target_compile_options(${NATIVE_LIB_NAME} PRIVATE -Wall -Wextra -O2)
endif()
