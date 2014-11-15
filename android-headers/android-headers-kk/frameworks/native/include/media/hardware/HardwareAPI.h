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

#ifndef HARDWARE_API_H_

#define HARDWARE_API_H_

#include <media/hardware/OMXPluginBase.h>
#include <media/hardware/MetadataBufferType.h>
#include <system/window.h>
#include <utils/RefBase.h>

#include <OMX_Component.h>

namespace android {

// A pointer to this struct is passed to the OMX_SetParameter when the extension
// index for the 'OMX.google.android.index.enableAndroidNativeBuffers' extension
// is given.
//
// When Android native buffer use is disabled for a port (the default state),
// the OMX node should operate as normal, and expect UseBuffer calls to set its
// buffers.  This is the mode that will be used when CPU access to the buffer is
// required.
//
// When Android native buffer use has been enabled for a given port, the video
// color format for the port is to be interpreted as an Android pixel format
// rather than an OMX color format.  Enabling Android native buffers may also
// change how the component receives the native buffers.  If store-metadata-mode
// is enabled on the port, the component will receive the buffers as specified
// in the section below. Otherwise, unless the node supports the
// 'OMX.google.android.index.useAndroidNativeBuffer2' extension, it should
// expect to receive UseAndroidNativeBuffer calls (via OMX_SetParameter) rather
// than UseBuffer calls for that port.
struct EnableAndroidNativeBuffersParams {
    OMX_U32 nSize;
    OMX_VERSIONTYPE nVersion;
    OMX_U32 nPortIndex;
    OMX_BOOL enable;
};

// A pointer to this struct is passed to OMX_SetParameter() when the extension
// index "OMX.google.android.index.storeMetaDataInBuffers"
// is given.
//
// When meta data is stored in the video buffers passed between OMX clients
// and OMX components, interpretation of the buffer data is up to the
// buffer receiver, and the data may or may not be the actual video data, but
// some information helpful for the receiver to locate the actual data.
// The buffer receiver thus needs to know how to interpret what is stored
// in these buffers, with mechanisms pre-determined externally. How to
// interpret the meta data is outside of the scope of this method.
//
// Currently, this is specifically used to pass meta data from video source
// (camera component, for instance) to video encoder to avoid memcpying of
// input video frame data. To do this, bStoreMetaData is set to OMX_TRUE.
// If bStoreMetaData is set to false, real YUV frame data will be stored
// in the buffers. In addition, if no OMX_SetParameter() call is made
// with the corresponding extension index, real YUV data is stored
// in the buffers.
//
// For video decoder output port, the metadata buffer layout is defined below.
//
// Metadata buffers are registered with the component using UseBuffer calls.
struct StoreMetaDataInBuffersParams {
    OMX_U32 nSize;
    OMX_VERSIONTYPE nVersion;
    OMX_U32 nPortIndex;
    OMX_BOOL bStoreMetaData;
};

// Meta data buffer layout used to transport output frames to the decoder for
// dynamic buffer handling.
struct VideoDecoderOutputMetaData {
  MetadataBufferType eType;
  buffer_handle_t pHandle;
};

// A pointer to this struct is passed to OMX_SetParameter() when the extension
// index "OMX.google.android.index.prepareForAdaptivePlayback" is given.
//
// This method is used to signal a video decoder, that the user has requested
// seamless resolution change support (if bEnable is set to OMX_TRUE).
// nMaxFrameWidth and nMaxFrameHeight are the dimensions of the largest
// anticipated frames in the video.  If bEnable is OMX_FALSE, no resolution
// change is expected, and the nMaxFrameWidth/Height fields are unused.
//
// If the decoder supports dynamic output buffers, it may ignore this
// request.  Otherwise, it shall request resources in such a way so that it
// avoids full port-reconfiguration (due to output port-definition change)
// during resolution changes.
//
// DO NOT USE THIS STRUCTURE AS IT WILL BE REMOVED.  INSTEAD, IMPLEMENT
// METADATA SUPPORT FOR VIDEO DECODERS.
struct PrepareForAdaptivePlaybackParams {
    OMX_U32 nSize;
    OMX_VERSIONTYPE nVersion;
    OMX_U32 nPortIndex;
    OMX_BOOL bEnable;
    OMX_U32 nMaxFrameWidth;
    OMX_U32 nMaxFrameHeight;
};

// A pointer to this struct is passed to OMX_SetParameter when the extension
// index for the 'OMX.google.android.index.useAndroidNativeBuffer' extension is
// given.  This call will only be performed if a prior call was made with the
// 'OMX.google.android.index.enableAndroidNativeBuffers' extension index,
// enabling use of Android native buffers.
struct UseAndroidNativeBufferParams {
    OMX_U32 nSize;
    OMX_VERSIONTYPE nVersion;
    OMX_U32 nPortIndex;
    OMX_PTR pAppPrivate;
    OMX_BUFFERHEADERTYPE **bufferHeader;
    const sp<ANativeWindowBuffer>& nativeBuffer;
};

// A pointer to this struct is passed to OMX_GetParameter when the extension
// index for the 'OMX.google.android.index.getAndroidNativeBufferUsage'
// extension is given.  The usage bits returned from this query will be used to
// allocate the Gralloc buffers that get passed to the useAndroidNativeBuffer
// command.
struct GetAndroidNativeBufferUsageParams {
    OMX_U32 nSize;              // IN
    OMX_VERSIONTYPE nVersion;   // IN
    OMX_U32 nPortIndex;         // IN
    OMX_U32 nUsage;             // OUT
};

// An enum OMX_COLOR_FormatAndroidOpaque to indicate an opaque colorformat
// is declared in media/stagefright/openmax/OMX_IVCommon.h
// This will inform the encoder that the actual
// colorformat will be relayed by the GRalloc Buffers.
// OMX_COLOR_FormatAndroidOpaque  = 0x7F000001,

// A pointer to this struct is passed to OMX_SetParameter when the extension
// index for the 'OMX.google.android.index.prependSPSPPSToIDRFrames' extension
// is given.
// A successful result indicates that future IDR frames will be prefixed by
// SPS/PPS.
struct PrependSPSPPSToIDRFramesParams {
    OMX_U32 nSize;
    OMX_VERSIONTYPE nVersion;
    OMX_BOOL bEnable;
};

}  // namespace android

extern android::OMXPluginBase *createOMXPlugin();

#endif  // HARDWARE_API_H_
