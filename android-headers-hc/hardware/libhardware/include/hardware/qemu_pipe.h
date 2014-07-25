/*
 * Copyright (C) 2011 The Android Open Source Project
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
#ifndef ANDROID_INCLUDE_HARDWARE_QEMU_PIPE_H
#define ANDROID_INCLUDE_HARDWARE_QEMU_PIPE_H

#include <sys/cdefs.h>

__BEGIN_DECLS

/* Try to open a new Qemu fast-pipe. This function returns a file descriptor
 * that can be used to communicate with a named service managed by the
 * emulator.
 *
 * This file descriptor can be used as a standard pipe/socket descriptor.
 *
 * 'pipeName' is the name of the emulator service you want to connect to.
 * E.g. 'opengles' or 'camera'.
 *
 * On success, return a valid file descriptor
 * Returns -1 on error, and errno gives the error code, e.g.:
 *
 *    EINVAL  -> unknown/unsupported pipeName
 *    ENOSYS  -> fast pipes not available in this system.
 *
 * ENOSYS should never happen, except if you're trying to run within a
 * misconfigured emulator.
 *
 * You should be able to open several pipes to the same pipe service,
 * except for a few special cases (e.g. GSM modem), where EBUSY will be
 * returned if more than one client tries to connect to it.
 */
extern int  qemu_pipe_open(const char*  pipeName);

__END_DECLS

#endif /* ANDROID_INCLUDE_HARDWARE_QEMUD_PIPE_H */
