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

// Non-blocking event logger intended for safe communication between processes via shared memory

#ifndef ANDROID_MEDIA_NBLOG_H
#define ANDROID_MEDIA_NBLOG_H

#include <binder/IMemory.h>
#include <utils/Mutex.h>
#include <media/nbaio/roundup.h>

namespace android {

class NBLog {

public:

class Writer;
class Reader;

private:

enum Event {
    EVENT_RESERVED,
    EVENT_STRING,               // ASCII string, not NUL-terminated
    EVENT_TIMESTAMP,            // clock_gettime(CLOCK_MONOTONIC)
};

// ---------------------------------------------------------------------------

// representation of a single log entry in private memory
struct Entry {
    Entry(Event event, const void *data, size_t length)
        : mEvent(event), mLength(length), mData(data) { }
    /*virtual*/ ~Entry() { }

    int     readAt(size_t offset) const;

private:
    friend class Writer;
    Event       mEvent;     // event type
    size_t      mLength;    // length of additional data, 0 <= mLength <= 255
    const void *mData;      // event type-specific data
};

// representation of a single log entry in shared memory
//  byte[0]             mEvent
//  byte[1]             mLength
//  byte[2]             mData[0]
//  ...
//  byte[2+i]           mData[i]
//  ...
//  byte[2+mLength-1]   mData[mLength-1]
//  byte[2+mLength]     duplicate copy of mLength to permit reverse scan
//  byte[3+mLength]     start of next log entry

// located in shared memory
struct Shared {
    Shared() : mRear(0) { }
    /*virtual*/ ~Shared() { }

    volatile int32_t mRear;     // index one byte past the end of most recent Entry
    char    mBuffer[0];         // circular buffer for entries
};

public:

// ---------------------------------------------------------------------------

// FIXME Timeline was intended to wrap Writer and Reader, but isn't actually used yet.
// For now it is just a namespace for sharedSize().
class Timeline : public RefBase {
public:
#if 0
    Timeline(size_t size, void *shared = NULL);
    virtual ~Timeline();
#endif

    // Input parameter 'size' is the desired size of the timeline in byte units.
    // Returns the size rounded up to a power-of-2, plus the constant size overhead for indices.
    static size_t sharedSize(size_t size);

#if 0
private:
    friend class    Writer;
    friend class    Reader;

    const size_t    mSize;      // circular buffer size in bytes, must be a power of 2
    bool            mOwn;       // whether I own the memory at mShared
    Shared* const   mShared;    // pointer to shared memory
#endif
};

// ---------------------------------------------------------------------------

// Writer is thread-safe with respect to Reader, but not with respect to multiple threads
// calling Writer methods.  If you need multi-thread safety for writing, use LockedWriter.
class Writer : public RefBase {
public:
    Writer();                   // dummy nop implementation without shared memory

    // Input parameter 'size' is the desired size of the timeline in byte units.
    // The size of the shared memory must be at least Timeline::sharedSize(size).
    Writer(size_t size, void *shared);
    Writer(size_t size, const sp<IMemory>& iMemory);

    virtual ~Writer() { }

    virtual void    log(const char *string);
    virtual void    logf(const char *fmt, ...) __attribute__ ((format (printf, 2, 3)));
    virtual void    logvf(const char *fmt, va_list ap);
    virtual void    logTimestamp();
    virtual void    logTimestamp(const struct timespec& ts);

    virtual bool    isEnabled() const;

    // return value for all of these is the previous isEnabled()
    virtual bool    setEnabled(bool enabled);   // but won't enable if no shared memory
            bool    enable()    { return setEnabled(true); }
            bool    disable()   { return setEnabled(false); }

    sp<IMemory>     getIMemory() const  { return mIMemory; }

private:
    void    log(Event event, const void *data, size_t length);
    void    log(const Entry *entry, bool trusted = false);

    const size_t    mSize;      // circular buffer size in bytes, must be a power of 2
    Shared* const   mShared;    // raw pointer to shared memory
    const sp<IMemory> mIMemory; // ref-counted version
    int32_t         mRear;      // my private copy of mShared->mRear
    bool            mEnabled;   // whether to actually log
};

// ---------------------------------------------------------------------------

// Similar to Writer, but safe for multiple threads to call concurrently
class LockedWriter : public Writer {
public:
    LockedWriter();
    LockedWriter(size_t size, void *shared);

    virtual void    log(const char *string);
    virtual void    logf(const char *fmt, ...) __attribute__ ((format (printf, 2, 3)));
    virtual void    logvf(const char *fmt, va_list ap);
    virtual void    logTimestamp();
    virtual void    logTimestamp(const struct timespec& ts);

    virtual bool    isEnabled() const;
    virtual bool    setEnabled(bool enabled);

private:
    mutable Mutex   mLock;
};

// ---------------------------------------------------------------------------

class Reader : public RefBase {
public:

    // Input parameter 'size' is the desired size of the timeline in byte units.
    // The size of the shared memory must be at least Timeline::sharedSize(size).
    Reader(size_t size, const void *shared);
    Reader(size_t size, const sp<IMemory>& iMemory);

    virtual ~Reader() { }

    void    dump(int fd, size_t indent = 0);
    bool    isIMemory(const sp<IMemory>& iMemory) const;

private:
    const size_t    mSize;      // circular buffer size in bytes, must be a power of 2
    const Shared* const mShared; // raw pointer to shared memory
    const sp<IMemory> mIMemory; // ref-counted version
    int32_t     mFront;         // index of oldest acknowledged Entry

    static const size_t kSquashTimestamp = 5; // squash this many or more adjacent timestamps
};

};  // class NBLog

}   // namespace android

#endif  // ANDROID_MEDIA_NBLOG_H
