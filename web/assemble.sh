#!/usr/bin/env bash
# Assemble the browser build into web/teavm/target/js.
#
# Everything that ends up there comes from a source under version control, so
# nothing has to be kept in step by hand:
#
#   classes.js        <- maven, from web/teavm
#   assets/, *.pak    <- the game assets, plus the desktop input mappings and
#                        the browser settings overlay, packed by pack_assets.py
#   index.html, *.js  <- web/runtime
#   sc68.js, .wasm    <- the replayer build, see web/sc68/build.sh
#
# Usage: web/assemble.sh [--quick]
#   --quick  skip the asset copy and repack, for a code only change
set -euo pipefail

WEB="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(dirname "$WEB")"
OUT="$WEB/teavm/target/js"
SC68_OUT="${SC68_OUT:-$HOME/Developer/sc68-web/out}"
QUICK="${1:-}"

if [ -z "${JAVA_HOME:-}" ] || ! "$JAVA_HOME/bin/java" -version 2>&1 | grep -q '"17'; then
    echo "JAVA_HOME must point at a JDK 17 - TeaVM rejects newer class files" >&2
    exit 1
fi

echo "== engine and game"
mvn -q -f "$HOME/Developer/lionengine-src/pom.xml" -DskipTests -Dcheckstyle.skip=true \
    -pl java/lionengine-core,java/lionengine-game,java/lionengine-core-web install
mvn -q -f "$ROOT/pom.xml" -DskipTests -Dcheckstyle.skip=true -pl java/lionheart-game install

echo "== browser build"
mvn -q -f "$WEB/teavm/pom.xml" -DskipTests -Dcheckstyle.skip=true package
mkdir -p "$OUT"

if [ "$QUICK" != "--quick" ]; then
    echo "== assets"
    rsync -a --delete --exclude '.DS_Store' \
        "$ROOT/assets/src/main/resources/com/b3dgs/lionheart/" "$OUT/assets/"
    # The input mappings live with the desktop module; pack_assets.py rewrites
    # them onto the browser device classes as it packs.
    cp "$ROOT/java/lionheart-pc/src/main/resources/com/b3dgs/lionheart/"input*.xml "$OUT/assets/"
    cp -R "$WEB/overlay/." "$OUT/assets/"
    python3 "$WEB/teavm/tools/pack_assets.py" "$OUT/assets" "$OUT"
fi

echo "== page and replayer"
cp "$WEB/runtime/"* "$OUT/"
if [ -f "$SC68_OUT/sc68.wasm" ]; then
    cp "$SC68_OUT/sc68.js" "$SC68_OUT/sc68.wasm" "$OUT/"
elif [ ! -f "$OUT/sc68.wasm" ]; then
    echo "no sc68 replayer - build it with web/sc68/build.sh, music will stay silent" >&2
fi

echo "== done: $OUT"
ls -la "$OUT" | awk 'NR>3 {printf "   %9s  %s\n", $5, $9}'
