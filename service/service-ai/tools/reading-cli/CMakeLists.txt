cmake_minimum_required(VERSION 3.22.1)
project(arcana_reading_cli C CXX)

# A desktop build of the app's own model code, for trying a model before it goes in the catalog.
# See README.md next to this file.
set(CMAKE_BUILD_TYPE Release)
set(MODULE "${CMAKE_CURRENT_LIST_DIR}/../..")
set(LLAMA_SRC "${MODULE}/llama.cpp")

set(LLAMA_CURL OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_COMMON OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_TOOLS OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_SERVER OFF CACHE BOOL "" FORCE)
add_subdirectory(${LLAMA_SRC} build-llama)

add_executable(reading-cli reading-cli.cpp "${MODULE}/src/main/cpp/arcana-session.cpp")
target_compile_features(reading-cli PRIVATE cxx_std_17)
target_include_directories(reading-cli PRIVATE "${MODULE}/src/main/cpp" "${LLAMA_SRC}/vendor")
target_link_libraries(reading-cli PRIVATE llama ggml)
