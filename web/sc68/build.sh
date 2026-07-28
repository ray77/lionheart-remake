#!/bin/bash
# Baut sc68 als WASM-Modul für den Browser.
#
# Kein configure: das Projekt ist von 2003 und bringt seine Konfigurationsköpfe
# fertig mit, die Quellen sind einfaches C ohne Systemabhängigkeiten. Also alle
# nötigen Übersetzungseinheiten direkt an emcc.
set -e

# Emscripten: EMSDK auf die eigene Installation zeigen lassen.
: "${EMSDK:=$HOME/emsdk}"
source "$EMSDK/emsdk_env.sh" >/dev/null 2>&1 || {
  echo "emsdk nicht gefunden. EMSDK=<pfad> setzen." >&2; exit 1; }

# sc68 2.2.1 bei Bedarf holen (nicht mitversioniert, fremder Quellcode).
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

# Sonst von configure gesetzt. Der Pfad ist bedeutungslos: die 18 Lionheart-Stuecke
# tragen ihren 68000-Abspielcode selbst (kein SCRE-Feld), es wird nie etwas
# nachgeladen. HAVE_GETENV verhindert, dass die Quelle ihr eigenes getenv erklaert.
INC="$INC -DSC68_SHARED_DATA_PATH='\"/\"' -DHAVE_GETENV=1"

# unice68 packt komprimierte Stücke aus, file68 liest das sc68-Dateiformat,
# emu68 ist der 68000, io68 sind die Klangbausteine (YM2149, Paula, MW).
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

echo "fertig:"
ls -la "$OUT"/sc68.js "$OUT"/sc68.wasm
