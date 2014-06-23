/*
 * Copyright (C) 2013 The Android Open Source Project
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


#ifndef ANDROID_INCLUDE_BT_GATT_CLIENT_H
#define ANDROID_INCLUDE_BT_GATT_CLIENT_H

#include <stdint.h>
#include "bt_gatt_types.h"

__BEGIN_DECLS

/**
 * Buffer sizes for maximum attribute length and maximum read/write
 * operation buffer size.
 */
#define BTGATT_MAX_ATTR_LEN 600

/** Buffer type for unformatted reads/writes */
typedef struct
{
    uint8_t             value[BTGATT_MAX_ATTR_LEN];
    uint16_t            len;
} btgatt_unformatted_value_t;

/** Parameters for GATT read operations */
typedef struct
{
    btgatt_srvc_id_t    srvc_id;
    btgatt_gatt_id_t    char_id;
    btgatt_gatt_id_t    descr_id;
    btgatt_unformatted_value_t value;
    uint16_t            value_type;
    uint8_t             status;
} btgatt_read_params_t;

/** Parameters for GATT write operations */
typedef struct
{
    btgatt_srvc_id_t    srvc_id;
    btgatt_gatt_id_t    char_id;
    btgatt_gatt_id_t    descr_id;
    uint8_t             status;
} btgatt_write_params_t;

/** Attribute change notification parameters */
typedef struct
{
    uint8_t             value[BTGATT_MAX_ATTR_LEN];
    bt_bdaddr_t         bda;
    btgatt_srvc_id_t    srvc_id;
    btgatt_gatt_id_t    char_id;
    uint16_t            len;
    uint8_t             is_notify;
} btgatt_notify_params_t;

typedef struct
{
    bt_bdaddr_t        *bda1;
    bt_uuid_t          *uuid1;
    uint16_t            u1;
    uint16_t            u2;
    uint16_t            u3;
    uint16_t            u4;
    uint16_t            u5;
} btgatt_test_params_t;

/** BT-GATT Client callback structure. */

/** Callback invoked in response to register_client */
typedef void (*register_client_callback)(int status, int client_if,
                bt_uuid_t *app_uuid);

/** Callback for scan results */
typedef void (*scan_result_callback)(bt_bdaddr_t* bda, int rssi, uint8_t* adv_data);

/** GATT open callback invoked in response to open */
typedef void (*connect_callback)(int conn_id, int status, int client_if, bt_bdaddr_t* bda);

/** Callback invoked in response to close */
typedef void (*disconnect_callback)(int conn_id, int status,
                int client_if, bt_bdaddr_t* bda);

/**
 * Invoked in response to search_service when the GATT service search
 * has been completed.
 */
typedef void (*search_complete_callback)(int conn_id, int status);

/** Reports GATT services on a remote device */
typedef void (*search_result_callback)( int conn_id, btgatt_srvc_id_t *srvc_id);

/** GATT characteristic enumeration result callback */
typedef void (*get_characteristic_callback)(int conn_id, int status,
                btgatt_srvc_id_t *srvc_id, btgatt_gatt_id_t *char_id,
                int char_prop);

/** GATT descriptor enumeration result callback */
typedef void (*get_descriptor_callback)(int conn_id, int status,
                btgatt_srvc_id_t *srvc_id, btgatt_gatt_id_t *char_id,
                btgatt_gatt_id_t *descr_id);

/** GATT included service enumeration result callback */
typedef void (*get_included_service_callback)(int conn_id, int status,
                btgatt_srvc_id_t *srvc_id, btgatt_srvc_id_t *incl_srvc_id);

/** Callback invoked in response to [de]register_for_notification */
typedef void (*register_for_notification_callback)(int conn_id,
                int registered, int status, btgatt_srvc_id_t *srvc_id,
                btgatt_gatt_id_t *char_id);

/**
 * Remote device notification callback, invoked when a remote device sends
 * a notification or indication that a client has registered for.
 */
typedef void (*notify_callback)(int conn_id, btgatt_notify_params_t *p_data);

/** Reports result of a GATT read operation */
typedef void (*read_characteristic_callback)(int conn_id, int status,
                btgatt_read_params_t *p_data);

/** GATT write characteristic operation callback */
typedef void (*write_characteristic_callback)(int conn_id, int status,
                btgatt_write_params_t *p_data);

/** GATT execute prepared write callback */
typedef void (*execute_write_callback)(int conn_id, int status);

/** Callback invoked in response to read_descriptor */
typedef void (*read_descriptor_callback)(int conn_id, int status,
                btgatt_read_params_t *p_data);

/** Callback invoked in response to write_descriptor */
typedef void (*write_descriptor_callback)(int conn_id, int status,
                btgatt_write_params_t *p_data);

/** Callback triggered in response to read_remote_rssi */
typedef void (*read_remote_rssi_callback)(int client_if, bt_bdaddr_t* bda,
                                          int rssi, int status);

/**
 * Callback indicationg the status of a listen() operation
 */
typedef void (*listen_callback)(int status, int server_if);

typedef struct {
    register_client_callback            register_client_cb;
    scan_result_callback                scan_result_cb;
    connect_callback                    open_cb;
    disconnect_callback                 close_cb;
    search_complete_callback            search_complete_cb;
    search_result_callback              search_result_cb;
    get_characteristic_callback         get_characteristic_cb;
    get_descriptor_callback             get_descriptor_cb;
    get_included_service_callback       get_included_service_cb;
    register_for_notification_callback  register_for_notification_cb;
    notify_callback                     notify_cb;
    read_characteristic_callback        read_characteristic_cb;
    write_characteristic_callback       write_characteristic_cb;
    read_descriptor_callback            read_descriptor_cb;
    write_descriptor_callback           write_descriptor_cb;
    execute_write_callback              execute_write_cb;
    read_remote_rssi_callback           read_remote_rssi_cb;
    listen_callback                     listen_cb;
} btgatt_client_callbacks_t;

/** Represents the standard BT-GATT client interface. */

typedef struct {
    /** Registers a GATT client application with the stack */
    bt_status_t (*register_client)( bt_uuid_t *uuid );

    /** Unregister a client application from the stack */
    bt_status_t (*unregister_client)(int client_if );

    /** Start or stop LE device scanning */
    bt_status_t (*scan)( int client_if, bool start );

    /** Create a connection to a remote LE or dual-mode device */
    bt_status_t (*connect)( int client_if, const bt_bdaddr_t *bd_addr,
                         bool is_direct );

    /** Disconnect a remote device or cancel a pending connection */
    bt_status_t (*disconnect)( int client_if, const bt_bdaddr_t *bd_addr,
                    int conn_id);

    /** Start or stop advertisements to listen for incoming connections */
    bt_status_t (*listen)(int client_if, bool start);

    /** Clear the attribute cache for a given device */
    bt_status_t (*refresh)( int client_if, const bt_bdaddr_t *bd_addr );

    /**
     * Enumerate all GATT services on a connected device.
     * Optionally, the results can be filtered for a given UUID.
     */
    bt_status_t (*search_service)(int conn_id, bt_uuid_t *filter_uuid );

    /**
     * Enumerate included services for a given service.
     * Set start_incl_srvc_id to NULL to get the first included service.
     */
    bt_status_t (*get_included_service)( int conn_id, btgatt_srvc_id_t *srvc_id,
                                         btgatt_srvc_id_t *start_incl_srvc_id);

    /**
     * Enumerate characteristics for a given service.
     * Set start_char_id to NULL to get the first characteristic.
     */
    bt_status_t (*get_characteristic)( int conn_id,
                    btgatt_srvc_id_t *srvc_id, btgatt_gatt_id_t *start_char_id);

    /**
     * Enumerate descriptors for a given characteristic.
     * Set start_descr_id to NULL to get the first descriptor.
     */
    bt_status_t (*get_descriptor)( int conn_id,
                    btgatt_srvc_id_t *srvc_id, btgatt_gatt_id_t *char_id,
                    btgatt_gatt_id_t *start_descr_id);

    /** Read a characteristic on a remote device */
    bt_status_t (*read_characteristic)( int conn_id,
                    btgatt_srvc_id_t *srvc_id, btgatt_gatt_id_t *char_id,
                    int auth_req );

    /** Write a remote characteristic */
    bt_status_t (*write_characteristic)(int conn_id,
                    btgatt_srvc_id_t *srvc_id, btgatt_gatt_id_t *char_id,
                    int write_type, int len, int auth_req,
                    char* p_value);

    /** Read the descriptor for a given characteristic */
    bt_status_t (*read_descriptor)(int conn_id,
                    btgatt_srvc_id_t *srvc_id, btgatt_gatt_id_t *char_id,
                    btgatt_gatt_id_t *descr_id, int auth_req);

    /** Write a remote descriptor for a given characteristic */
    bt_status_t (*write_descriptor)( int conn_id,
                    btgatt_srvc_id_t *srvc_id, btgatt_gatt_id_t *char_id,
                    btgatt_gatt_id_t *descr_id, int write_type, int len,
                    int auth_req, char* p_value);

    /** Execute a prepared write operation */
    bt_status_t (*execute_write)(int conn_id, int execute);

    /**
     * Register to receive notifications or indications for a given
     * characteristic
     */
    bt_status_t (*register_for_notification)( int client_if,
                    const bt_bdaddr_t *bd_addr, btgatt_srvc_id_t *srvc_id,
                    btgatt_gatt_id_t *char_id);

    /** Deregister a previous request for notifications/indications */
    bt_status_t (*deregister_for_notification)( int client_if,
                    const bt_bdaddr_t *bd_addr, btgatt_srvc_id_t *srvc_id,
                    btgatt_gatt_id_t *char_id);

    /** Request RSSI for a given remote device */
    bt_status_t (*read_remote_rssi)( int client_if, const bt_bdaddr_t *bd_addr);

    /** Determine the type of the remote device (LE, BR/EDR, Dual-mode) */
    int (*get_device_type)( const bt_bdaddr_t *bd_addr );

    /** Set the advertising data or scan response data */
    bt_status_t (*set_adv_data)(int server_if, bool set_scan_rsp, bool include_name,
                    bool include_txpower, int min_interval, int max_interval, int appearance,
                    uint16_t manufacturer_len, char* manufacturer_data);

    /** Test mode interface */
    bt_status_t (*test_command)( int command, btgatt_test_params_t* params);
} btgatt_client_interface_t;

__END_DECLS

#endif /* ANDROID_INCLUDE_BT_GATT_CLIENT_H */
