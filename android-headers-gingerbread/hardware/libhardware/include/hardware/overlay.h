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

#ifndef ANDROID_OVERLAY_INTERFACE_H
#define ANDROID_OVERLAY_INTERFACE_H

#include <cutils/native_handle.h>

#include <hardware/hardware.h>

#include <stdint.h>
#include <sys/cdefs.h>
#include <sys/types.h>

__BEGIN_DECLS

/**
 * The id of this module
 */
#define OVERLAY_HARDWARE_MODULE_ID "overlay"

/**
 * Name of the overlay device to open
 */
#define OVERLAY_HARDWARE_CONTROL    "control"
#define OVERLAY_HARDWARE_DATA       "data"

/*****************************************************************************/

/* possible overlay formats */
enum {
    OVERLAY_FORMAT_RGBA_8888    = HAL_PIXEL_FORMAT_RGBA_8888,
    OVERLAY_FORMAT_RGB_565      = HAL_PIXEL_FORMAT_RGB_565,
    OVERLAY_FORMAT_BGRA_8888    = HAL_PIXEL_FORMAT_BGRA_8888,
    OVERLAY_FORMAT_YCbYCr_422_I = 0x14,
    OVERLAY_FORMAT_CbYCrY_422_I = 0x16,
    OVERLAY_FORMAT_DEFAULT      = 99    // The actual color format is determined
                                        // by the overlay
};

/* values for copybit_set_parameter(OVERLAY_TRANSFORM) */
enum {
    /* flip source image horizontally */
    OVERLAY_TRANSFORM_FLIP_H    = HAL_TRANSFORM_FLIP_H,
    /* flip source image vertically */
    OVERLAY_TRANSFORM_FLIP_V    = HAL_TRANSFORM_FLIP_V,
    /* rotate source image 90 degrees */
    OVERLAY_TRANSFORM_ROT_90    = HAL_TRANSFORM_ROT_90,
    /* rotate source image 180 degrees */
    OVERLAY_TRANSFORM_ROT_180   = HAL_TRANSFORM_ROT_180,
    /* rotate source image 270 degrees */
    OVERLAY_TRANSFORM_ROT_270   = HAL_TRANSFORM_ROT_270
};

/* names for setParameter() */
enum {
    /* rotation of the source image in degrees (0 to 359) */
    OVERLAY_ROTATION_DEG  = 1,
    /* enable or disable dithering */
    OVERLAY_DITHER        = 3,
    /* transformation applied (this is a superset of COPYBIT_ROTATION_DEG) */
    OVERLAY_TRANSFORM    = 4,
};

/* enable/disable value setParameter() */
enum {
    OVERLAY_DISABLE = 0,
    OVERLAY_ENABLE  = 1
};

/* names for get() */
enum {
    /* Maximum amount of minification supported by the hardware*/
    OVERLAY_MINIFICATION_LIMIT      = 1,
    /* Maximum amount of magnification supported by the hardware */
    OVERLAY_MAGNIFICATION_LIMIT     = 2,
    /* Number of fractional bits support by the overlay scaling engine */
    OVERLAY_SCALING_FRAC_BITS       = 3,
    /* Supported rotation step in degrees. */
    OVERLAY_ROTATION_STEP_DEG       = 4,
    /* horizontal alignment in pixels */
    OVERLAY_HORIZONTAL_ALIGNMENT    = 5,
    /* vertical alignment in pixels */
    OVERLAY_VERTICAL_ALIGNMENT      = 6,
    /* width alignment restrictions. negative number for max. power-of-two */
    OVERLAY_WIDTH_ALIGNMENT         = 7,
    /* height alignment restrictions. negative number for max. power-of-two */
    OVERLAY_HEIGHT_ALIGNMENT        = 8,
};

/*****************************************************************************/

/* opaque reference to an Overlay kernel object */
typedef const native_handle* overlay_handle_t;

typedef struct overlay_t {
    uint32_t            w;
    uint32_t            h;
    int32_t             format;
    uint32_t            w_stride;
    uint32_t            h_stride;
    uint32_t            reserved[3];
    /* returns a reference to this overlay's handle (the caller doesn't
     * take ownership) */
    overlay_handle_t    (*getHandleRef)(struct overlay_t* overlay);
    uint32_t            reserved_procs[7];
} overlay_t;

typedef void* overlay_buffer_t;

/*****************************************************************************/

/**
 * Every hardware module must have a data structure named HAL_MODULE_INFO_SYM
 * and the fields of this data structure must begin with hw_module_t
 * followed by module specific information.
 */
struct overlay_module_t {
    struct hw_module_t common;
};

/*****************************************************************************/

/**
 * Every device data structure must begin with hw_device_t
 * followed by module specific public methods and attributes.
 */

struct overlay_control_device_t {
    struct hw_device_t common;
    
    /* get static informations about the capabilities of the overlay engine */
    int (*get)(struct overlay_control_device_t *dev, int name);

    /* creates an overlay matching the given parameters as closely as possible.
     * returns an error if no more overlays are available. The actual
     * size and format is returned in overlay_t. */
    overlay_t* (*createOverlay)(struct overlay_control_device_t *dev,
            uint32_t w, uint32_t h, int32_t format);
    
    /* destroys an overlay. This call releases all
     * resources associated with overlay_t and make it invalid */
    void (*destroyOverlay)(struct overlay_control_device_t *dev,
            overlay_t* overlay);

    /* set position and scaling of the given overlay as closely as possible.
     * if scaling cannot be performed, overlay must be centered. */
    int (*setPosition)(struct overlay_control_device_t *dev,
            overlay_t* overlay, 
            int x, int y, uint32_t w, uint32_t h);

    /* returns the actual position and size of the overlay */
    int (*getPosition)(struct overlay_control_device_t *dev,
            overlay_t* overlay, 
            int* x, int* y, uint32_t* w, uint32_t* h);

    /* sets configurable parameters for this overlay. returns an error if not
     * supported. */
    int (*setParameter)(struct overlay_control_device_t *dev,
            overlay_t* overlay, int param, int value);

    int (*stage)(struct overlay_control_device_t *dev, overlay_t* overlay);
    int (*commit)(struct overlay_control_device_t *dev, overlay_t* overlay);
};


struct overlay_data_device_t {
    struct hw_device_t common;

    /* initialize the overlay from the given handle. this associates this
     * overlay data module to its control module */
    int (*initialize)(struct overlay_data_device_t *dev,
            overlay_handle_t handle);

    /* can be called to change the width and height of the overlay. */
    int (*resizeInput)(struct overlay_data_device_t *dev,
            uint32_t w, uint32_t h);

    int (*setCrop)(struct overlay_data_device_t *dev,
            uint32_t x, uint32_t y, uint32_t w, uint32_t h) ;

    int (*getCrop)(struct overlay_data_device_t *dev,
       uint32_t* x, uint32_t* y, uint32_t* w, uint32_t* h) ;

    int (*setParameter)(struct overlay_data_device_t *dev,
            int param, int value);

    /* blocks until an overlay buffer is available and return that buffer. */
    int (*dequeueBuffer)(struct overlay_data_device_t *dev,
		         overlay_buffer_t *buf);

    /* release the overlay buffer and post it */
    int (*queueBuffer)(struct overlay_data_device_t *dev,
            overlay_buffer_t buffer);

    /* returns the address of a given buffer if supported, NULL otherwise. */
    void* (*getBufferAddress)(struct overlay_data_device_t *dev,
            overlay_buffer_t buffer);

    int (*getBufferCount)(struct overlay_data_device_t *dev);
};


/*****************************************************************************/

/** convenience API for opening and closing a device */

static inline int overlay_control_open(const struct hw_module_t* module, 
        struct overlay_control_device_t** device) {
    return module->methods->open(module, 
            OVERLAY_HARDWARE_CONTROL, (struct hw_device_t**)device);
}

static inline int overlay_control_close(struct overlay_control_device_t* device) {
    return device->common.close(&device->common);
}

static inline int overlay_data_open(const struct hw_module_t* module, 
        struct overlay_data_device_t** device) {
    return module->methods->open(module, 
            OVERLAY_HARDWARE_DATA, (struct hw_device_t**)device);
}

static inline int overlay_data_close(struct overlay_data_device_t* device) {
    return device->common.close(&device->common);
}

__END_DECLS

#endif  // ANDROID_OVERLAY_INTERFACE_H
