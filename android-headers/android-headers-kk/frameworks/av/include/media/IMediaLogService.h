/*
 * Copyright (C) 2013 The Android Open Source Project
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

#ifndef ANDROID_IMEDIALOGSERVICE_H
#define ANDROID_IMEDIALOGSERVICE_H

#include <binder/IInterface.h>
#include <binder/IMemory.h>
#include <binder/Parcel.h>

namespace android {

class IMediaLogService: public IInterface
{
public:
    DECLARE_META_INTERFACE(MediaLogService);

    virtual void    registerWriter(const sp<IMemory>& shared, size_t size, const char *name) = 0;
    virtual void    unregisterWriter(const sp<IMemory>& shared) = 0;

};

class BnMediaLogService: public BnInterface<IMediaLogService>
{
public:
    virtual status_t    onTransact(uint32_t code, const Parcel& data, Parcel* reply,
                                uint32_t flags = 0);
};

}   // namespace android

#endif  // ANDROID_IMEDIALOGSERVICE_H
