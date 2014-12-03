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

#ifndef ANDROID_AUDIO_PIPE_H
#define ANDROID_AUDIO_PIPE_H

#include "NBAIO.h"

namespace android {

// Pipe is multi-thread safe for readers (see PipeReader), but safe for only a single writer thread.
// It cannot UNDERRUN on write, unless we allow designation of a master reader that provides the
// time-base. Readers can be added and removed dynamically, and it's OK to have no readers.
class Pipe : public NBAIO_Sink {

    friend class PipeReader;

public:
    // maxFrames will be rounded up to a power of 2, and all slots are available. Must be >= 2.
    // buffer is an optional parameter specifying the virtual address of the pipe buffer,
    // which must be of size roundup(maxFrames) * Format_frameSize(format) bytes.
    Pipe(size_t maxFrames, const NBAIO_Format& format, void *buffer = NULL);

    // If a buffer was specified in the constructor, it is not automatically freed by destructor.
    virtual ~Pipe();

    // NBAIO_Port interface

    //virtual ssize_t negotiate(const NBAIO_Format offers[], size_t numOffers,
    //                          NBAIO_Format counterOffers[], size_t& numCounterOffers);
    //virtual NBAIO_Format format() const;

    // NBAIO_Sink interface

    //virtual size_t framesWritten() const;
    //virtual size_t framesUnderrun() const;
    //virtual size_t underruns() const;

    // The write side of a pipe permits overruns; flow control is the caller's responsibility.
    // It doesn't return +infinity because that would guarantee an overrun.
    virtual ssize_t availableToWrite() const { return mMaxFrames; }

    virtual ssize_t write(const void *buffer, size_t count);
    //virtual ssize_t writeVia(writeVia_t via, size_t total, void *user, size_t block);

private:
    const size_t    mMaxFrames;     // always a power of 2
    void * const    mBuffer;
    volatile int32_t mRear;         // written by android_atomic_release_store
    volatile int32_t mReaders;      // number of PipeReader clients currently attached to this Pipe
    const bool      mFreeBufferInDestructor;
};

}   // namespace android

#endif  // ANDROID_AUDIO_PIPE_H
