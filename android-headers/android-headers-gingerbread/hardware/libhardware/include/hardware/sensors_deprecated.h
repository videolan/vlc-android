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

#define SENSORS_HARDWARE_CONTROL    "control"
#define SENSORS_HARDWARE_DATA       "data"

__BEGIN_DECLS

typedef struct {
    int             sensor;
    union {
        sensors_vec_t   vector;
        sensors_vec_t   orientation;
        sensors_vec_t   acceleration;
        sensors_vec_t   magnetic;
        float           temperature;
        float           distance;
        float           light;
    };
    int64_t         time;
    uint32_t        reserved;
} sensors_data_t;

struct sensors_control_device_t {
    struct hw_device_t common;
    native_handle_t* (*open_data_source)(struct sensors_control_device_t *dev);
    int (*close_data_source)(struct sensors_control_device_t *dev);
    int (*activate)(struct sensors_control_device_t *dev, 
            int handle, int enabled);
    int (*set_delay)(struct sensors_control_device_t *dev, int32_t ms);
    int (*wake)(struct sensors_control_device_t *dev);
};

struct sensors_data_device_t {
    struct hw_device_t common;
    int (*data_open)(struct sensors_data_device_t *dev, native_handle_t* nh);
    int (*data_close)(struct sensors_data_device_t *dev);
    int (*poll)(struct sensors_data_device_t *dev, 
            sensors_data_t* data);
};

static inline int sensors_control_open(const struct hw_module_t* module, 
        struct sensors_control_device_t** device) {
    return module->methods->open(module, 
            SENSORS_HARDWARE_CONTROL, (struct hw_device_t**)device);
}

static inline int sensors_control_close(struct sensors_control_device_t* device) {
    return device->common.close(&device->common);
}

static inline int sensors_data_open(const struct hw_module_t* module, 
        struct sensors_data_device_t** device) {
    return module->methods->open(module, 
            SENSORS_HARDWARE_DATA, (struct hw_device_t**)device);
}

static inline int sensors_data_close(struct sensors_data_device_t* device) {
    return device->common.close(&device->common);
}

__END_DECLS
