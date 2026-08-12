# Project-level configuration.
cmake_minimum_required(VERSION 3.14)
project(retailzw_pos LANGUAGES CXX)

# The name of the executable created for the application. Change this to change
# the on-disk name of your application.
set(BINARY_NAME "RetailZWPOS")

# Explicitly opt in to modern CMake behaviors to avoid warnings with recent
# versions of CMake.
cmake_policy(VERSION 3.14...3.25)

# Define build configuration option.
get_property(IS_MULTICONFIG GLOBAL PROPERTY GENERATOR_IS_MULTI_CONFIG)
if(IS_MULTICONFIG)
  set(CMAKE_CONFIGURATION_TYPES "Debug;Profile;Release"
    CACHE STRING "" FORCE)
else()
  if(NOT CMAKE_BUILD_TYPE AND NOT CMAKE_CONFIGURATION_TYPES)
    set(CMAKE_BUILD_TYPE "Debug" CACHE
      STRING "Flutter build mode" FORCE)
    set_property(CACHE CMAKE_BUILD_TYPE PROPERTY STRINGS
      "Debug" "Profile" "Release")
  endif()
endif()
# Define settings for the Profile build mode.
set(CMAKE_EXE_LINKER_FLAGS_PROFILE "${CMAKE_EXE_LINKER_FLAGS_RELEASE}")
set(CMAKE_SHARED_LINKER_FLAGS_PROFILE "${CMAKE_SHARED_LINKER_FLAGS_RELEASE}")
set(CMAKE_C_FLAGS_PROFILE "${CMAKE_C_FLAGS_RELEASE}")
set(CMAKE_CXX_FLAGS_PROFILE "${CMAKE_CXX_FLAGS_RELEASE}")

# Use Unicode for all projects. permission_handler_windows 0.2.x still uses
# Microsoft's deprecated experimental coroutine shim, so keep that plugin
# buildable with current Visual Studio releases until the package is upgraded.
add_definitions(
  -DUNICODE
  -D_UNICODE
  -D_SILENCE_EXPERIMENTAL_COROUTINE_DEPRECATION_WARNINGS)

# Compilation settings that should be applied to most targets.
#
# Be cautious about adding new options here, as plugins use this function by
# default. In most cases, you should add new options to specific targets instead
# of modifying this function.
function(APPLY_STANDARD_SETTINGS TARGET)
  target_compile_features(${TARGET} PUBLIC cxx_std_17)
  target_compile_options(${TARGET} PRIVATE /W4 /WX /wd"4100")
  target_compile_options(${TARGET} PRIVATE /EHsc)
  target_compile_definitions(${TARGET} PRIVATE "_HAS_EXCEPTIONS=0")
  target_compile_definitions(${TARGET} PRIVATE "$<$<CONFIG:Debug>:_DEBUG>")
endfunction()

# Flutter library and tool build rules.
set(FLUTTER_MANAGED_DIR "${CMAKE_CURRENT_SOURCE_DIR}/flutter")
add_subdirectory(${FLUTTER_MANAGED_DIR})

# Application build; see runner/CMakeLists.txt.
add_subdirectory("runner")


# Generated plugin build rules, which manage building the plugins and adding
# them to the application.
include(flutter/generated_plugins.cmake)

# Visual Studio 2026 no longer adds ATL/MFC headers to every plugin target.
# flutter_secure_storage_windows uses CString, so expose the installed ATL
# include directory explicitly while keeping credentials in Windows storage.
file(GLOB RETAILZW_ATL_INCLUDE_DIRS
  "${CMAKE_CURRENT_SOURCE_DIR}/../build/toolchain/atl/headers/Contents/VC/Tools/MSVC/*/atlmfc/include"
  "C:/Program Files/Microsoft Visual Studio/*/*/VC/Tools/MSVC/*/atlmfc/include"
  "C:/Program Files (x86)/Microsoft Visual Studio/*/*/VC/Tools/MSVC/*/atlmfc/include")
file(GLOB RETAILZW_ATL_LIBRARY_DIRS
  "${CMAKE_CURRENT_SOURCE_DIR}/../build/toolchain/atl/x64/Contents/VC/Tools/MSVC/*/atlmfc/lib/x64"
  "C:/Program Files/Microsoft Visual Studio/*/*/VC/Tools/MSVC/*/atlmfc/lib/x64"
  "C:/Program Files (x86)/Microsoft Visual Studio/*/*/VC/Tools/MSVC/*/atlmfc/lib/x64")
if(TARGET flutter_secure_storage_windows_plugin)
  if(RETAILZW_ATL_INCLUDE_DIRS)
    list(GET RETAILZW_ATL_INCLUDE_DIRS 0 RETAILZW_ATL_INCLUDE_DIR)
    target_include_directories(flutter_secure_storage_windows_plugin
      PRIVATE "${RETAILZW_ATL_INCLUDE_DIR}")
    if(RETAILZW_ATL_LIBRARY_DIRS)
      list(GET RETAILZW_ATL_LIBRARY_DIRS 0 RETAILZW_ATL_LIBRARY_DIR)
      target_link_directories(flutter_secure_storage_windows_plugin
        PRIVATE "${RETAILZW_ATL_LIBRARY_DIR}")
    endif()
  else()
    message(FATAL_ERROR
      "ATL headers are required. Run packaging/windows/build_windows.ps1 "
      "to restore the verified build-only ATL package.")
  endif()
endif()


# === Installation ===
# Support files are copied into place next to the executable, so that it can
# run in place. This is done instead of making a separate bundle (as on Linux)
# so that building and running from within Visual Studio will work.
set(BUILD_BUNDLE_DIR "$<TARGET_FILE_DIR:${BINARY_NAME}>")
# Make the "install" step default, as it's required to run.
set(CMAKE_VS_INCLUDE_INSTALL_TO_DEFAULT_BUILD 1)
if(CMAKE_INSTALL_PREFIX_INITIALIZED_TO_DEFAULT)
  set(CMAKE_INSTALL_PREFIX "${BUILD_BUNDLE_DIR}" CACHE PATH "..." FORCE)
endif()

set(INSTALL_BUNDLE_DATA_DIR "${CMAKE_INSTALL_PREFIX}/data")
set(INSTALL_BUNDLE_LIB_DIR "${CMAKE_INSTALL_PREFIX}")

install(TARGETS ${BINARY_NAME} RUNTIME DESTINATION "${CMAKE_INSTALL_PREFIX}"
  COMPONENT Runtime)

install(FILES "${FLUTTER_ICU_DATA_FILE}" DESTINATION "${INSTALL_BUNDLE_DATA_DIR}"
  COMPONENT Runtime)

install(FILES "${FLUTTER_LIBRARY}" DESTINATION "${INSTALL_BUNDLE_LIB_DIR}"
  COMPONENT Runtime)

if(PLUGIN_BUNDLED_LIBRARIES)
  install(FILES "${PLUGIN_BUNDLED_LIBRARIES}"
    DESTINATION "${INSTALL_BUNDLE_LIB_DIR}"
    COMPONENT Runtime)
endif()

# Copy the native assets provided by the build.dart from all packages.
set(NATIVE_ASSETS_DIR "${PROJECT_BUILD_DIR}native_assets/windows/")
install(DIRECTORY "${NATIVE_ASSETS_DIR}"
   DESTINATION "${INSTALL_BUNDLE_LIB_DIR}"
   COMPONENT Runtime)

# Fully re-copy the assets directory on each build to avoid having stale files
# from a previous install.
set(FLUTTER_ASSET_DIR_NAME "flutter_assets")
install(CODE "
  file(REMOVE_RECURSE \"${INSTALL_BUNDLE_DATA_DIR}/${FLUTTER_ASSET_DIR_NAME}\")
  " COMPONENT Runtime)
install(DIRECTORY "${PROJECT_BUILD_DIR}/${FLUTTER_ASSET_DIR_NAME}"
  DESTINATION "${INSTALL_BUNDLE_DATA_DIR}" COMPONENT Runtime)

# Install the AOT library on non-Debug builds only.
install(FILES "${AOT_LIBRARY}" DESTINATION "${INSTALL_BUNDLE_DATA_DIR}"
  CONFIGURATIONS Profile;Release
  COMPONENT Runtime)
