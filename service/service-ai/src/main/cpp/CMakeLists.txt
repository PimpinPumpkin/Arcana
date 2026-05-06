cmake_minimum_required(VERSION 3.22.1)

project("arcana-llama" VERSION 1.0.0 LANGUAGES C CXX)

set(CMAKE_C_STANDARD 11)
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_C_STANDARD_REQUIRED ON)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# Path from this CMakeLists to the vendored llama.cpp submodule:
#   service/service-ai/src/main/cpp/CMakeLists.txt
#   service/service-ai/llama.cpp/
set(LLAMA_SRC "${CMAKE_CURRENT_LIST_DIR}/../../../llama.cpp")

if(NOT EXISTS "${LLAMA_SRC}/CMakeLists.txt")
    message(FATAL_ERROR
        "llama.cpp submodule not found at ${LLAMA_SRC}.\n"
        "Run: git submodule update --init --recursive")
endif()

# Build llama.cpp as a subdirectory. The arguments controlling what gets built
# (LLAMA_BUILD_TESTS=OFF, GGML_BACKEND_DL=ON, etc.) live in service-ai's
# build.gradle.kts so they show up next to the rest of the Android config.
add_subdirectory(${LLAMA_SRC} build-llama)

add_library(arcana-llama SHARED
    arcana-llama.cpp)

target_include_directories(arcana-llama PRIVATE
    ${LLAMA_SRC}
    ${LLAMA_SRC}/include
    ${LLAMA_SRC}/common
    ${LLAMA_SRC}/ggml/include)

target_link_libraries(arcana-llama
    llama
    llama-common
    android
    log)
