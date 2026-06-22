cmake_minimum_required(VERSION 3.22.1)
project("ordoread-llama")

# Options llama.cpp pour Android
set(LLAMA_NATIVE OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_SERVER OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_COMMON OFF CACHE BOOL "" FORCE)
set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)
set(GGML_OPENMP OFF CACHE BOOL "" FORCE)
set(GGML_VULKAN OFF CACHE BOOL "" FORCE)

# Inclure llama.cpp
add_subdirectory(${CMAKE_SOURCE_DIR}/../../../../llama.cpp llama.cpp)

# Notre bibliothèque JNI
add_library(ordoread-llama SHARED llama_jni.cpp)

target_include_directories(ordoread-llama PRIVATE
    ${CMAKE_SOURCE_DIR}/../../../../llama.cpp/include
)

target_link_libraries(ordoread-llama
    llama
    log
    android
)
