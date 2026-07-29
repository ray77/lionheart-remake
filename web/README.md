# Lionheart Remake in the browser

The game compiled ahead of time to JavaScript with [TeaVM][teavm], running on
`lionengine-core-web`. No plugin, no JVM in the page: the Java bytecode is
translated before it ever reaches a browser.

Measured on the swamp and ancient town stages: **60 frames a second**, logic at
the original 50 Hz, sound effects and chip music playing.

## What is here

| Path | Contents |
|---|---|
| `teavm/` | The Maven project that produces the JavaScript build |
| `teavm/src/main/java/probe/WebLionheart.java` | Entry point: loads the assets, installs the browser backends, starts the loop |
| `teavm/src/main/java/com/b3dgs/lionengine/web/ReflectRegistry.java` | Generated constructor table, replaces runtime reflection |
| `teavm/tools/GenRegistry.java` | Regenerates that table on a desktop JVM |
| `teavm/tools/pack_assets.py` | Packs the assets into `assets.pak` + `assets.lst` |
| `sc68/` | The Atari chip music replayer, compiled to WebAssembly |
| `runtime/` | The page itself and the music player glue |
| `overlay/` | Files that join the assets for the browser build |
| `assemble.sh` | Builds and gathers everything into `teavm/target/js` |
| `serve.py` | Serves that directory, with the range requests the asset pack needs |

The engine side lives in the companion branch of
[lionengine][engine], module `lionengine-core-web`.

## Building

Needs **JDK 17** (TeaVM rejects newer class files) and
[Emscripten][emsdk] for the music replayer.

```bash
export JAVA_HOME=/path/to/temurin-17

cd web/sc68 && ./build.sh    # once: fetches sc68 2.2.1, produces sc68.wasm
cd ../.. && web/assemble.sh  # engine, game, browser build, assets, page
python3 web/serve.py         # http://localhost:8800/
```

`assemble.sh --quick` skips the asset copy and repack, for a code only change.
Serving over HTTP is not optional - the page fetches its assets, and it asks for
the pack in ranges, which `serve.py` answers and a plain `http.server` does not.

Everything under `teavm/target/js` is derived; nothing there is edited by hand.

## Notes on the port

Things that behave differently once the code is compiled ahead of time, kept
here because each one cost a while to find:

- **`Math.round` disagrees with Java on negative halves.** TeaVM sends them away
  from zero, Java sends them up. Map chunks placed with it landed 257 pixels
  apart instead of 256, and the background showed through the seam.
- **Iteration order is not the desktop's.** An object moved by *another* object
  never noticed, because whether it had already backed up its position depended
  on that order. The swinging platforms held nobody.
- **`clearRect` makes a canvas transparent, not black.** The frame is composited
  onto the screen afterwards, so everything not drawn showed the previous frame.
- **A canvas kept on the graphics card is expensive to read back.** Reading it
  every frame cost 77 ms until the context was created with
  `willReadFrequently`.
- **Unboxing `null` raises a JavaScript `TypeError`,** which no
  `catch (NullPointerException)` will see.
- **Regular expressions are best avoided.** A `Pattern.split` that returns
  nothing disables whatever it configured, silently.
- **The browser holds audio until the visitor interacts with the page.** A game
  that starts straight into a stage plays its first sounds before that happens.

[teavm]: https://teavm.org/
[emsdk]: https://emscripten.org/
[engine]: https://github.com/ray77/lionengine/tree/web
