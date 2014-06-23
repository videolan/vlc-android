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

#include <cutils/sched_policy.h>
#include <media/AudioSystem.h>
#include <media/AudioTimestamp.h>
#include <media/IAudioTrack.h>
#include <utils/threads.h>

namespace android {

// ----------------------------------------------------------------------------

class audio_track_cblk_t;
class AudioTrackClientProxy;
class StaticAudioTrackClientProxy;

// ----------------------------------------------------------------------------

class AudioTrack : public RefBase
{
public:
    enum channel_index {
        MONO   = 0,
        LEFT   = 0,
        RIGHT  = 1
    };

    /* Events used by AudioTrack callback function (callback_t).
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
        EVENT_BUFFER_END = 5,       // Playback head is at the end of the buffer.
                                    // Not currently used by android.media.AudioTrack.
        EVENT_NEW_IAUDIOTRACK = 6,  // IAudioTrack was re-created, either due to re-routing and
                                    // voluntary invalidation by mediaserver, or mediaserver crash.
        EVENT_STREAM_END = 7,       // Sent after all the buffers queued in AF and HW are played
                                    // back (after stop is called)
        EVENT_NEW_TIMESTAMP = 8,    // Delivered periodically and when there's a significant change
                                    // in the mapping from frame position to presentation time.
                                    // See AudioTimestamp for the information included with event.
    };

    /* Client should declare Buffer on the stack and pass address to obtainBuffer()
     * and releaseBuffer().  See also callback_t for EVENT_MORE_DATA.
     */

    class Buffer
    {
    public:
        // FIXME use m prefix
        size_t      frameCount;   // number of sample frames corresponding to size;
                                  // on input it is the number of frames desired,
                                  // on output is the number of frames actually filled
                                  // (currently ignored, but will make the primary field in future)

        size_t      size;         // input/output in bytes == frameCount * frameSize
                                  // on output is the number of bytes actually filled
                                  // FIXME this is redundant with respect to frameCount,
                                  // and TRANSFER_OBTAIN mode is broken for 8-bit data
                                  // since we don't define the frame format

        union {
            void*       raw;
            short*      i16;      // signed 16-bit
            int8_t*     i8;       // unsigned 8-bit, offset by 0x80
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
     *          - EVENT_MARKER: pointer to const uint32_t containing the marker position in frames.
     *          - EVENT_NEW_POS: pointer to const uint32_t containing the new position in frames.
     *          - EVENT_BUFFER_END: unused.
     *          - EVENT_NEW_IAUDIOTRACK: unused.
     *          - EVENT_STREAM_END: unused.
     *          - EVENT_NEW_TIMESTAMP: pointer to const AudioTimestamp.
     */

    typedef void (*callback_t)(int event, void* user, void *info);

    /* Returns the minimum frame count required for the successful creation of
     * an AudioTrack object.
     * Returned status (from utils/Errors.h) can be:
     *  - NO_ERROR: successful operation
     *  - NO_INIT: audio server or audio hardware not initialized
     *  - BAD_VALUE: unsupported configuration
     */

    static status_t getMinFrameCount(size_t* frameCount,
                                     audio_stream_type_t streamType,
                                     uint32_t sampleRate);

    /* How data is transferred to AudioTrack
     */
    enum transfer_type {
        TRANSFER_DEFAULT,   // not specified explicitly; determine from the other parameters
        TRANSFER_CALLBACK,  // callback EVENT_MORE_DATA
        TRANSFER_OBTAIN,    // FIXME deprecated: call obtainBuffer() and releaseBuffer()
        TRANSFER_SYNC,      // synchronous write()
        TRANSFER_SHARED,    // shared memory
    };

    /* Constructs an uninitialized AudioTrack. No connection with
     * AudioFlinger takes place.  Use set() after this.
     */
                        AudioTrack();

    /* Creates an AudioTrack object and registers it with AudioFlinger.
     * Once created, the track needs to be started before it can be used.
     * Unspecified values are set to appropriate default values.
     * With this constructor, the track is configured for streaming mode.
     * Data to be rendered is supplied by write() or by the callback EVENT_MORE_DATA.
     * Intermixing a combination of write() and non-ignored EVENT_MORE_DATA is not allowed.
     *
     * Parameters:
     *
     * streamType:         Select the type of audio stream this track is attached to
     *                     (e.g. AUDIO_STREAM_MUSIC).
     * sampleRate:         Data source sampling rate in Hz.
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
     *                     This is expressed in units of frames at the initial source sample rate.
     * sessionId:          Specific session ID, or zero to use default.
     * transferType:       How data is transferred to AudioTrack.
     * threadCanCallJava:  Not present in parameter list, and so is fixed at false.
     */

                        AudioTrack( audio_stream_type_t streamType,
                                    uint32_t sampleRate,
                                    audio_format_t format,
                                    audio_channel_mask_t,
                                    int frameCount       = 0,
                                    audio_output_flags_t flags = AUDIO_OUTPUT_FLAG_NONE,
                                    callback_t cbf       = NULL,
                                    void* user           = NULL,
                                    int notificationFrames = 0,
                                    int sessionId        = 0,
                                    transfer_type transferType = TRANSFER_DEFAULT,
                                    const audio_offload_info_t *offloadInfo = NULL);

    /* Creates an audio track and registers it with AudioFlinger.
     * With this constructor, the track is configured for static buffer mode.
     * The format must not be 8-bit linear PCM.
     * Data to be rendered is passed in a shared memory buffer
     * identified by the argument sharedBuffer, which must be non-0.
     * The memory should be initialized to the desired data before calling start().
     * The write() method is not supported in this case.
     * It is recommended to pass a callback function to be notified of playback end by an
     * EVENT_UNDERRUN event.
     */

                        AudioTrack( audio_stream_type_t streamType,
                                    uint32_t sampleRate,
                                    audio_format_t format,
                                    audio_channel_mask_t channelMask,
                                    const sp<IMemory>& sharedBuffer,
                                    audio_output_flags_t flags = AUDIO_OUTPUT_FLAG_NONE,
                                    callback_t cbf      = NULL,
                                    void* user          = NULL,
                                    int notificationFrames = 0,
                                    int sessionId       = 0,
                                    transfer_type transferType = TRANSFER_DEFAULT,
                                    const audio_offload_info_t *offloadInfo = NULL);

    /* Terminates the AudioTrack and unregisters it from AudioFlinger.
     * Also destroys all resources associated with the AudioTrack.
     */
protected:
                        virtual ~AudioTrack();
public:

    /* Initialize an AudioTrack that was created using the AudioTrack() constructor.
     * Don't call set() more than once, or after the AudioTrack() constructors that take parameters.
     * Returned status (from utils/Errors.h) can be:
     *  - NO_ERROR: successful initialization
     *  - INVALID_OPERATION: AudioTrack is already initialized
     *  - BAD_VALUE: invalid parameter (channelMask, format, sampleRate...)
     *  - NO_INIT: audio server or audio hardware not initialized
     * If status is not equal to NO_ERROR, don't call any other APIs on this AudioTrack.
     * If sharedBuffer is non-0, the frameCount parameter is ignored and
     * replaced by the shared buffer's total allocated size in frame units.
     *
     * Parameters not listed in the AudioTrack constructors above:
     *
     * threadCanCallJava:  Whether callbacks are made from an attached thread and thus can call JNI.
     */
            status_t    set(audio_stream_type_t streamType,
                            uint32_t sampleRate,
                            audio_format_t format,
                            audio_channel_mask_t channelMask,
                            int frameCount      = 0,
                            audio_output_flags_t flags = AUDIO_OUTPUT_FLAG_NONE,
                            callback_t cbf      = NULL,
                            void* user          = NULL,
                            int notificationFrames = 0,
                            const sp<IMemory>& sharedBuffer = 0,
                            bool threadCanCallJava = false,
                            int sessionId       = 0,
                            transfer_type transferType = TRANSFER_DEFAULT,
                            const audio_offload_info_t *offloadInfo = NULL);

    /* Result of constructing the AudioTrack. This must be checked for successful initialization
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

    /* Return frame size in bytes, which for linear PCM is
     * channelCount * (bit depth per channel / 8).
     * channelCount is determined from channelMask, and bit depth comes from format.
     * For non-linear formats, the frame size is typically 1 byte.
     */
            size_t      frameSize() const   { return mFrameSize; }

            uint32_t    channelCount() const { return mChannelCount; }
            uint32_t    frameCount() const  { return mFrameCount; }

    /* Return the static buffer specified in constructor or set(), or 0 for streaming mode */
            sp<IMemory> sharedBuffer() const { return mSharedBuffer; }

    /* After it's created the track is not active. Call start() to
     * make it active. If set, the callback will start being called.
     * If the track was previously paused, volume is ramped up over the first mix buffer.
     */
            status_t        start();

    /* Stop a track.
     * In static buffer mode, the track is stopped immediately.
     * In streaming mode, the callback will cease being called.  Note that obtainBuffer() still
     * works and will fill up buffers until the pool is exhausted, and then will return WOULD_BLOCK.
     * In streaming mode the stop does not occur immediately: any data remaining in the buffer
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
     * obtainBuffer returns WOULD_BLOCK. Note that obtainBuffer() still works
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

    /* Set source sample rate for this track in Hz, mostly used for games' sound effects
     */
            status_t    setSampleRate(uint32_t sampleRate);

    /* Return current source sample rate in Hz, or 0 if unknown */
            uint32_t    getSampleRate() const;

    /* Enables looping and sets the start and end points of looping.
     * Only supported for static buffer mode.
     *
     * Parameters:
     *
     * loopStart:   loop start in frames relative to start of buffer.
     * loopEnd:     loop end in frames relative to start of buffer.
     * loopCount:   number of loops to execute. Calling setLoop() with loopCount == 0 cancels any
     *              pending or active loop. loopCount == -1 means infinite looping.
     *
     * For proper operation the following condition must be respected:
     *      loopCount != 0 implies 0 <= loopStart < loopEnd <= frameCount().
     *
     * If the loop period (loopEnd - loopStart) is too small for the implementation to support,
     * setLoop() will return BAD_VALUE.  loopCount must be >= -1.
     *
     */
            status_t    setLoop(uint32_t loopStart, uint32_t loopEnd, int loopCount);

    /* Sets marker position. When playback reaches the number of frames specified, a callback with
     * event type EVENT_MARKER is called. Calling setMarkerPosition with marker == 0 cancels marker
     * notification callback.  To set a marker at a position which would compute as 0,
     * a workaround is to the set the marker at a nearby position such as ~0 or 1.
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

    /* Sets playback head position.
     * Only supported for static buffer mode.
     *
     * Parameters:
     *
     * position:  New playback head position in frames relative to start of buffer.
     *            0 <= position <= frameCount().  Note that end of buffer is permitted,
     *            but will result in an immediate underrun if started.
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
     *
     * Parameters:
     *
     *  position:  Address where to return play head position.
     *
     * Returned status (from utils/Errors.h) can be:
     *  - NO_ERROR: successful operation
     *  - BAD_VALUE:  position is NULL
     */
            status_t    getPosition(uint32_t *position) const;

    /* For static buffer mode only, this returns the current playback position in frames
     * relative to start of buffer.  It is analogous to the position units used by
     * setLoop() and setPosition().  After underrun, the position will be at end of buffer.
     */
            status_t    getBufferPosition(uint32_t *position);

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

    /* Obtains a buffer of up to "audioBuffer->frameCount" empty slots for frames.
     * After filling these slots with data, the caller should release them with releaseBuffer().
     * If the track buffer is not full, obtainBuffer() returns as many contiguous
     * [empty slots for] frames as are available immediately.
     * If the track buffer is full and track is stopped, obtainBuffer() returns WOULD_BLOCK
     * regardless of the value of waitCount.
     * If the track buffer is full and track is not stopped, obtainBuffer() blocks with a
     * maximum timeout based on waitCount; see chart below.
     * Buffers will be returned until the pool
     * is exhausted, at which point obtainBuffer() will either block
     * or return WOULD_BLOCK depending on the value of the "waitCount"
     * parameter.
     * Each sample is 16-bit signed PCM.
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

//EL_FIXME to be reconciled with new obtainBuffer() return codes and control block proxy
//            enum {
//            NO_MORE_BUFFERS = 0x80000001,   // same name in AudioFlinger.h, ok to be different value
//            TEAR_DOWN       = 0x80000002,
//            STOPPED = 1,
//            STREAM_END_WAIT,
//            STREAM_END
//        };

    /* Release a filled buffer of "audioBuffer->frameCount" frames for AudioFlinger to process. */
    // FIXME make private when obtainBuffer() for TRANSFER_OBTAIN is removed
            void        releaseBuffer(Buffer* audioBuffer);

    /* As a convenience we provide a write() interface to the audio buffer.
     * Input parameter 'size' is in byte units.
     * This is implemented on top of obtainBuffer/releaseBuffer. For best
     * performance use callbacks. Returns actual number of bytes written >= 0,
     * or one of the following negative status codes:
     *      INVALID_OPERATION   AudioTrack is configured for static buffer or streaming mode
     *      BAD_VALUE           size is invalid
     *      WOULD_BLOCK         when obtainBuffer() returns same, or
     *                          AudioTrack was stopped during the write
     *      or any other error code returned by IAudioTrack::start() or restoreTrack_l().
     */
            ssize_t     write(const void* buffer, size_t size);

    /*
     * Dumps the state of an audio track.
     */
            status_t    dump(int fd, const Vector<String16>& args) const;

    /*
     * Return the total number of frames which AudioFlinger desired but were unavailable,
     * and thus which resulted in an underrun.  Reset to zero by stop().
     */
            uint32_t    getUnderrunFrames() const;

    /* Get the flags */
            audio_output_flags_t getFlags() const { return mFlags; }

    /* Set parameters - only possible when using direct output */
            status_t    setParameters(const String8& keyValuePairs);

    /* Get parameters */
            String8     getParameters(const String8& keys);

    /* Poll for a timestamp on demand.
     * Use if EVENT_NEW_TIMESTAMP is not delivered often enough for your needs,
     * or if you need to get the most recent timestamp outside of the event callback handler.
     * Caution: calling this method too often may be inefficient;
     * if you need a high resolution mapping between frame position and presentation time,
     * consider implementing that at application level, based on the low resolution timestamps.
     * Returns NO_ERROR if timestamp is valid.
     */
            status_t    getTimestamp(AudioTimestamp& timestamp);

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
                void        pauseInternal(nsecs_t ns = 0LL);
                                        // like pause(), but only used internally within thread

        friend class AudioTrack;
        virtual bool        threadLoop();
        AudioTrack&         mReceiver;
        virtual ~AudioTrackThread();
        Mutex               mMyLock;    // Thread::mLock is private
        Condition           mMyCond;    // Thread::mThreadExitedCondition is private
        bool                mPaused;    // whether thread is requested to pause at next loop entry
        bool                mPausedInt; // whether thread internally requests pause
        nsecs_t             mPausedNs;  // if mPausedInt then associated timeout, otherwise ignored
        bool                mIgnoreNextPausedInt;   // whether to ignore next mPausedInt request
    };

            // body of AudioTrackThread::threadLoop()
            // returns the maximum amount of time before we would like to run again, where:
            //      0           immediately
            //      > 0         no later than this many nanoseconds from now
            //      NS_WHENEVER still active but no particular deadline
            //      NS_INACTIVE inactive so don't run again until re-started
            //      NS_NEVER    never again
            static const nsecs_t NS_WHENEVER = -1, NS_INACTIVE = -2, NS_NEVER = -3;
            nsecs_t processAudioBuffer(const sp<AudioTrackThread>& thread);
            status_t processStreamEnd(int32_t waitCount);


            // caller must hold lock on mLock for all _l methods

            status_t createTrack_l(audio_stream_type_t streamType,
                                 uint32_t sampleRate,
                                 audio_format_t format,
                                 size_t frameCount,
                                 audio_output_flags_t flags,
                                 const sp<IMemory>& sharedBuffer,
                                 audio_io_handle_t output,
                                 size_t epoch);

            // can only be called when mState != STATE_ACTIVE
            void flush_l();

            void setLoop_l(uint32_t loopStart, uint32_t loopEnd, int loopCount);
            audio_io_handle_t getOutput_l();

            // FIXME enum is faster than strcmp() for parameter 'from'
            status_t restoreTrack_l(const char *from);

            bool     isOffloaded() const
                { return (mFlags & AUDIO_OUTPUT_FLAG_COMPRESS_OFFLOAD) != 0; }

    // Next 3 fields may be changed if IAudioTrack is re-created, but always != 0
    sp<IAudioTrack>         mAudioTrack;
    sp<IMemory>             mCblkMemory;
    audio_track_cblk_t*     mCblk;                  // re-load after mLock.unlock()

    sp<AudioTrackThread>    mAudioTrackThread;
    float                   mVolume[2];
    float                   mSendLevel;
    uint32_t                mSampleRate;
    size_t                  mFrameCount;            // corresponds to current IAudioTrack
    size_t                  mReqFrameCount;         // frame count to request the next time a new
                                                    // IAudioTrack is needed


    // constant after constructor or set()
    audio_format_t          mFormat;                // as requested by client, not forced to 16-bit
    audio_stream_type_t     mStreamType;
    uint32_t                mChannelCount;
    audio_channel_mask_t    mChannelMask;
    transfer_type           mTransfer;

    // mFrameSize is equal to mFrameSizeAF for non-PCM or 16-bit PCM data.  For 8-bit PCM data, it's
    // twice as large as mFrameSize because data is expanded to 16-bit before it's stored in buffer.
    size_t                  mFrameSize;             // app-level frame size
    size_t                  mFrameSizeAF;           // AudioFlinger frame size

    status_t                mStatus;

    // can change dynamically when IAudioTrack invalidated
    uint32_t                mLatency;               // in ms

    // Indicates the current track state.  Protected by mLock.
    enum State {
        STATE_ACTIVE,
        STATE_STOPPED,
        STATE_PAUSED,
        STATE_PAUSED_STOPPING,
        STATE_FLUSHED,
        STATE_STOPPING,
    }                       mState;

    // for client callback handler
    callback_t              mCbf;                   // callback handler for events, or NULL
    void*                   mUserData;

    // for notification APIs
    uint32_t                mNotificationFramesReq; // requested number of frames between each
                                                    // notification callback,
                                                    // at initial source sample rate
    uint32_t                mNotificationFramesAct; // actual number of frames between each
                                                    // notification callback,
                                                    // at initial source sample rate
    bool                    mRefreshRemaining;      // processAudioBuffer() should refresh next 2

    // These are private to processAudioBuffer(), and are not protected by a lock
    uint32_t                mRemainingFrames;       // number of frames to request in obtainBuffer()
    bool                    mRetryOnPartialBuffer;  // sleep and retry after partial obtainBuffer()
    uint32_t                mObservedSequence;      // last observed value of mSequence

    sp<IMemory>             mSharedBuffer;
    uint32_t                mLoopPeriod;            // in frames, zero means looping is disabled
    uint32_t                mMarkerPosition;        // in wrapping (overflow) frame units
    bool                    mMarkerReached;
    uint32_t                mNewPosition;           // in frames
    uint32_t                mUpdatePeriod;          // in frames, zero means no EVENT_NEW_POS

    audio_output_flags_t    mFlags;
    int                     mSessionId;
    int                     mAuxEffectId;

    mutable Mutex           mLock;

    bool                    mIsTimed;
    int                     mPreviousPriority;          // before start()
    SchedPolicy             mPreviousSchedulingGroup;
    bool                    mAwaitBoost;    // thread should wait for priority boost before running

    // The proxy should only be referenced while a lock is held because the proxy isn't
    // multi-thread safe, especially the SingleStateQueue part of the proxy.
    // An exception is that a blocking ClientProxy::obtainBuffer() may be called without a lock,
    // provided that the caller also holds an extra reference to the proxy and shared memory to keep
    // them around in case they are replaced during the obtainBuffer().
    sp<StaticAudioTrackClientProxy> mStaticProxy;   // for type safety only
    sp<AudioTrackClientProxy>       mProxy;         // primary owner of the memory

    bool                    mInUnderrun;            // whether track is currently in underrun state
    String8                 mName;                  // server's name for this IAudioTrack

private:
    class DeathNotifier : public IBinder::DeathRecipient {
    public:
        DeathNotifier(AudioTrack* audioTrack) : mAudioTrack(audioTrack) { }
    protected:
        virtual void        binderDied(const wp<IBinder>& who);
    private:
        const wp<AudioTrack> mAudioTrack;
    };

    sp<DeathNotifier>       mDeathNotifier;
    uint32_t                mSequence;              // incremented for each new IAudioTrack attempt
    audio_io_handle_t       mOutput;                // cached output io handle
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
