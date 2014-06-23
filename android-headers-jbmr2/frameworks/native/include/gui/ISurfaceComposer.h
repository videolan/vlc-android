/*
 * Copyright (C) 2006 The Android Open Source Project
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

#ifndef ANDROID_GUI_ISURFACE_COMPOSER_H
#define ANDROID_GUI_ISURFACE_COMPOSER_H

#include <stdint.h>
#include <sys/types.h>

#include <utils/RefBase.h>
#include <utils/Errors.h>

#include <binder/IInterface.h>

#include <ui/PixelFormat.h>

#include <gui/IGraphicBufferAlloc.h>
#include <gui/ISurfaceComposerClient.h>

namespace android {
// ----------------------------------------------------------------------------

class ComposerState;
class DisplayState;
class DisplayInfo;
class IDisplayEventConnection;
class IMemoryHeap;

/*
 * This class defines the Binder IPC interface for accessing various
 * SurfaceFlinger features.
 */
class ISurfaceComposer: public IInterface {
public:
    DECLARE_META_INTERFACE(SurfaceComposer);

    // flags for setTransactionState()
    enum {
        eSynchronous = 0x01,
        eAnimation   = 0x02,
    };

    enum {
        eDisplayIdMain = 0,
        eDisplayIdHdmi = 1
    };

    /* create connection with surface flinger, requires
     * ACCESS_SURFACE_FLINGER permission
     */
    virtual sp<ISurfaceComposerClient> createConnection() = 0;

    /* create a graphic buffer allocator
     */
    virtual sp<IGraphicBufferAlloc> createGraphicBufferAlloc() = 0;

    /* return an IDisplayEventConnection */
    virtual sp<IDisplayEventConnection> createDisplayEventConnection() = 0;

    /* create a display
     * requires ACCESS_SURFACE_FLINGER permission.
     */
    virtual sp<IBinder> createDisplay(const String8& displayName,
            bool secure) = 0;

    /* get the token for the existing default displays. possible values
     * for id are eDisplayIdMain and eDisplayIdHdmi.
     */
    virtual sp<IBinder> getBuiltInDisplay(int32_t id) = 0;

    /* open/close transactions. requires ACCESS_SURFACE_FLINGER permission */
    virtual void setTransactionState(const Vector<ComposerState>& state,
            const Vector<DisplayState>& displays, uint32_t flags) = 0;

    /* signal that we're done booting.
     * Requires ACCESS_SURFACE_FLINGER permission
     */
    virtual void bootFinished() = 0;

    /* verify that an IGraphicBufferProducer was created by SurfaceFlinger.
     */
    virtual bool authenticateSurfaceTexture(
            const sp<IGraphicBufferProducer>& surface) const = 0;

    /* triggers screen off and waits for it to complete
     * requires ACCESS_SURFACE_FLINGER permission.
     */
    virtual void blank(const sp<IBinder>& display) = 0;

    /* triggers screen on and waits for it to complete
     * requires ACCESS_SURFACE_FLINGER permission.
     */
    virtual void unblank(const sp<IBinder>& display) = 0;

    /* returns information about a display
     * intended to be used to get information about built-in displays */
    virtual status_t getDisplayInfo(const sp<IBinder>& display, DisplayInfo* info) = 0;

    /* Capture the specified screen. requires READ_FRAME_BUFFER permission
     * This function will fail if there is a secure window on screen.
     */
    virtual status_t captureScreen(const sp<IBinder>& display,
            const sp<IGraphicBufferProducer>& producer,
            uint32_t reqWidth, uint32_t reqHeight,
            uint32_t minLayerZ, uint32_t maxLayerZ,
            bool isCpuConsumer) = 0;
};

// ----------------------------------------------------------------------------

class BnSurfaceComposer: public BnInterface<ISurfaceComposer> {
public:
    enum {
        // Note: BOOT_FINISHED must remain this value, it is called from
        // Java by ActivityManagerService.
        BOOT_FINISHED = IBinder::FIRST_CALL_TRANSACTION,
        CREATE_CONNECTION,
        CREATE_GRAPHIC_BUFFER_ALLOC,
        CREATE_DISPLAY_EVENT_CONNECTION,
        CREATE_DISPLAY,
        GET_BUILT_IN_DISPLAY,
        SET_TRANSACTION_STATE,
        AUTHENTICATE_SURFACE,
        BLANK,
        UNBLANK,
        GET_DISPLAY_INFO,
        CONNECT_DISPLAY,
        CAPTURE_SCREEN,
    };

    virtual status_t onTransact(uint32_t code, const Parcel& data,
            Parcel* reply, uint32_t flags = 0);
};

// ----------------------------------------------------------------------------

}; // namespace android

#endif // ANDROID_GUI_ISURFACE_COMPOSER_H
