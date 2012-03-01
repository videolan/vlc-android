/*
 * Copyright (C) 2010 The Android Open Source Project
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
#include <unistd.h>
#include <pthread.h>

/* Technical note:
 *
 * Possible states of a read/write lock:
 *
 *  - no readers and no writer (unlocked)
 *  - one or more readers sharing the lock at the same time (read-locked)
 *  - one writer holding the lock (write-lock)
 *
 * Additionally:
 *  - trying to get the write-lock while there are any readers blocks
 *  - trying to get the read-lock while there is a writer blocks
 *  - a single thread can acquire the lock multiple times in the same mode
 *
 *  - Posix states that behaviour is undefined it a thread tries to acquire
 *    the lock in two distinct modes (e.g. write after read, or read after write).
 *
 *  - This implementation tries to avoid writer starvation by making the readers
 *    block as soon as there is a waiting writer on the lock. However, it cannot
 *    completely eliminate it: each time the lock is unlocked, all waiting threads
 *    are woken and battle for it, which one gets it depends on the kernel scheduler
 *    and is semi-random.
 *
 */

#define  __likely(cond)    __builtin_expect(!!(cond), 1)
#define  __unlikely(cond)  __builtin_expect(!!(cond), 0)

#define  RWLOCKATTR_DEFAULT     0
#define  RWLOCKATTR_SHARED_MASK 0x0010

/* __get_thread and pthread_internal_t didn't change since introduced,
 * up to ics */
typedef struct pthread_internal_t
{
    struct pthread_internal_t*  next;
    struct pthread_internal_t** pref;
    pthread_attr_t              attr;
    pid_t                       kernel_id;
    pthread_cond_t              join_cond;
    int                         join_count;
    void*                       return_value;
    int                         intern;
    __pthread_cleanup_t*        cleanup_stack;
    void**                      tls;         /* thread-local storage area */
} pthread_internal_t;

extern pthread_internal_t* __get_thread(void);

/* Return a global kernel ID for the current thread */
static int __get_thread_id(void)
{
    return __get_thread()->kernel_id;
}

int pthread_rwlockattr_init(pthread_rwlockattr_t *attr)
{
    if (!attr)
        return EINVAL;

    *attr = PTHREAD_PROCESS_PRIVATE;
    return 0;
}

int pthread_rwlockattr_destroy(pthread_rwlockattr_t *attr)
{
    if (!attr)
        return EINVAL;

    *attr = -1;
    return 0;
}

int pthread_rwlockattr_setpshared(pthread_rwlockattr_t *attr, int  pshared)
{
    if (!attr)
        return EINVAL;

    switch (pshared) {
    case PTHREAD_PROCESS_PRIVATE:
    case PTHREAD_PROCESS_SHARED:
        *attr = pshared;
        return 0;
    default:
        return EINVAL;
    }
}

int pthread_rwlockattr_getpshared(pthread_rwlockattr_t *attr, int *pshared)
{
    if (!attr || !pshared)
        return EINVAL;

    *pshared = *attr;
    return 0;
}

int pthread_rwlock_init(pthread_rwlock_t *rwlock, const pthread_rwlockattr_t *attr)
{
    pthread_mutexattr_t*  lock_attr = NULL;
    pthread_condattr_t*   cond_attr = NULL;
    pthread_mutexattr_t   lock_attr0;
    pthread_condattr_t    cond_attr0;
    int                   ret;

    if (rwlock == NULL)
        return EINVAL;

    if (attr && *attr == PTHREAD_PROCESS_SHARED) {
        lock_attr = &lock_attr0;
        pthread_mutexattr_init(lock_attr);
        pthread_mutexattr_setpshared(lock_attr, PTHREAD_PROCESS_SHARED);

        cond_attr = &cond_attr0;
        pthread_condattr_init(cond_attr);
        pthread_condattr_setpshared(cond_attr, PTHREAD_PROCESS_SHARED);
    }

    ret = pthread_mutex_init(&rwlock->lock, lock_attr);
    if (ret != 0)
        return ret;

    ret = pthread_cond_init(&rwlock->cond, cond_attr);
    if (ret != 0) {
        pthread_mutex_destroy(&rwlock->lock);
        return ret;
    }

    rwlock->numLocks = 0;
    rwlock->pendingReaders = 0;
    rwlock->pendingWriters = 0;
    rwlock->writerThreadId = 0;

    return 0;
}

int pthread_rwlock_destroy(pthread_rwlock_t *rwlock)
{
    int  ret;

    if (rwlock == NULL)
        return EINVAL;

    if (rwlock->numLocks > 0)
        return EBUSY;

    pthread_cond_destroy(&rwlock->cond);
    pthread_mutex_destroy(&rwlock->lock);
    return 0;
}

/* Returns TRUE iff we can acquire a read lock. */
static __inline__ int read_precondition(pthread_rwlock_t *rwlock, int  thread_id)
{
    /* We can't have the lock if any writer is waiting for it (writer bias).
     * This tries to avoid starvation when there are multiple readers racing.
     */
    if (rwlock->pendingWriters > 0)
        return 0;

    /* We can have the lock if there is no writer, or if we write-own it */
    /* The second test avoids a self-dead lock in case of buggy code. */
    if (rwlock->writerThreadId == 0 || rwlock->writerThreadId == thread_id)
        return 1;

    /* Otherwise, we can't have it */
    return 0;
}

/* returns TRUE iff we can acquire a write lock. */
static __inline__ int write_precondition(pthread_rwlock_t *rwlock, int  thread_id)
{
    /* We can get the lock if nobody has it */
    if (rwlock->numLocks == 0)
        return 1;

    /* Or if we already own it */
    if (rwlock->writerThreadId == thread_id)
        return 1;

    /* Otherwise, not */
    return 0;
}

/* This function is used to waken any waiting thread contending
 * for the lock. One of them should be able to grab it after
 * that.
 */
static void _pthread_rwlock_pulse(pthread_rwlock_t *rwlock)
{
    if (rwlock->pendingReaders > 0 || rwlock->pendingWriters > 0)
        pthread_cond_broadcast(&rwlock->cond);
}


int pthread_rwlock_rdlock(pthread_rwlock_t *rwlock)
{
    return pthread_rwlock_timedrdlock(rwlock, NULL);
}

int pthread_rwlock_tryrdlock(pthread_rwlock_t *rwlock)
{
    int ret = 0;

    if (rwlock == NULL)
        return EINVAL;

    pthread_mutex_lock(&rwlock->lock);
    if (__unlikely(!read_precondition(rwlock, __get_thread_id())))
        ret = EBUSY;
    else
        rwlock->numLocks ++;
    pthread_mutex_unlock(&rwlock->lock);

    return ret;
}

int pthread_rwlock_timedrdlock(pthread_rwlock_t *rwlock, const struct timespec *abs_timeout)
{
    int thread_id, ret = 0;

    if (rwlock == NULL)
        return EINVAL;

    pthread_mutex_lock(&rwlock->lock);
    thread_id = __get_thread_id();
    if (__unlikely(!read_precondition(rwlock, thread_id))) {
        rwlock->pendingReaders += 1;
        do {
            ret = pthread_cond_timedwait(&rwlock->cond, &rwlock->lock, abs_timeout);
        } while (ret == 0 && !read_precondition(rwlock, thread_id));
        rwlock->pendingReaders -= 1;
        if (ret != 0)
            goto EXIT;
    }
    rwlock->numLocks ++;
EXIT:
    pthread_mutex_unlock(&rwlock->lock);
    return ret;
}


int pthread_rwlock_wrlock(pthread_rwlock_t *rwlock)
{
    return pthread_rwlock_timedwrlock(rwlock, NULL);
}

int pthread_rwlock_trywrlock(pthread_rwlock_t *rwlock)
{
    int thread_id, ret = 0;

    if (rwlock == NULL)
        return EINVAL;

    pthread_mutex_lock(&rwlock->lock);
    thread_id = __get_thread_id();
    if (__unlikely(!write_precondition(rwlock, thread_id))) {
        ret = EBUSY;
    } else {
        rwlock->numLocks ++;
        rwlock->writerThreadId = thread_id;
    }
    pthread_mutex_unlock(&rwlock->lock);
    return ret;
}

int pthread_rwlock_timedwrlock(pthread_rwlock_t *rwlock, const struct timespec *abs_timeout)
{
    int thread_id, ret = 0;

    if (rwlock == NULL)
        return EINVAL;

    pthread_mutex_lock(&rwlock->lock);
    thread_id = __get_thread_id();
    if (__unlikely(!write_precondition(rwlock, thread_id))) {
        /* If we can't read yet, wait until the rwlock is unlocked
         * and try again. Increment pendingReaders to get the
         * cond broadcast when that happens.
         */
        rwlock->pendingWriters += 1;
        do {
            ret = pthread_cond_timedwait(&rwlock->cond, &rwlock->lock, abs_timeout);
        } while (ret == 0 && !write_precondition(rwlock, thread_id));
        rwlock->pendingWriters -= 1;
        if (ret != 0)
            goto EXIT;
    }
    rwlock->numLocks ++;
    rwlock->writerThreadId = thread_id;
EXIT:
    pthread_mutex_unlock(&rwlock->lock);
    return ret;
}


int pthread_rwlock_unlock(pthread_rwlock_t *rwlock)
{
    int  ret = 0;

    if (rwlock == NULL)
        return EINVAL;

    pthread_mutex_lock(&rwlock->lock);

    /* The lock must be held */
    if (rwlock->numLocks == 0) {
        ret = EPERM;
        goto EXIT;
    }

    /* If it has only readers, writerThreadId is 0 */
    if (rwlock->writerThreadId == 0) {
        if (--rwlock->numLocks == 0)
            _pthread_rwlock_pulse(rwlock);
    }
    /* Otherwise, it has only a single writer, which
     * must be ourselves.
     */
    else {
        if (rwlock->writerThreadId != __get_thread_id()) {
            ret = EPERM;
            goto EXIT;
        }
        if (--rwlock->numLocks == 0) {
            rwlock->writerThreadId = 0;
            _pthread_rwlock_pulse(rwlock);
        }
    }
EXIT:
    pthread_mutex_unlock(&rwlock->lock);
    return ret;
}
