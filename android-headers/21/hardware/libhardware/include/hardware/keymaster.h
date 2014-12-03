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

#ifndef ANDROID_HARDWARE_KEYMASTER_H
#define ANDROID_HARDWARE_KEYMASTER_H

#include <stdint.h>
#include <sys/cdefs.h>
#include <sys/types.h>

#include <hardware/hardware.h>

__BEGIN_DECLS

/**
 * The id of this module
 */
#define KEYSTORE_HARDWARE_MODULE_ID "keystore"

#define KEYSTORE_KEYMASTER "keymaster"

/**
 * Settings for "module_api_version" and "hal_api_version"
 * fields in the keymaster_module initialization.
 */
#define KEYMASTER_HEADER_VERSION 3

#define KEYMASTER_MODULE_API_VERSION_0_2  HARDWARE_MODULE_API_VERSION(0, 2)
#define KEYMASTER_DEVICE_API_VERSION_0_2  HARDWARE_DEVICE_API_VERSION_2(0, 2, KEYMASTER_HEADER_VERSION)

#define KEYMASTER_MODULE_API_VERSION_0_3  HARDWARE_MODULE_API_VERSION(0, 3)
#define KEYMASTER_DEVICE_API_VERSION_0_3  HARDWARE_DEVICE_API_VERSION_2(0, 3, KEYMASTER_HEADER_VERSION)

/**
 * Flags for keymaster_device::flags
 */
enum {
    /*
     * Indicates this keymaster implementation does not have hardware that
     * keeps private keys out of user space.
     *
     * This should not be implemented on anything other than the default
     * implementation.
     */
    KEYMASTER_SOFTWARE_ONLY = 1 << 0,

    /*
     * This indicates that the key blobs returned via all the primitives
     * are sufficient to operate on their own without the trusted OS
     * querying userspace to retrieve some other data. Key blobs of
     * this type are normally returned encrypted with a
     * Key Encryption Key (KEK).
     *
     * This is currently used by "vold" to know whether the whole disk
     * encryption secret can be unwrapped without having some external
     * service started up beforehand since the "/data" partition will
     * be unavailable at that point.
     */
    KEYMASTER_BLOBS_ARE_STANDALONE = 1 << 1,

    /*
     * Indicates that the keymaster module supports DSA keys.
     */
    KEYMASTER_SUPPORTS_DSA = 1 << 2,

    /*
     * Indicates that the keymaster module supports EC keys.
     */
    KEYMASTER_SUPPORTS_EC = 1 << 3,
};

struct keystore_module {
    /**
     * Common methods of the keystore module.  This *must* be the first member of
     * keystore_module as users of this structure will cast a hw_module_t to
     * keystore_module pointer in contexts where it's known the hw_module_t references a
     * keystore_module.
     */
    hw_module_t common;
};

/**
 * Asymmetric key pair types.
 */
typedef enum {
    TYPE_RSA = 1,
    TYPE_DSA = 2,
    TYPE_EC = 3,
} keymaster_keypair_t;

/**
 * Parameters needed to generate an RSA key.
 */
typedef struct {
    uint32_t modulus_size;
    uint64_t public_exponent;
} keymaster_rsa_keygen_params_t;

/**
 * Parameters needed to generate a DSA key.
 */
typedef struct {
    uint32_t key_size;
    uint32_t generator_len;
    uint32_t prime_p_len;
    uint32_t prime_q_len;
    const uint8_t* generator;
    const uint8_t* prime_p;
    const uint8_t* prime_q;
} keymaster_dsa_keygen_params_t;

/**
 * Parameters needed to generate an EC key.
 *
 * Field size is the only parameter in version 2. The sizes correspond to these required curves:
 *
 * 192 = NIST P-192
 * 224 = NIST P-224
 * 256 = NIST P-256
 * 384 = NIST P-384
 * 521 = NIST P-521
 *
 * The parameters for these curves are available at: http://www.nsa.gov/ia/_files/nist-routines.pdf
 * in Chapter 4.
 */
typedef struct {
    uint32_t field_size;
} keymaster_ec_keygen_params_t;

/**
 * Digest type.
 */
typedef enum {
    DIGEST_NONE,
} keymaster_digest_t;

/**
 * Type of padding used for RSA operations.
 */
typedef enum {
    PADDING_NONE,
} keymaster_rsa_padding_t;


typedef struct {
    keymaster_digest_t digest_type;
} keymaster_dsa_sign_params_t;

typedef struct {
    keymaster_digest_t digest_type;
} keymaster_ec_sign_params_t;

typedef struct {
    keymaster_digest_t digest_type;
    keymaster_rsa_padding_t padding_type;
} keymaster_rsa_sign_params_t;

/**
 * The parameters that can be set for a given keymaster implementation.
 */
struct keymaster_device {
    /**
     * Common methods of the keymaster device.  This *must* be the first member of
     * keymaster_device as users of this structure will cast a hw_device_t to
     * keymaster_device pointer in contexts where it's known the hw_device_t references a
     * keymaster_device.
     */
    struct hw_device_t common;

    /**
     * THIS IS DEPRECATED. Use the new "module_api_version" and "hal_api_version"
     * fields in the keymaster_module initialization instead.
     */
    uint32_t client_version;

    /**
     * See flags defined for keymaster_device::flags above.
     */
    uint32_t flags;

    void* context;

    /**
     * Generates a public and private key. The key-blob returned is opaque
     * and must subsequently provided for signing and verification.
     *
     * Returns: 0 on success or an error code less than 0.
     */
    int (*generate_keypair)(const struct keymaster_device* dev,
            const keymaster_keypair_t key_type, const void* key_params,
            uint8_t** key_blob, size_t* key_blob_length);

    /**
     * Imports a public and private key pair. The imported keys will be in
     * PKCS#8 format with DER encoding (Java standard). The key-blob
     * returned is opaque and will be subsequently provided for signing
     * and verification.
     *
     * Returns: 0 on success or an error code less than 0.
     */
    int (*import_keypair)(const struct keymaster_device* dev,
            const uint8_t* key, const size_t key_length,
            uint8_t** key_blob, size_t* key_blob_length);

    /**
     * Gets the public key part of a key pair. The public key must be in
     * X.509 format (Java standard) encoded byte array.
     *
     * Returns: 0 on success or an error code less than 0.
     * On error, x509_data should not be allocated.
     */
    int (*get_keypair_public)(const struct keymaster_device* dev,
            const uint8_t* key_blob, const size_t key_blob_length,
            uint8_t** x509_data, size_t* x509_data_length);

    /**
     * Deletes the key pair associated with the key blob.
     *
     * This function is optional and should be set to NULL if it is not
     * implemented.
     *
     * Returns 0 on success or an error code less than 0.
     */
    int (*delete_keypair)(const struct keymaster_device* dev,
            const uint8_t* key_blob, const size_t key_blob_length);

    /**
     * Deletes all keys in the hardware keystore. Used when keystore is
     * reset completely.
     *
     * This function is optional and should be set to NULL if it is not
     * implemented.
     *
     * Returns 0 on success or an error code less than 0.
     */
    int (*delete_all)(const struct keymaster_device* dev);

    /**
     * Signs data using a key-blob generated before. This can use either
     * an asymmetric key or a secret key.
     *
     * Returns: 0 on success or an error code less than 0.
     */
    int (*sign_data)(const struct keymaster_device* dev,
            const void* signing_params,
            const uint8_t* key_blob, const size_t key_blob_length,
            const uint8_t* data, const size_t data_length,
            uint8_t** signed_data, size_t* signed_data_length);

    /**
     * Verifies data signed with a key-blob. This can use either
     * an asymmetric key or a secret key.
     *
     * Returns: 0 on successful verification or an error code less than 0.
     */
    int (*verify_data)(const struct keymaster_device* dev,
            const void* signing_params,
            const uint8_t* key_blob, const size_t key_blob_length,
            const uint8_t* signed_data, const size_t signed_data_length,
            const uint8_t* signature, const size_t signature_length);
};
typedef struct keymaster_device keymaster_device_t;


/* Convenience API for opening and closing keymaster devices */

static inline int keymaster_open(const struct hw_module_t* module,
        keymaster_device_t** device)
{
    int rc = module->methods->open(module, KEYSTORE_KEYMASTER,
            (struct hw_device_t**) device);

    return rc;
}

static inline int keymaster_close(keymaster_device_t* device)
{
    return device->common.close(&device->common);
}

__END_DECLS

#endif  // ANDROID_HARDWARE_KEYMASTER_H
