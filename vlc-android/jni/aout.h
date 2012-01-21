#ifndef LIBVLCJNI_AOUT_H
#define LIBVLCJNI_AOUT_H

#include <stdint.h>

int aout_open(void **opaque, char *format, unsigned *rate, unsigned *nb_channels);
void aout_play(void *opaque, const void *samples, unsigned count, int64_t pts);
void aout_close(void *opaque);

#endif // LIBVLCJNI_VOUT_H
