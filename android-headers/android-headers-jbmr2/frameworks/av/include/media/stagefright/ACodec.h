/*
 * Copyright (C) 2010 The Android Open Source Project
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

#ifndef A_CODEC_H_

#define A_CODEC_H_

#include <stdint.h>
#include <android/native_window.h>
#include <media/IOMX.h>
#include <media/stagefright/foundation/AHierarchicalStateMachine.h>
#include <media/stagefright/SkipCutBuffer.h>
#include <OMX_Audio.h>

#define TRACK_BUFFER_TIMING     0

namespace android {

struct ABuffer;
struct MemoryDealer;

struct ACodec : public AHierarchicalStateMachine {
    enum {
        kWhatFillThisBuffer      = 'fill',
        kWhatDrainThisBuffer     = 'drai',
        kWhatEOS                 = 'eos ',
        kWhatShutdownCompleted   = 'scom',
        kWhatFlushCompleted      = 'fcom',
        kWhatOutputFormatChanged = 'outC',
        kWhatError               = 'erro',
        kWhatComponentAllocated  = 'cAll',
        kWhatComponentConfigured = 'cCon',
        kWhatInputSurfaceCreated = 'isfc',
        kWhatSignaledInputEOS    = 'seos',
        kWhatBuffersAllocated    = 'allc',
        kWhatOMXDied             = 'OMXd',
    };

    ACodec();

    void setNotificationMessage(const sp<AMessage> &msg);
    void initiateSetup(const sp<AMessage> &msg);
    void signalFlush();
    void signalResume();
    void initiateShutdown(bool keepComponentAllocated = false);

    void signalSetParameters(const sp<AMessage> &msg);
    void signalEndOfInputStream();

    void initiateAllocateComponent(const sp<AMessage> &msg);
    void initiateConfigureComponent(const sp<AMessage> &msg);
    void initiateCreateInputSurface();
    void initiateStart();

    void signalRequestIDRFrame();

    struct PortDescription : public RefBase {
        size_t countBuffers();
        IOMX::buffer_id bufferIDAt(size_t index) const;
        sp<ABuffer> bufferAt(size_t index) const;

    private:
        friend struct ACodec;

        Vector<IOMX::buffer_id> mBufferIDs;
        Vector<sp<ABuffer> > mBuffers;

        PortDescription();
        void addBuffer(IOMX::buffer_id id, const sp<ABuffer> &buffer);

        DISALLOW_EVIL_CONSTRUCTORS(PortDescription);
    };

protected:
    virtual ~ACodec();

private:
    struct BaseState;
    struct UninitializedState;
    struct LoadedState;
    struct LoadedToIdleState;
    struct IdleToExecutingState;
    struct ExecutingState;
    struct OutputPortSettingsChangedState;
    struct ExecutingToIdleState;
    struct IdleToLoadedState;
    struct FlushingState;
    struct DeathNotifier;

    enum {
        kWhatSetup                   = 'setu',
        kWhatOMXMessage              = 'omx ',
        kWhatInputBufferFilled       = 'inpF',
        kWhatOutputBufferDrained     = 'outD',
        kWhatShutdown                = 'shut',
        kWhatFlush                   = 'flus',
        kWhatResume                  = 'resm',
        kWhatDrainDeferredMessages   = 'drai',
        kWhatAllocateComponent       = 'allo',
        kWhatConfigureComponent      = 'conf',
        kWhatCreateInputSurface      = 'cisf',
        kWhatSignalEndOfInputStream  = 'eois',
        kWhatStart                   = 'star',
        kWhatRequestIDRFrame         = 'ridr',
        kWhatSetParameters           = 'setP',
    };

    enum {
        kPortIndexInput  = 0,
        kPortIndexOutput = 1
    };

    enum {
        kFlagIsSecure   = 1,
    };

    struct BufferInfo {
        enum Status {
            OWNED_BY_US,
            OWNED_BY_COMPONENT,
            OWNED_BY_UPSTREAM,
            OWNED_BY_DOWNSTREAM,
            OWNED_BY_NATIVE_WINDOW,
        };

        IOMX::buffer_id mBufferID;
        Status mStatus;

        sp<ABuffer> mData;
        sp<GraphicBuffer> mGraphicBuffer;
    };

#if TRACK_BUFFER_TIMING
    struct BufferStats {
        int64_t mEmptyBufferTimeUs;
        int64_t mFillBufferDoneTimeUs;
    };

    KeyedVector<int64_t, BufferStats> mBufferStats;
#endif

    sp<AMessage> mNotify;

    sp<UninitializedState> mUninitializedState;
    sp<LoadedState> mLoadedState;
    sp<LoadedToIdleState> mLoadedToIdleState;
    sp<IdleToExecutingState> mIdleToExecutingState;
    sp<ExecutingState> mExecutingState;
    sp<OutputPortSettingsChangedState> mOutputPortSettingsChangedState;
    sp<ExecutingToIdleState> mExecutingToIdleState;
    sp<IdleToLoadedState> mIdleToLoadedState;
    sp<FlushingState> mFlushingState;
    sp<SkipCutBuffer> mSkipCutBuffer;

    AString mComponentName;
    uint32_t mFlags;
    uint32_t mQuirks;
    sp<IOMX> mOMX;
    IOMX::node_id mNode;
    sp<MemoryDealer> mDealer[2];

    sp<ANativeWindow> mNativeWindow;

    Vector<BufferInfo> mBuffers[2];
    bool mPortEOS[2];
    status_t mInputEOSResult;

    List<sp<AMessage> > mDeferredQueue;

    bool mSentFormat;
    bool mIsEncoder;

    bool mShutdownInProgress;

    // If "mKeepComponentAllocated" we only transition back to Loaded state
    // and do not release the component instance.
    bool mKeepComponentAllocated;

    int32_t mEncoderDelay;
    int32_t mEncoderPadding;

    bool mChannelMaskPresent;
    int32_t mChannelMask;

    status_t setCyclicIntraMacroblockRefresh(const sp<AMessage> &msg, int32_t mode);
    status_t allocateBuffersOnPort(OMX_U32 portIndex);
    status_t freeBuffersOnPort(OMX_U32 portIndex);
    status_t freeBuffer(OMX_U32 portIndex, size_t i);

    status_t allocateOutputBuffersFromNativeWindow();
    status_t cancelBufferToNativeWindow(BufferInfo *info);
    status_t freeOutputBuffersNotOwnedByComponent();
    BufferInfo *dequeueBufferFromNativeWindow();

    BufferInfo *findBufferByID(
            uint32_t portIndex, IOMX::buffer_id bufferID,
            ssize_t *index = NULL);

    status_t setComponentRole(bool isEncoder, const char *mime);
    status_t configureCodec(const char *mime, const sp<AMessage> &msg);

    status_t setVideoPortFormatType(
            OMX_U32 portIndex,
            OMX_VIDEO_CODINGTYPE compressionFormat,
            OMX_COLOR_FORMATTYPE colorFormat);

    status_t setSupportedOutputFormat();

    status_t setupVideoDecoder(
            const char *mime, int32_t width, int32_t height);

    status_t setupVideoEncoder(
            const char *mime, const sp<AMessage> &msg);

    status_t setVideoFormatOnPort(
            OMX_U32 portIndex,
            int32_t width, int32_t height,
            OMX_VIDEO_CODINGTYPE compressionFormat);

    status_t setupAACCodec(
            bool encoder,
            int32_t numChannels, int32_t sampleRate, int32_t bitRate,
            int32_t aacProfile, bool isADTS);

    status_t selectAudioPortFormat(
            OMX_U32 portIndex, OMX_AUDIO_CODINGTYPE desiredFormat);

    status_t setupAMRCodec(bool encoder, bool isWAMR, int32_t bitRate);
    status_t setupG711Codec(bool encoder, int32_t numChannels);

    status_t setupFlacCodec(
            bool encoder, int32_t numChannels, int32_t sampleRate, int32_t compressionLevel);

    status_t setupRawAudioFormat(
            OMX_U32 portIndex, int32_t sampleRate, int32_t numChannels);

    status_t setMinBufferSize(OMX_U32 portIndex, size_t size);

    status_t setupMPEG4EncoderParameters(const sp<AMessage> &msg);
    status_t setupH263EncoderParameters(const sp<AMessage> &msg);
    status_t setupAVCEncoderParameters(const sp<AMessage> &msg);

    status_t verifySupportForProfileAndLevel(int32_t profile, int32_t level);

    status_t configureBitrate(
            int32_t bitrate, OMX_VIDEO_CONTROLRATETYPE bitrateMode);

    status_t setupErrorCorrectionParameters();

    status_t initNativeWindow();

    status_t pushBlankBuffersToNativeWindow();

    // Returns true iff all buffers on the given port have status
    // OWNED_BY_US or OWNED_BY_NATIVE_WINDOW.
    bool allYourBuffersAreBelongToUs(OMX_U32 portIndex);

    bool allYourBuffersAreBelongToUs();

    void waitUntilAllPossibleNativeWindowBuffersAreReturnedToUs();

    size_t countBuffersOwnedByComponent(OMX_U32 portIndex) const;
    size_t countBuffersOwnedByNativeWindow() const;

    void deferMessage(const sp<AMessage> &msg);
    void processDeferredMessages();

    void sendFormatChange();

    void signalError(
            OMX_ERRORTYPE error = OMX_ErrorUndefined,
            status_t internalError = UNKNOWN_ERROR);

    status_t requestIDRFrame();
    status_t setParameters(const sp<AMessage> &params);

    // Send EOS on input stream.
    void onSignalEndOfInputStream();

    DISALLOW_EVIL_CONSTRUCTORS(ACodec);
};

}  // namespace android

#endif  // A_CODEC_H_
