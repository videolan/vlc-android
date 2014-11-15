/*
 * Copyright (C) 2008 The Android Open Source Project
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
#ifndef _NETLINKEVENT_H
#define _NETLINKEVENT_H

#include <sysutils/NetlinkListener.h>

#define NL_PARAMS_MAX 32

class NetlinkEvent {
    int  mSeq;
    char *mPath;
    int  mAction;
    char *mSubsystem;
    char *mParams[NL_PARAMS_MAX];

public:
    const static int NlActionUnknown;
    const static int NlActionAdd;
    const static int NlActionRemove;
    const static int NlActionChange;
    const static int NlActionLinkDown;
    const static int NlActionLinkUp;

    NetlinkEvent();
    virtual ~NetlinkEvent();

    bool decode(char *buffer, int size, int format = NetlinkListener::NETLINK_FORMAT_ASCII);
    const char *findParam(const char *paramName);

    const char *getSubsystem() { return mSubsystem; }
    int getAction() { return mAction; }

    void dump();

 protected:
    bool parseBinaryNetlinkMessage(char *buffer, int size);
    bool parseAsciiNetlinkMessage(char *buffer, int size);
};

#endif
