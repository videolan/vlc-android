/*****************************************************************************
 * java_java_event_thread.c
 *****************************************************************************
 * Copyright © 2015 VLC authors, VideoLAN and VideoLabs
 *
 * This program is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation; either version 2.1 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston MA 02110-1301, USA.
 *****************************************************************************/

#include <stdlib.h>
#include <sys/queue.h>
#include <pthread.h>

#include "java_event_thread.h"
#include "utils.h"

#define LOG_TAG "JavaEventThread"
#include "log.h"

#define THREAD_NAME "JavaEventThread"
extern int jni_attach_thread(JNIEnv **env, const char *thread_name);
extern void jni_detach_thread();
extern int jni_get_env(JNIEnv **env);

typedef struct event_queue_elm event_queue_elm;
struct event_queue_elm
{
    java_event event;
    TAILQ_ENTRY(event_queue_elm) next;
};
typedef TAILQ_HEAD(, event_queue_elm) EVENT_QUEUE;

struct java_event_thread {
    bool b_run;
    pthread_mutex_t lock;
    pthread_cond_t cond;
    pthread_t thread;
    EVENT_QUEUE queue;
    jweak jobj;
};

static void *
JavaEventThread_thread(void *data)
{
    JNIEnv *env;
    java_event_thread *p_java_event_thread = data;

    if (jni_attach_thread(&env, THREAD_NAME) < 0)
        return NULL;

    pthread_mutex_lock(&p_java_event_thread->lock);
    while (p_java_event_thread->b_run)
    {
        event_queue_elm *event_elm;
        java_event *p_jevent;

        while (p_java_event_thread->b_run &&
               !(event_elm = TAILQ_FIRST(&p_java_event_thread->queue)))
            pthread_cond_wait(&p_java_event_thread->cond,
                              &p_java_event_thread->lock);

        if (!p_java_event_thread->b_run || event_elm == NULL)
            continue;

        p_jevent = &event_elm->event;
        TAILQ_REMOVE(&p_java_event_thread->queue, event_elm, next);

        pthread_mutex_unlock(&p_java_event_thread->lock);

        (*env)->CallVoidMethod(env, p_java_event_thread->jobj,
                               fields.VLCObject.dispatchEventFromNativeID,
                               p_jevent->type, p_jevent->arg1, p_jevent->arg2);

        free(event_elm);

        pthread_mutex_lock(&p_java_event_thread->lock);
    }
    pthread_mutex_unlock(&p_java_event_thread->lock);

    jni_detach_thread();

    return NULL;
}

java_event_thread *
JavaEventThread_create(jweak jobj)
{
    java_event_thread *p_java_event_thread = calloc(1, sizeof(java_event_thread));
    if (!p_java_event_thread)
        return NULL;

    pthread_mutex_init(&p_java_event_thread->lock, NULL);
    pthread_cond_init(&p_java_event_thread->cond, NULL);
    TAILQ_INIT(&p_java_event_thread->queue);

    p_java_event_thread->jobj = jobj;
    p_java_event_thread->b_run = true;
    pthread_create(&p_java_event_thread->thread, NULL,
                   JavaEventThread_thread, p_java_event_thread);

    return p_java_event_thread;
}

void
JavaEventThread_destroy(java_event_thread *p_java_event_thread)
{
    event_queue_elm *event_elm, *event_elm_next;

    pthread_mutex_lock(&p_java_event_thread->lock);
    p_java_event_thread->b_run = false;

    for (event_elm = TAILQ_FIRST(&p_java_event_thread->queue);
         event_elm != NULL; event_elm = event_elm_next)
    {
        event_elm_next = TAILQ_NEXT(event_elm, next);
        TAILQ_REMOVE(&p_java_event_thread->queue, event_elm, next);
        free(event_elm);
    }
    pthread_cond_signal(&p_java_event_thread->cond);
    pthread_mutex_unlock(&p_java_event_thread->lock);

    pthread_join(p_java_event_thread->thread, NULL);

    pthread_mutex_destroy(&p_java_event_thread->lock);
    pthread_cond_destroy(&p_java_event_thread->cond);

    free(p_java_event_thread);
}

void
JavaEventThread_add(java_event_thread *p_java_event_thread,
                    java_event *p_java_event)
{
    event_queue_elm *event_elm = calloc(1, sizeof(event_queue_elm));
    if (!event_elm)
        return;
    event_elm->event = *p_java_event;

    pthread_mutex_lock(&p_java_event_thread->lock);
    TAILQ_INSERT_TAIL(&p_java_event_thread->queue, event_elm, next);
    pthread_cond_signal(&p_java_event_thread->cond);
    pthread_mutex_unlock(&p_java_event_thread->lock);
}
