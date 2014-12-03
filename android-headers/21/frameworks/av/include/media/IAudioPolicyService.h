/*
 * Copyright (C) 2009 The Android Open Source Project
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

#ifndef ANDROID_IAUDIOPOLICYSERVICE_H
#define ANDROID_IAUDIOPOLICYSERVICE_H

#include <stdint.h>
#include <sys/types.h>
#include <unistd.h>

#include <utils/RefBase.h>
#include <utils/Errors.h>
#include <binder/IInterface.h>
#include <media/AudioSystem.h>
#include <media/IAudioPolicyServiceClient.h>

#include <system/audio_policy.h>

namespace android {

// ----------------------------------------------------------------------------

class IAudioPolicyService : public IInterface
{
public:
    DECLARE_META_INTERFACE(AudioPolicyService);

    //
    // IAudioPolicyService interface (see AudioPolicyInterface for method descriptions)
    //
    virtual status_t setDeviceConnectionState(audio_devices_t device,
                                              audio_policy_dev_state_t state,
                                              const char *device_address) = 0;
    virtual audio_policy_dev_state_t getDeviceConnectionState(audio_devices_t device,
                                                                  const char *device_address) = 0;
    virtual status_t setPhoneState(audio_mode_t state) = 0;
    virtual status_t setForceUse(audio_policy_force_use_t usage,
                                    audio_policy_forced_cfg_t config) = 0;
    virtual audio_policy_forced_cfg_t getForceUse(audio_policy_force_use_t usage) = 0;
    virtual audio_io_handle_t getOutput(audio_stream_type_t stream,
                                        uint32_t samplingRate = 0,
                                        audio_format_t format = AUDIO_FORMAT_DEFAULT,
                                        audio_channel_mask_t channelMask = 0,
                                        audio_output_flags_t flags = AUDIO_OUTPUT_FLAG_NONE,
                                        const audio_offload_info_t *offloadInfo = NULL) = 0;
    virtual audio_io_handle_t getOutputForAttr(const audio_attributes_t *attr,
                                            uint32_t samplingRate = 0,
                                            audio_format_t format = AUDIO_FORMAT_DEFAULT,
                                            audio_channel_mask_t channelMask = 0,
                                            audio_output_flags_t flags = AUDIO_OUTPUT_FLAG_NONE,
                                            const audio_offload_info_t *offloadInfo = NULL) = 0;
    virtual status_t startOutput(audio_io_handle_t output,
                                 audio_stream_type_t stream,
                                 int session = 0) = 0;
    virtual status_t stopOutput(audio_io_handle_t output,
                                audio_stream_type_t stream,
                                int session = 0) = 0;
    virtual void releaseOutput(audio_io_handle_t output) = 0;
    virtual audio_io_handle_t getInput(audio_source_t inputSource,
                                    uint32_t samplingRate,
                                    audio_format_t format,
                                    audio_channel_mask_t channelMask,
                                    int audioSession,
                                    audio_input_flags_t flags) = 0;
    virtual status_t startInput(audio_io_handle_t input,
                                audio_session_t session) = 0;
    virtual status_t stopInput(audio_io_handle_t input,
                               audio_session_t session) = 0;
    virtual void releaseInput(audio_io_handle_t input,
                              audio_session_t session) = 0;
    virtual status_t initStreamVolume(audio_stream_type_t stream,
                                      int indexMin,
                                      int indexMax) = 0;
    virtual status_t setStreamVolumeIndex(audio_stream_type_t stream,
                                          int index,
                                          audio_devices_t device) = 0;
    virtual status_t getStreamVolumeIndex(audio_stream_type_t stream,
                                          int *index,
                                          audio_devices_t device) = 0;
    virtual uint32_t getStrategyForStream(audio_stream_type_t stream) = 0;
    virtual audio_devices_t getDevicesForStream(audio_stream_type_t stream) = 0;
    virtual audio_io_handle_t getOutputForEffect(const effect_descriptor_t *desc) = 0;
    virtual status_t registerEffect(const effect_descriptor_t *desc,
                                    audio_io_handle_t io,
                                    uint32_t strategy,
                                    int session,
                                    int id) = 0;
    virtual status_t unregisterEffect(int id) = 0;
    virtual status_t setEffectEnabled(int id, bool enabled) = 0;
    virtual bool     isStreamActive(audio_stream_type_t stream, uint32_t inPastMs = 0) const = 0;
    virtual bool     isStreamActiveRemotely(audio_stream_type_t stream, uint32_t inPastMs = 0)
                             const = 0;
    virtual bool     isSourceActive(audio_source_t source) const = 0;
    virtual status_t queryDefaultPreProcessing(int audioSession,
                                              effect_descriptor_t *descriptors,
                                              uint32_t *count) = 0;
   // Check if offload is possible for given format, stream type, sample rate,
    // bit rate, duration, video and streaming or offload property is enabled
    virtual bool isOffloadSupported(const audio_offload_info_t& info) = 0;

    /* List available audio ports and their attributes */
    virtual status_t listAudioPorts(audio_port_role_t role,
                                    audio_port_type_t type,
                                    unsigned int *num_ports,
                                    struct audio_port *ports,
                                    unsigned int *generation) = 0;

    /* Get attributes for a given audio port */
    virtual status_t getAudioPort(struct audio_port *port) = 0;

    /* Create an audio patch between several source and sink ports */
    virtual status_t createAudioPatch(const struct audio_patch *patch,
                                       audio_patch_handle_t *handle) = 0;

    /* Release an audio patch */
    virtual status_t releaseAudioPatch(audio_patch_handle_t handle) = 0;

    /* List existing audio patches */
    virtual status_t listAudioPatches(unsigned int *num_patches,
                                      struct audio_patch *patches,
                                      unsigned int *generation) = 0;
    /* Set audio port configuration */
    virtual status_t setAudioPortConfig(const struct audio_port_config *config) = 0;

    virtual void registerClient(const sp<IAudioPolicyServiceClient>& client) = 0;

    virtual status_t acquireSoundTriggerSession(audio_session_t *session,
                                           audio_io_handle_t *ioHandle,
                                           audio_devices_t *device) = 0;

    virtual status_t releaseSoundTriggerSession(audio_session_t session) = 0;

    virtual audio_mode_t getPhoneState() = 0;
};


// ----------------------------------------------------------------------------

class BnAudioPolicyService : public BnInterface<IAudioPolicyService>
{
public:
    virtual status_t    onTransact( uint32_t code,
                                    const Parcel& data,
                                    Parcel* reply,
                                    uint32_t flags = 0);
};

// ----------------------------------------------------------------------------

}; // namespace android

#endif // ANDROID_IAUDIOPOLICYSERVICE_H
