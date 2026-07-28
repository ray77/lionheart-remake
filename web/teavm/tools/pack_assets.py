#!/usr/bin/env python3
"""Baut das Datenpaket fuer den Browser-Lauf.

Der Browser kann kein Verzeichnis lesen, darum kommt alles in eine einzige
Datei (assets.pak) plus ein Verzeichnis (assets.lst):

    D <offset> <laenge> <pfad>   Rohbytes, liegen im Paket
    I <pfad>                     zusaetzlich als Bild zu laden

Bilder stehen doppelt drin: die Zeichenschicht braucht das vom Browser
dekodierte Bild, der Motor liest bei manchen Dateien aber auch die Rohbytes
(Groesse aus dem PNG-Kopf), darum sind sie ebenfalls im Paket.

Nebenbei werden die Belegungsdateien angepasst: sie zeigen im Original auf die
Desktop-Eingabeklassen, die es im Browser nicht gibt.
"""

import os
import re
import sys

# Desktop-Eingabeklassen -> Browser-Gegenstuecke.
DEVICE_SWAP = {
    "com.b3dgs.lionengine.awt.Keyboard": "com.b3dgs.lionengine.web.KeyboardWeb",
    "com.b3dgs.lionengine.awt.Mouse": "com.b3dgs.lionengine.web.MouseWeb",
}
# Geraete, die es im Browser nicht gibt: ganzer Block faellt weg.
DEVICE_DROP = ("com.b3dgs.lionheart.Gamepad",)

DEVICE_BLOCK = re.compile(
    r"[ \t]*<lionengine:device\b[^>]*class=\"(?P<cls>[^\"]+)\"[^>]*"
    r"(?:/>|>.*?</lionengine:device>)[ \t]*\r?\n",
    re.DOTALL,
)


def patch_input(text):
    """Belegung auf die Browser-Eingabeklassen umschreiben."""

    def keep(match):
        if match.group("cls") in DEVICE_DROP:
            return ""
        return match.group(0)

    text = DEVICE_BLOCK.sub(keep, text)
    for old, new in DEVICE_SWAP.items():
        text = text.replace(old, new)
    return text


def main():
    root = sys.argv[1] if len(sys.argv) > 1 else "target/js/assets"
    out = sys.argv[2] if len(sys.argv) > 2 else "target/js"

    paths = []
    for base, _, names in os.walk(root):
        for name in names:
            full = os.path.join(base, name)
            rel = os.path.relpath(full, root).replace(os.sep, "/")
            if name.startswith("."):
                continue
            paths.append((rel, full))
    paths.sort()

    lines = []
    offset = 0
    patched = 0
    with open(os.path.join(out, "assets.pak"), "wb") as pak:
        for rel, full in paths:
            with open(full, "rb") as src:
                data = src.read()
            if re.match(r"^input.*\.xml$", os.path.basename(rel)):
                fixed = patch_input(data.decode("utf-8")).encode("utf-8")
                if fixed != data:
                    data = fixed
                    patched += 1
                    with open(full, "wb") as dst:
                        dst.write(data)
            pak.write(data)
            lines.append("D %d %d %s" % (offset, len(data), rel))
            offset += len(data)

    for rel, _ in paths:
        if rel.lower().endswith(".png"):
            lines.append("I " + rel)

    with open(os.path.join(out, "assets.lst"), "w") as lst:
        lst.write("\n".join(lines) + "\n")

    print("%d Dateien, %d Bilder, %.1f MB, %d Belegungen angepasst"
          % (len(paths), sum(1 for r, _ in paths if r.lower().endswith(".png")),
             offset / 1048576.0, patched))


if __name__ == "__main__":
    main()
