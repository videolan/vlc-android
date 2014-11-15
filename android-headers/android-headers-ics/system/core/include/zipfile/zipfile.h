/*
 * Copyright (C) 2008 The Android Open Source Project
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

#ifndef _ZIPFILE_ZIPFILE_H
#define _ZIPFILE_ZIPFILE_H

#include <stddef.h>

#ifdef __cplusplus
extern "C" {
#endif

typedef void* zipfile_t;
typedef void* zipentry_t;

// Provide a buffer.  Returns NULL on failure.
zipfile_t init_zipfile(const void* data, size_t size);

// Release the zipfile resources.
void release_zipfile(zipfile_t file);

// Get a named entry object.  Returns NULL if it doesn't exist
// or if we won't be able to decompress it.  The zipentry_t is
// freed by release_zipfile()
zipentry_t lookup_zipentry(zipfile_t file, const char* entryName);

// Return the size of the entry.
size_t get_zipentry_size(zipentry_t entry);

// return the filename of this entry, you own the memory returned
char* get_zipentry_name(zipentry_t entry);

// The buffer must be 1.001 times the buffer size returned
// by get_zipentry_size.  Returns nonzero on failure.
int decompress_zipentry(zipentry_t entry, void* buf, int bufsize);

// iterate through the entries in the zip file.  pass a pointer to
// a void* initialized to NULL to start.  Returns NULL when done
zipentry_t iterate_zipfile(zipfile_t file, void** cookie);

#ifdef __cplusplus
} // extern "C"
#endif

#endif // _ZIPFILE_ZIPFILE_H
