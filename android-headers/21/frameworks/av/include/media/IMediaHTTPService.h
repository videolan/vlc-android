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

#ifndef I_MEDIA_HTTP_SERVICE_H_

#define I_MEDIA_HTTP_SERVICE_H_

#include <binder/IInterface.h>
#include <media/stagefright/foundation/ABase.h>

namespace android {

struct IMediaHTTPConnection;

/** MUST stay in sync with IMediaHTTPService.aidl */

struct IMediaHTTPService : public IInterface {
    DECLARE_META_INTERFACE(MediaHTTPService);

    virtual sp<IMediaHTTPConnection> makeHTTPConnection() = 0;

private:
    DISALLOW_EVIL_CONSTRUCTORS(IMediaHTTPService);
};

}  // namespace android

#endif  // I_MEDIA_HTTP_SERVICE_H_
