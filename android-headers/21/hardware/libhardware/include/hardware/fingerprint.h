/*
 * Copyright (C) 2014 The Android Open Source Project
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

#ifndef ANDROID_INCLUDE_HARDWARE_FINGERPRINT_H
#define ANDROID_INCLUDE_HARDWARE_FINGERPRINT_H

#define FINGERPRINT_MODULE_API_VERSION_1_0 HARDWARE_MODULE_API_VERSION(1, 0)
#define FINGERPRINT_HARDWARE_MODULE_ID "fingerprint"

typedef enum fingerprint_msg_type {
    FINGERPRINT_ERROR = -1,
    FINGERPRINT_ACQUIRED = 1,
    FINGERPRINT_PROCESSED = 2,
    FINGERPRINT_TEMPLATE_ENROLLING = 3,
    FINGERPRINT_TEMPLATE_REMOVED = 4
} fingerprint_msg_type_t;

typedef enum fingerprint_error {
    FINGERPRINT_ERROR_HW_UNAVAILABLE = 1,
    FINGERPRINT_ERROR_UNABLE_TO_PROCESS = 2,
    FINGERPRINT_ERROR_TIMEOUT = 3,
    FINGERPRINT_ERROR_NO_SPACE = 4  /* No space available to store a template */
} fingerprint_error_t;

typedef enum fingerprint_acquired_info {
    FINGERPRINT_ACQUIRED_GOOD = 0,
    FINGERPRINT_ACQUIRED_PARTIAL = 1,
    FINGERPRINT_ACQUIRED_INSUFFICIENT = 2,
    FINGERPRINT_ACQUIRED_IMAGER_DIRTY = 4,
    FINGERPRINT_ACQUIRED_TOO_SLOW = 8,
    FINGERPRINT_ACQUIRED_TOO_FAST = 16
} fingerprint_acquired_info_t;

typedef struct fingerprint_enroll {
    uint32_t id;
    /* samples_remaining goes from N (no data collected, but N scans needed)
     * to 0 (no more data is needed to build a template).
     * The progress indication may be augmented by a bitmap encoded indication
     * of finger area that needs to be presented by the user.
     * Bit numbers mapped to physical location:
     *
     *        distal
     *        +-+-+-+
     *        |2|1|0|
     *        |5|4|3|
     * medial |8|7|6| lateral
     *        |b|a|9|
     *        |e|d|c|
     *        +-+-+-+
     *        proximal
     *
     */
    uint16_t data_collected_bmp;
    uint16_t samples_remaining;
} fingerprint_enroll_t;

typedef struct fingerprint_removed {
    uint32_t id;
} fingerprint_removed_t;

typedef struct fingerprint_acquired {
    fingerprint_acquired_info_t acquired_info; /* information about the image */
} fingerprint_acquired_t;

typedef struct fingerprint_processed {
    uint32_t id; /* 0 is a special id and means no match */
} fingerprint_processed_t;

typedef struct fingerprint_msg {
    fingerprint_msg_type_t type;
    union {
        uint64_t raw;
        fingerprint_error_t error;
        fingerprint_enroll_t enroll;
        fingerprint_removed_t removed;
        fingerprint_acquired_t acquired;
        fingerprint_processed_t processed;
    } data;
} fingerprint_msg_t;

/* Callback function type */
typedef void (*fingerprint_notify_t)(fingerprint_msg_t msg);

/* Synchronous operation */
typedef struct fingerprint_device {
    /**
     * Common methods of the fingerprint device. This *must* be the first member
     * of fingerprint_device as users of this structure will cast a hw_device_t
     * to fingerprint_device pointer in contexts where it's known
     * the hw_device_t references a fingerprint_device.
     */
    struct hw_device_t common;

    /*
     * Fingerprint enroll request:
     * Switches the HAL state machine to collect and store a new fingerprint
     * template. Switches back as soon as enroll is complete
     * (fingerprint_msg.type == FINGERPRINT_TEMPLATE_ENROLLING &&
     *  fingerprint_msg.data.enroll.samples_remaining == 0)
     * or after timeout_sec seconds.
     *
     * Function return: 0 if enrollment process can be successfully started
     *                 -1 otherwise. A notify() function may be called
     *                    indicating the error condition.
     */
    int (*enroll)(struct fingerprint_device *dev, uint32_t timeout_sec);

    /*
     * Cancel fingerprint enroll request:
     * Switches the HAL state machine back to accept a fingerprint scan mode.
     * (fingerprint_msg.type == FINGERPRINT_TEMPLATE_ENROLLING &&
     *  fingerprint_msg.data.enroll.samples_remaining == 0)
     * will indicate switch back to the scan mode.
     *
     * Function return: 0 if cancel request is accepted
     *                 -1 otherwise.
     */
    int (*enroll_cancel)(struct fingerprint_device *dev);

    /*
     * Fingerprint remove request:
     * deletes a fingerprint template.
     * If the fingerprint id is 0 the entire template database will be removed.
     * notify() will be called for each template deleted with
     * fingerprint_msg.type == FINGERPRINT_TEMPLATE_REMOVED and
     * fingerprint_msg.data.removed.id indicating each template id removed.
     *
     * Function return: 0 if fingerprint template(s) can be successfully deleted
     *                 -1 otherwise.
     */
    int (*remove)(struct fingerprint_device *dev, uint32_t fingerprint_id);

    /*
     * Set notification callback:
     * Registers a user function that would receive notifications from the HAL
     * The call will block if the HAL state machine is in busy state until HAL
     * leaves the busy state.
     *
     * Function return: 0 if callback function is successfuly registered
     *                 -1 otherwise.
     */
    int (*set_notify)(struct fingerprint_device *dev,
                        fingerprint_notify_t notify);

    /*
     * Client provided callback function to receive notifications.
     * Do not set by hand, use the function above instead.
     */
    fingerprint_notify_t notify;

    /* Reserved for future use. Must be NULL. */
    void* reserved[8 - 4];
} fingerprint_device_t;

typedef struct fingerprint_module {
    /**
     * Common methods of the fingerprint module. This *must* be the first member
     * of fingerprint_module as users of this structure will cast a hw_module_t
     * to fingerprint_module pointer in contexts where it's known
     * the hw_module_t references a fingerprint_module.
     */
    struct hw_module_t common;
} fingerprint_module_t;

#endif  /* ANDROID_INCLUDE_HARDWARE_FINGERPRINT_H */
