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

#ifndef ANDROID_AUDIO_LIBSNDFILE_SINK_H
#define ANDROID_AUDIO_LIBSNDFILE_SINK_H

#include "NBAIO.h"
#include "sndfile.h"

// Implementation of NBAIO_Sink that wraps a libsndfile opened in SFM_WRITE mode

namespace android {

class LibsndfileSink : public NBAIO_Sink {

public:
    LibsndfileSink(SNDFILE *sndfile, const SF_INFO &sfinfo);
    virtual ~LibsndfileSink();

    // NBAIO_Port interface

    //virtual ssize_t negotiate(const NBAIO_Format offers[], size_t numOffers,
    //                          NBAIO_Format counterOffers[], size_t& numCounterOffers);
    //virtual NBAIO_Format format() const;

    // NBAIO_Sink interface

    //virtual size_t framesWritten() const;
    //virtual size_t framesUnderrun() const;
    //virtual size_t underruns() const;
    //virtual ssize_t availableToWrite() const;
    virtual ssize_t write(const void *buffer, size_t count);
    //virtual ssize_t writeVia(writeVia_t via, size_t total, void *user, size_t block);

private:
    SNDFILE *                   mSndfile;
};

}   // namespace android

#endif  // ANDROID_AUDIO_LIBSNDFILE_SINK_H
