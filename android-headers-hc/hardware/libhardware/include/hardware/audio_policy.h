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


#ifndef ANDROID_AUDIO_POLICY_CORE_H
#define ANDROID_AUDIO_POLICY_CORE_H

#include <stdint.h>
#include <sys/cdefs.h>
#include <sys/types.h>

#include <cutils/bitops.h>

__BEGIN_DECLS

/* The enums were moved here mostly from
 * frameworks/base/include/media/AudioSystem.h
 */

/* request to open a direct output with get_output() (by opposition to
 * sharing an output with other AudioTracks)
 */
typedef enum {
    AUDIO_POLICY_OUTPUT_FLAG_INDIRECT = 0x0,
    AUDIO_POLICY_OUTPUT_FLAG_DIRECT = 0x1
} audio_policy_output_flags_t;

/* device categories used for audio_policy->set_force_use() */
typedef enum {
    AUDIO_POLICY_FORCE_NONE,
    AUDIO_POLICY_FORCE_SPEAKER,
    AUDIO_POLICY_FORCE_HEADPHONES,
    AUDIO_POLICY_FORCE_BT_SCO,
    AUDIO_POLICY_FORCE_BT_A2DP,
    AUDIO_POLICY_FORCE_WIRED_ACCESSORY,
    AUDIO_POLICY_FORCE_BT_CAR_DOCK,
    AUDIO_POLICY_FORCE_BT_DESK_DOCK,
    AUDIO_POLICY_FORCE_ANALOG_DOCK,
    AUDIO_POLICY_FORCE_DIGITAL_DOCK,

    AUDIO_POLICY_FORCE_CFG_CNT,
    AUDIO_POLICY_FORCE_CFG_MAX = AUDIO_POLICY_FORCE_CFG_CNT - 1,

    AUDIO_POLICY_FORCE_DEFAULT = AUDIO_POLICY_FORCE_NONE,
} audio_policy_forced_cfg_t;

/* usages used for audio_policy->set_force_use() */
typedef enum {
    AUDIO_POLICY_FORCE_FOR_COMMUNICATION,
    AUDIO_POLICY_FORCE_FOR_MEDIA,
    AUDIO_POLICY_FORCE_FOR_RECORD,
    AUDIO_POLICY_FORCE_FOR_DOCK,

    AUDIO_POLICY_FORCE_USE_CNT,
    AUDIO_POLICY_FORCE_USE_MAX = AUDIO_POLICY_FORCE_USE_CNT - 1,
} audio_policy_force_use_t;

/* device connection states used for audio_policy->set_device_connection_state()
 */
typedef enum {
    AUDIO_POLICY_DEVICE_STATE_UNAVAILABLE,
    AUDIO_POLICY_DEVICE_STATE_AVAILABLE,

    AUDIO_POLICY_DEVICE_STATE_CNT,
    AUDIO_POLICY_DEVICE_STATE_MAX = AUDIO_POLICY_DEVICE_STATE_CNT - 1,
} audio_policy_dev_state_t;

typedef enum {
    /* Used to generate a tone to notify the user of a
     * notification/alarm/ringtone while they are in a call. */
    AUDIO_POLICY_TONE_IN_CALL_NOTIFICATION = 0,

    AUDIO_POLICY_TONE_CNT,
    AUDIO_POLICY_TONE_MAX                  = AUDIO_POLICY_TONE_CNT - 1,
} audio_policy_tone_t;


static inline bool audio_is_low_visibility(audio_stream_type_t stream)
{
    switch (stream) {
    case AUDIO_STREAM_SYSTEM:
    case AUDIO_STREAM_NOTIFICATION:
    case AUDIO_STREAM_RING:
        return true;
    default:
        return false;
    }
}


__END_DECLS

#endif  // ANDROID_AUDIO_POLICY_CORE_H
