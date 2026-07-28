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
 * Startet Lionheart im Browser.
 *
 * <p>
 * Reihenfolge: erst alle Spieldaten holen (Datendateien als ein Paket, Bilder
 * einzeln, weil der Browser sie selbst dekodieren muss), dann die Unterbauten
 * einhaengen und zuletzt die Spielschleife starten.
 * </p>
 */
public final class WebLionheart {

    /** Wurzel der Spieldaten auf dem Server. */
    private static final String ASSETS = "assets/";
    /* Der Motor leitet die Spielflaeche aus dem Seitenverhaeltnis der Ausgabe ab
     * (Util.getResolution: faktor = 208 / hoehe). 640x480 ergab 277x208 und damit einen
     * viel zu schmalen Ausschnitt. 1110x624 ist genau das Dreifache von 370x208 - das
     * Breitbildmass, fuer das die Hintergruende gezeichnet sind (mountain.png ist 370 breit). */
    /** Bildschirmbreite. */
    private static final int WIDTH = 1110;
    /** Bildschirmhoehe. */
    private static final int HEIGHT = 624;


    public static void main(String[] args) {
        try {
            status("hole Verzeichnis…");
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
            status(data + " Datendateien entpackt, lade Bilder…");

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
            status(data + " Daten + " + images + " Bilder geladen — starte Spiel…");
            boot();
        } catch (Throwable t) {
            status("FEHLER: " + t);
            status("<pre id=\"stack\">" + trace(t) + "</pre>");
        }
    }

    /** Datei holen, dabei anhalten bis sie da ist. */
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

    /** Bild laden, dabei anhalten bis es dekodiert ist. */
    @Async
    private static native boolean awaitImage(HTMLImageElement img, String src);

    private static void awaitImage(HTMLImageElement img, String src, AsyncCallback<Boolean> callback) {
        img.addEventListener("load", e -> callback.complete(Boolean.TRUE));
        img.addEventListener("error", e -> callback.complete(Boolean.FALSE));
        img.setSrc(src);
    }

    /** Unterbauten einhaengen und Spiel starten. */
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

            /* Klaenge laufen ueber die Browser-Tonausgabe. Die Musik liegt als sc68 vor
             * (Atari-Chipmusik) - die spielt der nach WASM uebersetzte sc68-Abspieler,
             * siehe sc68player.js. */
            com.b3dgs.lionengine.audio.AudioFactory.addFormat(
                new com.b3dgs.lionengine.web.audio.AudioFormatWeb());
            com.b3dgs.lionengine.audio.AudioFactory.addFormat(
                com.b3dgs.lionengine.web.audio.AudioFormatWeb.chip());

            /* Die Spiellogik laeuft mit 50 Hz wie auf dem Amiga, gezeichnet wird so oft der
             * Browser laesst. Der Desktop trennt das genauso (LoopHybrid). */
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
                /* Testzugang: ?trainer=true#level=02 startet ohne Umweg ueber Menu und Intro
                 * mitten im gewaehlten Abschnitt. TRAINING ist der dafuer vorgesehene Spieltyp
                 * ("custom startup on a single chosen stage"), der Schummelschalter steckt in
                 * InitConfig. */
                final com.b3dgs.lionengine.Media stage =
                    com.b3dgs.lionheart.Util.getStage("original",
                                                      com.b3dgs.lionheart.Difficulty.NORMAL,
                                                      level > 0 ? level : 1);
                if (stage.exists()) {
                    final java.util.Map<Integer, Integer> controls = new java.util.HashMap<>();
                    controls.put(Integer.valueOf(0), Integer.valueOf(0));

                    /* STORY statt TRAINING: nur dieser Spieltyp haengt am Ende eines Abschnitts
                     * den naechsten an (World.loadNextStage). TRAINING wuerde ins Menue
                     * zurueckkehren - laut Motor spielt es genau einen gewaehlten Abschnitt. */
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
                    status("Testzugang: Abschnitt " + (level > 0 ? level : 1)
                           + (trainer ? ", Trainer an" : "")
                           + (sx >= 0 ? ", Start bei Kachel " + sx + "/" + readNumber(url, "y=") : ""));
                } else {
                    status("Abschnitt " + level + " gibt es nicht, starte normal.");
                }
            }

            if (Boolean.TRUE.equals(direct)) {
                /* Geradewegs in die Szene: die Ladesequenz schoebe bei STORY erst das Intro
                 * dazwischen. Ihr Vorwaermen der Klaenge entfaellt dabei - im Browser werden
                 * die ohnehin beim Anlegen dekodiert. */
                Loader.start(config, com.b3dgs.lionheart.Scene.class, game, Boolean.FALSE);
            } else {
                Loader.start(config, com.b3dgs.lionheart.Loading.class, game, direct);
            }
            status("Spiel beendet.");
        } catch (Throwable t) {
            status("FEHLER beim Start: " + t);
            status("<pre id=\"stack\">" + trace(t) + "</pre>");
            throw t;
        }
    }

    /** Den Weg zum Fehler holen: bei einem JS-Fehler steckt er im ausgepackten Objekt. */
    private static String trace(Throwable t) {
        final JSObject raw = JSExceptions.getJSException(t);
        if (raw != null) {
            return jsStack(raw);
        }
        final StringBuilder out = new StringBuilder();
        for (StackTraceElement e : t.getStackTrace()) {
            out.append(e).append('\n');
        }
        return out.length() == 0 ? "(kein Weg bekannt)" : out.toString();
    }

    @JSBody(params = "e", script = "return e && e.stack ? String(e.stack) : String(e);")
    private static native String jsStack(JSObject e);

    /**
     * Startpunkt aus der Adresse lesen, in Kacheln: <code>&amp;x=370&amp;y=12</code>.
     *
     * <p>
     * Der Motor rechnet die Angabe selbst mit der Kachelbreite hoch, deshalb sind es Kacheln
     * und keine Bildpunkte. Ohne Angabe bleibt es beim gewoehnlichen Startpunkt des Abschnitts.
     * </p>
     *
     * @param url Abfrage und Sprungmarke.
     * @return Der Startpunkt, leer wenn keiner angegeben ist.
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
     * Zahl hinter einem Schluessel lesen.
     *
     * @param url Adresszeile.
     * @param key Schluessel samt Gleichheitszeichen.
     * @return Die Zahl, -1 wenn nicht vorhanden.
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

    /** Adresszeile holen: Abfrage und Sprungmarke zusammen. */
    @JSBody(params = {}, script = "return window.location.search + window.location.hash;")
    private static native String urlParams();

    /**
     * Abschnittsnummer aus der Adresse lesen.
     *
     * @param url Abfrage und Sprungmarke.
     * @return Die Nummer, 0 wenn keine angegeben ist.
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
