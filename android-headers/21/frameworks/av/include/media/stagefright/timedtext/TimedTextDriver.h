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

#ifndef TIMED_TEXT_DRIVER_H_
#define TIMED_TEXT_DRIVER_H_

#include <media/stagefright/foundation/ABase.h> // for DISALLOW_* macro
#include <utils/Errors.h> // for status_t
#include <utils/RefBase.h>
#include <utils/threads.h>

namespace android {

class ALooper;
struct IMediaHTTPService;
class MediaPlayerBase;
class MediaSource;
class Parcel;
class TimedTextPlayer;
class TimedTextSource;
class DataSource;

class TimedTextDriver {
public:
    TimedTextDriver(
            const wp<MediaPlayerBase> &listener,
            const sp<IMediaHTTPService> &httpService);

    ~TimedTextDriver();

    status_t start();
    status_t pause();
    status_t selectTrack(size_t index);
    status_t unselectTrack(size_t index);

    status_t seekToAsync(int64_t timeUs);

    status_t addInBandTextSource(
            size_t trackIndex, const sp<MediaSource>& source);

    status_t addOutOfBandTextSource(
            size_t trackIndex, const char *uri, const char *mimeType);

    // Caller owns the file desriptor and caller is responsible for closing it.
    status_t addOutOfBandTextSource(
            size_t trackIndex, int fd, off64_t offset,
            off64_t length, const char *mimeType);

    void getExternalTrackInfo(Parcel *parcel);
    size_t countExternalTracks() const;

private:
    Mutex mLock;

    enum State {
        UNINITIALIZED,
        PREPARED,
        PLAYING,
        PAUSED,
    };

    enum TextSourceType {
        TEXT_SOURCE_TYPE_IN_BAND = 0,
        TEXT_SOURCE_TYPE_OUT_OF_BAND,
    };

    sp<ALooper> mLooper;
    sp<TimedTextPlayer> mPlayer;
    wp<MediaPlayerBase> mListener;
    sp<IMediaHTTPService> mHTTPService;

    // Variables to be guarded by mLock.
    State mState;
    size_t mCurrentTrackIndex;
    KeyedVector<size_t, sp<TimedTextSource> > mTextSourceVector;
    Vector<TextSourceType> mTextSourceTypeVector;

    // -- End of variables to be guarded by mLock

    status_t selectTrack_l(size_t index);

    status_t createOutOfBandTextSource(
            size_t trackIndex, const char* mimeType,
            const sp<DataSource>& dataSource);

    DISALLOW_EVIL_CONSTRUCTORS(TimedTextDriver);
};

}  // namespace android

#endif  // TIMED_TEXT_DRIVER_H_
