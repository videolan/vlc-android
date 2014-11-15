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

#ifndef MEDIA_ADAPTER_H
#define MEDIA_ADAPTER_H

#include <media/stagefright/foundation/ABase.h>
#include <media/stagefright/MediaSource.h>
#include <media/stagefright/MediaBuffer.h>
#include <media/stagefright/MetaData.h>
#include <utils/threads.h>

namespace android {

// Convert the MediaMuxer's push model into MPEG4Writer's pull model.
// Used only by the MediaMuxer for now.
struct MediaAdapter : public MediaSource, public MediaBufferObserver {
public:
    // MetaData is used to set the format and returned at getFormat.
    MediaAdapter(const sp<MetaData> &meta);
    virtual ~MediaAdapter();
    /////////////////////////////////////////////////
    // Inherited functions from MediaSource
    /////////////////////////////////////////////////

    virtual status_t start(MetaData *params = NULL);
    virtual status_t stop();
    virtual sp<MetaData> getFormat();
    virtual status_t read(
            MediaBuffer **buffer, const ReadOptions *options = NULL);

    /////////////////////////////////////////////////
    // Inherited functions from MediaBufferObserver
    /////////////////////////////////////////////////

    virtual void signalBufferReturned(MediaBuffer *buffer);

    /////////////////////////////////////////////////
    // Non-inherited functions:
    /////////////////////////////////////////////////

    // pushBuffer() will wait for the read() finish, and read() will have a
    // deep copy, such that after pushBuffer return, the buffer can be re-used.
    status_t pushBuffer(MediaBuffer *buffer);

private:
    Mutex mAdapterLock;
    // Make sure the read() wait for the incoming buffer.
    Condition mBufferReadCond;
    // Make sure the pushBuffer() wait for the current buffer consumed.
    Condition mBufferReturnedCond;

    MediaBuffer *mCurrentMediaBuffer;

    bool mStarted;
    sp<MetaData> mOutputFormat;

    DISALLOW_EVIL_CONSTRUCTORS(MediaAdapter);
};

}  // namespace android

#endif  // MEDIA_ADAPTER_H
