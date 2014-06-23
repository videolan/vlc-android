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

#ifndef ANDROID_AUDIO_LIBSNDFILE_SOURCE_H
#define ANDROID_AUDIO_LIBSNDFILE_SOURCE_H

#include "NBAIO.h"
#include "sndfile.h"

// Implementation of NBAIO_Source that wraps a libsndfile opened in SFM_READ mode

namespace android {

class LibsndfileSource : public NBAIO_Source {

public:
    // If 'loop' is true and it permits seeking, then we'll act as an infinite source
    LibsndfileSource(SNDFILE *sndfile, const SF_INFO &sfinfo, bool loop = false);
    virtual ~LibsndfileSource();

    // NBAIO_Port interface

    //virtual ssize_t negotiate(const NBAIO_Format offers[], size_t numOffers,
    //                          NBAIO_Format counterOffers[], size_t& numCounterOffers);
    //virtual NBAIO_Format format() const;

    // NBAIO_Source interface

    //virtual size_t framesRead() const;
    //virtual size_t framesOverrun();
    //virtual size_t overruns();
    virtual ssize_t availableToRead();
    virtual ssize_t read(void *buffer, size_t count);
    //virtual ssize_t readVia(readVia_t via, size_t total, void *user, size_t block);

private:
    SNDFILE *   mSndfile;
    sf_count_t  mEstimatedFramesUntilEOF;
    bool        mLooping;
    bool        mReadAnyFramesThisLoopCycle;
};

}   // namespace android

#endif  // ANDROID_AUDIO_LIBSNDFILE_SOURCE_H
