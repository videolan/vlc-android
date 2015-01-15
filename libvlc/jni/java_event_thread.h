/*****************************************************************************
 * java_java_event_thread.h
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

#ifndef JAVA_EVENT_THREAD_H
#define JAVA_EVENT_THREAD_H

#include <jni.h>
#include <vlc/vlc.h>
#include <vlc/libvlc_events.h>

typedef struct java_event_thread java_event_thread;

typedef struct java_event java_event;
struct java_event
{
    int type;
    long arg1;
    long arg2;
};

java_event_thread *JavaEventThread_create(jweak jobj);
void JavaEventThread_destroy(java_event_thread *p_java_event_thread);
void JavaEventThread_add(java_event_thread *p_java_event_thread,
                         java_event *p_java_event);

#endif // JAVA_EVENT_THREAD_H
