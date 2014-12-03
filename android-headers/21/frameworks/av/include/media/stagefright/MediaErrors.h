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

#ifndef MEDIA_ERRORS_H_

#define MEDIA_ERRORS_H_

#include <utils/Errors.h>

namespace android {

enum {
    // status_t map for errors in the media framework
    // OK or NO_ERROR or 0 represents no error.

    // See system/core/include/utils/Errors.h
    // System standard errors from -1 through (possibly) -133
    //
    // Errors with special meanings and side effects.
    // INVALID_OPERATION:  Operation attempted in an illegal state (will try to signal to app).
    // DEAD_OBJECT:        Signal from CodecBase to MediaCodec that MediaServer has died.
    // NAME_NOT_FOUND:     Signal from CodecBase to MediaCodec that the component was not found.

    // Media errors
    MEDIA_ERROR_BASE        = -1000,

    ERROR_ALREADY_CONNECTED = MEDIA_ERROR_BASE,
    ERROR_NOT_CONNECTED     = MEDIA_ERROR_BASE - 1,
    ERROR_UNKNOWN_HOST      = MEDIA_ERROR_BASE - 2,
    ERROR_CANNOT_CONNECT    = MEDIA_ERROR_BASE - 3,
    ERROR_IO                = MEDIA_ERROR_BASE - 4,
    ERROR_CONNECTION_LOST   = MEDIA_ERROR_BASE - 5,
    ERROR_MALFORMED         = MEDIA_ERROR_BASE - 7,
    ERROR_OUT_OF_RANGE      = MEDIA_ERROR_BASE - 8,
    ERROR_BUFFER_TOO_SMALL  = MEDIA_ERROR_BASE - 9,
    ERROR_UNSUPPORTED       = MEDIA_ERROR_BASE - 10,
    ERROR_END_OF_STREAM     = MEDIA_ERROR_BASE - 11,

    // Not technically an error.
    INFO_FORMAT_CHANGED    = MEDIA_ERROR_BASE - 12,
    INFO_DISCONTINUITY     = MEDIA_ERROR_BASE - 13,
    INFO_OUTPUT_BUFFERS_CHANGED = MEDIA_ERROR_BASE - 14,

    // The following constant values should be in sync with
    // drm/drm_framework_common.h
    DRM_ERROR_BASE = -2000,

    ERROR_DRM_UNKNOWN                        = DRM_ERROR_BASE,
    ERROR_DRM_NO_LICENSE                     = DRM_ERROR_BASE - 1,
    ERROR_DRM_LICENSE_EXPIRED                = DRM_ERROR_BASE - 2,
    ERROR_DRM_SESSION_NOT_OPENED             = DRM_ERROR_BASE - 3,
    ERROR_DRM_DECRYPT_UNIT_NOT_INITIALIZED   = DRM_ERROR_BASE - 4,
    ERROR_DRM_DECRYPT                        = DRM_ERROR_BASE - 5,
    ERROR_DRM_CANNOT_HANDLE                  = DRM_ERROR_BASE - 6,
    ERROR_DRM_TAMPER_DETECTED                = DRM_ERROR_BASE - 7,
    ERROR_DRM_NOT_PROVISIONED                = DRM_ERROR_BASE - 8,
    ERROR_DRM_DEVICE_REVOKED                 = DRM_ERROR_BASE - 9,
    ERROR_DRM_RESOURCE_BUSY                  = DRM_ERROR_BASE - 10,
    ERROR_DRM_INSUFFICIENT_OUTPUT_PROTECTION = DRM_ERROR_BASE - 11,
    ERROR_DRM_LAST_USED_ERRORCODE            = DRM_ERROR_BASE - 11,

    ERROR_DRM_VENDOR_MAX                     = DRM_ERROR_BASE - 500,
    ERROR_DRM_VENDOR_MIN                     = DRM_ERROR_BASE - 999,

    // Heartbeat Error Codes
    HEARTBEAT_ERROR_BASE = -3000,
    ERROR_HEARTBEAT_TERMINATE_REQUESTED                     = HEARTBEAT_ERROR_BASE,

    // NDK Error codes
    // frameworks/av/include/ndk/NdkMediaError.h
    // from -10000 (0xFFFFD8F0 - 0xFFFFD8EC)
    // from -20000 (0xFFFFB1E0 - 0xFFFFB1D7)

    // Codec errors are permitted from 0x80001000 through 0x9000FFFF
    ERROR_CODEC_MAX    = (signed)0x9000FFFF,
    ERROR_CODEC_MIN    = (signed)0x80001000,

    // System unknown errors from 0x80000000 - 0x80000007 (INT32_MIN + 7)
    // See system/core/include/utils/Errors.h
};

// action codes for MediaCodecs that tell the upper layer and application
// the severity of any error.
enum ActionCode {
    ACTION_CODE_FATAL,
    ACTION_CODE_TRANSIENT,
    ACTION_CODE_RECOVERABLE,
};

// returns true if err is a recognized DRM error code
static inline bool isCryptoError(status_t err) {
    return (ERROR_DRM_LAST_USED_ERRORCODE <= err && err <= ERROR_DRM_UNKNOWN)
            || (ERROR_DRM_VENDOR_MIN <= err && err <= ERROR_DRM_VENDOR_MAX);
}

}  // namespace android

#endif  // MEDIA_ERRORS_H_
