cmake_minimum_required(VERSION 3.22.1)
project(agba C CXX)

include(CheckSymbolExists)
include(CheckCSourceCompiles)

set(MGBA_ROOT ${CMAKE_CURRENT_SOURCE_DIR}/../../../../mgba)

set(MGBA_SOURCES
    src/arm/arm.c
    src/arm/decoder-arm.c
    src/arm/decoder-thumb.c
    src/arm/isa-arm.c
    src/arm/isa-thumb.c
    src/core/bitmap-cache.c
    src/core/cache-set.c
    src/core/cheats.c
    src/core/config.c
    src/core/core.c
    src/core/interface.c
    src/core/log.c
    src/core/map-cache.c
    src/core/rewind.c
    src/core/serialize.c
    src/core/sync.c
    src/core/tile-cache.c
    src/core/timing.c
    src/core/version.c
    src/gb/audio.c
    src/gb/cheats.c
    src/gb/core.c
    src/gb/gb.c
    src/gb/io.c
    src/gb/mbc.c
    src/gb/mbc/huc-3.c
    src/gb/mbc/licensed.c
    src/gb/mbc/mbc.c
    src/gb/mbc/pocket-cam.c
    src/gb/mbc/tama5.c
    src/gb/mbc/unlicensed.c
    src/gb/memory.c
    src/gb/overrides.c
    src/gb/renderers/cache-set.c
    src/gb/renderers/software.c
    src/gb/serialize.c
    src/gb/sio.c
    src/gb/timer.c
    src/gb/video.c
    src/gba/audio.c
    src/gba/bios.c
    src/gba/cart/ereader.c
    src/gba/cart/gpio.c
    src/gba/cart/matrix.c
    src/gba/cart/unlicensed.c
    src/gba/cart/vfame.c
    src/gba/cheats.c
    src/gba/cheats/codebreaker.c
    src/gba/cheats/gameshark.c
    src/gba/cheats/parv3.c
    src/gba/core.c
    src/gba/dma.c
    src/gba/gba.c
    src/gba/hle-bios.c
    src/gba/io.c
    src/gba/memory.c
    src/gba/overrides.c
    src/gba/renderers/cache-set.c
    src/gba/renderers/common.c
    src/gba/renderers/software-bg.c
    src/gba/renderers/software-mode0.c
    src/gba/renderers/software-obj.c
    src/gba/renderers/video-software.c
    src/gba/savedata.c
    src/gba/serialize.c
    src/gba/sio.c
    src/gba/sio/gbp.c
    src/gba/timer.c
    src/gba/video.c
    src/platform/posix/memory.c
    src/sm83/isa-sm83.c
    src/sm83/sm83.c
    src/third-party/inih/ini.c
    src/util/audio-buffer.c
    src/util/audio-resampler.c
    src/util/circle-buffer.c
    src/util/configuration.c
    src/util/crc32.c
    src/util/formatting.c
    src/util/gbk-table.c
    src/util/geometry.c
    src/util/hash.c
    src/util/image.c
    src/util/interpolator.c
    src/util/md5.c
    src/util/patch-fast.c
    src/util/patch-ips.c
    src/util/patch-ups.c
    src/util/patch.c
    src/util/sha1.c
    src/util/string.c
    src/util/table.c
    src/util/vector.c
    src/util/vfs.c
    src/util/vfs/vfs-fd.c
    src/util/vfs/vfs-mem.c
)
list(TRANSFORM MGBA_SOURCES PREPEND ${MGBA_ROOT}/)

set(MGBA_DEFINES _GNU_SOURCE)

foreach(symbol strdup strndup strlcpy vasprintf localtime_r newlocale freelocale uselocale)
    string(TOUPPER ${symbol} upper)
    check_symbol_exists(${symbol} "string.h;stdio.h;stdlib.h;time.h;locale.h" HAVE_${upper})
    if(HAVE_${upper})
        list(APPEND MGBA_DEFINES HAVE_${upper})
    endif()
endforeach()
if(HAVE_NEWLOCALE AND HAVE_FREELOCALE)
    list(APPEND MGBA_DEFINES HAVE_LOCALE HAVE_STRTOF_L)
endif()

add_library(mgba STATIC ${MGBA_SOURCES})
target_include_directories(mgba PUBLIC ${MGBA_ROOT}/include PRIVATE ${MGBA_ROOT}/src)
target_compile_definitions(mgba PUBLIC ${MGBA_DEFINES})
target_compile_options(mgba PRIVATE -fwrapv -w -O3 -fPIC)
set_target_properties(mgba PROPERTIES C_STANDARD 11 C_EXTENSIONS ON)

add_library(agba SHARED
    AudioOutput.cpp
    Bridge.cpp
    Emulator.cpp
)
set_target_properties(agba PROPERTIES CXX_STANDARD 17 CXX_STANDARD_REQUIRED ON)
target_compile_options(agba PRIVATE -O3 -Wall)
target_link_libraries(agba mgba aaudio log m)
target_link_options(agba PRIVATE -Wl,--no-undefined -Wl,-z,max-page-size=16384)
