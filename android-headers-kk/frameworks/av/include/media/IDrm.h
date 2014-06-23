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

#include <binder/IInterface.h>
#include <media/stagefright/foundation/ABase.h>
#include <media/drm/DrmAPI.h>
#include <media/IDrmClient.h>

#ifndef ANDROID_IDRM_H_

#define ANDROID_IDRM_H_

namespace android {

struct AString;

struct IDrm : public IInterface {
    DECLARE_META_INTERFACE(Drm);

    virtual status_t initCheck() const = 0;

    virtual bool isCryptoSchemeSupported(const uint8_t uuid[16], const String8 &mimeType) = 0;

    virtual status_t createPlugin(const uint8_t uuid[16]) = 0;

    virtual status_t destroyPlugin() = 0;

    virtual status_t openSession(Vector<uint8_t> &sessionId) = 0;

    virtual status_t closeSession(Vector<uint8_t> const &sessionId) = 0;

    virtual status_t
        getKeyRequest(Vector<uint8_t> const &sessionId,
                      Vector<uint8_t> const &initData,
                      String8 const &mimeType, DrmPlugin::KeyType keyType,
                      KeyedVector<String8, String8> const &optionalParameters,
                      Vector<uint8_t> &request, String8 &defaultUrl) = 0;

    virtual status_t provideKeyResponse(Vector<uint8_t> const &sessionId,
                                        Vector<uint8_t> const &response,
                                        Vector<uint8_t> &keySetId) = 0;

    virtual status_t removeKeys(Vector<uint8_t> const &keySetId) = 0;

    virtual status_t restoreKeys(Vector<uint8_t> const &sessionId,
                                 Vector<uint8_t> const &keySetId) = 0;

    virtual status_t queryKeyStatus(Vector<uint8_t> const &sessionId,
                                    KeyedVector<String8, String8> &infoMap) const = 0;

    virtual status_t getProvisionRequest(Vector<uint8_t> &request,
                                         String8 &defaulUrl) = 0;

    virtual status_t provideProvisionResponse(Vector<uint8_t> const &response) = 0;

    virtual status_t getSecureStops(List<Vector<uint8_t> > &secureStops) = 0;

    virtual status_t releaseSecureStops(Vector<uint8_t> const &ssRelease) = 0;

    virtual status_t getPropertyString(String8 const &name, String8 &value) const = 0;
    virtual status_t getPropertyByteArray(String8 const &name,
                                          Vector<uint8_t> &value) const = 0;
    virtual status_t setPropertyString(String8 const &name,
                                       String8 const &value ) const = 0;
    virtual status_t setPropertyByteArray(String8 const &name,
                                          Vector<uint8_t> const &value) const = 0;

    virtual status_t setCipherAlgorithm(Vector<uint8_t> const &sessionId,
                                        String8 const &algorithm) = 0;

    virtual status_t setMacAlgorithm(Vector<uint8_t> const &sessionId,
                                     String8 const &algorithm) = 0;

    virtual status_t encrypt(Vector<uint8_t> const &sessionId,
                             Vector<uint8_t> const &keyId,
                             Vector<uint8_t> const &input,
                             Vector<uint8_t> const &iv,
                             Vector<uint8_t> &output) = 0;

    virtual status_t decrypt(Vector<uint8_t> const &sessionId,
                             Vector<uint8_t> const &keyId,
                             Vector<uint8_t> const &input,
                             Vector<uint8_t> const &iv,
                             Vector<uint8_t> &output) = 0;

    virtual status_t sign(Vector<uint8_t> const &sessionId,
                          Vector<uint8_t> const &keyId,
                          Vector<uint8_t> const &message,
                          Vector<uint8_t> &signature) = 0;

    virtual status_t verify(Vector<uint8_t> const &sessionId,
                            Vector<uint8_t> const &keyId,
                            Vector<uint8_t> const &message,
                            Vector<uint8_t> const &signature,
                            bool &match) = 0;

    virtual status_t setListener(const sp<IDrmClient>& listener) = 0;

private:
    DISALLOW_EVIL_CONSTRUCTORS(IDrm);
};

struct BnDrm : public BnInterface<IDrm> {
    virtual status_t onTransact(
            uint32_t code, const Parcel &data, Parcel *reply,
            uint32_t flags = 0);
private:
    void readVector(const Parcel &data, Vector<uint8_t> &vector) const;
    void writeVector(Parcel *reply, Vector<uint8_t> const &vector) const;
};

}  // namespace android

#endif // ANDROID_IDRM_H_

