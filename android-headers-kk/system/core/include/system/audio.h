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


#ifndef ANDROID_AUDIO_CORE_H
#define ANDROID_AUDIO_CORE_H

#include <stdbool.h>
#include <stdint.h>
#include <sys/cdefs.h>
#include <sys/types.h>

#include <cutils/bitops.h>

__BEGIN_DECLS

/* The enums were moved here mostly from
 * frameworks/base/include/media/AudioSystem.h
 */

/* device address used to refer to the standard remote submix */
#define AUDIO_REMOTE_SUBMIX_DEVICE_ADDRESS "0"

typedef int audio_io_handle_t;

/* Audio stream types */
typedef enum {
    AUDIO_STREAM_DEFAULT          = -1,
    AUDIO_STREAM_VOICE_CALL       = 0,
    AUDIO_STREAM_SYSTEM           = 1,
    AUDIO_STREAM_RING             = 2,
    AUDIO_STREAM_MUSIC            = 3,
    AUDIO_STREAM_ALARM            = 4,
    AUDIO_STREAM_NOTIFICATION     = 5,
    AUDIO_STREAM_BLUETOOTH_SCO    = 6,
    AUDIO_STREAM_ENFORCED_AUDIBLE = 7, /* Sounds that cannot be muted by user and must be routed to speaker */
    AUDIO_STREAM_DTMF             = 8,
    AUDIO_STREAM_TTS              = 9,

    AUDIO_STREAM_CNT,
    AUDIO_STREAM_MAX              = AUDIO_STREAM_CNT - 1,
} audio_stream_type_t;

/* Do not change these values without updating their counterparts
 * in media/java/android/media/MediaRecorder.java!
 */
typedef enum {
    AUDIO_SOURCE_DEFAULT             = 0,
    AUDIO_SOURCE_MIC                 = 1,
    AUDIO_SOURCE_VOICE_UPLINK        = 2,
    AUDIO_SOURCE_VOICE_DOWNLINK      = 3,
    AUDIO_SOURCE_VOICE_CALL          = 4,
    AUDIO_SOURCE_CAMCORDER           = 5,
    AUDIO_SOURCE_VOICE_RECOGNITION   = 6,
    AUDIO_SOURCE_VOICE_COMMUNICATION = 7,
    AUDIO_SOURCE_REMOTE_SUBMIX       = 8, /* Source for the mix to be presented remotely.      */
                                          /* An example of remote presentation is Wifi Display */
                                          /*  where a dongle attached to a TV can be used to   */
                                          /*  play the mix captured by this audio source.      */
    AUDIO_SOURCE_CNT,
    AUDIO_SOURCE_MAX                 = AUDIO_SOURCE_CNT - 1,
    AUDIO_SOURCE_HOTWORD             = 1999, /* A low-priority, preemptible audio source for
                                                for background software hotword detection.
                                                Same tuning as AUDIO_SOURCE_VOICE_RECOGNITION.
                                                Used only internally to the framework. Not exposed
                                                at the audio HAL. */
} audio_source_t;

/* special audio session values
 * (XXX: should this be living in the audio effects land?)
 */
typedef enum {
    /* session for effects attached to a particular output stream
     * (value must be less than 0)
     */
    AUDIO_SESSION_OUTPUT_STAGE = -1,

    /* session for effects applied to output mix. These effects can
     * be moved by audio policy manager to another output stream
     * (value must be 0)
     */
    AUDIO_SESSION_OUTPUT_MIX = 0,
} audio_session_t;

/* Audio sub formats (see enum audio_format). */

/* PCM sub formats */
typedef enum {
    AUDIO_FORMAT_PCM_SUB_16_BIT          = 0x1, /* DO NOT CHANGE - PCM signed 16 bits */
    AUDIO_FORMAT_PCM_SUB_8_BIT           = 0x2, /* DO NOT CHANGE - PCM unsigned 8 bits */
    AUDIO_FORMAT_PCM_SUB_32_BIT          = 0x3, /* PCM signed .31 fixed point */
    AUDIO_FORMAT_PCM_SUB_8_24_BIT        = 0x4, /* PCM signed 7.24 fixed point */
} audio_format_pcm_sub_fmt_t;

/* MP3 sub format field definition : can use 11 LSBs in the same way as MP3
 * frame header to specify bit rate, stereo mode, version...
 */
typedef enum {
    AUDIO_FORMAT_MP3_SUB_NONE            = 0x0,
} audio_format_mp3_sub_fmt_t;

/* AMR NB/WB sub format field definition: specify frame block interleaving,
 * bandwidth efficient or octet aligned, encoding mode for recording...
 */
typedef enum {
    AUDIO_FORMAT_AMR_SUB_NONE            = 0x0,
} audio_format_amr_sub_fmt_t;

/* AAC sub format field definition: specify profile or bitrate for recording... */
typedef enum {
    AUDIO_FORMAT_AAC_SUB_NONE            = 0x0,
} audio_format_aac_sub_fmt_t;

/* VORBIS sub format field definition: specify quality for recording... */
typedef enum {
    AUDIO_FORMAT_VORBIS_SUB_NONE         = 0x0,
} audio_format_vorbis_sub_fmt_t;

/* Audio format consists in a main format field (upper 8 bits) and a sub format
 * field (lower 24 bits).
 *
 * The main format indicates the main codec type. The sub format field
 * indicates options and parameters for each format. The sub format is mainly
 * used for record to indicate for instance the requested bitrate or profile.
 * It can also be used for certain formats to give informations not present in
 * the encoded audio stream (e.g. octet alignement for AMR).
 */
typedef enum {
    AUDIO_FORMAT_INVALID             = 0xFFFFFFFFUL,
    AUDIO_FORMAT_DEFAULT             = 0,
    AUDIO_FORMAT_PCM                 = 0x00000000UL, /* DO NOT CHANGE */
    AUDIO_FORMAT_MP3                 = 0x01000000UL,
    AUDIO_FORMAT_AMR_NB              = 0x02000000UL,
    AUDIO_FORMAT_AMR_WB              = 0x03000000UL,
    AUDIO_FORMAT_AAC                 = 0x04000000UL,
    AUDIO_FORMAT_HE_AAC_V1           = 0x05000000UL,
    AUDIO_FORMAT_HE_AAC_V2           = 0x06000000UL,
    AUDIO_FORMAT_VORBIS              = 0x07000000UL,
    AUDIO_FORMAT_MAIN_MASK           = 0xFF000000UL,
    AUDIO_FORMAT_SUB_MASK            = 0x00FFFFFFUL,

    /* Aliases */
    AUDIO_FORMAT_PCM_16_BIT          = (AUDIO_FORMAT_PCM |
                                        AUDIO_FORMAT_PCM_SUB_16_BIT),
    AUDIO_FORMAT_PCM_8_BIT           = (AUDIO_FORMAT_PCM |
                                        AUDIO_FORMAT_PCM_SUB_8_BIT),
    AUDIO_FORMAT_PCM_32_BIT          = (AUDIO_FORMAT_PCM |
                                        AUDIO_FORMAT_PCM_SUB_32_BIT),
    AUDIO_FORMAT_PCM_8_24_BIT        = (AUDIO_FORMAT_PCM |
                                        AUDIO_FORMAT_PCM_SUB_8_24_BIT),
} audio_format_t;

enum {
    /* output channels */
    AUDIO_CHANNEL_OUT_FRONT_LEFT            = 0x1,
    AUDIO_CHANNEL_OUT_FRONT_RIGHT           = 0x2,
    AUDIO_CHANNEL_OUT_FRONT_CENTER          = 0x4,
    AUDIO_CHANNEL_OUT_LOW_FREQUENCY         = 0x8,
    AUDIO_CHANNEL_OUT_BACK_LEFT             = 0x10,
    AUDIO_CHANNEL_OUT_BACK_RIGHT            = 0x20,
    AUDIO_CHANNEL_OUT_FRONT_LEFT_OF_CENTER  = 0x40,
    AUDIO_CHANNEL_OUT_FRONT_RIGHT_OF_CENTER = 0x80,
    AUDIO_CHANNEL_OUT_BACK_CENTER           = 0x100,
    AUDIO_CHANNEL_OUT_SIDE_LEFT             = 0x200,
    AUDIO_CHANNEL_OUT_SIDE_RIGHT            = 0x400,
    AUDIO_CHANNEL_OUT_TOP_CENTER            = 0x800,
    AUDIO_CHANNEL_OUT_TOP_FRONT_LEFT        = 0x1000,
    AUDIO_CHANNEL_OUT_TOP_FRONT_CENTER      = 0x2000,
    AUDIO_CHANNEL_OUT_TOP_FRONT_RIGHT       = 0x4000,
    AUDIO_CHANNEL_OUT_TOP_BACK_LEFT         = 0x8000,
    AUDIO_CHANNEL_OUT_TOP_BACK_CENTER       = 0x10000,
    AUDIO_CHANNEL_OUT_TOP_BACK_RIGHT        = 0x20000,

    AUDIO_CHANNEL_OUT_MONO     = AUDIO_CHANNEL_OUT_FRONT_LEFT,
    AUDIO_CHANNEL_OUT_STEREO   = (AUDIO_CHANNEL_OUT_FRONT_LEFT |
                                  AUDIO_CHANNEL_OUT_FRONT_RIGHT),
    AUDIO_CHANNEL_OUT_QUAD     = (AUDIO_CHANNEL_OUT_FRONT_LEFT |
                                  AUDIO_CHANNEL_OUT_FRONT_RIGHT |
                                  AUDIO_CHANNEL_OUT_BACK_LEFT |
                                  AUDIO_CHANNEL_OUT_BACK_RIGHT),
    AUDIO_CHANNEL_OUT_SURROUND = (AUDIO_CHANNEL_OUT_FRONT_LEFT |
                                  AUDIO_CHANNEL_OUT_FRONT_RIGHT |
                                  AUDIO_CHANNEL_OUT_FRONT_CENTER |
                                  AUDIO_CHANNEL_OUT_BACK_CENTER),
    AUDIO_CHANNEL_OUT_5POINT1  = (AUDIO_CHANNEL_OUT_FRONT_LEFT |
                                  AUDIO_CHANNEL_OUT_FRONT_RIGHT |
                                  AUDIO_CHANNEL_OUT_FRONT_CENTER |
                                  AUDIO_CHANNEL_OUT_LOW_FREQUENCY |
                                  AUDIO_CHANNEL_OUT_BACK_LEFT |
                                  AUDIO_CHANNEL_OUT_BACK_RIGHT),
    // matches the correct AudioFormat.CHANNEL_OUT_7POINT1_SURROUND definition for 7.1
    AUDIO_CHANNEL_OUT_7POINT1  = (AUDIO_CHANNEL_OUT_FRONT_LEFT |
                                  AUDIO_CHANNEL_OUT_FRONT_RIGHT |
                                  AUDIO_CHANNEL_OUT_FRONT_CENTER |
                                  AUDIO_CHANNEL_OUT_LOW_FREQUENCY |
                                  AUDIO_CHANNEL_OUT_BACK_LEFT |
                                  AUDIO_CHANNEL_OUT_BACK_RIGHT |
                                  AUDIO_CHANNEL_OUT_SIDE_LEFT |
                                  AUDIO_CHANNEL_OUT_SIDE_RIGHT),
    AUDIO_CHANNEL_OUT_ALL      = (AUDIO_CHANNEL_OUT_FRONT_LEFT |
                                  AUDIO_CHANNEL_OUT_FRONT_RIGHT |
                                  AUDIO_CHANNEL_OUT_FRONT_CENTER |
                                  AUDIO_CHANNEL_OUT_LOW_FREQUENCY |
                                  AUDIO_CHANNEL_OUT_BACK_LEFT |
                                  AUDIO_CHANNEL_OUT_BACK_RIGHT |
                                  AUDIO_CHANNEL_OUT_FRONT_LEFT_OF_CENTER |
                                  AUDIO_CHANNEL_OUT_FRONT_RIGHT_OF_CENTER |
                                  AUDIO_CHANNEL_OUT_BACK_CENTER|
                                  AUDIO_CHANNEL_OUT_SIDE_LEFT|
                                  AUDIO_CHANNEL_OUT_SIDE_RIGHT|
                                  AUDIO_CHANNEL_OUT_TOP_CENTER|
                                  AUDIO_CHANNEL_OUT_TOP_FRONT_LEFT|
                                  AUDIO_CHANNEL_OUT_TOP_FRONT_CENTER|
                                  AUDIO_CHANNEL_OUT_TOP_FRONT_RIGHT|
                                  AUDIO_CHANNEL_OUT_TOP_BACK_LEFT|
                                  AUDIO_CHANNEL_OUT_TOP_BACK_CENTER|
                                  AUDIO_CHANNEL_OUT_TOP_BACK_RIGHT),

    /* input channels */
    AUDIO_CHANNEL_IN_LEFT            = 0x4,
    AUDIO_CHANNEL_IN_RIGHT           = 0x8,
    AUDIO_CHANNEL_IN_FRONT           = 0x10,
    AUDIO_CHANNEL_IN_BACK            = 0x20,
    AUDIO_CHANNEL_IN_LEFT_PROCESSED  = 0x40,
    AUDIO_CHANNEL_IN_RIGHT_PROCESSED = 0x80,
    AUDIO_CHANNEL_IN_FRONT_PROCESSED = 0x100,
    AUDIO_CHANNEL_IN_BACK_PROCESSED  = 0x200,
    AUDIO_CHANNEL_IN_PRESSURE        = 0x400,
    AUDIO_CHANNEL_IN_X_AXIS          = 0x800,
    AUDIO_CHANNEL_IN_Y_AXIS          = 0x1000,
    AUDIO_CHANNEL_IN_Z_AXIS          = 0x2000,
    AUDIO_CHANNEL_IN_VOICE_UPLINK    = 0x4000,
    AUDIO_CHANNEL_IN_VOICE_DNLINK    = 0x8000,

    AUDIO_CHANNEL_IN_MONO   = AUDIO_CHANNEL_IN_FRONT,
    AUDIO_CHANNEL_IN_STEREO = (AUDIO_CHANNEL_IN_LEFT | AUDIO_CHANNEL_IN_RIGHT),
    AUDIO_CHANNEL_IN_FRONT_BACK = (AUDIO_CHANNEL_IN_FRONT | AUDIO_CHANNEL_IN_BACK),
    AUDIO_CHANNEL_IN_ALL    = (AUDIO_CHANNEL_IN_LEFT |
                               AUDIO_CHANNEL_IN_RIGHT |
                               AUDIO_CHANNEL_IN_FRONT |
                               AUDIO_CHANNEL_IN_BACK|
                               AUDIO_CHANNEL_IN_LEFT_PROCESSED |
                               AUDIO_CHANNEL_IN_RIGHT_PROCESSED |
                               AUDIO_CHANNEL_IN_FRONT_PROCESSED |
                               AUDIO_CHANNEL_IN_BACK_PROCESSED|
                               AUDIO_CHANNEL_IN_PRESSURE |
                               AUDIO_CHANNEL_IN_X_AXIS |
                               AUDIO_CHANNEL_IN_Y_AXIS |
                               AUDIO_CHANNEL_IN_Z_AXIS |
                               AUDIO_CHANNEL_IN_VOICE_UPLINK |
                               AUDIO_CHANNEL_IN_VOICE_DNLINK),
};

typedef uint32_t audio_channel_mask_t;

typedef enum {
    AUDIO_MODE_INVALID          = -2,
    AUDIO_MODE_CURRENT          = -1,
    AUDIO_MODE_NORMAL           = 0,
    AUDIO_MODE_RINGTONE         = 1,
    AUDIO_MODE_IN_CALL          = 2,
    AUDIO_MODE_IN_COMMUNICATION = 3,

    AUDIO_MODE_CNT,
    AUDIO_MODE_MAX              = AUDIO_MODE_CNT - 1,
} audio_mode_t;

typedef enum {
    AUDIO_IN_ACOUSTICS_AGC_ENABLE    = 0x0001,
    AUDIO_IN_ACOUSTICS_AGC_DISABLE   = 0,
    AUDIO_IN_ACOUSTICS_NS_ENABLE     = 0x0002,
    AUDIO_IN_ACOUSTICS_NS_DISABLE    = 0,
    AUDIO_IN_ACOUSTICS_TX_IIR_ENABLE = 0x0004,
    AUDIO_IN_ACOUSTICS_TX_DISABLE    = 0,
} audio_in_acoustics_t;

enum {
    AUDIO_DEVICE_NONE                          = 0x0,
    /* reserved bits */
    AUDIO_DEVICE_BIT_IN                        = 0x80000000,
    AUDIO_DEVICE_BIT_DEFAULT                   = 0x40000000,
    /* output devices */
    AUDIO_DEVICE_OUT_EARPIECE                  = 0x1,
    AUDIO_DEVICE_OUT_SPEAKER                   = 0x2,
    AUDIO_DEVICE_OUT_WIRED_HEADSET             = 0x4,
    AUDIO_DEVICE_OUT_WIRED_HEADPHONE           = 0x8,
    AUDIO_DEVICE_OUT_BLUETOOTH_SCO             = 0x10,
    AUDIO_DEVICE_OUT_BLUETOOTH_SCO_HEADSET     = 0x20,
    AUDIO_DEVICE_OUT_BLUETOOTH_SCO_CARKIT      = 0x40,
    AUDIO_DEVICE_OUT_BLUETOOTH_A2DP            = 0x80,
    AUDIO_DEVICE_OUT_BLUETOOTH_A2DP_HEADPHONES = 0x100,
    AUDIO_DEVICE_OUT_BLUETOOTH_A2DP_SPEAKER    = 0x200,
    AUDIO_DEVICE_OUT_AUX_DIGITAL               = 0x400,
    AUDIO_DEVICE_OUT_ANLG_DOCK_HEADSET         = 0x800,
    AUDIO_DEVICE_OUT_DGTL_DOCK_HEADSET         = 0x1000,
    AUDIO_DEVICE_OUT_USB_ACCESSORY             = 0x2000,
    AUDIO_DEVICE_OUT_USB_DEVICE                = 0x4000,
    AUDIO_DEVICE_OUT_REMOTE_SUBMIX             = 0x8000,
    AUDIO_DEVICE_OUT_DEFAULT                   = AUDIO_DEVICE_BIT_DEFAULT,
    AUDIO_DEVICE_OUT_ALL      = (AUDIO_DEVICE_OUT_EARPIECE |
                                 AUDIO_DEVICE_OUT_SPEAKER |
                                 AUDIO_DEVICE_OUT_WIRED_HEADSET |
                                 AUDIO_DEVICE_OUT_WIRED_HEADPHONE |
                                 AUDIO_DEVICE_OUT_BLUETOOTH_SCO |
                                 AUDIO_DEVICE_OUT_BLUETOOTH_SCO_HEADSET |
                                 AUDIO_DEVICE_OUT_BLUETOOTH_SCO_CARKIT |
                                 AUDIO_DEVICE_OUT_BLUETOOTH_A2DP |
                                 AUDIO_DEVICE_OUT_BLUETOOTH_A2DP_HEADPHONES |
                                 AUDIO_DEVICE_OUT_BLUETOOTH_A2DP_SPEAKER |
                                 AUDIO_DEVICE_OUT_AUX_DIGITAL |
                                 AUDIO_DEVICE_OUT_ANLG_DOCK_HEADSET |
                                 AUDIO_DEVICE_OUT_DGTL_DOCK_HEADSET |
                                 AUDIO_DEVICE_OUT_USB_ACCESSORY |
                                 AUDIO_DEVICE_OUT_USB_DEVICE |
                                 AUDIO_DEVICE_OUT_REMOTE_SUBMIX |
                                 AUDIO_DEVICE_OUT_DEFAULT),
    AUDIO_DEVICE_OUT_ALL_A2DP = (AUDIO_DEVICE_OUT_BLUETOOTH_A2DP |
                                 AUDIO_DEVICE_OUT_BLUETOOTH_A2DP_HEADPHONES |
                                 AUDIO_DEVICE_OUT_BLUETOOTH_A2DP_SPEAKER),
    AUDIO_DEVICE_OUT_ALL_SCO  = (AUDIO_DEVICE_OUT_BLUETOOTH_SCO |
                                 AUDIO_DEVICE_OUT_BLUETOOTH_SCO_HEADSET |
                                 AUDIO_DEVICE_OUT_BLUETOOTH_SCO_CARKIT),
    AUDIO_DEVICE_OUT_ALL_USB  = (AUDIO_DEVICE_OUT_USB_ACCESSORY |
                                 AUDIO_DEVICE_OUT_USB_DEVICE),

    /* input devices */
    AUDIO_DEVICE_IN_COMMUNICATION         = AUDIO_DEVICE_BIT_IN | 0x1,
    AUDIO_DEVICE_IN_AMBIENT               = AUDIO_DEVICE_BIT_IN | 0x2,
    AUDIO_DEVICE_IN_BUILTIN_MIC           = AUDIO_DEVICE_BIT_IN | 0x4,
    AUDIO_DEVICE_IN_BLUETOOTH_SCO_HEADSET = AUDIO_DEVICE_BIT_IN | 0x8,
    AUDIO_DEVICE_IN_WIRED_HEADSET         = AUDIO_DEVICE_BIT_IN | 0x10,
    AUDIO_DEVICE_IN_AUX_DIGITAL           = AUDIO_DEVICE_BIT_IN | 0x20,
    AUDIO_DEVICE_IN_VOICE_CALL            = AUDIO_DEVICE_BIT_IN | 0x40,
    AUDIO_DEVICE_IN_BACK_MIC              = AUDIO_DEVICE_BIT_IN | 0x80,
    AUDIO_DEVICE_IN_REMOTE_SUBMIX         = AUDIO_DEVICE_BIT_IN | 0x100,
    AUDIO_DEVICE_IN_ANLG_DOCK_HEADSET     = AUDIO_DEVICE_BIT_IN | 0x200,
    AUDIO_DEVICE_IN_DGTL_DOCK_HEADSET     = AUDIO_DEVICE_BIT_IN | 0x400,
    AUDIO_DEVICE_IN_USB_ACCESSORY         = AUDIO_DEVICE_BIT_IN | 0x800,
    AUDIO_DEVICE_IN_USB_DEVICE            = AUDIO_DEVICE_BIT_IN | 0x1000,
    AUDIO_DEVICE_IN_DEFAULT               = AUDIO_DEVICE_BIT_IN | AUDIO_DEVICE_BIT_DEFAULT,

    AUDIO_DEVICE_IN_ALL     = (AUDIO_DEVICE_IN_COMMUNICATION |
                               AUDIO_DEVICE_IN_AMBIENT |
                               AUDIO_DEVICE_IN_BUILTIN_MIC |
                               AUDIO_DEVICE_IN_BLUETOOTH_SCO_HEADSET |
                               AUDIO_DEVICE_IN_WIRED_HEADSET |
                               AUDIO_DEVICE_IN_AUX_DIGITAL |
                               AUDIO_DEVICE_IN_VOICE_CALL |
                               AUDIO_DEVICE_IN_BACK_MIC |
                               AUDIO_DEVICE_IN_REMOTE_SUBMIX |
                               AUDIO_DEVICE_IN_ANLG_DOCK_HEADSET |
                               AUDIO_DEVICE_IN_DGTL_DOCK_HEADSET |
                               AUDIO_DEVICE_IN_USB_ACCESSORY |
                               AUDIO_DEVICE_IN_USB_DEVICE |
                               AUDIO_DEVICE_IN_DEFAULT),
    AUDIO_DEVICE_IN_ALL_SCO = AUDIO_DEVICE_IN_BLUETOOTH_SCO_HEADSET,
};

typedef uint32_t audio_devices_t;

/* the audio output flags serve two purposes:
 * - when an AudioTrack is created they indicate a "wish" to be connected to an
 * output stream with attributes corresponding to the specified flags
 * - when present in an output profile descriptor listed for a particular audio
 * hardware module, they indicate that an output stream can be opened that
 * supports the attributes indicated by the flags.
 * the audio policy manager will try to match the flags in the request
 * (when getOuput() is called) to an available output stream.
 */
typedef enum {
    AUDIO_OUTPUT_FLAG_NONE = 0x0,       // no attributes
    AUDIO_OUTPUT_FLAG_DIRECT = 0x1,     // this output directly connects a track
                                        // to one output stream: no software mixer
    AUDIO_OUTPUT_FLAG_PRIMARY = 0x2,    // this output is the primary output of
                                        // the device. It is unique and must be
                                        // present. It is opened by default and
                                        // receives routing, audio mode and volume
                                        // controls related to voice calls.
    AUDIO_OUTPUT_FLAG_FAST = 0x4,       // output supports "fast tracks",
                                        // defined elsewhere
    AUDIO_OUTPUT_FLAG_DEEP_BUFFER = 0x8, // use deep audio buffers
    AUDIO_OUTPUT_FLAG_COMPRESS_OFFLOAD = 0x10,  // offload playback of compressed
                                                // streams to hardware codec
    AUDIO_OUTPUT_FLAG_NON_BLOCKING = 0x20 // use non-blocking write
} audio_output_flags_t;

/* The audio input flags are analogous to audio output flags.
 * Currently they are used only when an AudioRecord is created,
 * to indicate a preference to be connected to an input stream with
 * attributes corresponding to the specified flags.
 */
typedef enum {
    AUDIO_INPUT_FLAG_NONE = 0x0,        // no attributes
    AUDIO_INPUT_FLAG_FAST = 0x1,        // prefer an input that supports "fast tracks"
} audio_input_flags_t;

/* Additional information about compressed streams offloaded to
 * hardware playback
 * The version and size fields must be initialized by the caller by using
 * one of the constants defined here.
 */
typedef struct {
    uint16_t version;                   // version of the info structure
    uint16_t size;                      // total size of the structure including version and size
    uint32_t sample_rate;               // sample rate in Hz
    audio_channel_mask_t channel_mask;  // channel mask
    audio_format_t format;              // audio format
    audio_stream_type_t stream_type;    // stream type
    uint32_t bit_rate;                  // bit rate in bits per second
    int64_t duration_us;                // duration in microseconds, -1 if unknown
    bool has_video;                     // true if stream is tied to a video stream
    bool is_streaming;                  // true if streaming, false if local playback
} audio_offload_info_t;

#define AUDIO_MAKE_OFFLOAD_INFO_VERSION(maj,min) \
            ((((maj) & 0xff) << 8) | ((min) & 0xff))

#define AUDIO_OFFLOAD_INFO_VERSION_0_1 AUDIO_MAKE_OFFLOAD_INFO_VERSION(0, 1)
#define AUDIO_OFFLOAD_INFO_VERSION_CURRENT AUDIO_OFFLOAD_INFO_VERSION_0_1

static const audio_offload_info_t AUDIO_INFO_INITIALIZER = {
    version: AUDIO_OFFLOAD_INFO_VERSION_CURRENT,
    size: sizeof(audio_offload_info_t),
};

static inline bool audio_is_output_device(audio_devices_t device)
{
    if (((device & AUDIO_DEVICE_BIT_IN) == 0) &&
            (popcount(device) == 1) && ((device & ~AUDIO_DEVICE_OUT_ALL) == 0))
        return true;
    else
        return false;
}

static inline bool audio_is_input_device(audio_devices_t device)
{
    if ((device & AUDIO_DEVICE_BIT_IN) != 0) {
        device &= ~AUDIO_DEVICE_BIT_IN;
        if ((popcount(device) == 1) && ((device & ~AUDIO_DEVICE_IN_ALL) == 0))
            return true;
    }
    return false;
}

static inline bool audio_is_output_devices(audio_devices_t device)
{
    return (device & AUDIO_DEVICE_BIT_IN) == 0;
}


static inline bool audio_is_a2dp_device(audio_devices_t device)
{
    if ((popcount(device) == 1) && (device & AUDIO_DEVICE_OUT_ALL_A2DP))
        return true;
    else
        return false;
}

static inline bool audio_is_bluetooth_sco_device(audio_devices_t device)
{
    device &= ~AUDIO_DEVICE_BIT_IN;
    if ((popcount(device) == 1) && (device & (AUDIO_DEVICE_OUT_ALL_SCO |
                   AUDIO_DEVICE_IN_BLUETOOTH_SCO_HEADSET)))
        return true;
    else
        return false;
}

static inline bool audio_is_usb_device(audio_devices_t device)
{
    if ((popcount(device) == 1) && (device & AUDIO_DEVICE_OUT_ALL_USB))
        return true;
    else
        return false;
}

static inline bool audio_is_remote_submix_device(audio_devices_t device)
{
    if ((device & AUDIO_DEVICE_OUT_REMOTE_SUBMIX) == AUDIO_DEVICE_OUT_REMOTE_SUBMIX
            || (device & AUDIO_DEVICE_IN_REMOTE_SUBMIX) == AUDIO_DEVICE_IN_REMOTE_SUBMIX)
        return true;
    else
        return false;
}

static inline bool audio_is_input_channel(audio_channel_mask_t channel)
{
    if ((channel & ~AUDIO_CHANNEL_IN_ALL) == 0)
        return channel != 0;
    else
        return false;
}

static inline bool audio_is_output_channel(audio_channel_mask_t channel)
{
    if ((channel & ~AUDIO_CHANNEL_OUT_ALL) == 0)
        return channel != 0;
    else
        return false;
}

/* Derive an output channel mask from a channel count.
 * This is to be used when the content channel mask is unknown. The 1, 2, 4, 5, 6, 7 and 8 channel
 * cases are mapped to the standard game/home-theater layouts, but note that 4 is mapped to quad,
 * and not stereo + FC + mono surround. A channel count of 3 is arbitrarily mapped to stereo + FC
 * for continuity with stereo.
 * Returns the matching channel mask, or 0 if the number of channels exceeds that of the
 * configurations for which a default channel mask is defined.
 */
static inline audio_channel_mask_t audio_channel_out_mask_from_count(uint32_t channel_count)
{
    switch(channel_count) {
    case 1:
        return AUDIO_CHANNEL_OUT_MONO;
    case 2:
        return AUDIO_CHANNEL_OUT_STEREO;
    case 3:
        return (AUDIO_CHANNEL_OUT_STEREO | AUDIO_CHANNEL_OUT_FRONT_CENTER);
    case 4: // 4.0
        return AUDIO_CHANNEL_OUT_QUAD;
    case 5: // 5.0
        return (AUDIO_CHANNEL_OUT_QUAD | AUDIO_CHANNEL_OUT_FRONT_CENTER);
    case 6: // 5.1
        return AUDIO_CHANNEL_OUT_5POINT1;
    case 7: // 6.1
        return (AUDIO_CHANNEL_OUT_5POINT1 | AUDIO_CHANNEL_OUT_BACK_CENTER);
    case 8:
        return AUDIO_CHANNEL_OUT_7POINT1;
    default:
        return 0;
    }
}

/* Similar to above, but for input.  Currently handles only mono and stereo. */
static inline audio_channel_mask_t audio_channel_in_mask_from_count(uint32_t channel_count)
{
    switch (channel_count) {
    case 1:
        return AUDIO_CHANNEL_IN_MONO;
    case 2:
        return AUDIO_CHANNEL_IN_STEREO;
    default:
        return 0;
    }
}

static inline bool audio_is_valid_format(audio_format_t format)
{
    switch (format & AUDIO_FORMAT_MAIN_MASK) {
    case AUDIO_FORMAT_PCM:
        if (format != AUDIO_FORMAT_PCM_16_BIT &&
                format != AUDIO_FORMAT_PCM_8_BIT) {
            return false;
        }
    case AUDIO_FORMAT_MP3:
    case AUDIO_FORMAT_AMR_NB:
    case AUDIO_FORMAT_AMR_WB:
    case AUDIO_FORMAT_AAC:
    case AUDIO_FORMAT_HE_AAC_V1:
    case AUDIO_FORMAT_HE_AAC_V2:
    case AUDIO_FORMAT_VORBIS:
        return true;
    default:
        return false;
    }
}

static inline bool audio_is_linear_pcm(audio_format_t format)
{
    return ((format & AUDIO_FORMAT_MAIN_MASK) == AUDIO_FORMAT_PCM);
}

static inline size_t audio_bytes_per_sample(audio_format_t format)
{
    size_t size = 0;

    switch (format) {
    case AUDIO_FORMAT_PCM_32_BIT:
    case AUDIO_FORMAT_PCM_8_24_BIT:
        size = sizeof(int32_t);
        break;
    case AUDIO_FORMAT_PCM_16_BIT:
        size = sizeof(int16_t);
        break;
    case AUDIO_FORMAT_PCM_8_BIT:
        size = sizeof(uint8_t);
        break;
    default:
        break;
    }
    return size;
}

__END_DECLS

#endif  // ANDROID_AUDIO_CORE_H
