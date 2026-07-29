#!/usr/bin/env python3
"""Builds the asset pack for the browser build.

A browser cannot list a directory, so everything goes into one file
(assets.pak) plus an index (assets.lst):

    D <offset> <length> <path>   raw bytes, inside the pack
    I <path>                     additionally to be loaded as an image

Images appear twice: the drawing layer needs the image decoded by the browser,
while the engine reads the raw bytes of some files as well (size from the PNG
header), so they are in the pack too.

On the way the input mappings are rewritten: they name the desktop input
classes, which do not exist in a browser.
"""
import os
import re
import sys

# Desktop input classes -> browser counterparts.
DEVICE_SWAP = {
    "com.b3dgs.lionengine.awt.Keyboard": "com.b3dgs.lionengine.web.KeyboardWeb",
    "com.b3dgs.lionengine.awt.Mouse": "com.b3dgs.lionengine.web.MouseWeb",
}
# Devices a browser does not have: the whole block is dropped.
DEVICE_DROP = ("com.b3dgs.lionheart.Gamepad",)

DEVICE_BLOCK = re.compile(
    r"[ \t]*<lionengine:device\b[^>]*class=\"(?P<cls>[^\"]+)\"[^>]*"
    r"(?:/>|>.*?</lionengine:device>)[ \t]*\r?\n",
    re.DOTALL,
)


def patch_input(text):
    """Rewrite the mapping onto the browser input classes."""

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

    print("%d files, %d images, %.1f MB, %d mappings rewritten"
          % (len(paths), sum(1 for r, _ in paths if r.lower().endswith(".png")),
             offset / 1048576.0, patched))


if __name__ == "__main__":
    main()
