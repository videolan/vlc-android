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

// Implementation of NBAIO_Source that wraps an AudioBufferProvider

#ifndef ANDROID_AUDIO_BUFFER_PROVIDER_SOURCE_H
#define ANDROID_AUDIO_BUFFER_PROVIDER_SOURCE_H

#include "NBAIO.h"
#include <media/AudioBufferProvider.h>

namespace android {

class AudioBufferProviderSource : public NBAIO_Source {

public:
    AudioBufferProviderSource(AudioBufferProvider *provider, NBAIO_Format format);
    virtual ~AudioBufferProviderSource();

    // NBAIO_Port interface

    //virtual ssize_t negotiate(const NBAIO_Format offers[], size_t numOffers,
    //                          NBAIO_Format counterOffers[], size_t& numCounterOffers);
    //virtual NBAIO_Format format();

    // NBAIO_Source interface

    //virtual size_t framesRead() const;
    //virtual size_t framesOverrun();
    //virtual size_t overruns();
    virtual ssize_t availableToRead();
    virtual ssize_t read(void *buffer, size_t count, int64_t readPTS);
    virtual ssize_t readVia(readVia_t via, size_t total, void *user,
                            int64_t readPTS, size_t block);

private:
    AudioBufferProvider * const mProvider;
    AudioBufferProvider::Buffer mBuffer;    // current buffer
    size_t                      mConsumed;  // number of frames consumed so far from current buffer
};

}   // namespace android

#endif  // ANDROID_AUDIO_BUFFER_PROVIDER_SOURCE_H
