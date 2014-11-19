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

#ifndef ANDROID_APP_OPS_MANAGER_H
#define ANDROID_APP_OPS_MANAGER_H

#include <binder/IAppOpsService.h>

#include <utils/threads.h>

// ---------------------------------------------------------------------------
namespace android {

class AppOpsManager
{
public:
    enum {
        MODE_ALLOWED = IAppOpsService::MODE_ALLOWED,
        MODE_IGNORED = IAppOpsService::MODE_IGNORED,
        MODE_ERRORED = IAppOpsService::MODE_ERRORED
    };

    enum {
        OP_NONE = -1,
        OP_COARSE_LOCATION = 0,
        OP_FINE_LOCATION = 1,
        OP_GPS = 2,
        OP_VIBRATE = 3,
        OP_READ_CONTACTS = 4,
        OP_WRITE_CONTACTS = 5,
        OP_READ_CALL_LOG = 6,
        OP_WRITE_CALL_LOG = 7,
        OP_READ_CALENDAR = 8,
        OP_WRITE_CALENDAR = 9,
        OP_WIFI_SCAN = 10,
        OP_POST_NOTIFICATION = 11,
        OP_NEIGHBORING_CELLS = 12,
        OP_CALL_PHONE = 13,
        OP_READ_SMS = 14,
        OP_WRITE_SMS = 15,
        OP_RECEIVE_SMS = 16,
        OP_RECEIVE_EMERGECY_SMS = 17,
        OP_RECEIVE_MMS = 18,
        OP_RECEIVE_WAP_PUSH = 19,
        OP_SEND_SMS = 20,
        OP_READ_ICC_SMS = 21,
        OP_WRITE_ICC_SMS = 22,
        OP_WRITE_SETTINGS = 23,
        OP_SYSTEM_ALERT_WINDOW = 24,
        OP_ACCESS_NOTIFICATIONS = 25,
        OP_CAMERA = 26,
        OP_RECORD_AUDIO = 27,
        OP_PLAY_AUDIO = 28
    };

    AppOpsManager();

    int32_t checkOp(int32_t op, int32_t uid, const String16& callingPackage);
    int32_t noteOp(int32_t op, int32_t uid, const String16& callingPackage);
    int32_t startOp(int32_t op, int32_t uid, const String16& callingPackage);
    void finishOp(int32_t op, int32_t uid, const String16& callingPackage);
    void startWatchingMode(int32_t op, const String16& packageName,
            const sp<IAppOpsCallback>& callback);
    void stopWatchingMode(const sp<IAppOpsCallback>& callback);

private:
    Mutex mLock;
    sp<IAppOpsService> mService;

    sp<IAppOpsService> getService();
};


}; // namespace android
// ---------------------------------------------------------------------------
#endif // ANDROID_APP_OPS_MANAGER_H
