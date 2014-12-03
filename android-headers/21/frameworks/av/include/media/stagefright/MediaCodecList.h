/*
 * Copyright 2012, The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#ifndef MEDIA_CODEC_LIST_H_

#define MEDIA_CODEC_LIST_H_

#include <media/stagefright/foundation/ABase.h>
#include <media/stagefright/foundation/AString.h>
#include <media/IMediaCodecList.h>
#include <media/IOMX.h>
#include <media/MediaCodecInfo.h>

#include <sys/types.h>
#include <utils/Errors.h>
#include <utils/KeyedVector.h>
#include <utils/Vector.h>
#include <utils/StrongPointer.h>

namespace android {

struct AMessage;

struct MediaCodecList : public BnMediaCodecList {
    static sp<IMediaCodecList> getInstance();

    virtual ssize_t findCodecByType(
            const char *type, bool encoder, size_t startIndex = 0) const;

    virtual ssize_t findCodecByName(const char *name) const;

    virtual size_t countCodecs() const;

    virtual sp<MediaCodecInfo> getCodecInfo(size_t index) const {
        return mCodecInfos.itemAt(index);
    }

    // to be used by MediaPlayerService alone
    static sp<IMediaCodecList> getLocalInstance();

private:
    enum Section {
        SECTION_TOPLEVEL,
        SECTION_DECODERS,
        SECTION_DECODER,
        SECTION_DECODER_TYPE,
        SECTION_ENCODERS,
        SECTION_ENCODER,
        SECTION_ENCODER_TYPE,
        SECTION_INCLUDE,
    };

    static sp<IMediaCodecList> sCodecList;
    static sp<IMediaCodecList> sRemoteList;

    status_t mInitCheck;
    Section mCurrentSection;
    Vector<Section> mPastSections;
    int32_t mDepth;
    AString mHrefBase;

    Vector<sp<MediaCodecInfo> > mCodecInfos;
    sp<MediaCodecInfo> mCurrentInfo;
    sp<IOMX> mOMX;

    MediaCodecList();
    ~MediaCodecList();

    status_t initCheck() const;
    void parseXMLFile(const char *path);
    void parseTopLevelXMLFile(const char *path);

    static void StartElementHandlerWrapper(
            void *me, const char *name, const char **attrs);

    static void EndElementHandlerWrapper(void *me, const char *name);

    void startElementHandler(const char *name, const char **attrs);
    void endElementHandler(const char *name);

    status_t includeXMLFile(const char **attrs);
    status_t addMediaCodecFromAttributes(bool encoder, const char **attrs);
    void addMediaCodec(bool encoder, const char *name, const char *type = NULL);

    status_t addQuirk(const char **attrs);
    status_t addTypeFromAttributes(const char **attrs);
    status_t addLimit(const char **attrs);
    status_t addFeature(const char **attrs);
    void addType(const char *name);

    status_t initializeCapabilities(const char *type);

    DISALLOW_EVIL_CONSTRUCTORS(MediaCodecList);
};

}  // namespace android

#endif  // MEDIA_CODEC_LIST_H_

