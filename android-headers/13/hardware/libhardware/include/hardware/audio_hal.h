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


#ifndef ANDROID_AUDIO_HAL_INTERFACE_H
#define ANDROID_AUDIO_HAL_INTERFACE_H

#include <stdint.h>
#include <strings.h>
#include <sys/cdefs.h>
#include <sys/types.h>

#include <cutils/bitops.h>

#include <hardware/hardware.h>
#include <hardware/audio.h>

__BEGIN_DECLS

/**
 * The id of this module
 */
#define AUDIO_HARDWARE_MODULE_ID "audio"

/**
 * Name of the audio devices to open
 */
#define AUDIO_HARDWARE_INTERFACE "audio_hw_if"

/**************************************/

/* common audio stream parameters and operations */
struct audio_stream {

    /**
     * sampling rate is in Hz - eg. 44100
     */
    uint32_t (*get_sample_rate)(const struct audio_stream *stream);
    int (*set_sample_rate)(struct audio_stream *stream, uint32_t rate);

    /**
     * size of output buffer in bytes - eg. 4800
     */
    size_t (*get_buffer_size)(const struct audio_stream *stream);

    /**
     * the channel mask -
     *  e.g. AUDIO_CHANNEL_OUT_STEREO or AUDIO_CHANNEL_IN_STEREO
     */
    uint32_t (*get_channels)(const struct audio_stream *stream);

    /**
     * audio format - eg. AUDIO_FORMAT_PCM_16_BIT
     */
    int (*get_format)(const struct audio_stream *stream);
    int (*set_format)(struct audio_stream *stream, int format);

    /**
     * Put the audio hardware input/output into standby mode.
     * Returns 0 on success and <0 on failure.
     */
    int (*standby)(struct audio_stream *stream);

    /** dump the state of the audio input/output device */
    int (*dump)(const struct audio_stream *stream, int fd);

    audio_devices_t (*get_device)(const struct audio_stream *stream);
    int (*set_device)(struct audio_stream *stream, audio_devices_t device);

    /**
     * set/get audio stream parameters. The function accepts a list of
     * parameter key value pairs in the form: key1=value1;key2=value2;...
     *
     * Some keys are reserved for standard parameters (See AudioParameter class)
     *
     * If the implementation does not accept a parameter change while
     * the output is active but the parameter is acceptable otherwise, it must
     * return -ENOSYS.
     *
     * The audio flinger will put the stream in standby and then change the
     * parameter value.
     */
    int (*set_parameters)(struct audio_stream *stream, const char *kv_pairs);

    /*
     * Returns a pointer to a heap allocated string. The caller is responsible
     * for freeing the memory for it.
     */
    char * (*get_parameters)(const struct audio_stream *stream,
                             const char *keys);
};
typedef struct audio_stream audio_stream_t;

/**
 * audio_stream_out is the abstraction interface for the audio output hardware.
 *
 * It provides information about various properties of the audio output
 * hardware driver.
 */

struct audio_stream_out {
    struct audio_stream common;

    /**
     * return the audio hardware driver latency in milli seconds.
     */
    uint32_t (*get_latency)(const struct audio_stream_out *stream);

    /**
     * Use this method in situations where audio mixing is done in the
     * hardware. This method serves as a direct interface with hardware,
     * allowing you to directly set the volume as apposed to via the framework.
     * This method might produce multiple PCM outputs or hardware accelerated
     * codecs, such as MP3 or AAC.
     */
    int (*set_volume)(struct audio_stream_out *stream, float left, float right);

    /**
     * write audio buffer to driver. Returns number of bytes written
     */
    ssize_t (*write)(struct audio_stream_out *stream, const void* buffer,
                     size_t bytes);

    /* return the number of audio frames written by the audio dsp to DAC since
     * the output has exited standby
     */
    int (*get_render_position)(const struct audio_stream_out *stream,
                               uint32_t *dsp_frames);
};
typedef struct audio_stream_out audio_stream_out_t;

struct audio_stream_in {
    struct audio_stream common;

    /** set the input gain for the audio driver. This method is for
     *  for future use */
    int (*set_gain)(struct audio_stream_in *stream, float gain);

    /** read audio buffer in from audio driver */
    ssize_t (*read)(struct audio_stream_in *stream, void* buffer,
                    size_t bytes);

    /**
     * Return the amount of input frames lost in the audio driver since the
     * last call of this function.
     * Audio driver is expected to reset the value to 0 and restart counting
     * upon returning the current value by this function call.
     * Such loss typically occurs when the user space process is blocked
     * longer than the capacity of audio driver buffers.
     *
     * Unit: the number of input audio frames
     */
    uint32_t (*get_input_frames_lost)(struct audio_stream_in *stream);
};
typedef struct audio_stream_in audio_stream_in_t;

/**
 * return the frame size (number of bytes per sample).
 */
static inline uint32_t audio_stream_frame_size(struct audio_stream *s)
{
    int chan_samp_sz;

    switch (s->get_format(s)) {
    case AUDIO_FORMAT_PCM_16_BIT:
        chan_samp_sz = sizeof(int16_t);
        break;
    case AUDIO_FORMAT_PCM_8_BIT:
    default:
        chan_samp_sz = sizeof(int8_t);
        break;
    }

    return popcount(s->get_channels(s)) * chan_samp_sz;
}


/**********************************************************************/

/**
 * Every hardware module must have a data structure named HAL_MODULE_INFO_SYM
 * and the fields of this data structure must begin with hw_module_t
 * followed by module specific information.
 */
struct audio_module {
    struct hw_module_t common;
};

struct audio_hw_device {
    struct hw_device_t common;

    /**
     * used by audio flinger to enumerate what devices are supported by
     * each audio_hw_device implementation.
     *
     * Return value is a bitmask of 1 or more values of audio_devices_t
     */
    uint32_t (*get_supported_devices)(const struct audio_hw_device *dev);

    /**
     * check to see if the audio hardware interface has been initialized.
     * returns 0 on success, -ENODEV on failure.
     */
    int (*init_check)(const struct audio_hw_device *dev);

    /** set the audio volume of a voice call. Range is between 0.0 and 1.0 */
    int (*set_voice_volume)(struct audio_hw_device *dev, float volume);

    /**
     * set the audio volume for all audio activities other than voice call.
     * Range between 0.0 and 1.0. If any value other than 0 is returned,
     * the software mixer will emulate this capability.
     */
    int (*set_master_volume)(struct audio_hw_device *dev, float volume);

    /**
     * setMode is called when the audio mode changes. AUDIO_MODE_NORMAL mode
     * is for standard audio playback, AUDIO_MODE_RINGTONE when a ringtone is
     * playing, and AUDIO_MODE_IN_CALL when a call is in progress.
     */
    int (*set_mode)(struct audio_hw_device *dev, int mode);

    /* mic mute */
    int (*set_mic_mute)(struct audio_hw_device *dev, bool state);
    int (*get_mic_mute)(const struct audio_hw_device *dev, bool *state);

    /* set/get global audio parameters */
    int (*set_parameters)(struct audio_hw_device *dev, const char *kv_pairs);

    /*
     * Returns a pointer to a heap allocated string. The caller is responsible
     * for freeing the memory for it.
     */
    char * (*get_parameters)(const struct audio_hw_device *dev,
                             const char *keys);

    /* Returns audio input buffer size according to parameters passed or
     * 0 if one of the parameters is not supported
     */
    size_t (*get_input_buffer_size)(const struct audio_hw_device *dev,
                                    uint32_t sample_rate, int format,
                                    int channel_count);

    /** This method creates and opens the audio hardware output stream */
    int (*open_output_stream)(struct audio_hw_device *dev, uint32_t devices,
                              int *format, uint32_t *channels,
                              uint32_t *sample_rate,
                              struct audio_stream_out **out);

    void (*close_output_stream)(struct audio_hw_device *dev,
                                struct audio_stream_out* out);

    /** This method creates and opens the audio hardware input stream */
    int (*open_input_stream)(struct audio_hw_device *dev, uint32_t devices,
                             int *format, uint32_t *channels,
                             uint32_t *sample_rate,
                             audio_in_acoustics_t acoustics,
                             struct audio_stream_in **stream_in);

    void (*close_input_stream)(struct audio_hw_device *dev,
                               struct audio_stream_in *in);

    /** This method dumps the state of the audio hardware */
    int (*dump)(const struct audio_hw_device *dev, int fd);
};
typedef struct audio_hw_device audio_hw_device_t;

/** convenience API for opening and closing a supported device */

static inline int audio_hw_device_open(const struct hw_module_t* module,
                                       struct audio_hw_device** device)
{
    return module->methods->open(module, AUDIO_HARDWARE_INTERFACE,
                                 (struct hw_device_t**)device);
}

static inline int audio_hw_device_close(struct audio_hw_device* device)
{
    return device->common.close(&device->common);
}


__END_DECLS

#endif  // ANDROID_AUDIO_INTERFACE_H
