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

#ifndef ANDROID_AUDIO_PIPE_READER_H
#define ANDROID_AUDIO_PIPE_READER_H

#include "Pipe.h"

namespace android {

// PipeReader is safe for only a single thread
class PipeReader : public NBAIO_Source {

public:

    // Construct a PipeReader and associate it with a Pipe
    // FIXME make this constructor a factory method of Pipe.
    PipeReader(Pipe& pipe);
    virtual ~PipeReader();

    // NBAIO_Port interface

    //virtual ssize_t negotiate(const NBAIO_Format offers[], size_t numOffers,
    //                          NBAIO_Format counterOffers[], size_t& numCounterOffers);
    //virtual NBAIO_Format format() const;

    // NBAIO_Source interface

    //virtual size_t framesRead() const;
    virtual size_t framesOverrun() { return mFramesOverrun; }
    virtual size_t overruns()  { return mOverruns; }

    virtual ssize_t availableToRead();

    virtual ssize_t read(void *buffer, size_t count, int64_t readPTS);

    // NBAIO_Source end

#if 0   // until necessary
    Pipe& pipe() const { return mPipe; }
#endif

private:
    Pipe&       mPipe;
    int32_t     mFront;         // follows behind mPipe.mRear
    size_t      mFramesOverrun;
    size_t      mOverruns;
};

}   // namespace android

#endif  // ANDROID_AUDIO_PIPE_READER_H
