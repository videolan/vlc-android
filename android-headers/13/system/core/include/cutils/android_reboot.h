/*
 * Copyright 2011, The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#ifndef __CUTILS_ANDROID_REBOOT_H__
#define __CUTILS_ANDROID_REBOOT_H__

__BEGIN_DECLS

/* Commands */
#define ANDROID_RB_RESTART  0xDEAD0001
#define ANDROID_RB_POWEROFF 0xDEAD0002
#define ANDROID_RB_RESTART2 0xDEAD0003

/* Flags */
#define ANDROID_RB_FLAG_NO_SYNC       0x1
#define ANDROID_RB_FLAG_NO_REMOUNT_RO 0x2

int android_reboot(int cmd, int flags, char *arg);

__END_DECLS

#endif /* __CUTILS_ANDROID_REBOOT_H__ */
