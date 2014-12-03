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

#ifndef _CHARACTER_ENCODING_DETECTOR_H
#define _CHARACTER_ENCODING_DETECTOR_H

#include <media/mediascanner.h>

#include "StringArray.h"

#include "unicode/ucnv.h"
#include "unicode/ucsdet.h"
#include "unicode/ustring.h"

namespace android {

class CharacterEncodingDetector {

    public:
    CharacterEncodingDetector();
        ~CharacterEncodingDetector();

        void addTag(const char *name, const char *value);
        size_t size();

        void detectAndConvert();
        status_t getTag(int index, const char **name, const char**value);

    private:
        const UCharsetMatch *getPreferred(
                const char *input, size_t len,
                const UCharsetMatch** ucma, size_t matches,
                bool *goodmatch, int *highestmatch);

        bool isFrequent(const uint16_t *values, uint32_t c);

        // cached name and value strings, for native encoding support.
        // TODO: replace these with byte blob arrays that don't require the data to be
        // singlenullbyte-terminated
        StringArray     mNames;
        StringArray     mValues;

        UConverter*     mUtf8Conv;
};



};  // namespace android

#endif
