#ifndef _FRAMEWORK_CLIENT_H
#define _FRAMEWORK_CLIENT_H

#include "../../../frameworks/base/include/utils/List.h"

#include <pthread.h>

class FrameworkClient {
    int             mSocket;
    pthread_mutex_t mWriteMutex;

public:
    FrameworkClient(int sock);
    virtual ~FrameworkClient() {}

    int sendMsg(const char *msg);
    int sendMsg(const char *msg, const char *data);
};

typedef android::List<FrameworkClient *> FrameworkClientCollection;
#endif
