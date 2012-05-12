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

#ifndef _CUTILS_KLOG_H_
#define _CUTILS_KLOG_H_

void klog_init(void);
void klog_set_level(int level);
void klog_close(void);
void klog_write(int level, const char *fmt, ...)
    __attribute__ ((format(printf, 2, 3)));

#define KLOG_ERROR(tag,x...)   klog_write(3, "<3>" tag ": " x)
#define KLOG_WARNING(tag,x...) klog_write(4, "<4>" tag ": " x)
#define KLOG_NOTICE(tag,x...)  klog_write(5, "<5>" tag ": " x)
#define KLOG_INFO(tag,x...)    klog_write(6, "<6>" tag ": " x)
#define KLOG_DEBUG(tag,x...)   klog_write(7, "<7>" tag ": " x)

#define KLOG_DEFAULT_LEVEL  3  /* messages <= this level are logged */

#endif
