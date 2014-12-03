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

#ifndef MEDIA_HTTP_H_

#define MEDIA_HTTP_H_

#include <media/stagefright/foundation/AString.h>

#include "include/HTTPBase.h"

namespace android {

struct IMediaHTTPConnection;

struct MediaHTTP : public HTTPBase {
    MediaHTTP(const sp<IMediaHTTPConnection> &conn);

    virtual status_t connect(
            const char *uri,
            const KeyedVector<String8, String8> *headers,
            off64_t offset);

    virtual void disconnect();

    virtual status_t initCheck() const;

    virtual ssize_t readAt(off64_t offset, void *data, size_t size);

    virtual status_t getSize(off64_t *size);

    virtual uint32_t flags();

    virtual status_t reconnectAtOffset(off64_t offset);

protected:
    virtual ~MediaHTTP();

    virtual sp<DecryptHandle> DrmInitialization(const char* mime);
    virtual void getDrmInfo(sp<DecryptHandle> &handle, DrmManagerClient **client);
    virtual String8 getUri();
    virtual String8 getMIMEType() const;

private:
    status_t mInitCheck;
    sp<IMediaHTTPConnection> mHTTPConnection;

    KeyedVector<String8, String8> mLastHeaders;
    AString mLastURI;

    bool mCachedSizeValid;
    off64_t mCachedSize;

    sp<DecryptHandle> mDecryptHandle;
    DrmManagerClient *mDrmManagerClient;

    void clearDRMState_l();

    DISALLOW_EVIL_CONSTRUCTORS(MediaHTTP);
};

}  // namespace android

#endif  // MEDIA_HTTP_H_
