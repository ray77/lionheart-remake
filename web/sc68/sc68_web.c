/* Schmale Hülle um sc68 für den Browser.
 *
 * sc68 ist abrufend gebaut: api68_process() füllt einen PCM-Puffer, so oft man
 * will. Das passt genau auf eine Web-Tonquelle, die sich ihre Häppchen holt,
 * wenn sie welche braucht. Deshalb wird hier nichts vorgerendert - die Musik
 * läuft endlos weiter und die Schleifenübergänge bleiben nahtlos.
 */
#include <stdlib.h>
#include <string.h>
#include <emscripten/emscripten.h>

#include "api68.h"

static api68_t *sc68 = 0;
static int rate_used = 0;

/* Anlegen. Liefert die tatsächlich benutzte Abtastrate, 0 bei Fehler. */
EMSCRIPTEN_KEEPALIVE
int sc68w_open(int rate)
{
    api68_init_t init;

    if (sc68) {
        return rate_used;
    }
    memset(&init, 0, sizeof(init));
    init.alloc = (void *(*)(unsigned int)) malloc;
    init.free = free;
    init.sampling_rate = (unsigned int) rate;

    sc68 = api68_init(&init);
    if (!sc68) {
        return 0;
    }
    rate_used = (int) api68_sampling_rate(sc68, 0);
    return rate_used;
}

/* Ein Stück aus dem Speicher laden. 0 = in Ordnung.
 *
 * Erst auswerfen: sc68 weist ein zweites Laden ab, solange noch etwas eingelegt
 * ist ("disk is already loaded"), und api68_stop() wirft nicht aus. Ohne das
 * spielt nur das allererste Stück einer Sitzung.
 */
EMSCRIPTEN_KEEPALIVE
int sc68w_load(const void *buf, int len)
{
    if (!sc68) {
        return -1;
    }
    api68_close(sc68);
    return api68_load_mem(sc68, buf, len);
}

/* Spur starten (1 = erste). */
EMSCRIPTEN_KEEPALIVE
int sc68w_play(int track)
{
    if (!sc68) {
        return -1;
    }
    return api68_play(sc68, track);
}

/* PCM abholen: frames Bilder, stereo 16 Bit. Liefert den sc68-Status. */
EMSCRIPTEN_KEEPALIVE
int sc68w_render(void *dst, int frames)
{
    if (!sc68) {
        memset(dst, 0, (size_t) frames * 4);
        return -1;
    }
    return api68_process(sc68, dst, frames);
}

EMSCRIPTEN_KEEPALIVE
int sc68w_stop(void)
{
    return sc68 ? api68_stop(sc68) : -1;
}

EMSCRIPTEN_KEEPALIVE
void sc68w_close(void)
{
    if (sc68) {
        api68_shutdown(sc68);
        sc68 = 0;
    }
}

EMSCRIPTEN_KEEPALIVE
const char *sc68w_error(void)
{
    return api68_error();
}
