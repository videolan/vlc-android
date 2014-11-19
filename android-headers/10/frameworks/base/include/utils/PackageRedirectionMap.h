/*
 * Copyright (C) 2005 The Android Open Source Project
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

#ifndef ANDROID_PACKAGEREDIRECTIONMAP_H
#define ANDROID_PACKAGEREDIRECTIONMAP_H

#include <binder/Parcel.h>

// ---------------------------------------------------------------------------

namespace android {

class PackageRedirectionMap
{
public:
    PackageRedirectionMap();
    ~PackageRedirectionMap();

    bool addRedirection(uint32_t fromIdent, uint32_t toIdent);
    uint32_t lookupRedirection(uint32_t fromIdent);

    // If there are no redirections present in this map, this method will
    // return -1.
    int getPackage();

    // Usage of the following methods is intended to be used only by the JNI
    // methods for the purpose of parceling.
    size_t getNumberOfTypes();
    size_t getNumberOfUsedTypes();

    size_t getNumberOfEntries(int type);
    size_t getNumberOfUsedEntries(int type);

    // Similar to lookupRedirection, but with no sanity checking.
    uint32_t getEntry(int type, int entry);

private:
    int mPackage;

    /*
     * Sparse array organized into two layers: first by type, then by entry.
     * The result of each lookup will be a qualified resource ID in the theme
     * package scope.
     *
     * Underneath each layer is a SharedBuffer which
     * indicates the array size.
     */
    uint32_t** mEntriesByType;
};

}  // namespace android

// ---------------------------------------------------------------------------

#endif // ANDROID_PACKAGEREDIRECTIONMAP_H
