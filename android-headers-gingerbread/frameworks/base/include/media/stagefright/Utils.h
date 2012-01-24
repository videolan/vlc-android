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

#ifndef UTILS_H_

#define UTILS_H_

#include <stdint.h>
#ifdef OMAP_ENHANCEMENT
#include <media/stagefright/openmax/OMX_Types.h>
#include <media/stagefright/openmax/OMX_Index.h>
#endif

namespace android {

#define FOURCC(c1, c2, c3, c4) \
    (c1 << 24 | c2 << 16 | c3 << 8 | c4)
#ifdef OMAP_ENHANCEMENT
#ifndef MAKEFOURCC_WMC
#define MAKEFOURCC_WMC(ch0, ch1, ch2, ch3) \
        ((OMX_U32)(OMX_U8)(ch0) | ((OMX_U32)(OMX_U8)(ch1) << 8) |   \
        ((OMX_U32)(OMX_U8)(ch2) << 16) | ((OMX_U32)(OMX_U8)(ch3) << 24 ))

#define mmioFOURCC_WMC(ch0, ch1, ch2, ch3)  MAKEFOURCC_WMC(ch0, ch1, ch2, ch3)
#endif

#define FOURCC_WMV3     mmioFOURCC_WMC('W','M','V','3')
#define FOURCC_WMV2     mmioFOURCC_WMC('W','M','V','2')
#define FOURCC_WMV1     mmioFOURCC_WMC('W','M','V','1')
#define FOURCC_WMVA     mmioFOURCC_WMC('W','M','V','A')
#define FOURCC_WVC1     mmioFOURCC_WMC('W','V','C','1')

#define VIDDEC_WMV_ELEMSTREAM                           0
#define VIDDEC_WMV_RCVSTREAM                            1

typedef enum VIDDEC_CUSTOM_PARAM_INDEX
{
    VideoDecodeCustomParamProcessMode = (OMX_IndexVendorStartUnused + 1),
    VideoDecodeCustomParamH264BitStreamFormat,
    VideoDecodeCustomParamWMVProfile,
    VideoDecodeCustomParamWMVFileType,
    VideoDecodeCustomParamParserEnabled,

} VIDDEC_CUSTOM_PARAM_INDEX;

typedef struct OMX_PARAM_WMVFILETYPE {
    OMX_U32 nSize;
    OMX_VERSIONTYPE nVersion;
    OMX_U32 nWmvFileType;
} OMX_PARAM_WMVFILETYPE;
#endif

uint16_t U16_AT(const uint8_t *ptr);
uint32_t U32_AT(const uint8_t *ptr);
uint64_t U64_AT(const uint8_t *ptr);

uint16_t U16LE_AT(const uint8_t *ptr);
uint32_t U32LE_AT(const uint8_t *ptr);
uint64_t U64LE_AT(const uint8_t *ptr);

uint64_t ntoh64(uint64_t x);
uint64_t hton64(uint64_t x);

}  // namespace android

#endif  // UTILS_H_
