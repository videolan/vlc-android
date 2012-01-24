/*
 * Copyright (C) Texas Instruments - http://www.ti.com/
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 */

#ifndef OVERLAY_RENDERER_H_
#define OVERLAY_RENDERER_H_

#include <utils/Log.h>
#include <OMX_Component.h>
#include <overlay_common.h>
#include <media/stagefright/MediaDebug.h>

#include <binder/Parcel.h>
#include <binder/IMemory.h>
#include <binder/IInterface.h>
#include <binder/MemoryBase.h>
#include <binder/MemoryHeapBase.h>

#include <ui/Overlay.h>
#include <surfaceflinger/Surface.h>
#include <surfaceflinger/ISurface.h>
#include <surfaceflinger/ISurfaceComposer.h>
#include <surfaceflinger/SurfaceComposerClient.h>

namespace android {

class IOverlayRenderer : public IInterface {
public:
    DECLARE_META_INTERFACE(OverlayRenderer);
    virtual int32_t createOverlayRenderer(uint32_t bufferCount,
                                int32_t displayWidth,
                                int32_t displayHeight,
                                uint32_t colorFormat,
                                int32_t decodedWidth,
                                int32_t decodedHeight,
                                int infoType,
                                void* info)=0;

    virtual void releaseMe()=0;
    virtual uint32_t getBufferCount()=0;
    virtual sp<IMemory> getBuffer(uint32_t index) = 0;
    virtual int32_t dequeueBuffer(uint32_t *index)=0;
    virtual int32_t queueBuffer(uint32_t index)=0;
    virtual int32_t setPosition(int32_t x, int32_t y)=0;
    virtual int32_t setSize(uint32_t w, uint32_t h)=0;
    virtual int32_t setOrientation(int32_t orientation, uint32_t flags)=0;
    virtual int32_t setCrop(uint32_t x, uint32_t y, uint32_t w, uint32_t h)=0;
};


class BnOverlayRenderer : public BnInterface<IOverlayRenderer> {
public:
    virtual status_t onTransact(uint32_t code, const Parcel &data, Parcel *reply, uint32_t flags = 0);
};


class OverlayRenderer : public BnOverlayRenderer{

public:

    OverlayRenderer();
    ~OverlayRenderer();
    int32_t createOverlayRenderer(uint32_t bufferCount,
                                int32_t displayWidth,
                                int32_t displayHeight,
                                uint32_t colorFormat,
                                int32_t decodedWidth,
                                int32_t decodedHeight,
                                int infoType,
                                void* info);
    void releaseMe();
    uint32_t getBufferCount() { return mBufferCount; }
    sp<IMemory> getBuffer(uint32_t index);
    int32_t dequeueBuffer(uint32_t *index);
    int32_t queueBuffer(uint32_t index);
    int32_t setPosition(int32_t x, int32_t y) ;
    int32_t setSize(uint32_t w, uint32_t h) ;
    int32_t setCrop(uint32_t x, uint32_t y, uint32_t w, uint32_t h);
    int32_t setOrientation(int32_t orientation, uint32_t flags);

private:

    int32_t createSurface();
    int32_t createBuffers();

    int32_t mInitCheck;
    int32_t mDisplayWidth, mDisplayHeight;
    int32_t mDecodedWidth, mDecodedHeight;
    uint32_t mColorFormat;
    uint32_t mBufferCount;
    unsigned int mInfoType;

    /* Surface details */
    sp<Overlay> mOverlay;
    sp<SurfaceComposerClient> mSurfaceClient;
    sp<SurfaceControl> mSurfaceControl;
    sp<ISurface> mISurface;

    sp<MemoryHeapBase> mVideoHeaps[NUM_OVERLAY_BUFFERS_MAX];
    Vector< sp<IMemory> > mOverlayAddresses;

    /* copy of previously set window values */
    int32_t win_x;
    int32_t win_y;

};

}

#endif

