/*
 * Copyright (C) 2008 The Android Open Source Project
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

#ifndef ANDROID_AUDIORECORD_H
#define ANDROID_AUDIORECORD_H

#include <cutils/sched_policy.h>
#include <media/AudioSystem.h>
#include <media/IAudioRecord.h>
#include <utils/threads.h>

namespace android {

// ----------------------------------------------------------------------------

struct audio_track_cblk_t;
class AudioRecordClientProxy;

// ----------------------------------------------------------------------------

class AudioRecord : public RefBase
{
public:

    /* Events used by AudioRecord callback function (callback_t).
     * Keep in sync with frameworks/base/media/java/android/media/AudioRecord.java NATIVE_EVENT_*.
     */
    enum event_type {
        EVENT_MORE_DATA = 0,        // Request to read available data from buffer.
                                    // If this event is delivered but the callback handler
                                    // does not want to read the available data, the handler must
                                    // explicitly
                                    // ignore the event by setting frameCount to zero.
        EVENT_OVERRUN = 1,          // Buffer overrun occurred.
        EVENT_MARKER = 2,           // Record head is at the specified marker position
                                    // (See setMarkerPosition()).
        EVENT_NEW_POS = 3,          // Record head is at a new position
                                    // (See setPositionUpdatePeriod()).
        EVENT_NEW_IAUDIORECORD = 4, // IAudioRecord was re-created, either due to re-routing and
                                    // voluntary invalidation by mediaserver, or mediaserver crash.
    };

    /* Client should declare Buffer on the stack and pass address to obtainBuffer()
     * and releaseBuffer().  See also callback_t for EVENT_MORE_DATA.
     */

    class Buffer
    {
    public:
        // FIXME use m prefix
        size_t      frameCount;     // number of sample frames corresponding to size;
                                    // on input it is the number of frames available,
                                    // on output is the number of frames actually drained
                                    // (currently ignored but will make the primary field in future)

        size_t      size;           // input/output in bytes == frameCount * frameSize
                                    // on output is the number of bytes actually drained
                                    // FIXME this is redundant with respect to frameCount,
                                    // and TRANSFER_OBTAIN mode is broken for 8-bit data
                                    // since we don't define the frame format

        union {
            void*       raw;
            short*      i16;        // signed 16-bit
            int8_t*     i8;         // unsigned 8-bit, offset by 0x80
        };
    };

    /* As a convenience, if a callback is supplied, a handler thread
     * is automatically created with the appropriate priority. This thread
     * invokes the callback when a new buffer becomes available or various conditions occur.
     * Parameters:
     *
     * event:   type of event notified (see enum AudioRecord::event_type).
     * user:    Pointer to context for use by the callback receiver.
     * info:    Pointer to optional parameter according to event type:
     *          - EVENT_MORE_DATA: pointer to AudioRecord::Buffer struct. The callback must not read
     *            more bytes than indicated by 'size' field and update 'size' if fewer bytes are
     *            consumed.
     *          - EVENT_OVERRUN: unused.
     *          - EVENT_MARKER: pointer to const uint32_t containing the marker position in frames.
     *          - EVENT_NEW_POS: pointer to const uint32_t containing the new position in frames.
     *          - EVENT_NEW_IAUDIORECORD: unused.
     */

    typedef void (*callback_t)(int event, void* user, void *info);

    /* Returns the minimum frame count required for the successful creation of
     * an AudioRecord object.
     * Returned status (from utils/Errors.h) can be:
     *  - NO_ERROR: successful operation
     *  - NO_INIT: audio server or audio hardware not initialized
     *  - BAD_VALUE: unsupported configuration
     * frameCount is guaranteed to be non-zero if status is NO_ERROR,
     * and is undefined otherwise.
     */

     static status_t getMinFrameCount(size_t* frameCount,
                                      uint32_t sampleRate,
                                      audio_format_t format,
                                      audio_channel_mask_t channelMask);

    /* How data is transferred from AudioRecord
     */
    enum transfer_type {
        TRANSFER_DEFAULT,   // not specified explicitly; determine from the other parameters
        TRANSFER_CALLBACK,  // callback EVENT_MORE_DATA
        TRANSFER_OBTAIN,    // FIXME deprecated: call obtainBuffer() and releaseBuffer()
        TRANSFER_SYNC,      // synchronous read()
    };

    /* Constructs an uninitialized AudioRecord. No connection with
     * AudioFlinger takes place.  Use set() after this.
     */
                        AudioRecord();

    /* Creates an AudioRecord object and registers it with AudioFlinger.
     * Once created, the track needs to be started before it can be used.
     * Unspecified values are set to appropriate default values.
     *
     * Parameters:
     *
     * inputSource:        Select the audio input to record from (e.g. AUDIO_SOURCE_DEFAULT).
     * sampleRate:         Data sink sampling rate in Hz.
     * format:             Audio format (e.g AUDIO_FORMAT_PCM_16_BIT for signed
     *                     16 bits per sample).
     * channelMask:        Channel mask, such that audio_is_input_channel(channelMask) is true.
     * frameCount:         Minimum size of track PCM buffer in frames. This defines the
     *                     application's contribution to the
     *                     latency of the track.  The actual size selected by the AudioRecord could
     *                     be larger if the requested size is not compatible with current audio HAL
     *                     latency.  Zero means to use a default value.
     * cbf:                Callback function. If not null, this function is called periodically
     *                     to consume new data and inform of marker, position updates, etc.
     * user:               Context for use by the callback receiver.
     * notificationFrames: The callback function is called each time notificationFrames PCM
     *                     frames are ready in record track output buffer.
     * sessionId:          Not yet supported.
     * transferType:       How data is transferred from AudioRecord.
     * flags:              See comments on audio_input_flags_t in <system/audio.h>
     * threadCanCallJava:  Not present in parameter list, and so is fixed at false.
     */

                        AudioRecord(audio_source_t inputSource,
                                    uint32_t sampleRate,
                                    audio_format_t format,
                                    audio_channel_mask_t channelMask,
                                    size_t frameCount = 0,
                                    callback_t cbf = NULL,
                                    void* user = NULL,
                                    uint32_t notificationFrames = 0,
                                    int sessionId = AUDIO_SESSION_ALLOCATE,
                                    transfer_type transferType = TRANSFER_DEFAULT,
                                    audio_input_flags_t flags = AUDIO_INPUT_FLAG_NONE);

    /* Terminates the AudioRecord and unregisters it from AudioFlinger.
     * Also destroys all resources associated with the AudioRecord.
     */
protected:
                        virtual ~AudioRecord();
public:

    /* Initialize an AudioRecord that was created using the AudioRecord() constructor.
     * Don't call set() more than once, or after an AudioRecord() constructor that takes parameters.
     * Returned status (from utils/Errors.h) can be:
     *  - NO_ERROR: successful intialization
     *  - INVALID_OPERATION: AudioRecord is already initialized or record device is already in use
     *  - BAD_VALUE: invalid parameter (channelMask, format, sampleRate...)
     *  - NO_INIT: audio server or audio hardware not initialized
     *  - PERMISSION_DENIED: recording is not allowed for the requesting process
     * If status is not equal to NO_ERROR, don't call any other APIs on this AudioRecord.
     *
     * Parameters not listed in the AudioRecord constructors above:
     *
     * threadCanCallJava:  Whether callbacks are made from an attached thread and thus can call JNI.
     */
            status_t    set(audio_source_t inputSource,
                            uint32_t sampleRate,
                            audio_format_t format,
                            audio_channel_mask_t channelMask,
                            size_t frameCount = 0,
                            callback_t cbf = NULL,
                            void* user = NULL,
                            uint32_t notificationFrames = 0,
                            bool threadCanCallJava = false,
                            int sessionId = AUDIO_SESSION_ALLOCATE,
                            transfer_type transferType = TRANSFER_DEFAULT,
                            audio_input_flags_t flags = AUDIO_INPUT_FLAG_NONE);

    /* Result of constructing the AudioRecord. This must be checked for successful initialization
     * before using any AudioRecord API (except for set()), because using
     * an uninitialized AudioRecord produces undefined results.
     * See set() method above for possible return codes.
     */
            status_t    initCheck() const   { return mStatus; }

    /* Returns this track's estimated latency in milliseconds.
     * This includes the latency due to AudioRecord buffer size,
     * and audio hardware driver.
     */
            uint32_t    latency() const     { return mLatency; }

   /* getters, see constructor and set() */

            audio_format_t format() const   { return mFormat; }
            uint32_t    channelCount() const    { return mChannelCount; }
            size_t      frameCount() const  { return mFrameCount; }
            size_t      frameSize() const   { return mFrameSize; }
            audio_source_t inputSource() const  { return mInputSource; }

    /* After it's created the track is not active. Call start() to
     * make it active. If set, the callback will start being called.
     * If event is not AudioSystem::SYNC_EVENT_NONE, the capture start will be delayed until
     * the specified event occurs on the specified trigger session.
     */
            status_t    start(AudioSystem::sync_event_t event = AudioSystem::SYNC_EVENT_NONE,
                              int triggerSession = 0);

    /* Stop a track.  The callback will cease being called.  Note that obtainBuffer() still
     * works and will drain buffers until the pool is exhausted, and then will return WOULD_BLOCK.
     */
            void        stop();
            bool        stopped() const;

    /* Return the sink sample rate for this record track in Hz.
     * Unlike AudioTrack, the sample rate is const after initialization, so doesn't need a lock.
     */
            uint32_t    getSampleRate() const   { return mSampleRate; }

    /* Return the notification frame count.
     * This is approximately how often the callback is invoked, for transfer type TRANSFER_CALLBACK.
     */
            size_t      notificationFrames() const  { return mNotificationFramesAct; }

    /* Sets marker position. When record reaches the number of frames specified,
     * a callback with event type EVENT_MARKER is called. Calling setMarkerPosition
     * with marker == 0 cancels marker notification callback.
     * To set a marker at a position which would compute as 0,
     * a workaround is to set the marker at a nearby position such as ~0 or 1.
     * If the AudioRecord has been opened with no callback function associated,
     * the operation will fail.
     *
     * Parameters:
     *
     * marker:   marker position expressed in wrapping (overflow) frame units,
     *           like the return value of getPosition().
     *
     * Returned status (from utils/Errors.h) can be:
     *  - NO_ERROR: successful operation
     *  - INVALID_OPERATION: the AudioRecord has no callback installed.
     */
            status_t    setMarkerPosition(uint32_t marker);
            status_t    getMarkerPosition(uint32_t *marker) const;

    /* Sets position update period. Every time the number of frames specified has been recorded,
     * a callback with event type EVENT_NEW_POS is called.
     * Calling setPositionUpdatePeriod with updatePeriod == 0 cancels new position notification
     * callback.
     * If the AudioRecord has been opened with no callback function associated,
     * the operation will fail.
     * Extremely small values may be rounded up to a value the implementation can support.
     *
     * Parameters:
     *
     * updatePeriod:  position update notification period expressed in frames.
     *
     * Returned status (from utils/Errors.h) can be:
     *  - NO_ERROR: successful operation
     *  - INVALID_OPERATION: the AudioRecord has no callback installed.
     */
            status_t    setPositionUpdatePeriod(uint32_t updatePeriod);
            status_t    getPositionUpdatePeriod(uint32_t *updatePeriod) const;

    /* Return the total number of frames recorded since recording started.
     * The counter will wrap (overflow) periodically, e.g. every ~27 hours at 44.1 kHz.
     * It is reset to zero by stop().
     *
     * Parameters:
     *
     *  position:  Address where to return record head position.
     *
     * Returned status (from utils/Errors.h) can be:
     *  - NO_ERROR: successful operation
     *  - BAD_VALUE:  position is NULL
     */
            status_t    getPosition(uint32_t *position) const;

    /* Returns a handle on the audio input used by this AudioRecord.
     *
     * Parameters:
     *  none.
     *
     * Returned value:
     *  handle on audio hardware input
     */
            audio_io_handle_t    getInput() const;

    /* Returns the audio session ID associated with this AudioRecord.
     *
     * Parameters:
     *  none.
     *
     * Returned value:
     *  AudioRecord session ID.
     *
     * No lock needed because session ID doesn't change after first set().
     */
            int    getSessionId() const { return mSessionId; }

    /* Obtains a buffer of up to "audioBuffer->frameCount" full frames.
     * After draining these frames of data, the caller should release them with releaseBuffer().
     * If the track buffer is not empty, obtainBuffer() returns as many contiguous
     * full frames as are available immediately.
     * If the track buffer is empty and track is stopped, obtainBuffer() returns WOULD_BLOCK
     * regardless of the value of waitCount.
     * If the track buffer is empty and track is not stopped, obtainBuffer() blocks with a
     * maximum timeout based on waitCount; see chart below.
     * Buffers will be returned until the pool
     * is exhausted, at which point obtainBuffer() will either block
     * or return WOULD_BLOCK depending on the value of the "waitCount"
     * parameter.
     *
     * obtainBuffer() and releaseBuffer() are deprecated for direct use by applications,
     * which should use read() or callback EVENT_MORE_DATA instead.
     *
     * Interpretation of waitCount:
     *  +n  limits wait time to n * WAIT_PERIOD_MS,
     *  -1  causes an (almost) infinite wait time,
     *   0  non-blocking.
     *
     * Buffer fields
     * On entry:
     *  frameCount  number of frames requested
     * After error return:
     *  frameCount  0
     *  size        0
     *  raw         undefined
     * After successful return:
     *  frameCount  actual number of frames available, <= number requested
     *  size        actual number of bytes available
     *  raw         pointer to the buffer
     */

    /* FIXME Deprecated public API for TRANSFER_OBTAIN mode */
            status_t    obtainBuffer(Buffer* audioBuffer, int32_t waitCount)
                                __attribute__((__deprecated__));

private:
    /* If nonContig is non-NULL, it is an output parameter that will be set to the number of
     * additional non-contiguous frames that are available immediately.
     * FIXME We could pass an array of Buffers instead of only one Buffer to obtainBuffer(),
     * in case the requested amount of frames is in two or more non-contiguous regions.
     * FIXME requested and elapsed are both relative times.  Consider changing to absolute time.
     */
            status_t    obtainBuffer(Buffer* audioBuffer, const struct timespec *requested,
                                     struct timespec *elapsed = NULL, size_t *nonContig = NULL);
public:

    /* Release an emptied buffer of "audioBuffer->frameCount" frames for AudioFlinger to re-fill. */
    // FIXME make private when obtainBuffer() for TRANSFER_OBTAIN is removed
            void        releaseBuffer(Buffer* audioBuffer);

    /* As a convenience we provide a read() interface to the audio buffer.
     * Input parameter 'size' is in byte units.
     * This is implemented on top of obtainBuffer/releaseBuffer. For best
     * performance use callbacks. Returns actual number of bytes read >= 0,
     * or one of the following negative status codes:
     *      INVALID_OPERATION   AudioRecord is configured for streaming mode
     *      BAD_VALUE           size is invalid
     *      WOULD_BLOCK         when obtainBuffer() returns same, or
     *                          AudioRecord was stopped during the read
     *      or any other error code returned by IAudioRecord::start() or restoreRecord_l().
     */
            ssize_t     read(void* buffer, size_t size);

    /* Return the number of input frames lost in the audio driver since the last call of this
     * function.  Audio driver is expected to reset the value to 0 and restart counting upon
     * returning the current value by this function call.  Such loss typically occurs when the
     * user space process is blocked longer than the capacity of audio driver buffers.
     * Units: the number of input audio frames.
     * FIXME The side-effect of resetting the counter may be incompatible with multi-client.
     * Consider making it more like AudioTrack::getUnderrunFrames which doesn't have side effects.
     */
            uint32_t    getInputFramesLost() const;

private:
    /* copying audio record objects is not allowed */
                        AudioRecord(const AudioRecord& other);
            AudioRecord& operator = (const AudioRecord& other);

    /* a small internal class to handle the callback */
    class AudioRecordThread : public Thread
    {
    public:
        AudioRecordThread(AudioRecord& receiver, bool bCanCallJava = false);

        // Do not call Thread::requestExitAndWait() without first calling requestExit().
        // Thread::requestExitAndWait() is not virtual, and the implementation doesn't do enough.
        virtual void        requestExit();

                void        pause();    // suspend thread from execution at next loop boundary
                void        resume();   // allow thread to execute, if not requested to exit

    private:
                void        pauseInternal(nsecs_t ns = 0LL);
                                        // like pause(), but only used internally within thread

        friend class AudioRecord;
        virtual bool        threadLoop();
        AudioRecord&        mReceiver;
        virtual ~AudioRecordThread();
        Mutex               mMyLock;    // Thread::mLock is private
        Condition           mMyCond;    // Thread::mThreadExitedCondition is private
        bool                mPaused;    // whether thread is requested to pause at next loop entry
        bool                mPausedInt; // whether thread internally requests pause
        nsecs_t             mPausedNs;  // if mPausedInt then associated timeout, otherwise ignored
        bool                mIgnoreNextPausedInt;   // whether to ignore next mPausedInt request
    };

            // body of AudioRecordThread::threadLoop()
            // returns the maximum amount of time before we would like to run again, where:
            //      0           immediately
            //      > 0         no later than this many nanoseconds from now
            //      NS_WHENEVER still active but no particular deadline
            //      NS_INACTIVE inactive so don't run again until re-started
            //      NS_NEVER    never again
            static const nsecs_t NS_WHENEVER = -1, NS_INACTIVE = -2, NS_NEVER = -3;
            nsecs_t processAudioBuffer();

            // caller must hold lock on mLock for all _l methods

            status_t openRecord_l(size_t epoch);

            // FIXME enum is faster than strcmp() for parameter 'from'
            status_t restoreRecord_l(const char *from);

    sp<AudioRecordThread>   mAudioRecordThread;
    mutable Mutex           mLock;

    // Current client state:  false = stopped, true = active.  Protected by mLock.  If more states
    // are added, consider changing this to enum State { ... } mState as in AudioTrack.
    bool                    mActive;

    // for client callback handler
    callback_t              mCbf;               // callback handler for events, or NULL
    void*                   mUserData;

    // for notification APIs
    uint32_t                mNotificationFramesReq; // requested number of frames between each
                                                    // notification callback
                                                    // as specified in constructor or set()
    uint32_t                mNotificationFramesAct; // actual number of frames between each
                                                    // notification callback
    bool                    mRefreshRemaining;      // processAudioBuffer() should refresh
                                                    // mRemainingFrames and mRetryOnPartialBuffer

    // These are private to processAudioBuffer(), and are not protected by a lock
    uint32_t                mRemainingFrames;       // number of frames to request in obtainBuffer()
    bool                    mRetryOnPartialBuffer;  // sleep and retry after partial obtainBuffer()
    uint32_t                mObservedSequence;      // last observed value of mSequence

    uint32_t                mMarkerPosition;    // in wrapping (overflow) frame units
    bool                    mMarkerReached;
    uint32_t                mNewPosition;       // in frames
    uint32_t                mUpdatePeriod;      // in frames, zero means no EVENT_NEW_POS

    status_t                mStatus;

    size_t                  mFrameCount;            // corresponds to current IAudioRecord, value is
                                                    // reported back by AudioFlinger to the client
    size_t                  mReqFrameCount;         // frame count to request the first or next time
                                                    // a new IAudioRecord is needed, non-decreasing

    // constant after constructor or set()
    uint32_t                mSampleRate;
    audio_format_t          mFormat;
    uint32_t                mChannelCount;
    size_t                  mFrameSize;         // app-level frame size == AudioFlinger frame size
    audio_source_t          mInputSource;
    uint32_t                mLatency;           // in ms
    audio_channel_mask_t    mChannelMask;
    audio_input_flags_t     mFlags;
    int                     mSessionId;
    transfer_type           mTransfer;

    // Next 5 fields may be changed if IAudioRecord is re-created, but always != 0
    // provided the initial set() was successful
    sp<IAudioRecord>        mAudioRecord;
    sp<IMemory>             mCblkMemory;
    audio_track_cblk_t*     mCblk;              // re-load after mLock.unlock()
    sp<IMemory>             mBufferMemory;
    audio_io_handle_t       mInput;             // returned by AudioSystem::getInput()

    int                     mPreviousPriority;  // before start()
    SchedPolicy             mPreviousSchedulingGroup;
    bool                    mAwaitBoost;    // thread should wait for priority boost before running

    // The proxy should only be referenced while a lock is held because the proxy isn't
    // multi-thread safe.
    // An exception is that a blocking ClientProxy::obtainBuffer() may be called without a lock,
    // provided that the caller also holds an extra reference to the proxy and shared memory to keep
    // them around in case they are replaced during the obtainBuffer().
    sp<AudioRecordClientProxy> mProxy;

    bool                    mInOverrun;         // whether recorder is currently in overrun state

private:
    class DeathNotifier : public IBinder::DeathRecipient {
    public:
        DeathNotifier(AudioRecord* audioRecord) : mAudioRecord(audioRecord) { }
    protected:
        virtual void        binderDied(const wp<IBinder>& who);
    private:
        const wp<AudioRecord> mAudioRecord;
    };

    sp<DeathNotifier>       mDeathNotifier;
    uint32_t                mSequence;              // incremented for each new IAudioRecord attempt
};

}; // namespace android

#endif // ANDROID_AUDIORECORD_H
