/*
 * Copyright (C) 2005 The Android Open Source Project
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

//
// Handy utility functions and portability code.
//
#ifndef _LIBS_UTILS_MISC_H
#define _LIBS_UTILS_MISC_H

#include <sys/time.h>
#include <utils/Endian.h>

/* get #of elements in a static array */
#ifndef NELEM
# define NELEM(x) ((int) (sizeof(x) / sizeof((x)[0])))
#endif

namespace android {

/*
 * Some utility functions for working with files.  These could be made
 * part of a "File" class.
 */
typedef enum FileType {
    kFileTypeUnknown = 0,
    kFileTypeNonexistent,       // i.e. ENOENT
    kFileTypeRegular,
    kFileTypeDirectory,
    kFileTypeCharDev,
    kFileTypeBlockDev,
    kFileTypeFifo,
    kFileTypeSymlink,
    kFileTypeSocket,
} FileType;
/* get the file's type; follows symlinks */
FileType getFileType(const char* fileName);
/* get the file's modification date; returns -1 w/errno set on failure */
time_t getFileModDate(const char* fileName);

typedef void (*sysprop_change_callback)(void);
void add_sysprop_change_callback(sysprop_change_callback cb, int priority);
void report_sysprop_change();

}; // namespace android

#endif // _LIBS_UTILS_MISC_H
