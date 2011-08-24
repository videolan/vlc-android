#ifndef THUMBNAILER_H
#define THUMBNAILER_H

#include <pthread.h>

typedef struct
{
    libvlc_media_player_t *p_mp;

    char b_hasThumb;

    char *p_frameData;
    char *p_thumbnail;

    unsigned i_thumbnailOffset;
    unsigned i_lineSize;
    unsigned i_nbLines;
    unsigned i_picPitch;

    unsigned i_nbReceivedFrames;

    pthread_mutex_t doneMutex;
    pthread_cond_t doneCondVar;
} thumbnailer_sys_t;


void *thumbnailer_lock(void *opaque, void **pixels);
void thumbnailer_unlock(void *opaque, void *picture, void *const *p_pixels);

#endif // THUMBNAILER_H
