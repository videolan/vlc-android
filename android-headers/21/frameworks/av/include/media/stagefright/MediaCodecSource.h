/*
 * Copyright (C) 2014 The Android Open Source Project
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

#ifndef MediaCodecSource_H_
#define MediaCodecSource_H_

#include <media/stagefright/foundation/ABase.h>
#include <media/stagefright/foundation/AHandlerReflector.h>
#include <media/stagefright/MediaSource.h>

namespace android {

class ALooper;
class AMessage;
class IGraphicBufferProducer;
class MediaCodec;
class MetaData;

struct MediaCodecSource : public MediaSource,
                          public MediaBufferObserver {
    enum FlagBits {
        FLAG_USE_SURFACE_INPUT      = 1,
        FLAG_USE_METADATA_INPUT     = 2,
    };

    static sp<MediaCodecSource> Create(
            const sp<ALooper> &looper,
            const sp<AMessage> &format,
            const sp<MediaSource> &source,
            uint32_t flags = 0);

    bool isVideo() const { return mIsVideo; }
    sp<IGraphicBufferProducer> getGraphicBufferProducer();

    // MediaSource
    virtual status_t start(MetaData *params = NULL);
    virtual status_t stop();
    virtual status_t pause();
    virtual sp<MetaData> getFormat() { return mMeta; }
    virtual status_t read(
            MediaBuffer **buffer,
            const ReadOptions *options = NULL);

    // MediaBufferObserver
    virtual void signalBufferReturned(MediaBuffer *buffer);

    // for AHandlerReflector
    void onMessageReceived(const sp<AMessage> &msg);

protected:
    virtual ~MediaCodecSource();

private:
    struct Puller;

    enum {
        kWhatPullerNotify,
        kWhatEncoderActivity,
        kWhatStart,
        kWhatStop,
        kWhatPause,
    };

    MediaCodecSource(
            const sp<ALooper> &looper,
            const sp<AMessage> &outputFormat,
            const sp<MediaSource> &source,
            uint32_t flags = 0);

    status_t onStart(MetaData *params);
    status_t init();
    status_t initEncoder();
    void releaseEncoder();
    status_t feedEncoderInputBuffers();
    void scheduleDoMoreWork();
    status_t doMoreWork(int32_t numInput, int32_t numOutput);
    void suspend();
    void resume(int64_t skipFramesBeforeUs = -1ll);
    void signalEOS(status_t err = ERROR_END_OF_STREAM);
    bool reachedEOS();
    status_t postSynchronouslyAndReturnError(const sp<AMessage> &msg);

    sp<ALooper> mLooper;
    sp<ALooper> mCodecLooper;
    sp<AHandlerReflector<MediaCodecSource> > mReflector;
    sp<AMessage> mOutputFormat;
    sp<MetaData> mMeta;
    sp<Puller> mPuller;
    sp<MediaCodec> mEncoder;
    uint32_t mFlags;
    List<uint32_t> mStopReplyIDQueue;
    bool mIsVideo;
    bool mStarted;
    bool mStopping;
    bool mDoMoreWorkPending;
    sp<AMessage> mEncoderActivityNotify;
    sp<IGraphicBufferProducer> mGraphicBufferProducer;
    Vector<sp<ABuffer> > mEncoderInputBuffers;
    Vector<sp<ABuffer> > mEncoderOutputBuffers;
    List<MediaBuffer *> mInputBufferQueue;
    List<size_t> mAvailEncoderInputIndices;
    List<int64_t> mDecodingTimeQueue; // decoding time (us) for video

    // audio drift time
    int64_t mFirstSampleTimeUs;
    List<int64_t> mDriftTimeQueue;

    // following variables are protected by mOutputBufferLock
    Mutex mOutputBufferLock;
    Condition mOutputBufferCond;
    List<MediaBuffer*> mOutputBufferQueue;
    bool mEncoderReachedEOS;
    status_t mErrorCode;

    DISALLOW_EVIL_CONSTRUCTORS(MediaCodecSource);
};

} // namespace android

#endif /* MediaCodecSource_H_ */
