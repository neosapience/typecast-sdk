# Typecast C/C++ SDK
# CMake build configuration
#
# Build options:
#   - TYPECAST_BUILD_SHARED: Build as shared library (default: ON)
#   - TYPECAST_BUILD_STATIC: Build as static library (default: OFF)
#   - TYPECAST_BUILD_EXAMPLES: Build example programs (default: ON)
#   - TYPECAST_BUILD_TESTS: Build test programs (default: ON)
#
# Usage:
#   mkdir build && cd build
#   cmake .. -DCMAKE_BUILD_TYPE=Release
#   cmake --build .

cmake_minimum_required(VERSION 3.14)
project(typecast VERSION 1.2.0 LANGUAGES C)

# Options
option(TYPECAST_BUILD_SHARED "Build shared library" ON)
option(TYPECAST_BUILD_STATIC "Build static library" OFF)
option(TYPECAST_BUILD_EXAMPLES "Build examples" ON)
option(TYPECAST_BUILD_TESTS "Build tests" ON)
option(TYPECAST_COVERAGE "Build with coverage instrumentation" OFF)

# C standard
set(CMAKE_C_STANDARD 11)
set(CMAKE_C_STANDARD_REQUIRED ON)

# Coverage instrumentation (GCC/Clang only)
if(TYPECAST_COVERAGE)
    if(CMAKE_C_COMPILER_ID MATCHES "GNU|Clang|AppleClang")
        message(STATUS "Coverage instrumentation enabled")
        add_compile_options(-O0 -g --coverage -fprofile-arcs -ftest-coverage)
        add_link_options(--coverage)
        # Force static build only when coverage is enabled to ensure
        # gcov data files are co-located with the test binary build dir.
        set(TYPECAST_BUILD_SHARED OFF CACHE BOOL "Build shared library" FORCE)
        set(TYPECAST_BUILD_STATIC ON CACHE BOOL "Build static library" FORCE)
        set(TYPECAST_BUILD_EXAMPLES OFF CACHE BOOL "Build examples" FORCE)
    else()
        message(FATAL_ERROR "TYPECAST_COVERAGE requires GCC or Clang")
    endif()
endif()

# Find CURL
find_package(CURL REQUIRED)

# Source files
set(TYPECAST_SOURCES
    src/typecast.c
    src/cJSON.c
)

set(TYPECAST_HEADERS
    include/typecast.h
    src/cJSON.h
)

# Shared library
if(TYPECAST_BUILD_SHARED)
    add_library(typecast SHARED ${TYPECAST_SOURCES})
    target_include_directories(typecast
        PUBLIC
            $<BUILD_INTERFACE:${CMAKE_CURRENT_SOURCE_DIR}/include>
            $<INSTALL_INTERFACE:include>
        PRIVATE
            ${CMAKE_CURRENT_SOURCE_DIR}/src
    )
    target_link_libraries(typecast PRIVATE CURL::libcurl)
    target_compile_definitions(typecast PRIVATE TYPECAST_BUILDING_DLL)
    
    # Set library version
    set_target_properties(typecast PROPERTIES
        VERSION ${PROJECT_VERSION}
        SOVERSION ${PROJECT_VERSION_MAJOR}
        OUTPUT_NAME typecast
    )
    
    # Windows: generate import library
    if(WIN32)
        set_target_properties(typecast PROPERTIES
            WINDOWS_EXPORT_ALL_SYMBOLS ON
        )
    endif()
endif()

# Static library
if(TYPECAST_BUILD_STATIC)
    add_library(typecast_static STATIC ${TYPECAST_SOURCES})
    target_include_directories(typecast_static
        PUBLIC
            $<BUILD_INTERFACE:${CMAKE_CURRENT_SOURCE_DIR}/include>
            $<INSTALL_INTERFACE:include>
        PRIVATE
            ${CMAKE_CURRENT_SOURCE_DIR}/src
    )
    target_link_libraries(typecast_static PRIVATE CURL::libcurl)
    target_compile_definitions(typecast_static PUBLIC TYPECAST_STATIC)
    
    set_target_properties(typecast_static PROPERTIES
        OUTPUT_NAME typecast_static
    )
endif()

# Examples
if(TYPECAST_BUILD_EXAMPLES)
    add_executable(example_simple examples/simple.c)
    target_include_directories(example_simple PRIVATE include)
    
    if(TYPECAST_BUILD_SHARED)
        target_link_libraries(example_simple PRIVATE typecast)
    elseif(TYPECAST_BUILD_STATIC)
        target_link_libraries(example_simple PRIVATE typecast_static CURL::libcurl)
    endif()
    
    # Copy DLL for Windows
    if(WIN32 AND TYPECAST_BUILD_SHARED)
        add_custom_command(TARGET example_simple POST_BUILD
            COMMAND ${CMAKE_COMMAND} -E copy_if_different
            $<TARGET_FILE:typecast>
            $<TARGET_FILE_DIR:example_simple>
        )
    endif()

    add_executable(example_with_timestamps examples/with_timestamps.c)
    target_include_directories(example_with_timestamps PRIVATE include)
    if(TYPECAST_BUILD_SHARED)
        target_link_libraries(example_with_timestamps PRIVATE typecast)
    elseif(TYPECAST_BUILD_STATIC)
        target_link_libraries(example_with_timestamps PRIVATE typecast_static CURL::libcurl)
    endif()
endif()

# Tests
if(TYPECAST_BUILD_TESTS)
    enable_testing()
    
    add_executable(test_typecast tests/test_main.c)
    target_include_directories(test_typecast PRIVATE include)
    
    if(TYPECAST_BUILD_SHARED)
        target_link_libraries(test_typecast PRIVATE typecast)
    elseif(TYPECAST_BUILD_STATIC)
        target_link_libraries(test_typecast PRIVATE typecast_static CURL::libcurl)
    endif()
    
    add_test(NAME typecast_tests COMMAND test_typecast)

    # Mock-server-driven coverage test (no API key required)
    add_executable(test_mock tests/test_mock.c)
    target_include_directories(test_mock PRIVATE include)

    if(TYPECAST_BUILD_SHARED)
        target_link_libraries(test_mock PRIVATE typecast)
    elseif(TYPECAST_BUILD_STATIC)
        target_link_libraries(test_mock PRIVATE typecast_static CURL::libcurl)
    endif()
    if(NOT WIN32)
        find_package(Threads REQUIRED)
        target_link_libraries(test_mock PRIVATE Threads::Threads)
    endif()

    add_test(NAME typecast_mock_tests COMMAND test_mock)

    # Timestamp TTS tests (fixture byte-equality, no API key required)
    add_executable(test_timestamps tests/test_timestamps.c)
    target_include_directories(test_timestamps PRIVATE include src)

    if(TYPECAST_BUILD_SHARED)
        target_link_libraries(test_timestamps PRIVATE typecast)
    elseif(TYPECAST_BUILD_STATIC)
        target_link_libraries(test_timestamps PRIVATE typecast_static CURL::libcurl)
    endif()
    if(NOT WIN32)
        target_link_libraries(test_timestamps PRIVATE Threads::Threads)
    endif()

    add_test(NAME typecast_timestamp_tests COMMAND test_timestamps
             WORKING_DIRECTORY ${CMAKE_CURRENT_SOURCE_DIR})

    # Integration test (requires API key)
    add_executable(test_integration tests/test_integration.c)
    target_include_directories(test_integration PRIVATE include)

    if(TYPECAST_BUILD_SHARED)
        target_link_libraries(test_integration PRIVATE typecast)
    elseif(TYPECAST_BUILD_STATIC)
        target_link_libraries(test_integration PRIVATE typecast_static CURL::libcurl)
    endif()
    
    # Copy DLL for Windows tests
    if(WIN32 AND TYPECAST_BUILD_SHARED)
        add_custom_command(TARGET test_typecast POST_BUILD
            COMMAND ${CMAKE_COMMAND} -E copy_if_different
            $<TARGET_FILE:typecast>
            $<TARGET_FILE_DIR:test_typecast>
        )
        add_custom_command(TARGET test_integration POST_BUILD
            COMMAND ${CMAKE_COMMAND} -E copy_if_different
            $<TARGET_FILE:typecast>
            $<TARGET_FILE_DIR:test_integration>
        )
    endif()
endif()

# Install
include(GNUInstallDirs)

if(TYPECAST_BUILD_SHARED)
    install(TARGETS typecast
        EXPORT typecastTargets
        LIBRARY DESTINATION ${CMAKE_INSTALL_LIBDIR}
        ARCHIVE DESTINATION ${CMAKE_INSTALL_LIBDIR}
        RUNTIME DESTINATION ${CMAKE_INSTALL_BINDIR}
    )
endif()

if(TYPECAST_BUILD_STATIC)
    install(TARGETS typecast_static
        EXPORT typecastTargets
        ARCHIVE DESTINATION ${CMAKE_INSTALL_LIBDIR}
    )
endif()

install(FILES include/typecast.h
    DESTINATION ${CMAKE_INSTALL_INCLUDEDIR}
)

# Export targets
install(EXPORT typecastTargets
    FILE typecastTargets.cmake
    NAMESPACE typecast::
    DESTINATION ${CMAKE_INSTALL_LIBDIR}/cmake/typecast
)

# Create config file
include(CMakePackageConfigHelpers)
configure_package_config_file(
    ${CMAKE_CURRENT_SOURCE_DIR}/cmake/typecastConfig.cmake.in
    ${CMAKE_CURRENT_BINARY_DIR}/typecastConfig.cmake
    INSTALL_DESTINATION ${CMAKE_INSTALL_LIBDIR}/cmake/typecast
)

write_basic_package_version_file(
    ${CMAKE_CURRENT_BINARY_DIR}/typecastConfigVersion.cmake
    VERSION ${PROJECT_VERSION}
    COMPATIBILITY SameMajorVersion
)

install(FILES
    ${CMAKE_CURRENT_BINARY_DIR}/typecastConfig.cmake
    ${CMAKE_CURRENT_BINARY_DIR}/typecastConfigVersion.cmake
    DESTINATION ${CMAKE_INSTALL_LIBDIR}/cmake/typecast
)

# Package information
message(STATUS "Typecast SDK ${PROJECT_VERSION}")
message(STATUS "  Build shared: ${TYPECAST_BUILD_SHARED}")
message(STATUS "  Build static: ${TYPECAST_BUILD_STATIC}")
message(STATUS "  Build examples: ${TYPECAST_BUILD_EXAMPLES}")
message(STATUS "  Build tests: ${TYPECAST_BUILD_TESTS}")
