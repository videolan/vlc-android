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


#ifndef ANDROID_NFC_HAL_INTERFACE_H
#define ANDROID_NFC_HAL_INTERFACE_H

#include <stdint.h>
#include <strings.h>
#include <sys/cdefs.h>
#include <sys/types.h>

#include <hardware/hardware.h>

__BEGIN_DECLS

#define NFC_HARDWARE_MODULE_ID "nfc"

/*
 * Begin PN544 specific HAL
 */
#define NFC_PN544_CONTROLLER "pn544"

typedef struct nfc_module_t {
    struct hw_module_t common;
} nfc_module_t;

/*
 * PN544 linktypes.
 * UART
 * I2C
 * USB (uses UART DAL)
 */
typedef enum {
    PN544_LINK_TYPE_UART,
    PN544_LINK_TYPE_I2C,
    PN544_LINK_TYPE_USB,
    PN544_LINK_TYPE_INVALID,
} nfc_pn544_linktype;

typedef struct {
    struct hw_device_t common;

    /* The number of EEPROM registers to write */
    uint32_t num_eeprom_settings;

    /* The actual EEPROM settings
     * For PN544, each EEPROM setting is a 4-byte entry,
     * of the format [0x00, addr_msb, addr_lsb, value].
     */
    uint8_t* eeprom_settings;

    /* The link type to which the PN544 is connected */
    nfc_pn544_linktype linktype;

    /* The device node to which the PN544 is connected */
    const char* device_node;

    /* On Crespo we had an I2C issue that would cause us to sometimes read
     * the I2C slave address (0x57) over the bus. libnfc contains
     * a hack to ignore this byte and try to read the length byte
     * again.
     * Set to 0 to disable the workaround, 1 to enable it.
     */
    uint8_t enable_i2c_workaround;
} nfc_pn544_device_t;

static inline int nfc_pn544_open(const struct hw_module_t* module,
        nfc_pn544_device_t** dev) {
    return module->methods->open(module, NFC_PN544_CONTROLLER,
        (struct hw_device_t**) dev);
}

static inline int nfc_pn544_close(nfc_pn544_device_t* dev) {
    return dev->common.close(&dev->common);
}
/*
 * End PN544 specific HAL
 */

__END_DECLS

#endif // ANDROID_NFC_HAL_INTERFACE_H
