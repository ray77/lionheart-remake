/* Thin wrapper around sc68 for the browser.
 *
 * sc68 is pull based: api68_process() fills a PCM buffer whenever asked. That
 * matches a web audio source pulling its chunks when it needs them, so nothing
 * is rendered ahead - the music keeps going and loop points stay seamless.
 */
#include <stdlib.h>
#include <string.h>
#include <emscripten/emscripten.h>

#include "api68.h"

static api68_t *sc68 = 0;
static int rate_used = 0;

/* Create. Returns the sampling rate actually used, 0 on failure. */
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

/* Load a track from memory. 0 means fine.
 *
 * Eject first: sc68 refuses a second load while something is still in ("disk is
 * already loaded"), and api68_stop() does not eject. Without this only the very
 * first track of a session ever plays.
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

/* Start a track (1 is the first). */
EMSCRIPTEN_KEEPALIVE
int sc68w_play(int track)
{
    if (!sc68) {
        return -1;
    }
    return api68_play(sc68, track);
}

/* Pull PCM: frames frames, stereo 16 bit. Returns the sc68 status. */
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
