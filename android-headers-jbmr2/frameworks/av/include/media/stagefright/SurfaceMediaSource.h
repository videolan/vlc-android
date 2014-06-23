/*
 * Copyright (C) 2011 The Android Open Source Project
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

#ifndef ANDROID_GUI_SURFACEMEDIASOURCE_H
#define ANDROID_GUI_SURFACEMEDIASOURCE_H

#include <gui/IGraphicBufferProducer.h>
#include <gui/BufferQueue.h>

#include <utils/threads.h>
#include <utils/Vector.h>
#include <media/stagefright/MediaSource.h>
#include <media/stagefright/MediaBuffer.h>

namespace android {
// ----------------------------------------------------------------------------

class IGraphicBufferAlloc;
class String8;
class GraphicBuffer;

// ASSUMPTIONS
// 1. SurfaceMediaSource is initialized with width*height which
// can never change.  However, deqeueue buffer does not currently
// enforce this as in BufferQueue, dequeue can be used by Surface
// which can modify the default width and heght.  Also neither the width
// nor height can be 0.
// 2. setSynchronousMode is never used (basically no one should call
// setSynchronousMode(false)
// 3. setCrop, setTransform, setScalingMode should never be used
// 4. queueBuffer returns a filled buffer to the SurfaceMediaSource. In addition, a
// timestamp must be provided for the buffer. The timestamp is in
// nanoseconds, and must be monotonically increasing. Its other semantics
// (zero point, etc) are client-dependent and should be documented by the
// client.
// 5. Once disconnected, SurfaceMediaSource can be reused (can not
// connect again)
// 6. Stop is a hard stop, the last few frames held by the encoder
// may be dropped.  It is possible to wait for the buffers to be
// returned (but not implemented)

#define DEBUG_PENDING_BUFFERS   0

class SurfaceMediaSource : public MediaSource,
                                public MediaBufferObserver,
                                protected BufferQueue::ConsumerListener {
public:
    enum { MIN_UNDEQUEUED_BUFFERS = 4};

    struct FrameAvailableListener : public virtual RefBase {
        // onFrameAvailable() is called from queueBuffer() is the FIFO is
        // empty. You can use SurfaceMediaSource::getQueuedCount() to
        // figure out if there are more frames waiting.
        // This is called without any lock held can be called concurrently by
        // multiple threads.
        virtual void onFrameAvailable() = 0;
    };

    SurfaceMediaSource(uint32_t bufferWidth, uint32_t bufferHeight);

    virtual ~SurfaceMediaSource();

    // For the MediaSource interface for use by StageFrightRecorder:
    virtual status_t start(MetaData *params = NULL);
    virtual status_t stop();
    virtual status_t read(MediaBuffer **buffer,
            const ReadOptions *options = NULL);
    virtual sp<MetaData> getFormat();

    // Get / Set the frame rate used for encoding. Default fps = 30
    status_t setFrameRate(int32_t fps) ;
    int32_t getFrameRate( ) const;

    // The call for the StageFrightRecorder to tell us that
    // it is done using the MediaBuffer data so that its state
    // can be set to FREE for dequeuing
    virtual void signalBufferReturned(MediaBuffer* buffer);
    // end of MediaSource interface

    // getTimestamp retrieves the timestamp associated with the image
    // set by the most recent call to read()
    //
    // The timestamp is in nanoseconds, and is monotonically increasing. Its
    // other semantics (zero point, etc) are source-dependent and should be
    // documented by the source.
    int64_t getTimestamp();

    // setFrameAvailableListener sets the listener object that will be notified
    // when a new frame becomes available.
    void setFrameAvailableListener(const sp<FrameAvailableListener>& listener);

    // dump our state in a String
    void dump(String8& result) const;
    void dump(String8& result, const char* prefix, char* buffer,
                                                    size_t SIZE) const;

    // isMetaDataStoredInVideoBuffers tells the encoder whether we will
    // pass metadata through the buffers. Currently, it is force set to true
    bool isMetaDataStoredInVideoBuffers() const;

    sp<BufferQueue> getBufferQueue() const { return mBufferQueue; }

    // To be called before start()
    status_t setMaxAcquiredBufferCount(size_t count);

    // To be called before start()
    status_t setUseAbsoluteTimestamps();

protected:

    // Implementation of the BufferQueue::ConsumerListener interface.  These
    // calls are used to notify the Surface of asynchronous events in the
    // BufferQueue.
    virtual void onFrameAvailable();

    // Used as a hook to BufferQueue::disconnect()
    // This is called by the client side when it is done
    // TODO: Currently, this also sets mStopped to true which
    // is needed for unblocking the encoder which might be
    // waiting to read more frames. So if on the client side,
    // the same thread supplies the frames and also calls stop
    // on the encoder, the client has to call disconnect before
    // it calls stop.
    // In the case of the camera,
    // that need not be required since the thread supplying the
    // frames is separate than the one calling stop.
    virtual void onBuffersReleased();

    static bool isExternalFormat(uint32_t format);

private:
    // mBufferQueue is the exchange point between the producer and
    // this consumer
    sp<BufferQueue> mBufferQueue;

    // mBufferSlot caches GraphicBuffers from the buffer queue
    sp<GraphicBuffer> mBufferSlot[BufferQueue::NUM_BUFFER_SLOTS];


    // The permenent width and height of SMS buffers
    int mWidth;
    int mHeight;

    // mCurrentSlot is the buffer slot index of the buffer that is currently
    // being used by buffer consumer
    // (e.g. StageFrightRecorder in the case of SurfaceMediaSource or GLTexture
    // in the case of Surface).
    // It is initialized to INVALID_BUFFER_SLOT,
    // indicating that no buffer slot is currently bound to the texture. Note,
    // however, that a value of INVALID_BUFFER_SLOT does not necessarily mean
    // that no buffer is bound to the texture. A call to setBufferCount will
    // reset mCurrentTexture to INVALID_BUFFER_SLOT.
    int mCurrentSlot;

    // mCurrentBuffers is a list of the graphic buffers that are being used by
    // buffer consumer (i.e. the video encoder). It's possible that these
    // buffers are not associated with any buffer slots, so we must track them
    // separately.  Buffers are added to this list in read, and removed from
    // this list in signalBufferReturned
    Vector<sp<GraphicBuffer> > mCurrentBuffers;

    size_t mNumPendingBuffers;

#if DEBUG_PENDING_BUFFERS
    Vector<MediaBuffer *> mPendingBuffers;
#endif

    // mCurrentTimestamp is the timestamp for the current texture. It
    // gets set to mLastQueuedTimestamp each time updateTexImage is called.
    int64_t mCurrentTimestamp;

    // mFrameAvailableListener is the listener object that will be called when a
    // new frame becomes available. If it is not NULL it will be called from
    // queueBuffer.
    sp<FrameAvailableListener> mFrameAvailableListener;

    // mMutex is the mutex used to prevent concurrent access to the member
    // variables of SurfaceMediaSource objects. It must be locked whenever the
    // member variables are accessed.
    mutable Mutex mMutex;

    ////////////////////////// For MediaSource
    // Set to a default of 30 fps if not specified by the client side
    int32_t mFrameRate;

    // mStarted is a flag to check if the recording is going on
    bool mStarted;

    // mNumFramesReceived indicates the number of frames recieved from
    // the client side
    int mNumFramesReceived;
    // mNumFramesEncoded indicates the number of frames passed on to the
    // encoder
    int mNumFramesEncoded;

    // mFirstFrameTimestamp is the timestamp of the first received frame.
    // It is used to offset the output timestamps so recording starts at time 0.
    int64_t mFirstFrameTimestamp;
    // mStartTimeNs is the start time passed into the source at start, used to
    // offset timestamps.
    int64_t mStartTimeNs;

    size_t mMaxAcquiredBufferCount;

    bool mUseAbsoluteTimestamps;

    // mFrameAvailableCondition condition used to indicate whether there
    // is a frame available for dequeuing
    Condition mFrameAvailableCondition;

    Condition mMediaBuffersAvailableCondition;

    // Avoid copying and equating and default constructor
    DISALLOW_IMPLICIT_CONSTRUCTORS(SurfaceMediaSource);
};

// ----------------------------------------------------------------------------
}; // namespace android

#endif // ANDROID_GUI_SURFACEMEDIASOURCE_H
