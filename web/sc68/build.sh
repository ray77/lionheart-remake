#!/bin/bash
# Builds sc68 as a WebAssembly module for the browser.
#
# No configure: the project is from 2003, ships its config headers ready made,
# and the sources are plain C without system dependencies. So every translation
# unit needed goes straight to emcc.
set -e

# Emscripten: point EMSDK at your own installation.
: "${EMSDK:=$HOME/emsdk}"
source "$EMSDK/emsdk_env.sh" >/dev/null 2>&1 || {
  echo "emsdk not found. Set EMSDK=<path>." >&2; exit 1; }

# Fetch sc68 2.2.1 on demand (third party source, not vendored).
ROOT_EARLY="$(cd "$(dirname "$0")" && pwd)"
if [ ! -d "$ROOT_EARLY/sc68-2.2.1" ]; then
  curl -sL -o "$ROOT_EARLY/sc68-2.2.1.tar.gz" \
    "https://downloads.sourceforge.net/project/sc68/sc68/2.2.1/sc68-2.2.1.tar.gz"
  tar xzf "$ROOT_EARLY/sc68-2.2.1.tar.gz" -C "$ROOT_EARLY"
fi

ROOT="$(cd "$(dirname "$0")" && pwd)"
SRC="$ROOT/sc68-2.2.1"
OUT="$ROOT/out"
mkdir -p "$OUT"

INC="-I$SRC -I$SRC/api68 -I$SRC/emu68 -I$SRC/io68 -I$SRC/file68 -I$SRC/unice68"

# Normally set by configure. The path is meaningless here: the 18 Lionheart tracks
# carry their own 68000 replay code (no SCRE field), so nothing is ever loaded from
# it. HAVE_GETENV keeps the source from declaring its own getenv.
INC="$INC -DSC68_SHARED_DATA_PATH='\"/\"' -DHAVE_GETENV=1"

# unice68 unpacks compressed tracks, file68 reads the sc68 file format,
# emu68 is the 68000, io68 the sound chips (YM2149, Paula, MW).
FILES="$SRC/api68/api68.c $SRC/api68/conf68.c $SRC/api68/mixer68.c"
FILES="$FILES $(ls $SRC/emu68/*.c $SRC/io68/*.c $SRC/file68/*.c $SRC/unice68/*.c)"

emcc -O3 $INC \
  "$ROOT/sc68_web.c" $FILES \
  -o "$OUT/sc68.js" \
  -s MODULARIZE=1 \
  -s EXPORT_NAME=createSc68 \
  -s EXPORTED_FUNCTIONS='["_sc68w_open","_sc68w_load","_sc68w_play","_sc68w_render","_sc68w_stop","_sc68w_close","_sc68w_error","_malloc","_free"]' \
  -s EXPORTED_RUNTIME_METHODS='["cwrap","HEAPU8","HEAP16","UTF8ToString"]' \
  -s ALLOW_MEMORY_GROWTH=1 \
  -s ENVIRONMENT=web \
  -s FILESYSTEM=0 \
  -Wno-deprecated-non-prototype \
  -Wno-implicit-function-declaration \
  -Wno-incompatible-pointer-types \
  -Wno-int-conversion

echo "done:"
ls -la "$OUT"/sc68.js "$OUT"/sc68.wasm
