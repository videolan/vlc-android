/*
 * Copyright 2014, The Android Open Source Project
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

#ifndef MEDIA_CODEC_INFO_H_

#define MEDIA_CODEC_INFO_H_

#include <binder/Parcel.h>
#include <media/stagefright/foundation/ABase.h>
#include <media/stagefright/foundation/AString.h>

#include <sys/types.h>
#include <utils/Errors.h>
#include <utils/KeyedVector.h>
#include <utils/RefBase.h>
#include <utils/Vector.h>
#include <utils/StrongPointer.h>

namespace android {

struct AMessage;
struct Parcel;
struct CodecCapabilities;

struct MediaCodecInfo : public RefBase {
    struct ProfileLevel {
        uint32_t mProfile;
        uint32_t mLevel;
    };

    struct Capabilities : public RefBase {
        void getSupportedProfileLevels(Vector<ProfileLevel> *profileLevels) const;
        void getSupportedColorFormats(Vector<uint32_t> *colorFormats) const;
        uint32_t getFlags() const;
        const sp<AMessage> getDetails() const;

    private:
        Vector<ProfileLevel> mProfileLevels;
        Vector<uint32_t> mColorFormats;
        uint32_t mFlags;
        sp<AMessage> mDetails;

        Capabilities();

        // read object from parcel even if object creation fails
        static sp<Capabilities> FromParcel(const Parcel &parcel);
        status_t writeToParcel(Parcel *parcel) const;

        DISALLOW_EVIL_CONSTRUCTORS(Capabilities);

        friend class MediaCodecInfo;
    };

    bool isEncoder() const;
    bool hasQuirk(const char *name) const;
    void getSupportedMimes(Vector<AString> *mimes) const;
    const sp<Capabilities> getCapabilitiesFor(const char *mime) const;
    const char *getCodecName() const;

    /**
     * Serialization over Binder
     */
    static sp<MediaCodecInfo> FromParcel(const Parcel &parcel);
    status_t writeToParcel(Parcel *parcel) const;

private:
    // variable set only in constructor - these are accessed by MediaCodecList
    // to avoid duplication of same variables
    AString mName;
    bool mIsEncoder;
    bool mHasSoleMime; // was initialized with mime

    Vector<AString> mQuirks;
    KeyedVector<AString, sp<Capabilities> > mCaps;

    sp<Capabilities> mCurrentCaps; // currently initalized capabilities

    ssize_t getCapabilityIndex(const char *mime) const;

    /* Methods used by MediaCodecList to construct the info
     * object from XML.
     *
     * After info object is created:
     * - additional quirks can be added
     * - additional mimes can be added
     *   - OMX codec capabilities can be set for the current mime-type
     *   - a capability detail can be set for the current mime-type
     *   - a feature can be set for the current mime-type
     *   - info object can be completed when parsing of a mime-type is done
     */
    MediaCodecInfo(AString name, bool encoder, const char *mime);
    void addQuirk(const char *name);
    status_t addMime(const char *mime);
    status_t initializeCapabilities(const CodecCapabilities &caps);
    void addDetail(const AString &key, const AString &value);
    void addFeature(const AString &key, int32_t value);
    void addFeature(const AString &key, const char *value);
    void removeMime(const char *mime);
    void complete();

    DISALLOW_EVIL_CONSTRUCTORS(MediaCodecInfo);

    friend class MediaCodecList;
};

}  // namespace android

#endif  // MEDIA_CODEC_INFO_H_


