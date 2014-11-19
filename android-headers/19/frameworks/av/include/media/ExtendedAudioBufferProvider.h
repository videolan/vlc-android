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

#ifndef ANDROID_EXTENDED_AUDIO_BUFFER_PROVIDER_H
#define ANDROID_EXTENDED_AUDIO_BUFFER_PROVIDER_H

#include <media/AudioBufferProvider.h>
#include <media/AudioTimestamp.h>

namespace android {

class ExtendedAudioBufferProvider : public AudioBufferProvider {
public:
    virtual size_t  framesReady() const = 0;  // see description at AudioFlinger.h

    // Return the total number of frames that have been obtained and released
    virtual size_t  framesReleased() const { return 0; }

    // Invoked by buffer consumer when a new timestamp is available.
    // Default implementation ignores the timestamp.
    virtual void    onTimestamp(const AudioTimestamp& timestamp) { }
};

}   // namespace android

#endif  // ANDROID_EXTENDED_AUDIO_BUFFER_PROVIDER_H
