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

#ifndef _REBOOT_REBOOT_H
#define _REBOOT_REBOOT_H

#include <stddef.h>

#ifdef __cplusplus
extern "C" {
#endif

// reboot with a given reason
int reboot_wrapper(const char* reason);

#ifdef __cplusplus
} // extern "C"
#endif

#endif // _REBOOT_REBOOT_H
