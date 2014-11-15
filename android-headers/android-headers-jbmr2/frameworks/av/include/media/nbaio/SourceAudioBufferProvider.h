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

// Implementation of AudioBufferProvider that wraps an NBAIO_Source

#ifndef ANDROID_SOURCE_AUDIO_BUFFER_PROVIDER_H
#define ANDROID_SOURCE_AUDIO_BUFFER_PROVIDER_H

#include "NBAIO.h"
#include <media/ExtendedAudioBufferProvider.h>

namespace android {

class SourceAudioBufferProvider : public ExtendedAudioBufferProvider {

public:
    SourceAudioBufferProvider(const sp<NBAIO_Source>& source);
    virtual ~SourceAudioBufferProvider();

    // AudioBufferProvider interface
    virtual status_t getNextBuffer(Buffer *buffer, int64_t pts);
    virtual void     releaseBuffer(Buffer *buffer);

    // ExtendedAudioBufferProvider interface
    virtual size_t   framesReady() const;

private:
    const sp<NBAIO_Source> mSource;     // the wrapped source
    /*const*/ size_t    mFrameBitShift; // log2(frame size in bytes)
    void*               mAllocated; // pointer to base of allocated memory
    size_t              mSize;      // size of mAllocated in frames
    size_t              mOffset;    // frame offset within mAllocated of valid data
    size_t              mRemaining; // frame count within mAllocated of valid data
    size_t              mGetCount;  // buffer.frameCount of the most recent getNextBuffer
};

}   // namespace android

#endif  // ANDROID_SOURCE_AUDIO_BUFFER_PROVIDER_H
