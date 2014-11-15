/*
 * Copyright (C) 2010 The Android Open Source Project
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

#ifndef UTILS_LOOPER_H
#define UTILS_LOOPER_H

#include <utils/threads.h>
#include <utils/RefBase.h>
#include <utils/KeyedVector.h>
#include <utils/Timers.h>

#include <android/looper.h>

#include <sys/epoll.h>

/*
 * Declare a concrete type for the NDK's looper forward declaration.
 */
struct ALooper {
};

namespace android {

/**
 * A message that can be posted to a Looper.
 */
struct Message {
    Message() : what(0) { }
    Message(int what) : what(what) { }

    /* The message type. (interpretation is left up to the handler) */
    int what;
};


/**
 * Interface for a Looper message handler.
 *
 * The Looper holds a strong reference to the message handler whenever it has
 * a message to deliver to it.  Make sure to call Looper::removeMessages
 * to remove any pending messages destined for the handler so that the handler
 * can be destroyed.
 */
class MessageHandler : public virtual RefBase {
protected:
    virtual ~MessageHandler() { }

public:
    /**
     * Handles a message.
     */
    virtual void handleMessage(const Message& message) = 0;
};


/**
 * A simple proxy that holds a weak reference to a message handler.
 */
class WeakMessageHandler : public MessageHandler {
protected:
    virtual ~WeakMessageHandler();

public:
    WeakMessageHandler(const wp<MessageHandler>& handler);
    virtual void handleMessage(const Message& message);

private:
    wp<MessageHandler> mHandler;
};


/**
 * A looper callback.
 */
class LooperCallback : public virtual RefBase {
protected:
    virtual ~LooperCallback() { }

public:
    /**
     * Handles a poll event for the given file descriptor.
     * It is given the file descriptor it is associated with,
     * a bitmask of the poll events that were triggered (typically ALOOPER_EVENT_INPUT),
     * and the data pointer that was originally supplied.
     *
     * Implementations should return 1 to continue receiving callbacks, or 0
     * to have this file descriptor and callback unregistered from the looper.
     */
    virtual int handleEvent(int fd, int events, void* data) = 0;
};


/**
 * Wraps a ALooper_callbackFunc function pointer.
 */
class SimpleLooperCallback : public LooperCallback {
protected:
    virtual ~SimpleLooperCallback();

public:
    SimpleLooperCallback(ALooper_callbackFunc callback);
    virtual int handleEvent(int fd, int events, void* data);

private:
    ALooper_callbackFunc mCallback;
};


/**
 * A polling loop that supports monitoring file descriptor events, optionally
 * using callbacks.  The implementation uses epoll() internally.
 *
 * A looper can be associated with a thread although there is no requirement that it must be.
 */
class Looper : public ALooper, public RefBase {
protected:
    virtual ~Looper();

public:
    /**
     * Creates a looper.
     *
     * If allowNonCallbaks is true, the looper will allow file descriptors to be
     * registered without associated callbacks.  This assumes that the caller of
     * pollOnce() is prepared to handle callback-less events itself.
     */
    Looper(bool allowNonCallbacks);

    /**
     * Returns whether this looper instance allows the registration of file descriptors
     * using identifiers instead of callbacks.
     */
    bool getAllowNonCallbacks() const;

    /**
     * Waits for events to be available, with optional timeout in milliseconds.
     * Invokes callbacks for all file descriptors on which an event occurred.
     *
     * If the timeout is zero, returns immediately without blocking.
     * If the timeout is negative, waits indefinitely until an event appears.
     *
     * Returns ALOOPER_POLL_WAKE if the poll was awoken using wake() before
     * the timeout expired and no callbacks were invoked and no other file
     * descriptors were ready.
     *
     * Returns ALOOPER_POLL_CALLBACK if one or more callbacks were invoked.
     *
     * Returns ALOOPER_POLL_TIMEOUT if there was no data before the given
     * timeout expired.
     *
     * Returns ALOOPER_POLL_ERROR if an error occurred.
     *
     * Returns a value >= 0 containing an identifier if its file descriptor has data
     * and it has no callback function (requiring the caller here to handle it).
     * In this (and only this) case outFd, outEvents and outData will contain the poll
     * events and data associated with the fd, otherwise they will be set to NULL.
     *
     * This method does not return until it has finished invoking the appropriate callbacks
     * for all file descriptors that were signalled.
     */
    int pollOnce(int timeoutMillis, int* outFd, int* outEvents, void** outData);
    inline int pollOnce(int timeoutMillis) {
        return pollOnce(timeoutMillis, NULL, NULL, NULL);
    }

    /**
     * Like pollOnce(), but performs all pending callbacks until all
     * data has been consumed or a file descriptor is available with no callback.
     * This function will never return ALOOPER_POLL_CALLBACK.
     */
    int pollAll(int timeoutMillis, int* outFd, int* outEvents, void** outData);
    inline int pollAll(int timeoutMillis) {
        return pollAll(timeoutMillis, NULL, NULL, NULL);
    }

    /**
     * Wakes the poll asynchronously.
     *
     * This method can be called on any thread.
     * This method returns immediately.
     */
    void wake();

    /**
     * Adds a new file descriptor to be polled by the looper.
     * If the same file descriptor was previously added, it is replaced.
     *
     * "fd" is the file descriptor to be added.
     * "ident" is an identifier for this event, which is returned from pollOnce().
     * The identifier must be >= 0, or ALOOPER_POLL_CALLBACK if providing a non-NULL callback.
     * "events" are the poll events to wake up on.  Typically this is ALOOPER_EVENT_INPUT.
     * "callback" is the function to call when there is an event on the file descriptor.
     * "data" is a private data pointer to supply to the callback.
     *
     * There are two main uses of this function:
     *
     * (1) If "callback" is non-NULL, then this function will be called when there is
     * data on the file descriptor.  It should execute any events it has pending,
     * appropriately reading from the file descriptor.  The 'ident' is ignored in this case.
     *
     * (2) If "callback" is NULL, the 'ident' will be returned by ALooper_pollOnce
     * when its file descriptor has data available, requiring the caller to take
     * care of processing it.
     *
     * Returns 1 if the file descriptor was added, 0 if the arguments were invalid.
     *
     * This method can be called on any thread.
     * This method may block briefly if it needs to wake the poll.
     *
     * The callback may either be specified as a bare function pointer or as a smart
     * pointer callback object.  The smart pointer should be preferred because it is
     * easier to avoid races when the callback is removed from a different thread.
     * See removeFd() for details.
     */
    int addFd(int fd, int ident, int events, ALooper_callbackFunc callback, void* data);
    int addFd(int fd, int ident, int events, const sp<LooperCallback>& callback, void* data);

    /**
     * Removes a previously added file descriptor from the looper.
     *
     * When this method returns, it is safe to close the file descriptor since the looper
     * will no longer have a reference to it.  However, it is possible for the callback to
     * already be running or for it to run one last time if the file descriptor was already
     * signalled.  Calling code is responsible for ensuring that this case is safely handled.
     * For example, if the callback takes care of removing itself during its own execution either
     * by returning 0 or by calling this method, then it can be guaranteed to not be invoked
     * again at any later time unless registered anew.
     *
     * A simple way to avoid this problem is to use the version of addFd() that takes
     * a sp<LooperCallback> instead of a bare function pointer.  The LooperCallback will
     * be released at the appropriate time by the Looper.
     *
     * Returns 1 if the file descriptor was removed, 0 if none was previously registered.
     *
     * This method can be called on any thread.
     * This method may block briefly if it needs to wake the poll.
     */
    int removeFd(int fd);

    /**
     * Enqueues a message to be processed by the specified handler.
     *
     * The handler must not be null.
     * This method can be called on any thread.
     */
    void sendMessage(const sp<MessageHandler>& handler, const Message& message);

    /**
     * Enqueues a message to be processed by the specified handler after all pending messages
     * after the specified delay.
     *
     * The time delay is specified in uptime nanoseconds.
     * The handler must not be null.
     * This method can be called on any thread.
     */
    void sendMessageDelayed(nsecs_t uptimeDelay, const sp<MessageHandler>& handler,
            const Message& message);

    /**
     * Enqueues a message to be processed by the specified handler after all pending messages
     * at the specified time.
     *
     * The time is specified in uptime nanoseconds.
     * The handler must not be null.
     * This method can be called on any thread.
     */
    void sendMessageAtTime(nsecs_t uptime, const sp<MessageHandler>& handler,
            const Message& message);

    /**
     * Removes all messages for the specified handler from the queue.
     *
     * The handler must not be null.
     * This method can be called on any thread.
     */
    void removeMessages(const sp<MessageHandler>& handler);

    /**
     * Removes all messages of a particular type for the specified handler from the queue.
     *
     * The handler must not be null.
     * This method can be called on any thread.
     */
    void removeMessages(const sp<MessageHandler>& handler, int what);

    /**
     * Return whether this looper's thread is currently idling -- that is, whether it
     * stopped waiting for more work to do.  Note that this is intrinsically racy, since
     * its state can change before you get the result back.
     */
    bool isIdling() const;

    /**
     * Prepares a looper associated with the calling thread, and returns it.
     * If the thread already has a looper, it is returned.  Otherwise, a new
     * one is created, associated with the thread, and returned.
     *
     * The opts may be ALOOPER_PREPARE_ALLOW_NON_CALLBACKS or 0.
     */
    static sp<Looper> prepare(int opts);

    /**
     * Sets the given looper to be associated with the calling thread.
     * If another looper is already associated with the thread, it is replaced.
     *
     * If "looper" is NULL, removes the currently associated looper.
     */
    static void setForThread(const sp<Looper>& looper);

    /**
     * Returns the looper associated with the calling thread, or NULL if
     * there is not one.
     */
    static sp<Looper> getForThread();

private:
    struct Request {
        int fd;
        int ident;
        sp<LooperCallback> callback;
        void* data;
    };

    struct Response {
        int events;
        Request request;
    };

    struct MessageEnvelope {
        MessageEnvelope() : uptime(0) { }

        MessageEnvelope(nsecs_t uptime, const sp<MessageHandler> handler,
                const Message& message) : uptime(uptime), handler(handler), message(message) {
        }

        nsecs_t uptime;
        sp<MessageHandler> handler;
        Message message;
    };

    const bool mAllowNonCallbacks; // immutable

    int mWakeReadPipeFd;  // immutable
    int mWakeWritePipeFd; // immutable
    Mutex mLock;

    Vector<MessageEnvelope> mMessageEnvelopes; // guarded by mLock
    bool mSendingMessage; // guarded by mLock

    // Whether we are currently waiting for work.  Not protected by a lock,
    // any use of it is racy anyway.
    volatile bool mIdling;

    int mEpollFd; // immutable

    // Locked list of file descriptor monitoring requests.
    KeyedVector<int, Request> mRequests;  // guarded by mLock

    // This state is only used privately by pollOnce and does not require a lock since
    // it runs on a single thread.
    Vector<Response> mResponses;
    size_t mResponseIndex;
    nsecs_t mNextMessageUptime; // set to LLONG_MAX when none

    int pollInner(int timeoutMillis);
    void awoken();
    void pushResponse(int events, const Request& request);

    static void initTLSKey();
    static void threadDestructor(void *st);
};

} // namespace android

#endif // UTILS_LOOPER_H
