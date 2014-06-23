/*
 * Copyright (C) 2010 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#ifndef __LIB_UTILS_COMPAT_H
#define __LIB_UTILS_COMPAT_H

#include <unistd.h>

/* Compatibility definitions for non-Linux (i.e., BSD-based) hosts. */
#ifndef HAVE_OFF64_T
#if _FILE_OFFSET_BITS < 64
#error "_FILE_OFFSET_BITS < 64; large files are not supported on this platform"
#endif /* _FILE_OFFSET_BITS < 64 */

typedef off_t off64_t;

static inline off64_t lseek64(int fd, off64_t offset, int whence) {
    return lseek(fd, offset, whence);
}

#ifdef HAVE_PREAD
static inline ssize_t pread64(int fd, void* buf, size_t nbytes, off64_t offset) {
    return pread(fd, buf, nbytes, offset);
}
#endif

#endif /* !HAVE_OFF64_T */

#if HAVE_PRINTF_ZD
#  define ZD "%zd"
#  define ZD_TYPE ssize_t
#else
#  define ZD "%ld"
#  define ZD_TYPE long
#endif

/*
 * TEMP_FAILURE_RETRY is defined by some, but not all, versions of
 * <unistd.h>. (Alas, it is not as standard as we'd hoped!) So, if it's
 * not already defined, then define it here.
 */
#ifndef TEMP_FAILURE_RETRY
/* Used to retry syscalls that can return EINTR. */
#define TEMP_FAILURE_RETRY(exp) ({         \
    typeof (exp) _rc;                      \
    do {                                   \
        _rc = (exp);                       \
    } while (_rc == -1 && errno == EINTR); \
    _rc; })
#endif

#endif /* __LIB_UTILS_COMPAT_H */
