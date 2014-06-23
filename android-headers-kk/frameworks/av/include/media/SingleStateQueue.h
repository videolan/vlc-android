/*
 * Copyright (C) 2012 The Android Open Source Project
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

#ifndef SINGLE_STATE_QUEUE_H
#define SINGLE_STATE_QUEUE_H

// Non-blocking single element state queue, or
// Non-blocking single-reader / single-writer multi-word atomic load / store

#include <stdint.h>

namespace android {

template<typename T> class SingleStateQueue {

public:

    class Mutator;
    class Observer;

    struct Shared {
        // needs to be part of a union so don't define constructor or destructor

        friend class Mutator;
        friend class Observer;

private:
        void                init() { mAck = 0; mSequence = 0; }

        volatile int32_t    mAck;
#if 0
        int                 mPad[7];
        // cache line boundary
#endif
        volatile int32_t    mSequence;
        T                   mValue;
    };

    class Mutator {
    public:
        Mutator(Shared *shared);
        /*virtual*/ ~Mutator() { }

        // push new value onto state queue, overwriting previous value;
        // returns a sequence number which can be used with ack()
        int32_t push(const T& value);

        // return true if most recent push has been observed
        bool ack();

        // return true if a push with specified sequence number or later has been observed
        bool ack(int32_t sequence);

    private:
        int32_t     mSequence;
        Shared * const mShared;
    };

    class Observer {
    public:
        Observer(Shared *shared);
        /*virtual*/ ~Observer() { }

        // return true if value has changed
        bool poll(T& value);

    private:
        int32_t     mSequence;
        int         mSeed;  // for PRNG
        Shared * const mShared;
    };

#if 0
    SingleStateQueue(void /*Shared*/ *shared);
    /*virtual*/ ~SingleStateQueue() { }

    static size_t size() { return sizeof(Shared); }
#endif

};

}   // namespace android

#endif  // SINGLE_STATE_QUEUE_H
