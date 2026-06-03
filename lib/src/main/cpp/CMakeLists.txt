cmake_minimum_required(VERSION 3.22.1)

project("ai-chat" VERSION 1.0.0 LANGUAGES C CXX)

set(CMAKE_C_STANDARD 11)
set(CMAKE_C_STANDARD_REQUIRED true)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED true)

set(CMAKE_C_FLAGS   "${CMAKE_C_FLAGS}"   CACHE STRING "" FORCE)
set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS}" CACHE STRING "" FORCE)

if(DEFINED ANDROID_ABI)
    message(STATUS "Detected Android ABI: ${ANDROID_ABI}")
    if(ANDROID_ABI STREQUAL "arm64-v8a")
        set(GGML_SYSTEM_ARCH "ARM")
        set(GGML_CPU_KLEIDIAI ON)
        set(GGML_OPENMP ON)
    elseif(ANDROID_ABI STREQUAL "x86_64")
        set(GGML_SYSTEM_ARCH "x86")
        set(GGML_CPU_KLEIDIAI OFF)
        set(GGML_OPENMP OFF)
    else()
        message(FATAL_ERROR "Unsupported ABI: ${ANDROID_ABI}")
    endif()
endif()

set(LLAMA_SRC ${CMAKE_CURRENT_LIST_DIR}/../../../../llama.cpp)
option(LLAMA_ALLOW_PREBUILT "Use cached llama.cpp native libraries when they are available." ON)
set(LLAMA_PREBUILT_DIR "" CACHE PATH "Directory containing prebuilt llama.cpp JNI libraries.")
if(NOT LLAMA_PREBUILT_DIR)
    set(LLAMA_PREBUILT_DIR ${CMAKE_CURRENT_LIST_DIR}/../prebuilt)
endif()

set(LLAMA_PREBUILT_ABI_DIR ${LLAMA_PREBUILT_DIR}/${ANDROID_ABI})
set(LLAMA_DYNAMIC_LIBS
    ggml-base
    ggml
    ggml-cpu
    llama
    llama-common
)
set(LLAMA_STATIC_LIBS
    ggml-base
    ggml
    ggml-cpu
    llama
    llama-common
)

set(LLAMA_USE_PREBUILT_SHARED true)
foreach(lib ${LLAMA_DYNAMIC_LIBS})
    if(NOT EXISTS ${LLAMA_PREBUILT_ABI_DIR}/lib${lib}.so)
        set(LLAMA_USE_PREBUILT_SHARED false)
    endif()
endforeach()

set(LLAMA_USE_PREBUILT_STATIC true)
foreach(lib ${LLAMA_STATIC_LIBS})
    if(NOT EXISTS ${LLAMA_PREBUILT_ABI_DIR}/lib${lib}.a)
        set(LLAMA_USE_PREBUILT_STATIC false)
    endif()
endforeach()

if(LLAMA_ALLOW_PREBUILT AND (LLAMA_USE_PREBUILT_SHARED OR LLAMA_USE_PREBUILT_STATIC))
    message(STATUS "Using prebuilt llama.cpp libraries from ${LLAMA_PREBUILT_ABI_DIR}")
    foreach(lib ${LLAMA_DYNAMIC_LIBS})
        if(LLAMA_USE_PREBUILT_SHARED)
            add_library(${lib} SHARED IMPORTED GLOBAL)
            set_target_properties(${lib} PROPERTIES
                IMPORTED_LOCATION ${LLAMA_PREBUILT_ABI_DIR}/lib${lib}.so
            )
        else()
            add_library(${lib} STATIC IMPORTED GLOBAL)
            set_target_properties(${lib} PROPERTIES
                IMPORTED_LOCATION ${LLAMA_PREBUILT_ABI_DIR}/lib${lib}.a
            )
        endif()
    endforeach()
else()
    message(STATUS "Prebuilt llama.cpp libraries not found for ${ANDROID_ABI}; building from source.")
    add_subdirectory(${LLAMA_SRC} build-llama)
endif()

# 16 KB page size alignment for Android 15+
set(ALIGN_FLAGS -Wl,-z,max-page-size=16384 -Wl,-z,common-page-size=16384)
foreach(target llama-common-base llama-common llama ggml-base ggml-cpu ggml)
    if(TARGET ${target})
        get_target_property(is_imported ${target} IMPORTED)
        if(NOT is_imported)
            target_link_options(${target} PRIVATE ${ALIGN_FLAGS})
        endif()
    endif()
endforeach()

add_library(${CMAKE_PROJECT_NAME} SHARED ai_chat.cpp)
target_link_options(${CMAKE_PROJECT_NAME} PRIVATE ${ALIGN_FLAGS})

target_compile_definitions(${CMAKE_PROJECT_NAME} PRIVATE
        GGML_SYSTEM_ARCH=${GGML_SYSTEM_ARCH}
        GGML_CPU_KLEIDIAI=$<BOOL:${GGML_CPU_KLEIDIAI}>
        GGML_OPENMP=$<BOOL:${GGML_OPENMP}>
)

target_include_directories(${CMAKE_PROJECT_NAME} PRIVATE
        ${LLAMA_SRC}
        ${LLAMA_SRC}/common
        ${LLAMA_SRC}/vendor
        ${LLAMA_SRC}/include
        ${LLAMA_SRC}/ggml/include
        ${LLAMA_SRC}/ggml/src)

target_link_libraries(${CMAKE_PROJECT_NAME}
        llama
        llama-common
        ggml
        ggml-cpu
        ggml-base
        android
        log)
