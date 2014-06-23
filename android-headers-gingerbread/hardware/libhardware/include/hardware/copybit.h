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

#ifndef ANDROID_COPYBIT_INTERFACE_H
#define ANDROID_COPYBIT_INTERFACE_H

#include <hardware/hardware.h>

#include <stdint.h>
#include <sys/cdefs.h>
#include <sys/types.h>

__BEGIN_DECLS

/**
 * The id of this module
 */
#define COPYBIT_HARDWARE_MODULE_ID "copybit"

/**
 * Name of the graphics device to open
 */
#define COPYBIT_HARDWARE_COPYBIT0 "copybit0"

/* supported pixel-formats. these must be compatible with
 * graphics/PixelFormat.java, ui/PixelFormat.h, pixelflinger/format.h
 */
enum {
    COPYBIT_FORMAT_RGBA_8888    = HAL_PIXEL_FORMAT_RGBA_8888,
    COPYBIT_FORMAT_RGBX_8888    = HAL_PIXEL_FORMAT_RGBX_8888,
    COPYBIT_FORMAT_RGB_888      = HAL_PIXEL_FORMAT_RGB_888,
    COPYBIT_FORMAT_RGB_565      = HAL_PIXEL_FORMAT_RGB_565,
    COPYBIT_FORMAT_BGRA_8888    = HAL_PIXEL_FORMAT_BGRA_8888,
    COPYBIT_FORMAT_RGBA_5551    = HAL_PIXEL_FORMAT_RGBA_5551,
    COPYBIT_FORMAT_RGBA_4444    = HAL_PIXEL_FORMAT_RGBA_4444,
    COPYBIT_FORMAT_YCbCr_422_SP = 0x10,
    COPYBIT_FORMAT_YCrCb_420_SP = 0x11,
};

/* name for copybit_set_parameter */
enum {
    /* rotation of the source image in degrees (0 to 359) */
    COPYBIT_ROTATION_DEG    = 1,
    /* plane alpha value */
    COPYBIT_PLANE_ALPHA     = 2,
    /* enable or disable dithering */
    COPYBIT_DITHER          = 3,
    /* transformation applied (this is a superset of COPYBIT_ROTATION_DEG) */
    COPYBIT_TRANSFORM       = 4,
    /* blurs the copied bitmap. The amount of blurring cannot be changed 
     * at this time. */
    COPYBIT_BLUR            = 5
};

/* values for copybit_set_parameter(COPYBIT_TRANSFORM) */
enum {
    /* flip source image horizontally */
    COPYBIT_TRANSFORM_FLIP_H    = HAL_TRANSFORM_FLIP_H,
    /* flip source image vertically */
    COPYBIT_TRANSFORM_FLIP_V    = HAL_TRANSFORM_FLIP_V,
    /* rotate source image 90 degres */
    COPYBIT_TRANSFORM_ROT_90    = HAL_TRANSFORM_ROT_90,
    /* rotate source image 180 degres */
    COPYBIT_TRANSFORM_ROT_180   = HAL_TRANSFORM_ROT_180,
    /* rotate source image 270 degres */
    COPYBIT_TRANSFORM_ROT_270   = HAL_TRANSFORM_ROT_270,
};

/* enable/disable value copybit_set_parameter */
enum {
    COPYBIT_DISABLE = 0,
    COPYBIT_ENABLE  = 1
};

/* use get_static_info() to query static informations about the hardware */
enum {
    /* Maximum amount of minification supported by the hardware*/
    COPYBIT_MINIFICATION_LIMIT  = 1,
    /* Maximum amount of magnification supported by the hardware */
    COPYBIT_MAGNIFICATION_LIMIT = 2,
    /* Number of fractional bits support by the scaling engine */
    COPYBIT_SCALING_FRAC_BITS   = 3,
    /* Supported rotation step in degres. */
    COPYBIT_ROTATION_STEP_DEG   = 4,
};

/* Image structure */
struct copybit_image_t {
    /* width */
    uint32_t    w;
    /* height */
    uint32_t    h;
    /* format COPYBIT_FORMAT_xxx */
    int32_t     format;
    /* base of buffer with image */
    void        *base;
    /* handle to the image */
    native_handle_t* handle;
};

/* Rectangle */
struct copybit_rect_t {
    /* left */
    int l;
    /* top */
    int t;
    /* right */
    int r;
    /* bottom */
    int b;
};

/* Region */
struct copybit_region_t {
    int (*next)(struct copybit_region_t const *region, struct copybit_rect_t *rect);
};

/**
 * Every hardware module must have a data structure named HAL_MODULE_INFO_SYM
 * and the fields of this data structure must begin with hw_module_t
 * followed by module specific information.
 */
struct copybit_module_t {
    struct hw_module_t common;
};

/**
 * Every device data structure must begin with hw_device_t
 * followed by module specific public methods and attributes.
 */
struct copybit_device_t {
    struct hw_device_t common;

    /**
     * Set a copybit parameter.
     *
     * @param dev from open
     * @param name one for the COPYBIT_NAME_xxx
     * @param value one of the COPYBIT_VALUE_xxx
     *
     * @return 0 if successful
     */
    int (*set_parameter)(struct copybit_device_t *dev, int name, int value);

    /**
     * Get a static copybit information.
     *
     * @param dev from open
     * @param name one of the COPYBIT_STATIC_xxx
     *
     * @return value or -EINVAL if error
     */
    int (*get)(struct copybit_device_t *dev, int name);

    /**
     * Execute the bit blit copy operation
     *
     * @param dev from open
     * @param dst is the destination image
     * @param src is the source image
     * @param region the clip region
     *
     * @return 0 if successful
     */
    int (*blit)(struct copybit_device_t *dev,
                struct copybit_image_t const *dst,
                struct copybit_image_t const *src,
                struct copybit_region_t const *region);

    /**
     * Execute the stretch bit blit copy operation
     *
     * @param dev from open
     * @param dst is the destination image
     * @param src is the source image
     * @param dst_rect is the destination rectangle
     * @param src_rect is the source rectangle
     * @param region the clip region
     *
     * @return 0 if successful
     */
    int (*stretch)(struct copybit_device_t *dev,
                   struct copybit_image_t const *dst,
                   struct copybit_image_t const *src,
                   struct copybit_rect_t const *dst_rect,
                   struct copybit_rect_t const *src_rect,
                   struct copybit_region_t const *region);
};


/** convenience API for opening and closing a device */

static inline int copybit_open(const struct hw_module_t* module, 
        struct copybit_device_t** device) {
    return module->methods->open(module, 
            COPYBIT_HARDWARE_COPYBIT0, (struct hw_device_t**)device);
}

static inline int copybit_close(struct copybit_device_t* device) {
    return device->common.close(&device->common);
}


__END_DECLS

#endif  // ANDROID_COPYBIT_INTERFACE_H
