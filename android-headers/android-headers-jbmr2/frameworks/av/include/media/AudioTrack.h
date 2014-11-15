/*
 * Copyright (C) 2007 The Android Open Source Project
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

#ifndef ANDROID_AUDIOTRACK_H
#define ANDROID_AUDIOTRACK_H

#include <stdint.h>
#include <sys/types.h>

#include <media/IAudioFlinger.h>
#include <media/IAudioTrack.h>
#include <media/AudioSystem.h>

#include <utils/RefBase.h>
#include <utils/Errors.h>
#include <binder/IInterface.h>
#include <binder/IMemory.h>
#include <cutils/sched_policy.h>
#include <utils/threads.h>

namespace android {

// ----------------------------------------------------------------------------

class audio_track_cblk_t;
class AudioTrackClientProxy;

// ----------------------------------------------------------------------------

class AudioTrack : virtual public RefBase
{
public:
    enum channel_index {
        MONO   = 0,
        LEFT   = 0,
        RIGHT  = 1
    };

    /* Events used by AudioTrack callback function (audio_track_cblk_t).
     * Keep in sync with frameworks/base/media/java/android/media/AudioTrack.java NATIVE_EVENT_*.
     */
    enum event_type {
        EVENT_MORE_DATA = 0,        // Request to write more data to buffer.
                                    // If this event is delivered but the callback handler
                                    // does not want to write more data, the handler must explicitly
                                    // ignore the event by setting frameCount to zero.
        EVENT_UNDERRUN = 1,         // Buffer underrun occurred.
        EVENT_LOOP_END = 2,         // Sample loop end was reached; playback restarted from
                                    // loop start if loop count was not 0.
        EVENT_MARKER = 3,           // Playback head is at the specified marker position
                                    // (See setMarkerPosition()).
        EVENT_NEW_POS = 4,          // Playback head is at a new position
                                    // (See setPositionUpdatePeriod()).
        EVENT_BUFFER_END = 5        // Playback head is at the end of the buffer.
    };

    /* Client should declare Buffer on the stack and pass address to obtainBuffer()
     * and releaseBuffer().  See also callback_t for EVENT_MORE_DATA.
     */

    class Buffer
    {
    public:
        size_t      frameCount;   // number of sample frames corresponding to size;
                                  // on input it is the number of frames desired,
                                  // on output is the number of frames actually filled

        size_t      size;         // input/output in byte units
        union {
            void*       raw;
            short*      i16;    // signed 16-bit
            int8_t*     i8;     // unsigned 8-bit, offset by 0x80
        };
    };


    /* As a convenience, if a callback is supplied, a handler thread
     * is automatically created with the appropriate priority. This thread
     * invokes the callback when a new buffer becomes available or various conditions occur.
     * Parameters:
     *
     * event:   type of event notified (see enum AudioTrack::event_type).
     * user:    Pointer to context for use by the callback receiver.
     * info:    Pointer to optional parameter according to event type:
     *          - EVENT_MORE_DATA: pointer to AudioTrack::Buffer struct. The callback must not write
     *            more bytes than indicated by 'size' field and update 'size' if fewer bytes are
     *            written.
     *          - EVENT_UNDERRUN: unused.
     *          - EVENT_LOOP_END: pointer to an int indicating the number of loops remaining.
     *          - EVENT_MARKER: pointer to an uint32_t containing the marker position in frames.
     *          - EVENT_NEW_POS: pointer to an uint32_t containing the new position in frames.
     *          - EVENT_BUFFER_END: unused.
     */

    typedef void (*callback_t)(int event, void* user, void *info);

    /* Returns the minimum frame count required for the successful creation of
     * an AudioTrack object.
     * Returned status (from utils/Errors.h) can be:
     *  - NO_ERROR: successful operation
     *  - NO_INIT: audio server or audio hardware not initialized
     */

     static status_t getMinFrameCount(size_t* frameCount,
                                      audio_stream_type_t streamType = AUDIO_STREAM_DEFAULT,
                                      uint32_t sampleRate = 0);

    /* Constructs an uninitialized AudioTrack. No connection with
     * AudioFlinger takes place.  Use set() after this.
     */
                        AudioTrack();

    /* Creates an AudioTrack object and registers it with AudioFlinger.
     * Once created, the track needs to be started before it can be used.
     * Unspecified values are set to appropriate default values.
     * With this constructor, the track is configured for streaming mode.
     * Data to be rendered is supplied by write() or by the callback EVENT_MORE_DATA.
     * Intermixing a combination of write() and non-ignored EVENT_MORE_DATA is deprecated.
     *
     * Parameters:
     *
     * streamType:         Select the type of audio stream this track is attached to
     *                     (e.g. AUDIO_STREAM_MUSIC).
     * sampleRate:         Track sampling rate in Hz.
     * format:             Audio format (e.g AUDIO_FORMAT_PCM_16_BIT for signed
     *                     16 bits per sample).
     * channelMask:        Channel mask.
     * frameCount:         Minimum size of track PCM buffer in frames. This defines the
     *                     application's contribution to the
     *                     latency of the track. The actual size selected by the AudioTrack could be
     *                     larger if the requested size is not compatible with current audio HAL
     *                     configuration.  Zero means to use a default value.
     * flags:              See comments on audio_output_flags_t in <system/audio.h>.
     * cbf:                Callback function. If not null, this function is called periodically
     *                     to provide new data and inform of marker, position updates, etc.
     * user:               Context for use by the callback receiver.
     * notificationFrames: The callback function is called each time notificationFrames PCM
     *                     frames have been consumed from track input buffer.
     * sessionId:          Specific session ID, or zero to use default.
     * threadCanCallJava:  Whether callbacks are made from an attached thread and thus can call JNI.
     *                     If not present in parameter list, then fixed at false.
     */

                        AudioTrack( audio_stream_type_t streamType,
                                    uint32_t sampleRate  = 0,
                                    audio_format_t format = AUDIO_FORMAT_DEFAULT,
                                    audio_channel_mask_t channelMask = 0,
                                    int frameCount       = 0,
                                    audio_output_flags_t flags = AUDIO_OUTPUT_FLAG_NONE,
                                    callback_t cbf       = NULL,
                                    void* user           = NULL,
                                    int notificationFrames = 0,
                                    int sessionId        = 0);

    /* Creates an audio track and registers it with AudioFlinger.
     * With this constructor, the track is configured for static buffer mode.
     * The format must not be 8-bit linear PCM.
     * Data to be rendered is passed in a shared memory buffer
     * identified by the argument sharedBuffer, which must be non-0.
     * The memory should be initialized to the desired data before calling start().
     * The write() method is not supported in this case.
     * It is recommended to pass a callback function to be notified of playback end by an
     * EVENT_UNDERRUN event.
     * FIXME EVENT_MORE_DATA still occurs; it must be ignored.
     */

                        AudioTrack( audio_stream_type_t streamType,
                                    uint32_t sampleRate = 0,
                                    audio_format_t format = AUDIO_FORMAT_DEFAULT,
                                    audio_channel_mask_t channelMask = 0,
                                    const sp<IMemory>& sharedBuffer = 0,
                                    audio_output_flags_t flags = AUDIO_OUTPUT_FLAG_NONE,
                                    callback_t cbf      = NULL,
                                    void* user          = NULL,
                                    int notificationFrames = 0,
                                    int sessionId       = 0);

    /* Terminates the AudioTrack and unregisters it from AudioFlinger.
     * Also destroys all resources associated with the AudioTrack.
     */
                        ~AudioTrack();

    /* Initialize an uninitialized AudioTrack.
     * Returned status (from utils/Errors.h) can be:
     *  - NO_ERROR: successful initialization
     *  - INVALID_OPERATION: AudioTrack is already initialized
     *  - BAD_VALUE: invalid parameter (channelMask, format, sampleRate...)
     *  - NO_INIT: audio server or audio hardware not initialized
     * If sharedBuffer is non-0, the frameCount parameter is ignored and
     * replaced by the shared buffer's total allocated size in frame units.
     */
            status_t    set(audio_stream_type_t streamType = AUDIO_STREAM_DEFAULT,
                            uint32_t sampleRate = 0,
                            audio_format_t format = AUDIO_FORMAT_DEFAULT,
                            audio_channel_mask_t channelMask = 0,
                            int frameCount      = 0,
                            audio_output_flags_t flags = AUDIO_OUTPUT_FLAG_NONE,
                            callback_t cbf      = NULL,
                            void* user          = NULL,
                            int notificationFrames = 0,
                            const sp<IMemory>& sharedBuffer = 0,
                            bool threadCanCallJava = false,
                            int sessionId       = 0);

    /* Result of constructing the AudioTrack. This must be checked
     * before using any AudioTrack API (except for set()), because using
     * an uninitialized AudioTrack produces undefined results.
     * See set() method above for possible return codes.
     */
            status_t    initCheck() const   { return mStatus; }

    /* Returns this track's estimated latency in milliseconds.
     * This includes the latency due to AudioTrack buffer size, AudioMixer (if any)
     * and audio hardware driver.
     */
            uint32_t    latency() const     { return mLatency; }

    /* getters, see constructors and set() */

            audio_stream_type_t streamType() const { return mStreamType; }
            audio_format_t format() const   { return mFormat; }

    /* Return frame size in bytes, which for linear PCM is channelCount * (bit depth per channel / 8).
     * channelCount is determined from channelMask, and bit depth comes from format.
     * For non-linear formats, the frame size is typically 1 byte.
     */
            uint32_t    channelCount() const { return mChannelCount; }

            uint32_t    frameCount() const  { return mFrameCount; }
            size_t      frameSize() const   { return mFrameSize; }

    /* Return the static buffer specified in constructor or set(), or 0 for streaming mode */
            sp<IMemory> sharedBuffer() const { return mSharedBuffer; }

    /* After it's created the track is not active. Call start() to
     * make it active. If set, the callback will start being called.
     * If the track was previously paused, volume is ramped up over the first mix buffer.
     */
            void        start();

    /* Stop a track.
     * In static buffer mode, the track is stopped immediately.
     * In streaming mode, the callback will cease being called and
     * obtainBuffer returns STOPPED. Note that obtainBuffer() still works
     * and will fill up buffers until the pool is exhausted.
     * The stop does not occur immediately: any data remaining in the buffer
     * is first drained, mixed, and output, and only then is the track marked as stopped.
     */
            void        stop();
            bool        stopped() const;

    /* Flush a stopped or paused track. All previously buffered data is discarded immediately.
     * This has the effect of draining the buffers without mixing or output.
     * Flush is intended for streaming mode, for example before switching to non-contiguous content.
     * This function is a no-op if the track is not stopped or paused, or uses a static buffer.
     */
            void        flush();

    /* Pause a track. After pause, the callback will cease being called and
     * obtainBuffer returns STOPPED. Note that obtainBuffer() still works
     * and will fill up buffers until the pool is exhausted.
     * Volume is ramped down over the next mix buffer following the pause request,
     * and then the track is marked as paused.  It can be resumed with ramp up by start().
     */
            void        pause();

    /* Set volume for this track, mostly used for games' sound effects
     * left and right volumes. Levels must be >= 0.0 and <= 1.0.
     * This is the older API.  New applications should use setVolume(float) when possible.
     */
            status_t    setVolume(float left, float right);

    /* Set volume for all channels.  This is the preferred API for new applications,
     * especially for multi-channel content.
     */
            status_t    setVolume(float volume);

    /* Set the send level for this track. An auxiliary effect should be attached
     * to the track with attachEffect(). Level must be >= 0.0 and <= 1.0.
     */
            status_t    setAuxEffectSendLevel(float level);
            void        getAuxEffectSendLevel(float* level) const;

    /* Set sample rate for this track in Hz, mostly used for games' sound effects
     */
            status_t    setSampleRate(uint32_t sampleRate);

    /* Return current sample rate in Hz, or 0 if unknown */
            uint32_t    getSampleRate() const;

    /* Enables looping and sets the start and end points of looping.
     * Only supported for static buffer mode.
     *
     * Parameters:
     *
     * loopStart:   loop start expressed as the number of PCM frames played since AudioTrack start.
     * loopEnd:     loop end expressed as the number of PCM frames played since AudioTrack start.
     * loopCount:   number of loops to execute. Calling setLoop() with loopCount == 0 cancels any
     *              pending or active loop. loopCount = -1 means infinite looping.
     *
     * For proper operation the following condition must be respected:
     *          (loopEnd-loopStart) <= framecount()
     */
            status_t    setLoop(uint32_t loopStart, uint32_t loopEnd, int loopCount);

    /* Sets marker position. When playback reaches the number of frames specified, a callback with
     * event type EVENT_MARKER is called. Calling setMarkerPosition with marker == 0 cancels marker
     * notification callback.  To set a marker at a position which would compute as 0,
     * a workaround is to the set the marker at a nearby position such as -1 or 1.
     * If the AudioTrack has been opened with no callback function associated, the operation will
     * fail.
     *
     * Parameters:
     *
     * marker:   marker position expressed in wrapping (overflow) frame units,
     *           like the return value of getPosition().
     *
     * Returned status (from utils/Errors.h) can be:
     *  - NO_ERROR: successful operation
     *  - INVALID_OPERATION: the AudioTrack has no callback installed.
     */
            status_t    setMarkerPosition(uint32_t marker);
            status_t    getMarkerPosition(uint32_t *marker) const;

    /* Sets position update period. Every time the number of frames specified has been played,
     * a callback with event type EVENT_NEW_POS is called.
     * Calling setPositionUpdatePeriod with updatePeriod == 0 cancels new position notification
     * callback.
     * If the AudioTrack has been opened with no callback function associated, the operation will
     * fail.
     * Extremely small values may be rounded up to a value the implementation can support.
     *
     * Parameters:
     *
     * updatePeriod:  position update notification period expressed in frames.
     *
     * Returned status (from utils/Errors.h) can be:
     *  - NO_ERROR: successful operation
     *  - INVALID_OPERATION: the AudioTrack has no callback installed.
     */
            status_t    setPositionUpdatePeriod(uint32_t updatePeriod);
            status_t    getPositionUpdatePeriod(uint32_t *updatePeriod) const;

    /* Sets playback head position within AudioTrack buffer. The new position is specified
     * in number of frames.
     * This method must be called with the AudioTrack in paused or stopped state.
     * Note that the actual position set is <position> modulo the AudioTrack buffer size in frames.
     * Therefore using this method makes sense only when playing a "static" audio buffer
     * as opposed to streaming.
     * The getPosition() method on the other hand returns the total number of frames played since
     * playback start.
     *
     * Parameters:
     *
     * position:  New playback head position within AudioTrack buffer.
     *
     * Returned status (from utils/Errors.h) can be:
     *  - NO_ERROR: successful operation
     *  - INVALID_OPERATION: the AudioTrack is not stopped or paused, or is streaming mode.
     *  - BAD_VALUE: The specified position is beyond the number of frames present in AudioTrack
     *               buffer
     */
            status_t    setPosition(uint32_t position);

    /* Return the total number of frames played since playback start.
     * The counter will wrap (overflow) periodically, e.g. every ~27 hours at 44.1 kHz.
     * It is reset to zero by flush(), reload(), and stop().
     */
            status_t    getPosition(uint32_t *position);

    /* Forces AudioTrack buffer full condition. When playing a static buffer, this method avoids
     * rewriting the buffer before restarting playback after a stop.
     * This method must be called with the AudioTrack in paused or stopped state.
     * Not allowed in streaming mode.
     *
     * Returned status (from utils/Errors.h) can be:
     *  - NO_ERROR: successful operation
     *  - INVALID_OPERATION: the AudioTrack is not stopped or paused, or is streaming mode.
     */
            status_t    reload();

    /* Returns a handle on the audio output used by this AudioTrack.
     *
     * Parameters:
     *  none.
     *
     * Returned value:
     *  handle on audio hardware output
     */
            audio_io_handle_t    getOutput();

    /* Returns the unique session ID associated with this track.
     *
     * Parameters:
     *  none.
     *
     * Returned value:
     *  AudioTrack session ID.
     */
            int    getSessionId() const { return mSessionId; }

    /* Attach track auxiliary output to specified effect. Use effectId = 0
     * to detach track from effect.
     *
     * Parameters:
     *
     * effectId:  effectId obtained from AudioEffect::id().
     *
     * Returned status (from utils/Errors.h) can be:
     *  - NO_ERROR: successful operation
     *  - INVALID_OPERATION: the effect is not an auxiliary effect.
     *  - BAD_VALUE: The specified effect ID is invalid
     */
            status_t    attachAuxEffect(int effectId);

    /* Obtains a buffer of "frameCount" frames. The buffer must be
     * filled entirely, and then released with releaseBuffer().
     * If the track is stopped, obtainBuffer() returns
     * STOPPED instead of NO_ERROR as long as there are buffers available,
     * at which point NO_MORE_BUFFERS is returned.
     * Buffers will be returned until the pool
     * is exhausted, at which point obtainBuffer() will either block
     * or return WOULD_BLOCK depending on the value of the "blocking"
     * parameter.
     *
     * obtainBuffer() and releaseBuffer() are deprecated for direct use by applications,
     * which should use write() or callback EVENT_MORE_DATA instead.
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

        enum {
            NO_MORE_BUFFERS = 0x80000001,   // same name in AudioFlinger.h, ok to be different value
            STOPPED = 1
        };

            status_t    obtainBuffer(Buffer* audioBuffer, int32_t waitCount);

    /* Release a filled buffer of "frameCount" frames for AudioFlinger to process. */
            void        releaseBuffer(Buffer* audioBuffer);

    /* As a convenience we provide a write() interface to the audio buffer.
     * This is implemented on top of obtainBuffer/releaseBuffer. For best
     * performance use callbacks. Returns actual number of bytes written >= 0,
     * or one of the following negative status codes:
     *      INVALID_OPERATION   AudioTrack is configured for shared buffer mode
     *      BAD_VALUE           size is invalid
     *      STOPPED             AudioTrack was stopped during the write
     *      NO_MORE_BUFFERS     when obtainBuffer() returns same
     *      or any other error code returned by IAudioTrack::start() or restoreTrack_l().
     * Not supported for static buffer mode.
     */
            ssize_t     write(const void* buffer, size_t size);

    /*
     * Dumps the state of an audio track.
     */
            status_t dump(int fd, const Vector<String16>& args) const;

protected:
    /* copying audio tracks is not allowed */
                        AudioTrack(const AudioTrack& other);
            AudioTrack& operator = (const AudioTrack& other);

    /* a small internal class to handle the callback */
    class AudioTrackThread : public Thread
    {
    public:
        AudioTrackThread(AudioTrack& receiver, bool bCanCallJava = false);

        // Do not call Thread::requestExitAndWait() without first calling requestExit().
        // Thread::requestExitAndWait() is not virtual, and the implementation doesn't do enough.
        virtual void        requestExit();

                void        pause();    // suspend thread from execution at next loop boundary
                void        resume();   // allow thread to execute, if not requested to exit

    private:
        friend class AudioTrack;
        virtual bool        threadLoop();
        AudioTrack& mReceiver;
        ~AudioTrackThread();
        Mutex               mMyLock;    // Thread::mLock is private
        Condition           mMyCond;    // Thread::mThreadExitedCondition is private
        bool                mPaused;    // whether thread is currently paused
    };

            // body of AudioTrackThread::threadLoop()
            bool processAudioBuffer(const sp<AudioTrackThread>& thread);

            // caller must hold lock on mLock for all _l methods
            status_t createTrack_l(audio_stream_type_t streamType,
                                 uint32_t sampleRate,
                                 audio_format_t format,
                                 size_t frameCount,
                                 audio_output_flags_t flags,
                                 const sp<IMemory>& sharedBuffer,
                                 audio_io_handle_t output);

            // can only be called when !mActive
            void flush_l();

            status_t setLoop_l(uint32_t loopStart, uint32_t loopEnd, int loopCount);
            audio_io_handle_t getOutput_l();
            status_t restoreTrack_l(audio_track_cblk_t*& cblk, bool fromStart);
            bool stopped_l() const { return !mActive; }

    sp<IAudioTrack>         mAudioTrack;
    sp<IMemory>             mCblkMemory;
    sp<AudioTrackThread>    mAudioTrackThread;

    float                   mVolume[2];
    float                   mSendLevel;
    uint32_t                mSampleRate;
    size_t                  mFrameCount;            // corresponds to current IAudioTrack
    size_t                  mReqFrameCount;         // frame count to request the next time a new
                                                    // IAudioTrack is needed

    audio_track_cblk_t*     mCblk;                  // re-load after mLock.unlock()

            // Starting address of buffers in shared memory.  If there is a shared buffer, mBuffers
            // is the value of pointer() for the shared buffer, otherwise mBuffers points
            // immediately after the control block.  This address is for the mapping within client
            // address space.  AudioFlinger::TrackBase::mBuffer is for the server address space.
    void*                   mBuffers;

    audio_format_t          mFormat;                // as requested by client, not forced to 16-bit
    audio_stream_type_t     mStreamType;
    uint32_t                mChannelCount;
    audio_channel_mask_t    mChannelMask;

                // mFrameSize is equal to mFrameSizeAF for non-PCM or 16-bit PCM data.
                // For 8-bit PCM data, mFrameSizeAF is
                // twice as large because data is expanded to 16-bit before being stored in buffer.
    size_t                  mFrameSize;             // app-level frame size
    size_t                  mFrameSizeAF;           // AudioFlinger frame size

    status_t                mStatus;
    uint32_t                mLatency;

    bool                    mActive;                // protected by mLock

    callback_t              mCbf;                   // callback handler for events, or NULL
    void*                   mUserData;              // for client callback handler

    // for notification APIs
    uint32_t                mNotificationFramesReq; // requested number of frames between each
                                                    // notification callback
    uint32_t                mNotificationFramesAct; // actual number of frames between each
                                                    // notification callback
    sp<IMemory>             mSharedBuffer;
    int                     mLoopCount;
    uint32_t                mRemainingFrames;
    uint32_t                mMarkerPosition;        // in wrapping (overflow) frame units
    bool                    mMarkerReached;
    uint32_t                mNewPosition;           // in frames
    uint32_t                mUpdatePeriod;          // in frames

    bool                    mFlushed; // FIXME will be made obsolete by making flush() synchronous
    audio_output_flags_t    mFlags;
    int                     mSessionId;
    int                     mAuxEffectId;

    // When locking both mLock and mCblk->lock, must lock in this order to avoid deadlock:
    //      1. mLock
    //      2. mCblk->lock
    // It is OK to lock only mCblk->lock.
    mutable Mutex           mLock;

    bool                    mIsTimed;
    int                     mPreviousPriority;          // before start()
    SchedPolicy             mPreviousSchedulingGroup;
    AudioTrackClientProxy*  mProxy;
    bool                    mAwaitBoost;    // thread should wait for priority boost before running
};

class TimedAudioTrack : public AudioTrack
{
public:
    TimedAudioTrack();

    /* allocate a shared memory buffer that can be passed to queueTimedBuffer */
    status_t allocateTimedBuffer(size_t size, sp<IMemory>* buffer);

    /* queue a buffer obtained via allocateTimedBuffer for playback at the
       given timestamp.  PTS units are microseconds on the media time timeline.
       The media time transform (set with setMediaTimeTransform) set by the
       audio producer will handle converting from media time to local time
       (perhaps going through the common time timeline in the case of
       synchronized multiroom audio case) */
    status_t queueTimedBuffer(const sp<IMemory>& buffer, int64_t pts);

    /* define a transform between media time and either common time or
       local time */
    enum TargetTimeline {LOCAL_TIME, COMMON_TIME};
    status_t setMediaTimeTransform(const LinearTransform& xform,
                                   TargetTimeline target);
};

}; // namespace android

#endif // ANDROID_AUDIOTRACK_H
