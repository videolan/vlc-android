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

#ifndef ANDROID_AUDIO_STREAM_IN_SOURCE_H
#define ANDROID_AUDIO_STREAM_IN_SOURCE_H

#include <hardware/audio.h>
#include "NBAIO.h"

namespace android {

// not multi-thread safe
class AudioStreamInSource : public NBAIO_Source {

public:
    AudioStreamInSource(audio_stream_in *stream);
    virtual ~AudioStreamInSource();

    // NBAIO_Port interface

    virtual ssize_t negotiate(const NBAIO_Format offers[], size_t numOffers,
                              NBAIO_Format counterOffers[], size_t& numCounterOffers);
    //virtual NBAIO_Format format() const;

    // NBAIO_Sink interface

    //virtual size_t framesRead() const;
    virtual size_t framesOverrun();
    virtual size_t overruns() { (void) framesOverrun(); return mOverruns; }

    // This is an over-estimate, and could dupe the caller into making a blocking read()
    // FIXME Use an audio HAL API to query the buffer filling status when it's available.
    virtual ssize_t availableToRead() { return mStreamBufferSizeBytes >> mBitShift; }

    virtual ssize_t read(void *buffer, size_t count);

    // NBAIO_Sink end

#if 0   // until necessary
    audio_stream_in *stream() const { return mStream; }
#endif

private:
    audio_stream_in * const mStream;
    size_t              mStreamBufferSizeBytes; // as reported by get_buffer_size()
    size_t              mFramesOverrun;
    size_t              mOverruns;
};

}   // namespace android

#endif  // ANDROID_AUDIO_STREAM_IN_SOURCE_H
