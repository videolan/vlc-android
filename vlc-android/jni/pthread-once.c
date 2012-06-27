/*
 * Copyright (C) 2008 The Android Open Source Project
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *  * Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 *  * Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in
 *    the documentation and/or other materials provided with the
 *    distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS
 * FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
 * COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
 * INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING,
 * BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS
 * OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED
 * AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT
 * OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF
 * SUCH DAMAGE.
 */

#include <errno.h>
#include <pthread.h>
#include <unistd.h>

/* from bionic_atomic_inline.h */
# define ANDROID_MEMBAR_FULL() \
	do { __asm__ __volatile__ ("" ::: "memory"); } while (0)

/* NOTE: this implementation doesn't support a init function that throws a C++ exception
 *       or calls fork()
 */
int pthread_once( pthread_once_t*  once_control,  void (*init_routine)(void) )
{
    if( once_control == NULL || init_routine == NULL )
        return EINVAL;
    static pthread_mutex_t   once_lock = PTHREAD_RECURSIVE_MUTEX_INITIALIZER;
    volatile pthread_once_t* ocptr = once_control;

    pthread_once_t tmp = *ocptr;
    ANDROID_MEMBAR_FULL();
    if (tmp == PTHREAD_ONCE_INIT) {
        pthread_mutex_lock( &once_lock );
        if (*ocptr == PTHREAD_ONCE_INIT) {
            (*init_routine)();
            ANDROID_MEMBAR_FULL();
            *ocptr = ~PTHREAD_ONCE_INIT;
        }
        pthread_mutex_unlock( &once_lock );
    }
    return 0;
}
