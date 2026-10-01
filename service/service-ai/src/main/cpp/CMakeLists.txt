cmake_minimum_required(VERSION 3.22.1)
project(arcana_llama C CXX)

# llama.cpp runs the on-device model. It is built from source, from the submodule next to this
# module, so there is no prebuilt binary in the app.
set(LLAMA_SRC "${CMAKE_CURRENT_LIST_DIR}/../../../llama.cpp")
if(NOT EXISTS "${LLAMA_SRC}/CMakeLists.txt")
    message(FATAL_ERROR "llama.cpp is missing at ${LLAMA_SRC}. Run: git submodule update --init --recursive")
endif()

# The model is unusably slow unoptimized, so the library is built for speed in every variant.
set(CMAKE_BUILD_TYPE Release)
set(CMAKE_C_FLAGS_DEBUG "${CMAKE_C_FLAGS_RELEASE}")
set(CMAKE_CXX_FLAGS_DEBUG "${CMAKE_CXX_FLAGS_RELEASE}")

set(BUILD_SHARED_LIBS ON CACHE BOOL "" FORCE)
# One CPU library per instruction set, picked on the phone at run time. A 2021 budget chip has no
# dot-product instructions and a 2020 mid-range one does; a single build cannot serve both well.
set(GGML_NATIVE OFF CACHE BOOL "" FORCE)
set(GGML_BACKEND_DL ON CACHE BOOL "" FORCE)
set(GGML_CPU_ALL_VARIANTS ON CACHE BOOL "" FORCE)
set(GGML_LLAMAFILE OFF CACHE BOOL "" FORCE)
# Measured on a Pixel 4a 5G: no faster with OpenMP, and it is one more library to ship.
set(GGML_OPENMP OFF CACHE BOOL "" FORCE)
set(LLAMA_CURL OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_COMMON OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_TOOLS OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_SERVER OFF CACHE BOOL "" FORCE)

add_subdirectory(${LLAMA_SRC} build-llama)

add_library(arcana-llama SHARED arcana-llama.cpp arcana-session.cpp)
target_compile_features(arcana-llama PRIVATE cxx_std_17)
target_link_libraries(arcana-llama PRIVATE llama ggml log)
