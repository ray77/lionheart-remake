/* Musikwiedergabe über sc68 im WASM-Teil.
 *
 * sc68 ist abrufend: es füllt einen PCM-Puffer, so oft man will. Deshalb wird
 * hier nichts vorgerendert - ein Verarbeitungsknoten holt sich seine Häppchen,
 * wenn die Tonausgabe welche braucht. Die Stücke laufen damit endlos ohne Naht,
 * und eine Datei bleibt bei ihren ~70 KB statt als OGG aufzugehen.
 *
 * Nach aussen nur vier Aufrufe, die die Java-Seite bedient.
 */
(function () {
    'use strict';

    var STUECK = 4096;        // Bilder je Abruf, ~93 ms bei 44,1 kHz
    var ENDE = 32;            // API68_END

    var modul = null;         // WASM-Modul, null bis geladen
    var kontext = null;       // Tonausgabe, kommt von der Java-Seite
    var knoten = null;        // Verarbeitungsknoten
    var regler = null;        // Lautstärke
    var puffer = 0;           // Zeiger auf den PCM-Puffer im WASM-Speicher
    var lautstaerke = 1.0;
    var offen = false;
    var wartend = null;       // Stück, das vor dem Laden des Moduls kam

    function baueKette() {
        if (knoten || !kontext) {
            return;
        }
        regler = kontext.createGain();
        regler.gain.value = lautstaerke;
        regler.connect(kontext.destination);

        knoten = kontext.createScriptProcessor(STUECK, 0, 2);
        knoten.onaudioprocess = function (e) {
            var links = e.outputBuffer.getChannelData(0);
            var rechts = e.outputBuffer.getChannelData(1);
            var n = links.length;

            if (!modul || !offen) {
                links.fill(0);
                rechts.fill(0);
                return;
            }
            var status = modul._sc68w_render(puffer, n);
            var pcm = new Int16Array(modul.HEAP16.buffer, puffer, n * 2);
            for (var i = 0, j = 0; i < n; i++, j += 2) {
                links[i] = pcm[j] / 32768;
                rechts[i] = pcm[j + 1] / 32768;
            }
            var spitze = 0;
            for (var k = 0; k < n; k += 16) {
                var a = Math.abs(links[k]);
                if (a > spitze) {
                    spitze = a;
                }
            }
            window.lionMusic = {
                spielt: offen,
                spitze: spitze,
                abrufe: (window.lionMusic ? window.lionMusic.abrufe : 0) + 1
            };

            if (status & ENDE) {
                /* Ans Ende gekommen: wieder von vorn, damit die Musik im Abschnitt
                 * durchläuft. sc68 setzt dabei seinen Zustand selbst zurück. */
                modul._sc68w_play(1);
            }
        };
        knoten.connect(regler);
    }

    function starte(bytes) {
        if (!modul) {
            wartend = bytes;
            return;
        }
        baueKette();
        if (kontext && kontext.state !== 'running') {
            /* Der Browser haelt die Ausgabe an, bis jemand die Seite angefasst hat. Startet
             * das Spiel gleich in einen Abschnitt, ist das beim ersten Stueck noch nicht
             * passiert - also hier noch einmal nachfragen. */
            kontext.resume();
        }

        var p = modul._malloc(bytes.length);
        modul.HEAPU8.set(bytes, p);
        var ok = modul._sc68w_load(p, bytes.length);
        modul._free(p);
        window.lionMusikLetzt = {bytes: bytes.length, load: ok};
        if (ok !== 0) {
            offen = false;
            return;
        }
        modul._sc68w_play(1);
        offen = true;
    }

    window.lionSc68 = {
        /** Tonausgabe übernehmen, die die Java-Seite schon angelegt hat. */
        init: function (audioContext) {
            kontext = audioContext;
        },
        /** Stück aus Rohbytes starten, löst ein laufendes ab. */
        play: function (bytes) {
            starte(bytes);
        },
        stop: function () {
            offen = false;
            if (modul) {
                modul._sc68w_stop();
            }
        },
        setVolume: function (v) {
            lautstaerke = v;
            if (regler) {
                regler.gain.value = v;
            }
        },
        bereit: function () {
            return !!modul;
        },
        /** Tonausgabe herausgeben, nur zur Kontrolle. */
        kontext: function () {
            return kontext;
        }
    };

    createSc68().then(function (m) {
        modul = m;
        var rate = kontext ? kontext.sampleRate : 44100;
        modul._sc68w_open(rate | 0);
        puffer = modul._malloc(STUECK * 4);
        if (wartend) {
            var b = wartend;
            wartend = null;
            starte(b);
        }
    });
}());
