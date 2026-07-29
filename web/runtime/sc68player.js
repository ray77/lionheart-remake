/* Music playback through the sc68 replayer compiled to WebAssembly.
 *
 * sc68 is pull based: it fills a PCM buffer whenever asked. So nothing is
 * rendered ahead - a processing node pulls its chunks when the output needs
 * them. Tracks therefore loop without a seam, and a file stays at its original
 * few dozen kilobytes instead of growing into a stream.
 *
 * Four calls to the outside, all driven from the Java side.
 */
(function () {
    'use strict';

    var CHUNK = 4096;         // frames per pull, about 93 ms at 44.1 kHz
    var END = 32;             // API68_END

    var module = null;        // WebAssembly module, null until loaded
    var context = null;       // audio output, handed over by the Java side
    var node = null;          // processing node
    var gain = null;          // volume
    var buffer = 0;           // pointer to the PCM buffer inside the module
    var volume = 1.0;
    var open = false;
    var waiting = null;       // track handed over before the module was ready

    function buildChain() {
        if (node || !context) {
            return;
        }
        gain = context.createGain();
        gain.gain.value = volume;
        gain.connect(context.destination);

        node = context.createScriptProcessor(CHUNK, 0, 2);
        node.onaudioprocess = function (e) {
            var left = e.outputBuffer.getChannelData(0);
            var right = e.outputBuffer.getChannelData(1);
            var n = left.length;

            if (!module || !open) {
                left.fill(0);
                right.fill(0);
                return;
            }
            var status = module._sc68w_render(buffer, n);
            var pcm = new Int16Array(module.HEAP16.buffer, buffer, n * 2);
            for (var i = 0, j = 0; i < n; i++, j += 2) {
                left[i] = pcm[j] / 32768;
                right[i] = pcm[j + 1] / 32768;
            }
            var peak = 0;
            for (var k = 0; k < n; k += 16) {
                var a = Math.abs(left[k]);
                if (a > peak) {
                    peak = a;
                }
            }
            window.lionMusic = {
                playing: open,
                peak: peak,
                pulls: (window.lionMusic ? window.lionMusic.pulls : 0) + 1
            };

            if (status & END) {
                /* Reached the end: start over, so the music keeps going for the whole stage.
                 * sc68 resets its own state doing that. */
                module._sc68w_play(1);
            }
        };
        node.connect(gain);
    }

    function start(bytes) {
        if (!module) {
            waiting = bytes;
            return;
        }
        buildChain();
        if (context && context.state !== 'running') {
            /* The browser holds audio until someone has interacted with the page. A game that
             * starts straight into a stage plays its first track before that has happened, so
             * ask again here. */
            context.resume();
        }

        var p = module._malloc(bytes.length);
        module.HEAPU8.set(bytes, p);
        var ok = module._sc68w_load(p, bytes.length);
        module._free(p);
        window.lionMusicLast = {bytes: bytes.length, load: ok};
        if (ok !== 0) {
            open = false;
            return;
        }
        module._sc68w_play(1);
        open = true;
    }

    window.lionSc68 = {
        /** Take over the audio output the Java side has already created. */
        init: function (audioContext) {
            context = audioContext;
        },
        /** Start a track from raw bytes, replacing whatever is playing. */
        play: function (bytes) {
            start(bytes);
        },
        stop: function () {
            open = false;
            if (module) {
                module._sc68w_stop();
            }
        },
        setVolume: function (v) {
            volume = v;
            if (gain) {
                gain.gain.value = v;
            }
        },
        ready: function () {
            return !!module;
        },
        /** Hand out the audio output, for inspection only. */
        audioContext: function () {
            return context;
        }
    };

    createSc68().then(function (m) {
        module = m;
        var rate = context ? context.sampleRate : 44100;
        module._sc68w_open(rate | 0);
        buffer = module._malloc(CHUNK * 4);
        if (waiting) {
            var b = waiting;
            waiting = null;
            start(b);
        }
    });
}());
