package probe;

import org.teavm.interop.Async;
import org.teavm.interop.AsyncCallback;
import org.teavm.jso.JSBody;
import org.teavm.jso.JSExceptions;
import org.teavm.jso.JSObject;
import org.teavm.jso.ajax.XMLHttpRequest;
import org.teavm.jso.browser.Window;
import org.teavm.jso.dom.html.HTMLDocument;
import org.teavm.jso.dom.html.HTMLElement;
import org.teavm.jso.dom.html.HTMLImageElement;
import org.teavm.jso.typedarrays.ArrayBuffer;
import org.teavm.jso.typedarrays.Int8Array;

import com.b3dgs.lionengine.Config;
import com.b3dgs.lionengine.Medias;
import com.b3dgs.lionengine.Resolution;
import com.b3dgs.lionengine.UtilReflection;
import com.b3dgs.lionengine.graphic.Graphics;
import com.b3dgs.lionengine.graphic.engine.Loader;
import com.b3dgs.lionengine.web.AssetRegistry;
import com.b3dgs.lionengine.web.FactoryMediaWeb;
import com.b3dgs.lionengine.web.ReflectRegistry;
import com.b3dgs.lionengine.web.graphic.FactoryGraphicWeb;
import com.b3dgs.lionengine.web.xml.DocumentProviderWeb;

/**
 * Starts Lionheart in a browser.
 *
 * <p>
 * In this order: fetch the assets (data files as one pack, images one by one because the browser has to decode those
 * itself), install the backends, then start the game loop.
 * </p>
 */
public final class WebLionheart {

    /** Asset root on the server. */
    private static final String ASSETS = "assets/";
    /* The engine derives the game surface from the aspect ratio of the output
     * (Util.getResolution: factor = 208 / height). 640x480 gave 277x208, far too narrow a view.
     * 1110x624 is exactly three times 370x208 - the widescreen size the backgrounds are drawn
     * for (mountain.png is 370 wide). */
    /** Screen width. */
    private static final int WIDTH = 1110;
    /** Screen height. */
    private static final int HEIGHT = 624;


    public static void main(String[] args) {
        try {
            status("fetching index…");
            byte[] listBytes = fetch("assets.lst");
            byte[] pak = fetch("assets.pak");

            String[] lines = new String(listBytes).split("\n");
            HTMLDocument doc = Window.current().getDocument();
            int data = 0;
            int images = 0;

            for (String line : lines) {
                if (line.length() > 2 && line.charAt(0) == 'D') {
                    int a = line.indexOf(' ', 2);
                    int b = line.indexOf(' ', a + 1);
                    int offset = Integer.parseInt(line.substring(2, a));
                    int length = Integer.parseInt(line.substring(a + 1, b));
                    byte[] slice = new byte[length];
                    System.arraycopy(pak, offset, slice, 0, length);
                    AssetRegistry.register(line.substring(b + 1), slice);
                    data++;
                }
            }
            status(data + " data files unpacked, loading images…");

            for (String line : lines) {
                if (line.length() > 2 && line.charAt(0) == 'I') {
                    String path = line.substring(2);
                    HTMLImageElement img = (HTMLImageElement) doc.createElement("img");
                    if (awaitImage(img, ASSETS + path)) {
                        FactoryGraphicWeb.registerImage(path, img);
                        images++;
                    }
                }
            }
            status(data + " data + " + images + " images loaded — starting game…");
            boot();
        } catch (Throwable t) {
            status("ERROR: " + t);
            status("<pre id=\"stack\">" + trace(t) + "</pre>");
        }
    }

    /** Fetch a file, suspending until it has arrived. */
    @Async
    private static native byte[] fetch(String path);

    private static void fetch(String path, AsyncCallback<byte[]> callback) {
        XMLHttpRequest xhr = XMLHttpRequest.create();
        xhr.open("GET", path);
        xhr.setResponseType("arraybuffer");
        xhr.setOnReadyStateChange(() -> {
            if (xhr.getReadyState() == XMLHttpRequest.DONE) {
                ArrayBuffer buffer = (ArrayBuffer) xhr.getResponse();
                if (buffer == null) {
                    callback.complete(new byte[0]);
                    return;
                }
                Int8Array array = Int8Array.create(buffer);
                byte[] out = new byte[array.getLength()];
                for (int i = 0; i < out.length; i++) {
                    out[i] = array.get(i);
                }
                callback.complete(out);
            }
        });
        xhr.send();
    }

    /** Load an image, suspending until it is decoded. */
    @Async
    private static native boolean awaitImage(HTMLImageElement img, String src);

    private static void awaitImage(HTMLImageElement img, String src, AsyncCallback<Boolean> callback) {
        img.addEventListener("load", e -> callback.complete(Boolean.TRUE));
        img.addEventListener("error", e -> callback.complete(Boolean.FALSE));
        img.setSrc(src);
    }

    /** Install the backends and start the game. */
    private static void boot() throws Throwable {
        try {
            Medias.setFactoryMedia(new FactoryMediaWeb());
            Graphics.setFactoryGraphic(new FactoryGraphicWeb());
            com.b3dgs.lionengine.DocumentFactory.setProvider(new DocumentProviderWeb());
            UtilReflection.setCreator(new UtilReflection.Creator() {
                @Override
                public Object create(Class<?> type, Object[] params) {
                    return ReflectRegistry.create(type, params);
                }

                @Override
                public java.util.List<Class<?>[]> signatures(Class<?> type) {
                    return ReflectRegistry.signatures(type);
                }
            });
            UtilReflection.setClassResolver((loader, className) -> ReflectRegistry.resolve(className));

            /* Sound effects go through the browser audio engine. The music is sc68 (Atari chip
             * music), played by the sc68 replayer compiled to WebAssembly, see sc68player.js. */
            com.b3dgs.lionengine.audio.AudioFactory.addFormat(
                new com.b3dgs.lionengine.web.audio.AudioFormatWeb());
            com.b3dgs.lionengine.audio.AudioFactory.addFormat(
                com.b3dgs.lionengine.web.audio.AudioFormatWeb.chip());

            /* The game logic runs at 50 Hz as on the Amiga, rendering happens as often as the
             * browser allows. The desktop keeps the two apart the same way (LoopHybrid). */
            com.b3dgs.lionheart.Util.setLoopSupplier(
                () -> new com.b3dgs.lionengine.web.LoopWeb(com.b3dgs.lionheart.Constant.RESOLUTION.rate()));

            Resolution output = new Resolution(WIDTH, HEIGHT, 60);
            Config config = new Config(output, 32, true);

            com.b3dgs.lionheart.GameConfig game = new com.b3dgs.lionheart.GameConfig();
            Boolean direct = Boolean.FALSE;

            final String url = urlParams();
            final boolean trainer = url.contains("trainer=true");
            final int level = readLevel(url);

            if (trainer || level > 0) {
                /* Test entry: ?trainer=true#level=02 starts in the chosen stage, skipping menu
                 * and intro. The cheat flag lives in InitConfig. */
                final com.b3dgs.lionengine.Media stage =
                    com.b3dgs.lionheart.Util.getStage("original",
                                                      com.b3dgs.lionheart.Difficulty.NORMAL,
                                                      level > 0 ? level : 1);
                if (stage.exists()) {
                    final java.util.Map<Integer, Integer> controls = new java.util.HashMap<>();
                    controls.put(Integer.valueOf(0), Integer.valueOf(0));

                    /* STORY rather than TRAINING: only that type chains the next stage when one
                     * ends (World.loadNextStage). TRAINING would return to the menu, as it is
                     * meant to play exactly one chosen stage. */
                    game = new com.b3dgs.lionheart.GameConfig(
                        com.b3dgs.lionheart.GameType.STORY,
                        1,
                        java.util.Optional.empty(),
                        java.util.Optional.empty(),
                        true,
                        controls,
                        new com.b3dgs.lionheart.InitConfig(
                            stage,
                            com.b3dgs.lionheart.Constant.STATS_MAX_HEART - 1,
                            0,
                            com.b3dgs.lionheart.Constant.STATS_MAX_LIFE - 1,
                            0,
                            false,
                            com.b3dgs.lionheart.Constant.CREDITS,
                            com.b3dgs.lionheart.Difficulty.NORMAL,
                            trainer,
                            startpunkt(url)));
                    direct = Boolean.TRUE;
                    final int sx = readNumber(url, "x=");
                    status("test entry: stage " + (level > 0 ? level : 1)
                           + (trainer ? ", trainer on" : "")
                           + (sx >= 0 ? ", starting at tile " + sx + "/" + readNumber(url, "y=") : ""));
                } else {
                    status("stage " + level + " does not exist, starting normally.");
                }
            }

            if (Boolean.TRUE.equals(direct)) {
                /* Straight into the scene: for STORY the loading sequence would put the intro in
                 * between. Its sound warm up is skipped, which costs nothing here - in a browser
                 * the sounds are decoded when they are created anyway. */
                Loader.start(config, com.b3dgs.lionheart.Scene.class, game, Boolean.FALSE);
            } else {
                Loader.start(config, com.b3dgs.lionheart.Loading.class, game, direct);
            }
            status("game ended.");
        } catch (Throwable t) {
            status("ERROR while starting: " + t);
            status("<pre id=\"stack\">" + trace(t) + "</pre>");
            throw t;
        }
    }

    /** Get the call path of a failure: for a JavaScript error it sits in the unwrapped object. */
    private static String trace(Throwable t) {
        final JSObject raw = JSExceptions.getJSException(t);
        if (raw != null) {
            return jsStack(raw);
        }
        final StringBuilder out = new StringBuilder();
        for (StackTraceElement e : t.getStackTrace()) {
            out.append(e).append('\n');
        }
        return out.length() == 0 ? "(no call path)" : out.toString();
    }

    @JSBody(params = "e", script = "return e && e.stack ? String(e.stack) : String(e);")
    private static native String jsStack(JSObject e);

    /**
     * Read the spawn point from the address, in tiles: <code>&amp;x=370&amp;y=12</code>.
     *
     * <p>
     * The engine multiplies by the tile width itself, which is why these are tiles and not pixels. Without them the
     * stage keeps its usual start.
     * </p>
     *
     * @param url The query and the fragment.
     * @return The spawn point, empty when none is given.
     */
    private static java.util.Optional<com.b3dgs.lionengine.geom.Coord> startpunkt(String url) {
        final int x = readNumber(url, "x=");
        final int y = readNumber(url, "y=");
        if (x < 0 || y < 0) {
            return java.util.Optional.empty();
        }
        return java.util.Optional.of(new com.b3dgs.lionengine.geom.Coord(x, y));
    }

    /**
     * Read the number following a key.
     *
     * @param url The address.
     * @param key The key including the equals sign.
     * @return The number, -1 when absent.
     */
    private static int readNumber(String url, String key) {
        int at = url.indexOf(key);
        while (at > 0 && url.charAt(at - 1) != '&' && url.charAt(at - 1) != '?' && url.charAt(at - 1) != '#') {
            at = url.indexOf(key, at + 1);
        }
        if (at < 0) {
            return -1;
        }
        int end = at + key.length();
        while (end < url.length() && url.charAt(end) >= '0' && url.charAt(end) <= '9') {
            end++;
        }
        if (end == at + key.length()) {
            return -1;
        }
        return Integer.parseInt(url.substring(at + key.length(), end));
    }

    /** Get the address: query and fragment together. */
    @JSBody(params = {}, script = "return window.location.search + window.location.hash;")
    private static native String urlParams();

    /**
     * Read the stage number from the address.
     *
     * @param url The query and the fragment.
     * @return The number, 0 when none is given.
     */
    private static int readLevel(String url) {
        final int at = url.indexOf("level=");
        if (at < 0) {
            return 0;
        }
        int end = at + 6;
        while (end < url.length() && url.charAt(end) >= '0' && url.charAt(end) <= '9') {
            end++;
        }
        if (end == at + 6) {
            return 0;
        }
        return Integer.parseInt(url.substring(at + 6, end));
    }

    private static void status(String msg) {
        HTMLDocument doc = Window.current().getDocument();
        HTMLElement out = doc.getElementById("out");
        if (out != null) {
            HTMLElement line = doc.createElement("div");
            line.setInnerHTML(msg);
            out.appendChild(line);
        }
    }

    private WebLionheart() {
        // Startklasse
    }
}
