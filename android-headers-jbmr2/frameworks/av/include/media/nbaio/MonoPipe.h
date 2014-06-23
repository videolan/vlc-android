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

#ifndef ANDROID_AUDIO_MONO_PIPE_H
#define ANDROID_AUDIO_MONO_PIPE_H

#include <time.h>
#include <utils/LinearTransform.h>
#include "NBAIO.h"

namespace android {

// MonoPipe is similar to Pipe except:
//  - supports only a single reader, called MonoPipeReader
//  - write() cannot overrun; instead it will return a short actual count if insufficient space
//  - write() can optionally block if the pipe is full
// Like Pipe, it is not multi-thread safe for either writer or reader
// but writer and reader can be different threads.
class MonoPipe : public NBAIO_Sink {

    friend class MonoPipeReader;

public:
    // reqFrames will be rounded up to a power of 2, and all slots are available. Must be >= 2.
    // Note: whatever shares this object with another thread needs to do so in an SMP-safe way (like
    // creating it the object before creating the other thread, or storing the object with a
    // release_store). Otherwise the other thread could see a partially-constructed object.
    MonoPipe(size_t reqFrames, NBAIO_Format format, bool writeCanBlock = false);
    virtual ~MonoPipe();

    // NBAIO_Port interface

    //virtual ssize_t negotiate(const NBAIO_Format offers[], size_t numOffers,
    //                          NBAIO_Format counterOffers[], size_t& numCounterOffers);
    //virtual NBAIO_Format format() const;

    // NBAIO_Sink interface

    //virtual size_t framesWritten() const;
    //virtual size_t framesUnderrun() const;
    //virtual size_t underruns() const;

    virtual ssize_t availableToWrite() const;
    virtual ssize_t write(const void *buffer, size_t count);
    //virtual ssize_t writeVia(writeVia_t via, size_t total, void *user, size_t block);

    // MonoPipe's implementation of getNextWriteTimestamp works in conjunction
    // with MonoPipeReader.  Every time a MonoPipeReader reads from the pipe, it
    // receives a "readPTS" indicating the point in time for which the reader
    // would like to read data.  This "last read PTS" is offset by the amt of
    // data the reader is currently mixing and then cached cached along with the
    // updated read pointer.  This cached value is the local time for which the
    // reader is going to request data next time it reads data (assuming we are
    // in steady state and operating with no underflows).  Writers to the
    // MonoPipe who would like to know when their next write operation will hit
    // the speakers can call getNextWriteTimestamp which will return the value
    // of the last read PTS plus the duration of the amt of data waiting to be
    // read in the MonoPipe.
    virtual status_t getNextWriteTimestamp(int64_t *timestamp);

            // average number of frames present in the pipe under normal conditions.
            // See throttling mechanism in MonoPipe::write()
            size_t  getAvgFrames() const { return mSetpoint; }
            void    setAvgFrames(size_t setpoint);
            size_t  maxFrames() const { return mMaxFrames; }

            // Set the shutdown state for the write side of a pipe.
            // This may be called by an unrelated thread.  When shutdown state is 'true',
            // a write that would otherwise block instead returns a short transfer count.
            // There is no guarantee how long it will take for the shutdown to be recognized,
            // but it will not be an unbounded amount of time.
            // The state can be restored to normal by calling shutdown(false).
            void    shutdown(bool newState = true);

            // Return true if the write side of a pipe is currently shutdown.
            bool    isShutdown();

private:
    // A pair of methods and a helper variable which allows the reader and the
    // writer to update and observe the values of mFront and mNextRdPTS in an
    // atomic lock-less fashion.
    //
    // :: Important ::
    // Two assumptions must be true in order for this lock-less approach to
    // function properly on all systems.  First, there may only be one updater
    // thread in the system.  Second, the updater thread must be running at a
    // strictly higher priority than the observer threads.  Currently, both of
    // these assumptions are true.  The only updater is always a single
    // FastMixer thread (which runs with SCHED_FIFO/RT priority while the only
    // observer is always an AudioFlinger::PlaybackThread running with
    // traditional (non-RT) audio priority.
    void updateFrontAndNRPTS(int32_t newFront, int64_t newNextRdPTS);
    void observeFrontAndNRPTS(int32_t *outFront, int64_t *outNextRdPTS);
    volatile int32_t mUpdateSeq;

    const size_t    mReqFrames;     // as requested in constructor, unrounded
    const size_t    mMaxFrames;     // always a power of 2
    void * const    mBuffer;
    // mFront and mRear will never be separated by more than mMaxFrames.
    // 32-bit overflow is possible if the pipe is active for a long time, but if that happens it's
    // safe because we "&" with (mMaxFrames-1) at end of computations to calculate a buffer index.
    volatile int32_t mFront;        // written by the reader with updateFrontAndNRPTS, observed by
                                    // the writer with observeFrontAndNRPTS
    volatile int32_t mRear;         // written by writer with android_atomic_release_store,
                                    // read by reader with android_atomic_acquire_load
    volatile int64_t mNextRdPTS;    // written by the reader with updateFrontAndNRPTS, observed by
                                    // the writer with observeFrontAndNRPTS
    bool            mWriteTsValid;  // whether mWriteTs is valid
    struct timespec mWriteTs;       // time that the previous write() completed
    size_t          mSetpoint;      // target value for pipe fill depth
    const bool      mWriteCanBlock; // whether write() should block if the pipe is full

    int64_t offsetTimestampByAudioFrames(int64_t ts, size_t audFrames);
    LinearTransform mSamplesToLocalTime;

    bool            mIsShutdown;    // whether shutdown(true) was called, no barriers are needed
};

}   // namespace android

#endif  // ANDROID_AUDIO_MONO_PIPE_H
