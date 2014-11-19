/*
 * Copyright (C) 2012 The Android Open Source Project
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

#ifndef SKIP_CUT_BUFFER_H_

#define SKIP_CUT_BUFFER_H_

#include <media/stagefright/MediaBuffer.h>
#include <media/stagefright/foundation/ABuffer.h>

namespace android {

/**
 * utility class to cut the start and end off a stream of data in MediaBuffers
 *
 */
class SkipCutBuffer: public RefBase {
 public:
    // 'skip' is the number of bytes to skip from the beginning
    // 'cut' is the number of bytes to cut from the end
    SkipCutBuffer(int32_t skip, int32_t cut);

    // Submit one MediaBuffer for skipping and cutting. This may consume all or
    // some of the data in the buffer, or it may add data to it.
    // After this, the caller should continue processing the buffer as usual.
    void submit(MediaBuffer *buffer);
    void submit(const sp<ABuffer>& buffer);    // same as above, but with an ABuffer
    void clear();
    size_t size(); // how many bytes are currently stored in the buffer

 protected:
    virtual ~SkipCutBuffer();

 private:
    void write(const char *src, size_t num);
    size_t read(char *dst, size_t num);
    int32_t mFrontPadding;
    int32_t mBackPadding;
    int32_t mWriteHead;
    int32_t mReadHead;
    int32_t mCapacity;
    char* mCutBuffer;
    DISALLOW_EVIL_CONSTRUCTORS(SkipCutBuffer);
};

}  // namespace android

#endif  // OMX_CODEC_H_
