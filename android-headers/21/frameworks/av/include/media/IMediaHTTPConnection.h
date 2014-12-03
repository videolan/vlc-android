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

#ifndef I_MEDIA_HTTP_CONNECTION_H_

#define I_MEDIA_HTTP_CONNECTION_H_

#include <binder/IInterface.h>
#include <media/stagefright/foundation/ABase.h>
#include <utils/KeyedVector.h>

namespace android {

struct IMediaHTTPConnection;

/** MUST stay in sync with IMediaHTTPConnection.aidl */

struct IMediaHTTPConnection : public IInterface {
    DECLARE_META_INTERFACE(MediaHTTPConnection);

    virtual bool connect(
            const char *uri, const KeyedVector<String8, String8> *headers) = 0;

    virtual void disconnect() = 0;
    virtual ssize_t readAt(off64_t offset, void *data, size_t size) = 0;
    virtual off64_t getSize() = 0;
    virtual status_t getMIMEType(String8 *mimeType) = 0;
    virtual status_t getUri(String8 *uri) = 0;

private:
    DISALLOW_EVIL_CONSTRUCTORS(IMediaHTTPConnection);
};

}  // namespace android

#endif  // I_MEDIA_HTTP_CONNECTION_H_
