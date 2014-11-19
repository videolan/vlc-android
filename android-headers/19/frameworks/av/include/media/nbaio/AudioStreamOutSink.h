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

#ifndef ANDROID_AUDIO_STREAM_OUT_SINK_H
#define ANDROID_AUDIO_STREAM_OUT_SINK_H

#include <hardware/audio.h>
#include "NBAIO.h"

namespace android {

// not multi-thread safe
class AudioStreamOutSink : public NBAIO_Sink {

public:
    AudioStreamOutSink(audio_stream_out *stream);
    virtual ~AudioStreamOutSink();

    // NBAIO_Port interface

    virtual ssize_t negotiate(const NBAIO_Format offers[], size_t numOffers,
                              NBAIO_Format counterOffers[], size_t& numCounterOffers);
    //virtual NBAIO_Format format();

    // NBAIO_Sink interface

    //virtual size_t framesWritten() const;
    //virtual size_t framesUnderrun() const;
    //virtual size_t underruns() const;

    // This is an over-estimate, and could dupe the caller into making a blocking write()
    // FIXME Use an audio HAL API to query the buffer emptying status when it's available.
    virtual ssize_t availableToWrite() const { return mStreamBufferSizeBytes >> mBitShift; }

    virtual ssize_t write(const void *buffer, size_t count);

    // AudioStreamOutSink wraps a HAL's output stream.  Its
    // getNextWriteTimestamp method is simply a passthru to the HAL's underlying
    // implementation of GNWT (if any)
    virtual status_t getNextWriteTimestamp(int64_t *timestamp);

    virtual status_t getTimestamp(AudioTimestamp& timestamp);

    // NBAIO_Sink end

#if 0   // until necessary
    audio_stream_out *stream() const { return mStream; }
#endif

private:
    audio_stream_out * const mStream;
    size_t              mStreamBufferSizeBytes; // as reported by get_buffer_size()
};

}   // namespace android

#endif  // ANDROID_AUDIO_STREAM_OUT_SINK_H
