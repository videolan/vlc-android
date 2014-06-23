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

#ifndef ANDROID_SENSORS_INTERFACE_H
#define ANDROID_SENSORS_INTERFACE_H

#include <stdint.h>
#include <sys/cdefs.h>
#include <sys/types.h>

#include <hardware/hardware.h>
#include <cutils/native_handle.h>

__BEGIN_DECLS

/**
 * The id of this module
 */
#define SENSORS_HARDWARE_MODULE_ID "sensors"

/**
 * Name of the sensors device to open
 */
#define SENSORS_HARDWARE_POLL       "poll"

/**
 * Handles must be higher than SENSORS_HANDLE_BASE and must be unique.
 * A Handle identifies a given sensors. The handle is used to activate
 * and/or deactivate sensors.
 * In this version of the API there can only be 256 handles.
 */
#define SENSORS_HANDLE_BASE             0
#define SENSORS_HANDLE_BITS             8
#define SENSORS_HANDLE_COUNT            (1<<SENSORS_HANDLE_BITS)


/**
 * Sensor types
 */
#define SENSOR_TYPE_ACCELEROMETER       1
#define SENSOR_TYPE_MAGNETIC_FIELD      2
#define SENSOR_TYPE_ORIENTATION         3
#define SENSOR_TYPE_GYROSCOPE           4
#define SENSOR_TYPE_LIGHT               5
#define SENSOR_TYPE_PRESSURE            6
#define SENSOR_TYPE_TEMPERATURE         7
#define SENSOR_TYPE_PROXIMITY           8
#define SENSOR_TYPE_GRAVITY             9
#define SENSOR_TYPE_LINEAR_ACCELERATION 10
#define SENSOR_TYPE_ROTATION_VECTOR     11

/**
 * Values returned by the accelerometer in various locations in the universe.
 * all values are in SI units (m/s^2)
 */

#define GRAVITY_SUN             (275.0f)
#define GRAVITY_EARTH           (9.80665f)

/** Maximum magnetic field on Earth's surface */
#define MAGNETIC_FIELD_EARTH_MAX    (60.0f)

/** Minimum magnetic field on Earth's surface */
#define MAGNETIC_FIELD_EARTH_MIN    (30.0f)


/**
 * status of each sensor
 */

#define SENSOR_STATUS_UNRELIABLE        0
#define SENSOR_STATUS_ACCURACY_LOW      1
#define SENSOR_STATUS_ACCURACY_MEDIUM   2
#define SENSOR_STATUS_ACCURACY_HIGH     3

/**
 * Definition of the axis
 * ----------------------
 *
 * This API is relative to the screen of the device in its default orientation,
 * that is, if the device can be used in portrait or landscape, this API
 * is only relative to the NATURAL orientation of the screen. In other words,
 * the axis are not swapped when the device's screen orientation changes.
 * Higher level services /may/ perform this transformation.
 *
 *   x<0         x>0
 *                ^
 *                |
 *    +-----------+-->  y>0
 *    |           |
 *    |           |
 *    |           |
 *    |           |   / z<0
 *    |           |  /
 *    |           | /
 *    O-----------+/
 *    |[]  [ ]  []/
 *    +----------/+     y<0
 *              /
 *             /
 *           |/ z>0 (toward the sky)
 *
 *    O: Origin (x=0,y=0,z=0)
 *
 *
 * Orientation
 * ----------- 
 * 
 * All values are angles in degrees.
 * 
 * Orientation sensors return sensor events for all 3 axes at a constant
 * rate defined by setDelay().
 *
 * azimuth: angle between the magnetic north direction and the Y axis, around 
 *  the Z axis (0<=azimuth<360).
 *      0=North, 90=East, 180=South, 270=West
 * 
 * pitch: Rotation around X axis (-180<=pitch<=180), with positive values when
 *  the z-axis moves toward the y-axis.
 *
 * roll: Rotation around Y axis (-90<=roll<=90), with positive values when
 *  the x-axis moves towards the z-axis.
 *
 * Note: For historical reasons the roll angle is positive in the clockwise
 *  direction (mathematically speaking, it should be positive in the
 *  counter-clockwise direction):
 *
 *                Z
 *                ^
 *  (+roll)  .--> |
 *          /     |
 *         |      |  roll: rotation around Y axis
 *     X <-------(.)
 *                 Y
 *       note that +Y == -roll
 *
 *
 *
 * Note: This definition is different from yaw, pitch and roll used in aviation
 *  where the X axis is along the long side of the plane (tail to nose).
 *  
 *  
 * Acceleration
 * ------------
 *
 *  All values are in SI units (m/s^2) and measure the acceleration of the
 *  device minus the force of gravity.
 *  
 *  Acceleration sensors return sensor events for all 3 axes at a constant
 *  rate defined by setDelay().
 *
 *  x: Acceleration minus Gx on the x-axis 
 *  y: Acceleration minus Gy on the y-axis 
 *  z: Acceleration minus Gz on the z-axis
 *  
 *  Examples:
 *    When the device lies flat on a table and is pushed on its left side
 *    toward the right, the x acceleration value is positive.
 *    
 *    When the device lies flat on a table, the acceleration value is +9.81,
 *    which correspond to the acceleration of the device (0 m/s^2) minus the
 *    force of gravity (-9.81 m/s^2).
 *    
 *    When the device lies flat on a table and is pushed toward the sky, the
 *    acceleration value is greater than +9.81, which correspond to the
 *    acceleration of the device (+A m/s^2) minus the force of 
 *    gravity (-9.81 m/s^2).
 *    
 *    
 * Magnetic Field
 * --------------
 * 
 *  All values are in micro-Tesla (uT) and measure the ambient magnetic
 *  field in the X, Y and Z axis.
 *
 *  Magnetic Field sensors return sensor events for all 3 axes at a constant
 *  rate defined by setDelay().
 *
 * Gyroscope
 * ---------
 *  All values are in radians/second and measure the rate of rotation
 *  around the X, Y and Z axis.  The coordinate system is the same as is
 *  used for the acceleration sensor. Rotation is positive in the
 *  counter-clockwise direction (right-hand rule). That is, an observer
 *  looking from some positive location on the x, y or z axis at a device
 *  positioned on the origin would report positive rotation if the device
 *  appeared to be rotating counter clockwise. Note that this is the
 *  standard mathematical definition of positive rotation and does not agree
 *  with the definition of roll given earlier.
 *  The range should at least be 17.45 rad/s (ie: ~1000 deg/s).
 *
 * Proximity
 * ---------
 *
 * The distance value is measured in centimeters.  Note that some proximity
 * sensors only support a binary "close" or "far" measurement.  In this case,
 * the sensor should report its maxRange value in the "far" state and a value
 * less than maxRange in the "near" state.
 *
 * Proximity sensors report a value only when it changes and each time the
 * sensor is enabled. setDelay() is ignored.
 *
 * Light
 * -----
 *
 * The light sensor value is returned in SI lux units.
 *
 * Light sensors report a value only when it changes and each time the
 * sensor is enabled. setDelay() is ignored.
 *
 * Pressure
 * --------
 *
 * The pressure sensor value is returned in hectopascal (hPa)
 *
 * Pressure sensors report events at a constant rate defined by setDelay().
 *
 * Gravity
 * -------
 * A gravity output indicates the direction of and magnitude of gravity in the devices's
 * coordinates.  On Earth, the magnitude is 9.8.  Units are m/s^2.  The coordinate system
 * is the same as is used for the acceleration sensor.
 * When the device is at rest, the output of the gravity sensor should be identical
 * to that of the accelerometer.
 *
 * Linear Acceleration
 * -------------------
 * Indicates the linear acceleration of the device in device coordinates, not including gravity.
 * This output is essentially Acceleration - Gravity.  Units are m/s^2.  The coordinate system is
 * the same as is used for the acceleration sensor.
 * The output of the accelerometer, gravity and  linear-acceleration sensors must obey the
 * following relation:
 *
 *   acceleration = gravity + linear-acceleration
 *
 *
 * Rotation Vector
 * ---------------
 * A rotation vector represents the orientation of the device as a combination
 * of an angle and an axis, in which the device has rotated through an angle
 * theta around an axis <x, y, z>. The three elements of the rotation vector
 * are <x*sin(theta/2), y*sin(theta/2), z*sin(theta/2)>, such that the magnitude
 * of the rotation vector is equal to sin(theta/2), and the direction of the
 * rotation vector is equal to the direction of the axis of rotation. The three
 * elements of the rotation vector are equal to the last three components of a
 * unit quaternion <cos(theta/2), x*sin(theta/2), y*sin(theta/2), z*sin(theta/2)>.
 * Elements of the rotation vector are unitless.  The x, y, and z axis are defined
 * in the same was as for the acceleration sensor.
 *
 * The rotation-vector is stored as:
 *
 *   sensors_event_t.data[0] = x*sin(theta/2)
 *   sensors_event_t.data[1] = y*sin(theta/2)
 *   sensors_event_t.data[2] = z*sin(theta/2)
 *   sensors_event_t.data[3] =   cos(theta/2)
 *
 */

typedef struct {
    union {
        float v[3];
        struct {
            float x;
            float y;
            float z;
        };
        struct {
            float azimuth;
            float pitch;
            float roll;
        };
    };
    int8_t status;
    uint8_t reserved[3];
} sensors_vec_t;

/**
 * Union of the various types of sensor data
 * that can be returned.
 */
typedef struct sensors_event_t {
    /* must be sizeof(struct sensors_event_t) */
    int32_t version;

    /* sensor identifier */
    int32_t sensor;

    /* sensor type */
    int32_t type;

    /* reserved */
    int32_t reserved0;

    /* time is in nanosecond */
    int64_t timestamp;

    union {
        float           data[16];

        /* acceleration values are in meter per second per second (m/s^2) */
        sensors_vec_t   acceleration;

        /* magnetic vector values are in micro-Tesla (uT) */
        sensors_vec_t   magnetic;

        /* orientation values are in degrees */
        sensors_vec_t   orientation;

        /* gyroscope values are in rad/s */
        sensors_vec_t   gyro;

        /* temperature is in degrees centigrade (Celsius) */
        float           temperature;

        /* distance in centimeters */
        float           distance;

        /* light in SI lux units */
        float           light;

        /* pressure in hectopascal (hPa) */
        float           pressure;
    };
    uint32_t        reserved1[4];
} sensors_event_t;



struct sensor_t;

/**
 * Every hardware module must have a data structure named HAL_MODULE_INFO_SYM
 * and the fields of this data structure must begin with hw_module_t
 * followed by module specific information.
 */
struct sensors_module_t {
    struct hw_module_t common;

    /**
     * Enumerate all available sensors. The list is returned in "list".
     * @return number of sensors in the list
     */
    int (*get_sensors_list)(struct sensors_module_t* module,
            struct sensor_t const** list);
};

struct sensor_t {
    /* name of this sensors */
    const char*     name;
    /* vendor of the hardware part */
    const char*     vendor;
    /* version of the hardware part + driver. The value of this field is
     * left to the implementation and doesn't have to be monotonically
     * increasing.
     */    
    int             version;
    /* handle that identifies this sensors. This handle is used to activate
     * and deactivate this sensor. The value of the handle must be 8 bits
     * in this version of the API. 
     */
    int             handle;
    /* this sensor's type. */
    int             type;
    /* maximaum range of this sensor's value in SI units */
    float           maxRange;
    /* smallest difference between two values reported by this sensor */
    float           resolution;
    /* rough estimate of this sensor's power consumption in mA */
    float           power;
    /* minimum delay allowed between events in microseconds. A value of zero
     * means that this sensor doesn't report events at a constant rate, but
     * rather only when a new data is available */
    int32_t         minDelay;
    /* reserved fields, must be zero */
    void*           reserved[8];
};


/**
 * Every device data structure must begin with hw_device_t
 * followed by module specific public methods and attributes.
 */
struct sensors_poll_device_t {
    struct hw_device_t common;

    /** Activate/deactivate one sensor.
     *
     * @param handle is the handle of the sensor to change.
     * @param enabled set to 1 to enable, or 0 to disable the sensor.
     *
     * @return 0 on success, negative errno code otherwise
     */
    int (*activate)(struct sensors_poll_device_t *dev,
            int handle, int enabled);

    /**
     * Set the delay between sensor events in nanoseconds for a given sensor.
     * It is an error to set a delay inferior to the value defined by
     * sensor_t::minDelay. If sensor_t::minDelay is zero, setDelay() is
     * ignored and returns 0.
     *
     * @return 0 if successful, < 0 on error
     */
    int (*setDelay)(struct sensors_poll_device_t *dev,
            int handle, int64_t ns);

    /**
     * Returns an array of sensor data.
     * This function must block until events are available.
     *
     * @return the number of events read on success, or -errno in case of an error.
     * This function should never return 0 (no event).
     *
     */
    int (*poll)(struct sensors_poll_device_t *dev,
            sensors_event_t* data, int count);
};

/** convenience API for opening and closing a device */

static inline int sensors_open(const struct hw_module_t* module,
        struct sensors_poll_device_t** device) {
    return module->methods->open(module,
            SENSORS_HARDWARE_POLL, (struct hw_device_t**)device);
}

static inline int sensors_close(struct sensors_poll_device_t* device) {
    return device->common.close(&device->common);
}

__END_DECLS

#include <hardware/sensors_deprecated.h>

#endif  // ANDROID_SENSORS_INTERFACE_H
