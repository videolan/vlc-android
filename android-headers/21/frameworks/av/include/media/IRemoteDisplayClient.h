/*
 * Copyright (C) 2012 The Android Open Source Project
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

#ifndef ANDROID_IREMOTEDISPLAYCLIENT_H
#define ANDROID_IREMOTEDISPLAYCLIENT_H

#include <stdint.h>
#include <sys/types.h>

#include <utils/RefBase.h>
#include <binder/IInterface.h>
#include <binder/Parcel.h>

namespace android {

class IGraphicBufferProducer;

class IRemoteDisplayClient : public IInterface
{
public:
    DECLARE_META_INTERFACE(RemoteDisplayClient);

    enum {
        // Flag: The remote display is using a secure transport protocol such as HDCP.
        kDisplayFlagSecure = 1 << 0,
    };

    enum {
        // Error: An unknown / generic error occurred.
        kDisplayErrorUnknown = 1,
        // Error: The connection was dropped unexpectedly.
        kDisplayErrorConnectionDropped = 2,
    };

    // Indicates that the remote display has been connected successfully.
    // Provides a surface texture that the client should use to stream buffers to
    // the remote display.
    virtual void onDisplayConnected(const sp<IGraphicBufferProducer>& bufferProducer,
            uint32_t width, uint32_t height, uint32_t flags, uint32_t session) = 0; // one-way

    // Indicates that the remote display has been disconnected normally.
    // This method should only be called once the client has called 'dispose()'
    // on the IRemoteDisplay.
    // It is currently an error for the display to disconnect for any other reason.
    virtual void onDisplayDisconnected() = 0; // one-way

    // Indicates that a connection could not be established to the remote display
    // or an unrecoverable error occurred and the connection was severed.
    virtual void onDisplayError(int32_t error) = 0; // one-way
};


// ----------------------------------------------------------------------------

class BnRemoteDisplayClient : public BnInterface<IRemoteDisplayClient>
{
public:
    virtual status_t    onTransact( uint32_t code,
                                    const Parcel& data,
                                    Parcel* reply,
                                    uint32_t flags = 0);
};

}; // namespace android

#endif // ANDROID_IREMOTEDISPLAYCLIENT_H
