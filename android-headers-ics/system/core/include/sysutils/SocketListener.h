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
#ifndef _SOCKETLISTENER_H
#define _SOCKETLISTENER_H

#include <pthread.h>

#include <sysutils/SocketClient.h>

class SocketListener {
    int                     mSock;
    const char              *mSocketName;
    SocketClientCollection  *mClients;
    pthread_mutex_t         mClientsLock;
    bool                    mListen;
    int                     mCtrlPipe[2];
    pthread_t               mThread;

public:
    SocketListener(const char *socketName, bool listen);
    SocketListener(int socketFd, bool listen);

    virtual ~SocketListener();
    int startListener();
    int stopListener();

    void sendBroadcast(int code, const char *msg, bool addErrno);
    void sendBroadcast(const char *msg);

protected:
    virtual bool onDataAvailable(SocketClient *c) = 0;

private:
    static void *threadStart(void *obj);
    void runListener();
};
#endif
